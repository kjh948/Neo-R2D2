# ros2 — Neo-R2D2 ROS 2 워크스페이스 (Jazzy)

ROS 2 Jazzy 위에서 R2-D2를 구동하는 colcon 워크스페이스입니다.
`/cmd_vel`(Twist) → MCU 모터 제어, LDROBOT LiDAR, hector SLAM, MPU6050 IMU,
그리고 nav2 내비게이션까지 한 번에 올립니다.

```
                    ┌────────────┐   /cmd_vel (Twist)
 teleop 등 ────────▶│ r2d2_motor │────────────────────┐
                    └────────────┘                    │ MCU UART JSON
 /dev/ttyAMA0 ◀───────────────────────────────────────┘   {"cmd":"move",...}

                    ┌────────────┐   /dev/ttyAMA1
 LD19/LD14P ───────▶│ ldlidar    │──▶ /scan
                    └────────────┘        │
                    ┌────────────┐        ▼
                    │ hector_    │──▶ /map, TF: map → base_link
                    │ mapping    │
                    └────────────┘
                    ┌────────────┐   /dev/i2c-<bus>
 MPU6050 ──────────▶│ mpu6050drv │──▶ /imu (sensor_msgs/Imu)
                    └────────────┘
```

## 패키지 구성

| 패키지 | 종류 | 설명 |
|---|---|---|
| `src/r2d2_motor/` | 직접 개발 (ament_python) | Twist → MCU `move` 프레임 변환 노드 |
| `src/r2d2_bringup/` | 직접 개발 (ament_python) | motor + lidar + slam + imu 통합 launch |
| `src/ldlidar_ros2/` | 외부 클론 | LDROBOT LiDAR 드라이버 (sdk 서브모듈 포함) |
| `src/hector_slam_humble/` | 외부 클론 | hector SLAM (15개 패키지) |
| `src/ros2_mpu6050/` | 외부 클론 | MPU6050 I2C IMU 드라이버 + calibrate 유틸 (Pi5/Jazzy 패치) |
| `src/r2d2_navigation/` | 직접 개발 (ament_cmake) | Nav2 내비게이션 스택 + 지도 저장 스크립트 (§6) |

## 1. 모터 제어 — `r2d2_motor`

`geometry_msgs/Twist`를 구독해 R2-D2 MCU의 UART JSON 프로토콜
(`{"cmd":"move","power":P,"angle":A}`, power 0~100)로 변환합니다.
MCU의 `move(power, angle)`은 차동 운동학 `(v, ω)`의 극좌표 표현: angle 0/180 전후진,
**±90 제자리 회전**, 사이 각도는 호 회전(운영자 실물 확인; robotics.SE q18048 IK).

### 파일 구조

- `motor_node.py` — `/cmd_vel` 구독, 300ms 주기 move 반복 전송, 타임아웃 자동 정지
- `twist_mapping.py` — Twist → power/angle 순수 함수 (단위 테스트 11개)
- `r2d2_link.py` — 리포의 `r2d2/transport.py` + `commander.py` 최소 **벤더본**
  (move/ready/gin). UART 프로토콜이 바뀌면 이 파일도 동기화 필요
- `mcu_client.py` — 링크 브리지, `gin` 응답의 `charging-status` 추적
- `mcu_config.py` — 앱 설정(`r2d2/config_local.json`)에서 직렬 포트 기본값 로드

### 동작 규칙

- **시작 handshake**: `{"cmd":"ready"}` 전송 (Android 앱과 동일), 5초마다 `{"cmd":"gin"}` 상태 요청
- **구동 모델(운영자 확인)**: MCU `move(power, angle)`는 차동 운동학 `(v, ω)`의
  **극좌표(polar) 표현**이다 — angle 0=전진, 180=후진, **+90=제자리左转(두 다리 반대
  방향)**, −90=제자리右转, 사이 각도=전진하며 호(arc) 회전.
  ([robotics.SE q18048](https://robotics.stackexchange.com/q/18048)의 IK를 정규화 극좌표로 재표현)
- **mapping_mode 파라미터 3종**:
  - `differential` (기본, teleop용): Twist 우세 성분 → 0/180/±90 4방향 양자화
  - `tank` (nav2 내비게이션용): `(vx/max_lin, wz/max_ang)` → `power=√(v²+ω²)·100`,
    `angle=atan2(ω, v)` — 연속 각도, 회전 포함 (함수: `twist_mapping.tank_to_move`)
  - `holonomic`: (vx, vy) 병진 가설용 — 구동 모델 확인 결과 **사용 안 함** (코드만 잔존)
- **안전**: `cmd_vel_timeout`(0.5s) 내 Twist 없으면 `power:0` 1회 전송.
  `Twist=0` 수신 시 즉시 정지. 충전 중(`charging-status != 0`)이면 move 차단 (Commander 인터록)
- **직렬 포트 우선순위**: 명시 ROS 파라미터 > `r2d2/config_local.json`
  (자동 탐지, `~/.config/r2d2/config.json`·`/etc/r2d2/config.json`·`R2D2_CONFIG`도) > 내장 기본(`/dev/ttyS2`).
  **현재 이 장치는 ttyAMA0 사용.** `serial_port:=""`, `baudrate:=-1`이 "config 사용" 의미
- **r2d2 앱과 동시에 실행 불가**: 앱이 UART를 점유하고 있으면 본 노드는 포트를 열 수 없다.
  실행 전 `fuser /dev/ttyAMA0`으로 확인

### 튜닝 파라미터

`max_linear_velocity`(0.5 m/s), `max_angular_velocity`(1.0 rad/s),
`cmd_vel_timeout`(0.5 s), `repeat_period`(0.3 s), `gin_period`(5 s),
`deadband`(0.02), `min_power`(1), `invert_strafe`, `serial_port`, `baudrate`,
`config_file`, `mock`, `cmd_vel_topic`

## 2. LiDAR — `ldlidar_ros2`

- 출처: `https://github.com/ldrobotSensorTeam/ldlidar_ros2` (sdk 서브모듈 포함 클론)
- 지원 모델: LD06 / LD14 / LD14P / LD19. **우리 장비: LD19 (230400)**
- 로컬 수정:
  - `sdk/src/log_module.cpp`: `#include <pthread.h>` 추가
    (GCC 13+는 pthread를 암시 포함하지 않음 — 서브모듈이라 `git submodule update` 시
    재패치 필요)
  - `launch/ld19.launch.py`, `launch/ld14p.launch.py`: `port_name`을 launch 인자로
    파라미터화, 기본값 `/dev/ttyAMA1` (upstream은 `/dev/ttyUSB0` 하드코딩).
    `product_name`, `serial_baudrate`, `frame_id`, TF 높이(`laser_tf_z`=0.18)도 인자화
- 출력: `/scan` (frame `base_laser`), `base_link→base_laser` static TF 동시 발행
- 구동: `ros2 launch ldlidar_ros2 ld19.launch.py port_name:=/dev/ttyAMA1`
- LD14P 사용 시: `ros2 launch ldlidar_ros2 ld19.launch.py product_name:=LDLiDAR_LD14P`
  (또는 `ld14p.launch.py`)
- ⚠️ **빌드만 확인됨. LD19 본체 미연결 — 실물 런타임 테스트 남아있음**

## 3. SLAM — `hector_slam`

- 출처: `https://github.com/KiiiLin/hector_slam_humble` (Humble용, 15개 패키지).
  폴더명은 Jazzy 구동에 맞춰 `src/hector_slam`으로 rename했고, 내부 15개 패키지의
  이름(`hector_mapping` 등)은 원본 그대로.
- Jazzy适配: `hector_compressed_map_transport/src/map_to_image_node.cpp`의
  `#include <cv_bridge/cv_bridge.h>` → `<cv_bridge/cv_bridge.hpp>` (Jazzy에서 .h 제거됨).
  나머지 패키지는 무수정 빌드
- `hector_slam_launch`의 예제 launch들은 원작자 로봇용(`laser`, `nav` 등 frame)이라
  **Neo-R2D2에서는 `r2d2_bringup`의 yaml을 사용**
- 구성 (scan-only SLAM, `r2d2_bringup/config/hector_slam.yaml`):
  - `map_frame=map`, `odom_frame=base_link`, `base_frame=base_link` —
    wheel odom이 없어 hector가 `map→base_link`를 직접 발행
  - LD19 한계 반영: `laser_min_dist=0.05`, `laser_max_dist=12.0`
  - 느린 보행에 맞춘 갱신 임계값: `map_update_distance_thresh=0.2`,
    `map_update_angle_thresh=0.15`, `map_resolution=0.05`, `map_size=600`(30m×30m)

### TF 트리 (구동 시)

```
map ──(hector)──▶ base_link ──(ldlidar static, z=0.18)──▶ base_laser
```

## 4. IMU — `ros2_mpu6050` (kimsniper)

- 출처: `https://github.com/kimsniper/ros2_mpu6050` (i2c-dev C++ 드라이버 +
  **별도 캘리브레이션 유틸** 내장 — 선택 이유)
- 시스템 의존성: `sudo apt install libi2c-dev i2c-tools` ✅ 설치 완료
- 출력: `imu/mpu6050` 토픽 `sensor_msgs/Imu` (100Hz 고정 타이머,
  가속도 m/s² + 각속도 rad/s, **orientation은 무효 표시**(-1 covariance) —
  MPU6050에 지자기 센서가 없고 이 드라이버도 필터를 계산하지 않음)
- 캘리브레이션 (센서 연결 후, 로봇 정지 상태에서):
  ```bash
  ros2 run ros2_mpu6050 ros2_mpu6050_calibrate [디바이스] [주소]   # 기본 /dev/i2c-1 0x68
  #   → 500샘플 평균 offset을 화면에 출력. values를 config/params.yaml의
  #     gyro_*_offset / accel_*_offset에 기입 (gyro [deg/s], accel [m/s²])
  ```
- 로컬 패치 (Pi5/Jazzy适配):
  - `device`(`/dev/i2c-1`), `i2c_address`(0x68), `imu_frame`(`base_link`) ROS
    파라미터 추가 — upstream은 생성자 디폴트 인자로만 하드코딩
  - 캘리브레이션 유틸에 argv로 디바이스/주소 인자 추가
    (Pi5 두 자리 버스 `/dev/i2c-13`·`-14`, AD0-High 0x69 보드 지원)
  - `<array>` include 추가 (GCC13+에서 transitive include 제거 → 빌드 오류)
- 사용:
  ```bash
  ros2 launch ros2_mpu6050 ros2_mpu6050.launch.py            # config/params.yaml
  ros2 launch ros2_mpu6050 ros2_mpu6050.launch.py param_file:=/path/to/my.yaml
  ros2 topic echo /imu/mpu6050 --once
  ```
- 센서 미연결 시 동작(확인됨): `/dev/i2c-1` open은 성공 → device ID 검증 실패로
  `MPU6050 initialization failed!` 출력 후 프로세스는 살아있음. 연결 후 정상화
- **imu_filter_madgwick 검토 결론: 현 구성에서는 불필요 (보류)**
  - hector SLAM은 `/scan`만 사용 — IMU 불필요
  - `hector_imu_attitude_to_tf`는 `imu.orientation`(quaternion)을 그대로
    소비하는데 이 드라이버는 orientation을 발행하지 않음 → attitude TF를
    원할 때만필요
  - 필요해지는 시점(기울임 감시 / robot_localization EKF 등):
    `sudo apt install ros-jazzy-imu-filter-madgwick` 후
    `/imu/mpu6050` → `imu_filter_madgwick` → orientation 있는 `imu/data` →
    `hector_imu_attitude_to_tf` 순으로 연결. MAG 없는 MPU6050 특성상
    yaw는 드리프트 (roll/pitch는 accel 보정되어 실용적)
- ⚠️ **빌드·launch 검증 완료 / MPU6050 본체 미연결 — 실물 테스트 남아있음**

## 5. 통합 — `r2d2_bringup`

```bash
ros2 launch r2d2_bringup r2d2_bringup.launch.py                      # motor+lidar+slam
ros2 launch r2d2_bringup r2d2_bringup.launch.py mock:=true enable_lidar:=false
ros2 launch r2d2_bringup r2d2_bringup.launch.py lidar_product:=LDLiDAR_LD14P
ros2 launch r2d2_bringup r2d2_bringup.launch.py enable_imu:=true imu_device:=/dev/i2c-1
```

인자: `enable_motor`/`enable_lidar`/`enable_slam`/`enable_imu`, `mock`,
`lidar_port`(ttyAMA1), `lidar_product`, `lidar_baudrate`, `hector_params`(yaml 경로),
`imu_device`(/dev/i2c-1), `imu_frame`(base_link)

## 6. 내비게이션 — `r2d2_navigation`

linorobot2_navigation 구조(launch/config/maps/rviz)를 참고하되, **차동 + no-odometry**
사양에 맞춰 재설계했습니다.

```
r2d2_navigation/
├── config/nav2_params.yaml      # nav2 1.3.13 검증 완료
├── config/hector_nav.yaml       # 내비 모드 hector (지도 원점 분리)
├── launch/navigation.launch.py  # motor+lidar+hector+map_server+nav2 core
├── maps/placeholder.{pgm,yaml}  # 10×10m 방 (임시, 실측 지도로 교체)
├── rviz/nav2_config.rviz
└── scripts/save_map.sh          # /live_map → nav2 지도 저장
```

### 구동/추정 아키텍처 (핵심 결정)

| 계층 | 선택 | 이유 |
|---|---|---|
| 모션 | `r2d2_motor mapping_mode:=tank` | MCU (power, angle) = (v, ω) 극좌표 (운영자 확인, SE q18048) |
| 컨트롤러 | MPPI `motion_model: DiffDrive` (wz_max 0.8) | 기본 critics 전체 유효 (회전 가능) |
| 플래너 | NavFn | diff 로봇 표준, 경로에 heading 포함 |
| 위치추정 | **hector_mapping 상시 구동** (scan matching → `map→base_link` TF) | wheel odom 없음 → AMCL/odometry 불가. hector의 존재 이유 |
| 자기위치 보정 | RViz **2D Pose Estimate** → `/initialpose` (hector resetPose, [HectorMappingRos.cpp:670]) | 홈 출발 규칙 불필요. 지도에서 위치 클릭으로 좌표 정렬 |
| 지도 | `map_server`가 저장 지도 `/map` 발행, hector live map은 `/live_map`으로 remap (frame은 `map` 공유) | costmap 입력과 live 매핑의 토픽 충돌 방지 |
| goal checker | xy 0.15 / **yaw 0.25** (회전 유효) | — |
| BT | nav2 기본 recovery tree (**Spin 포함** 가능) | 커스텀 no-spin 트리 폐기됨 |

### 지도 워크플로 (hector → nav2)

```bash
# 1) 매핑: bringup으로 hector SLAM 구동, 로봇 주행하며 지도 채집
ros2 launch r2d2_bringup r2d2_bringup.launch.py

# 2) 저장: /live_map(hector 실시간 지도)을 nav2 형식으로
ros2 run r2d2_navigation save_map.sh maps/r2d2_home
#    (map_saver_cli -p map_topic:=live_map)

# 3) 내비: 저장 지도 사용 + hector가 실시간 자기위치 추정
ros2 launch r2d2_navigation navigation.launch.py map:=<절대경로>/r2d2_home.yaml
#    RViz: rviz2 -d $(ros2 pkg prefix r2d2_navigation)/share/r2d2_navigation/rviz/nav2_config.rviz
#    ① 2D Pose Estimate로 실제 위치 지정 → ② 2D Goal Pose로 목표 발행
```

`maps/placeholder.*`는 센서 미연결 단계에서 nav2 스택 전체 기동을 검증용으로
커밋된 더미 지도입니다. 실측 지도가 있으면 `map:=`로 교체.

### 검증 상태 (nav2)
- 1.3.13 파라미터 실측 통과: SmacPlanner→NavFn 교체, MPPI DiffDrive,
  NavigateToPoseNavigator, behavior spin복원, 전 서버 configure (headless,
  mock+hector-off에서 map TF 대기 로그만 정상 출력)
- ⬜ 실물 e2e (LiDAR/IMU/MCU 연결 후): `/scan`→hector→costmap→경로추종

## 빌드

```bash
cd ~/workspace/Neo-R2D2/ros2
source /opt/ros/jazzy/setup.bash
git clone --recursive https://github.com/ldrobotSensorTeam/ldlidar_ros2.git src/   # 최초 1회
git clone https://github.com/KiiiLin/hector_slam_humble.git src/hector_slam        # 최초 1회
git clone https://github.com/kimsniper/ros2_mpu6050.git src/ros2_mpu6050  # 최초 1회
sudo apt install libi2c-dev i2c-tools                     # IMU 드라이버에 필요 (설치 완료)
colcon build                    # python은 --symlink-install 권장
source install/setup.bash
```

## 검증 상태

| 항목 | 상태 |
|---|---|
| Twist→move 매핑 단위 테스트 | ✅ 24/24 (`colcon test`, differential/holonomic/tank 포함) |
| motor 노드 mock 스모크 | ✅ ready/gin/0.3→`power60 angle0` 300ms 반복/타임아웃 정지 확인 |
| config 반영(ttyAMA0) | ✅ `config_local.json` 자동 로드, 파라미터 오버라이드 확인 |
| 전체 패키지 빌드 | ✅ 0 failures (Pi5, Jazzy, nav2 1.3.13) |
| bringup 기동 | ✅ motor(mock)+hector 파라미터 반영 확인 (LiDAR 제외) |
| nav2 스택 headless 검증 | ✅ map_server/NavFn/MPPI-DiffDrive/BT(기본 트리)/behavior/velocity_smoother 전 서버 configure (mock, hector-off — TF 대기 로그만) |
| nav2 실물 e2e | ⬜ LiDAR+MCU 연결 후 (§6 검증 상태) |
| ldlidar 실물 | ⬜ 미연결 (빌드/launch 인자만 검증) |
| MCU 실물 UART | ⬜ 미검증 |
| ros2_mpu6050 빌드/launch | ✅ 패치 후 빌드 통과, launch/param 검증, 미연결 시 "initialization failed" 안내 확인 |
| MPU6050 실물 | ⬜ 미연결 (연결 후 calibrate + /imu/mpu6050 확인 필요) |

## 실물 연결 시 체크리스트

1. r2d2 앱 종료 (ttyAMA0 점유 확인: `fuser /dev/ttyAMA0`)
2. 회전 방향 확인: `ros2 topic pub /cmd_vel ... angular:{z:0.5}` → 로봇이
   **왼쪽(반시계)으로 제자리 회전**해야 정상 (H2 확인된 모델 기준). 반대면
   `invert_strafe:=true`
3. 속도 체감 보고 `max_linear_velocity` 조정 (power 스케일)
4. LiDAR: `/dev/ttyAMA1` 없으면 Pi UART 오버레이(`config.txt`의 `enable_uart`/
   `dtoverlay=uart1`) 확인, `ros2 topic echo /scan --once`로 데이터 수신 확인
5. SLAM: rviz2(원격 PC)에서 `/map` + `/scan` + TF 표시
6. 충전 독에 물리면 move가 차단되는지 `gin` 응답으로 확인
7. IMU(MPU6050): VCC→3.3V, GND, SDA/SCL을 쓰는 버스의 GPIO에 (i2c-1 = GPIO2/3),
   AD0는 GND(0x68) 또는 VCC(0x69 — `i2c_address` 파라미터로 지원).
   `i2cdetect -y 1`에 68 표시 확인 →
   `ros2 run ros2_mpu6050 ros2_mpu6050_calibrate`로 offset 측정 →
   `config/params.yaml` 기입 → `ros2 launch ros2_mpu6050 ros2_mpu6050.launch.py`
   → `ros2 topic echo /imu/mpu6050 --once`

## 알려진 사항

- `src/ldlidar_ros2`, `src/hector_slam`은 vendoring 완료(네스트 .git 제거,
  본 리포에 커밋됨). `src/ros2_mpu6050`도 커밋 시 `.git` 제거 필요
- `r2d2_link.py`는 `r2d2` 패키지의 벤더본 — 원본 프로토콜 변경 시 동기화 필요
- `build/`, `install/`, `log/`은 `ros2/.gitignore`로 제외
