# Neo-R2D2 ROS 2 How-To

## 1. 매핑 (SLAM)

### slam_toolbox (기본, posegraph 저장)
```bash
# 맵 생성하며 주행
ros2 launch r2d2_navigation navigation.launch.py enable_nav2:=false

# 맵 저장 (maps/<name>_posegraph.* 생성)
ros2 run r2d2_navigation save_map.sh my_map

# 저장된 맵으로 로컬라이제이션
ros2 launch r2d2_navigation navigation.launch.py posegraph:=~/maps/my_map_posegraph enable_nav2:=false
```

### hector_slam (pgm/yaml만 저장)
```bash
# 맵 생성
ros2 launch r2d2_bringup hector.launch.py

# 맵 저장 (SKIP_POSEGRAPH=1로 pgm/yaml만)
SKIP_POSEGRAPH=1 ros2 run r2d2_navigation save_map.sh hector_map

# 로컬라이제이션 (map_server + AMCL)
ros2 launch r2d2_navigation navigation.launch.py localization:=map_server map_yaml:=~/maps/hector_map.yaml enable_nav2:=false
```

---

## 2. 주행 (Navigation)

### Nav2 전체 스택 (RPP 컨트롤러 기본)
```bash
ros2 launch r2d2_navigation navigation.launch.py
# RViz에서 2D Goal Pose 클릭 → 자율 주행
```

### 컨트롤러 변경 (MPPI)
```bash
ros2 launch r2d2_navigation navigation.launch.py controller:=mppi
```

### 키보드 조작 (teleop)
```bash
ros2 run teleop_twist_keyboard teleop_twist_keyboard --ros-args -r /cmd_vel:=/cmd_vel_nav
```

### YOLO 사람 추적 (head follow + 제자리 회전)

`r2d2_app`의 tracker는 `/yolo/tracking`에서 사람 하나를 선택해 track ID를
유지하고, `/r2d2/head_shift`로 head를 따라 움직입니다. Head가 한계에 닿으면
`/cmd_vel`에 angular.z만 보내 베이스를 제자리 회전시켜 head를 중앙 범위로
되돌립니다.

```bash
# Terminal 1: MCU/motor는 순수 회전을 지원하는 tank mapping으로 시작
ros2 launch r2d2_bringup r2d2_bringup.launch.py mapping_mode:=tank

# Terminal 2: YOLO detection/tracking (기본 카메라 /dev/video0)
ros2 launch r2d2_vision vision.launch.py

# Terminal 3: 추적 제어
ros2 launch r2d2_app person_tracking.launch.py
```

실제 구동 전에 `mock:=true`로 motor 명령을 확인하고, 실차에서는 낮은
`base_rotation_speed`와 여유 공간으로 시작합니다. `image_width`는 카메라
프레임 너비에 맞추고, 필요하면 `head_limit_deg`, `head_recenter_deg`,
`head_deadband_ratio`, `base_rotation_sign`을 조정합니다.

**추적 중에는 Nav2 controller, teleop 등 다른 `/cmd_vel` 발행자를 함께
구동하지 마세요.** Motor는 `/cmd_vel` 하나를 공유하며, 경쟁 발행은 의도하지
않은 이동을 만들 수 있습니다. YOLO tracking 토픽과 `/r2d2/set_head_angle`
서비스가 준비되지 않으면 tracker는 head 초기화 완료까지 베이스를 정지합니다.
대상이 timeout 동안 사라지면 정지 후 head 중앙 복귀를 요청합니다.
MCU 충전 인터록이 켜져도 tracker는 정지하며, 인터록 해제 뒤 head를 다시
중앙화한 다음 추적을 재개합니다.

---

## 3. Motor Benchmark & 검증 도구

### motion_bench (cmd_vel ↔ odom 정합성 검증)
```bash
# 기본 (1m 직진 → 180도 회전 → 1m 복귀)
ros2 run r2d2_motor motion_bench

# 파라미터 조정
ros2 run r2d2_motor motion_bench --distance 1.0 --angle-deg 180 --speed 0.10 --omega 0.30 --csv /tmp/bench.csv

# 결과 해석: odom 도달비 90~110% 면 캘리브레이션 정상
```

### check_odom (실시간 odom 모니터링)
```bash
# odom만
python3 src/r2d2_bringup/scripts/check_odom.py

# + 라이더 벽 구간 거리
python3 src/r2d2_bringup/scripts/check_odom.py --scan
```

### hector_scan_align_check (hector 미러링 검증, 하드웨어 불필요)
```bash
# hector만 띄운 상태에서
ros2 launch r2d2_bringup hector.launch.py enable_motor:=false enable_lidar:=false
python3 src/r2d2_bringup/scripts/hector_scan_align_check.py

# 환경변수로 TF/미러링 실험
HECTOR_TF_ROLL=0 python3 src/r2d2_bringup/scripts/hector_scan_align_check.py
HECTOR_SCAN_MIRROR=0 python3 src/r2d2_bringup/scripts/hector_scan_align_check.py
```

---

## 4. 유용한 런치 옵션

| 옵션 | 기본값 | 설명 |
|------|--------|------|
| `mock:=true` | false | UART 없이 모터 노드 실행 (벤치 테스트) |
| `enable_nav2:=false` | true | Nav2 없이 센서+오도메트리만 |
| `enable_slam_loc:=false` | true | SLAM/로컬라이제이션 끔 (raw rf2o만) |
| `mapping_mode:=tank` | differential | motor_node 매핑 모드 (nav2용) |
| `repeat_period:=0.1` | 0.3 | MCU move 프레임 재전송 주기 (초) |
| `cmd_vel_timeout:=0.5` | 0.5 | Twist 없으면 자동 정지 (초) |

### 예: 모터 노드만 mock으로 빠르게 테스트
```bash
ros2 launch r2d2_motor motor.launch.py mock:=true repeat_period:=0.1
```

---

## 5. 주요 토픽/프레임

| 토픽 | 타입 | 설명 |
|------|------|------|
| `/cmd_vel` | Twist | 모터 노드 입력 (velocity_smoother 출력) |
| `/cmd_vel_nav` | Twist | 컨트롤러 출력 (smoother 입력) |
| `/odom` | Odometry | EKF 퓨즈 오도메트리 (odom→base_link TF 발행) |
| `/odom_rf2o` | Odometry | rf2o raw scan-matching 오도메트리 |
| `/scan` | LaserScan | COIN-D4 라이더 데이터 |
| `/map` | OccupancyGrid | slam_toolbox 또는 map_server 맵 |
| `/r2d2/status` | McuStatus | MCU 상태 (배터리, 충전, 모드 등) |

---

## 6. 캘리브레이션 파라미터 (motor_node / twist_mapping)

```bash
# 벤치 실측값 (2026-10-26)
max_linear_velocity:=0.17     # power 100 → 0.167 m/s
forward_gain:=0.001525        # v = 0.001525*P + 0.0145
rotation_gain:=0.00797        # omega = 0.00797*P (in-place)
wheel_separation:=0.235       # 트랙 폭 [m]
```

재측정 필요 시: `motion_bench`로 검증 후 launch 인자로 덮어쓰기.

---

## 7. 문제해결

### 이전 세션 잔여 노드 정리
```bash
pgrep -af 'slam_toolbox|rf2o_laser_odometry|single_coin_d4|motor_node|hector_mapping'
pkill -f '...'
```

### RViz에서 맵 안 보일 때
- `/initialpose` (2D Pose Estimate)로 재로컬라이제이션
- AMCL 모드면 `set_initial_pose:=true` 확인

### cmd_vel이 안 먹힐 때
- `r2d2_motor` 노드 실행 중인지 확인 (`ros2 node list`)
- `/r2d2/status`에서 `charging: 0`인지 확인 (충전 중이면 모션 차단)