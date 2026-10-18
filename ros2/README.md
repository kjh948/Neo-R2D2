# ros2 — Neo-R2D2 ROS 2 워크스페이스 (Jazzy)

ROS 2 Jazzy 위에서 R2-D2를 구동하는 colcon 워크스페이스입니다.
`/cmd_vel`(Twist) → MCU 모터 제어, COIN-D3 LiDAR, hector SLAM, MPU6050 IMU,
그리고 nav2 내비게이션까지 한 번에 올립니다.

```
                    ┌────────────┐   /cmd_vel (Twist)
 teleop 등 ────────▶│ r2d2_motor │────────────────────┐
                    └────────────┘                    │ MCU UART JSON
 /dev/ttyAMA0 ◀───────────────────────────────────────┘   {"cmd":"move",...}

                    ┌──────────────┐   /dev/ttyAMA1
 COIN-D3 ──────────▶│coin_d4_driver│──▶ /scan
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
| `src/coin_d4_driver/` | 외부 클론 | ROBOTIS LiDAR 드라이버 (COIN-D3/D4, LDS-02/03 계열) |
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

## 2. LiDAR — `coin_d4_driver`

- 출처: `https://github.com/ROBOTIS-GIT/coin_d4_driver` (LDS 계열 드라이버, vendoring 완료)
- 프로토콜은 `version` 파라미터로 선택: 1=M1C1_Mini_v1(115200), 2=M1C1_Mini_v2(150000),
  3=M1CT_Coin_Plus(115200), 4=M1CT_TOF(230400) — baudrate는 version이 결정( yaml값 덮어씀).
  **우리 장비: COIN-D3 (`params/single_lidar_node.yaml` 기준 version 4, `/dev/ttyAMA1`)**
- 출력: `/scan`. 노드 이름은 `lidar_node`(하드코딩). **드라이버는 TF를 발행하지 않음** —
  `base_link→base_laser` static TF(z=0.18)와 frame_id `base_laser` 오버라이드는
  `r2d2_bringup`/`r2d2_navigation` launch에서 담당 (hector·nav2가 `base_laser` 사용)
- 단독 구동: `ros2 launch coin_d4_driver single_lidar_node.launch.py`
  (param 파일 갈아끼우기: `single_lidar_node_param:=...`)
- ⚠️ 이전에 쓰던 `ldlidar_ros2`(LD19) 패키지는 삭제됨 (2026-10, COIN-D3 교체)
- ✅ 실물 동작 확인됨 (COIN-D3, `/dev/ttyAMA1` — 운영자 검증)

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
  - COIN-D3 한계 반영: `laser_min_dist=0.05`, `laser_max_dist=12.0`
  - 느린 보행에 맞춘 갱신 임계값: `map_update_distance_thresh=0.2`,
    `map_update_angle_thresh=0.15`, `map_resolution=0.05`, `map_size=600`(30m×30m)

### TF 트리 (구동 시)

```
map ──(hector)──▶ base_link ──(bringup static TF, z=0.18)──▶ base_laser
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
ros2 launch r2d2_bringup r2d2_bringup.launch.py lidar_version:=4
ros2 launch r2d2_bringup r2d2_bringup.launch.py enable_imu:=true imu_device:=/dev/i2c-1
```

인자: `enable_motor`/`enable_lidar`/`enable_slam`/`enable_imu`, `mock`,
`lidar_port`(ttyAMA1), `lidar_version`(coin_d4_driver protocol), `laser_tf_z`(0.18),
`hector_params`(yaml 경로), `imu_device`(/dev/i2c-1), `imu_frame`(base_link)

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
# 1) 매핑: Pi에서 bringup으로 hector SLAM 구동, 원격 PC의 RViz로 확인하며 주행 (§7)
ros2 launch r2d2_bringup r2d2_bringup.launch.py            # Pi
ros2 launch r2d2_bringup remote.launch.py                   # 원격 PC (rviz_slam.rviz)

# 2) 저장: bringup 세션은 /map 기본값 그대로
ros2 run r2d2_navigation save_map.sh maps/r2d2_home         # Pi 또는 원격(어디서든)
#    navigation 세션(실시간지도가 /live_map으로 remap됨)일 때:
MAP_TOPIC=live_map ros2 run r2d2_navigation save_map.sh maps/r2d2_home

# 3) 내비: 저장 지도 사용 + hector가 실시간 자기위치 추정
ros2 launch r2d2_navigation navigation.launch.py map:=<절대경로>/r2d2_home.yaml
#    원격 RViz: ros2 launch r2d2_bringup remote.launch.py \
#        rviz_config:=$(ros2 pkg prefix r2d2_navigation)/share/r2d2_navigation/rviz/nav2_config.rviz
#    ① 2D Pose Estimate로 실제 위치 지정 → ② 2D Goal Pose로 목표 발행
```

`maps/placeholder.*`는 센서 미연결 단계에서 nav2 스택 전체 기동을 검증용으로
커밋된 더미 지도입니다. 실측 지도가 있으면 `map:=`로 교체.

### 검증 상태 (nav2)
- 1.3.13 파라미터 실측 통과: SmacPlanner→NavFn 교체, MPPI DiffDrive,
  NavigateToPoseNavigator, behavior spin복원, 전 서버 configure (headless,
  mock+hector-off에서 map TF 대기 로그만 정상 출력)
- ⬜ 실물 e2e (LiDAR/IMU/MCU 연결 후): `/scan`→hector→costmap→경로추종

## 7. 원격 PC 세팅 (RViz — hector 매핑/내비 모니터링)

Pi에는 디스플레이가 없으므로 `/map` 시각화·지도 저장·2D Pose Estimate는 원격 PC의
RViz 2가 담당한다. **원격 PC는 노드를 직접 실행하지 않는다** — 같은 DDS 도메인으로
Pi의 토픽/TF를 구독만 하면 되므로 드라이버(coin_d4_driver, hector_slam, motor)를
원격에 빌드할 필요가 없다.

### 1) ROS 2 설치 (Ubuntu 24.04 — Jazzy가 Pi와 동일)

```bash
# ROS 2 Jazzy 기본 설치 후, RViz 포함 데스크톱 메타패키지를 추가:
sudo apt install ros-jazzy-desktop
# ⚠️ 이 로봇은 CycloneDDS를 쓰므로(Pi ~/.bashrc에 export 고정) 원격에도 같은
#    RMW를 설치·지정해야 디스커버리가 된다. FastDDS(기본)로 두면 토픽이 안 보인다:
sudo apt install ros-jazzy-rmw-cyclonedds-cpp
echo 'export RMW_IMPLEMENTATION=rmw_cyclonedds_cpp' >> ~/.bashrc
# (지도 저장도 원격에서 하려면) sudo apt install ros-jazzy-navigation2
```

### 2) 이 워크스페이스의 원격 부분만 받기 (launch/config용)

```bash
git clone <Neo-R2D2 repo> && cd Neo-R2D2/ros2
colcon build --packages-select r2d2_bringup     # ament_python 단 몇 초
source install/setup.bash
ros2 launch r2d2_bringup remote.launch.py        # = rviz2 -d config/rviz_slam.rviz
```

빌드 자체가 귀찮으면 클론 없이 파일만 옮겨 실행해도 된다:
`rviz2 -d rviz_slam.rviz` (git으로 해당 파일만 받아도 무방).

### 3) 네트워크/DDS 설정 (Pi와 원격 PC)

| 항목 | 설정 |
|---|---|
| `RMW_IMPLEMENTATION` | **양쪽 다 `rmw_cyclonedds_cpp`** — Pi는 `~/.bashrc`에 export됨(120행). RMW가 다르면(예: 원격이 DDS 기본 FastDDS) 디스커버리가 안 돼 토픽이 0개 |
| 물리 연결 | 같은 LAN 서브넷 권장(유선>와이파이). 스마트폰 테더링/기업 AP는 **AP isolation** 차단 흔함 |
| `ROS_DOMAIN_ID` | 양쪽 동일 (미지정 = 0. Pi `~/.bashrc`에 `export ROS_DOMAIN_ID=…`로 고정해뒀다면 원격도 동일값) |
| `ROS_LOCALHOST_ONLY` | 양쪽 `0`(기본값). 실수로 `1`이면 원격이 아무 토픽도 못 본다 |
| IP 확인 | Pi: `hostname -I`. DNS보다 IP 지정이 안정적 |

동작 확인 (Pi에서 bringup 구동 중 원격 PC에서):

```bash
source /opt/ros/jazzy/setup.bash   # (워크스페이스 소스 불필요 — 원격 토픽 구독만)
ros2 daemon stop && ros2 daemon start     # 이전 캐시 초기화 (단골 실수)
ros2 topic list               # /scan /map /tf 가 보이면 성공
ros2 topic hz /scan           # 대략 5Hz 정도 수신 확인
```

**멀티캐스트가 막히는 네트워크**(학교/사무실 WiFi)에서 안 보이면: CycloneDDS 유니캐스트
피어 지정으로 우회 가능 — 안 막히는 쪽(Pi)은 그대로 두고, 원격 PC에서만:

```bash
export CYCLONEDDS_URI='<CycloneDDS><Domain><General><AllowMulticast>false</AllowMulticast></General><Peers><Peer address="Pi_IP"/></Peers></Domain></CycloneDDS>'
```

그래도 여의치 않으면 폴백으로 `ssh -X`를 써 Pi에서 rviz를 직접 띄우기
(`ssh -X pi@<ip>` → `rviz2 -d .../rviz_slam.rviz`) — Pi가 X11 포워딩, 느리지만 동작한다.

### 4) 원격 PC에서의 everyday 사용 흐름

- **매핑**: §6 워크플로 1)처럼 Pi bringup + 원격 `remote.launch.py` → map/scan/TF 확인
- **지도 저장**: 원격에서 `MAP_TOPIC=map ros2 run r2d2_navigation save_map.sh ...`
  (map_saver는 토픽 구독만 하면 되므로 어느 머신에서 실행하든 관계없다)
- **내비**: `remote.launch.py rviz_config:=…/nav2_config.rviz` → 2D Pose Estimate/Goal 사용

## 빌드

```bash
cd ~/workspace/Neo-R2D2/ros2
source /opt/ros/jazzy/setup.bash
git clone -b jazzy https://github.com/ROBOTIS-GIT/coin_d4_driver.git src/coin_d4_driver   # 최초 1회
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
| LiDAR 실물 (COIN-D3) | ✅ coin_d4_driver 스캔 수신 확인 (/dev/ttyAMA1) |
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

- `src/coin_d4_driver`, `src/hector_slam`은 vendoring 완료(네스트 .git 제거,
  본 리포에 커밋됨). `src/ros2_mpu6050`도 커밋 시 `.git` 제거 필요.
  (구 `src/ldlidar_ros2`는 COIN-D3 교체로 삭제)
- `r2d2_link.py`는 `r2d2` 패키지의 벤더본 — 원본 프로토콜 변경 시 동기화 필요
- `build/`, `install/`, `log/`은 `ros2/.gitignore`로 제외


## 구동팁
- ros2 run teleop_twist_keyboard teleop_twist_keyboard 를 이용하여 모터제어