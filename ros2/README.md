# ros2 — Neo-R2D2 ROS 2 워크스페이스 (Jazzy)

ROS 2 Jazzy 위에서 R2-D2를 구동하는 colcon 워크스페이스입니다.
`/cmd_vel`(Twist) → MCU 모터 제어, COIN-D3 LiDAR, rf2o 레이저 오도메트리 + slam_toolbox, MPU6050 IMU,
그리고 nav2 내비게이션까지 한 번에 올립니다.

```
                    ┌────────────┐   /cmd_vel (Twist)
 teleop 등 ────────▶│ r2d2_motor │────────────────────┐
                    └────────────┘                    │ MCU UART JSON
 /dev/ttyAMA0 ◀───────────────────────────────────────┘   {"cmd":"move",...}

                    ┌──────────────┐   /dev/ttyAMA1
 COIN-D3 ──────────▶│coin_d4_driver│──▶ /scan
                    └────────────┘        │
                    ┌─────────────┐        ▼
                    │ rf2o_laser_ │──▶ /odom, TF: odom → base_link
                    │ odometry    │
                    ├─────────────┤
                    │ slam_toolbox│──▶ /map, TF: map → odom
                    └─────────────┘
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
| `src/rf2o_laser_odometry/` | 외부 클론 (vendoring) | scan-to-scan 레이저 오도메트리 (`/odom` + odom→base_link TF). slam_toolbox는 apt(`ros-jazzy-slam-toolbox`) |
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
`deadband`(0.02), `min_power`(1), `serial_port`, `baudrate`,
`config_file`, `mock`, `cmd_vel_topic`
- `invert_strafe`는 **기본 true** (2026-10-18 실물 확인: +angular.z에 로봇이 우회전 →
  MCU 각도 부호가 ROS 컨벤션과 반대라 wz를 반전). 정방향 장착/퍼밍 변경 시 `false`

## 2. LiDAR — `coin_d4_driver`

- 출처: `https://github.com/ROBOTIS-GIT/coin_d4_driver` (LDS 계열 드라이버, vendoring 완료)
- 프로토콜은 `version` 파라미터로 선택: 1=M1C1_Mini_v1(115200), 2=M1C1_Mini_v2(150000),
  3=M1CT_Coin_Plus(115200), 4=M1CT_TOF(230400) — baudrate는 version이 결정( yaml값 덮어씀).
  **우리 장비: COIN-D3 (`params/single_lidar_node.yaml` 기준 version 4, `/dev/ttyAMA1`)**
- 출력: `/scan`. 노드 이름은 `lidar_node`(하드코딩). **드라이버는 TF를 발행하지 않음** —
  `base_link→base_laser` static TF(x=0.18 전방, z=0.083, **roll=π** — 실측 2026-10-24)와 frame_id `base_laser` 오버라이드는
  `r2d2_bringup`/`r2d2_navigation` launch에서 담당 (slam_toolbox·rf2o·nav2가 `base_laser` 사용)
- 실물 장착: COIN-D4가 **거꾸로(윗면이 아래)** 달려 있어 static TF에 roll=π를 넣음
  (RViz에서 좌우 반전 보상의 근거. 바르게 재장착하면 `laser_tf_roll:=0`)
- 단독 구동: `ros2 launch coin_d4_driver single_lidar_node.launch.py`
  (param 파일 갈아끼우기: `single_lidar_node_param:=...`)
- ⚠️ 이전에 쓰던 `ldlidar_ros2`(LD19) 패키지는 삭제됨 (2026-10, COIN-D3 교체)
- ✅ 실물 동작 확인됨 (COIN-D3, `/dev/ttyAMA1` — 운영자 검증)

## 3. SLAM — `rf2o_laser_odometry` + `slam_toolbox`

> 2026-10-19: hector_slam 전면 교체. wheel odom이 없는 로봇에 scan 기반
> 오도메트리(rf2o)를 더한 **표준 nav2 TF 체인**(map→odom→base_link) 구성.
> hector_slam(vendored 15개 패키지)·`hector_slam.yaml`·`hector_nav.yaml`·
> `map_server`(placeholder 지도)는 삭제됨.

* **rf2o_laser_odometry** — `/scan` 연속 스캔 매칭으로 `odom→base_link` TF + `/odom` 발행
  - 출처: `https://github.com/MAPIRlab/rf2o_laser_odometry` (`ros2` 브랜치) vendoring
  - 로컬 패치: `package.xml` — 포닥-era 유물 삭제: `cmake_modules` build/run_depend
    제거(CMakeLists는 미사용 실측 확인), `<run_depend>` → `<exec_depend>`, `nav_msgs` 누락 보완.
    소스 코드 무수정 빌드(Jazzy/GCC13 arm64)
  - 파라미터는 launch에 dict로 직접 주입 (`r2d2_bringup`/`r2d2_navigation`):
    `odom_topic: /odom` (upstream 기본 `/odom_rf2o`), `init_pose_from_topic: ""`
    (upstream 기본이 `/base_pose_ground_truth` — 반드시 비워야 함), `freq: 20`
    (실측은 스캔 수신률 ≈10Hz로 출력)
* **slam_toolbox** — apt `ros-jazzy-slam-toolbox` (2.8.5, 설치 확인)
  - 매핑(bringup): `async_slam_toolbox_node`, `r2d2_bringup/config/slam_toolbox.yaml`
    (`/opt/ros`의 `mapper_params_online_async.yaml` 기반. `base_frame: base_link` —
    템플릿 기본 `base_footprint`는 이 로봇에 없음, resolution 0.05,
    laser 0.05–12m, slow-walker `minimum_travel_*` 0.2/0.15 계승)
  - 내비(navigation): `localization_slam_toolbox_node`,
    `r2d2_navigation/config/slam_toolbox_localization.yaml` — `map_file_name`은
    launch의 `posegraph:=` 인자가 RewrittenYaml로 주입
  - ⚠️ 실측 함정 두 개 (2026-10-19, jazzy 2.8.5):
    1) localization 모드에서 `map_start_at_dock` **미지원** — dock 설정 시 SIGSEGV.
       `map_start_pose: [0,0,0]` 필수 (yaml에 반영됨)
    2) **움직임 없이 만든 단일 노드 포즈그래그를 deserialize하면 SIGSEGV**
       (`loadSerializedPoseGraph`, async/localization 공통). 매핑 세션에서는 반드시
       0.5m 이상 주행 후 저장할 것 — 정지 상태로 `save_map.sh` 실행하면 그 파일 로드 불가
  - 노드명은 기본값 `slam_toolbox` 유지 (서비스 스페이스 `/slam_toolbox/*` 관례)

### TF 트리 (구동 시)

```
map ──(slam_toolbox)──▶ odom ──(rf2o)──▶ base_link ──(bringup static TF, x=0.18/z=0.083, roll=π: 뒤집힘 장착 보정)──▶ base_laser
```

### 기하 실측값 (2026-10-24)

| 항목 | 값 | 반영 위치 |
|---|---|---|
| Wheel separation | **23.5 cm** | 차동 운동학 스케일 — nav2 footprint는 바깥Shell 실측 전까지 0.4 m placeholder (≥0.235 guarantee만 코멘트) |
| LiDAR from base_link | **x = +18 cm (전방), z = 8.3 cm**, roll=π(뒤집힘) | bringup `laser_tf_x/z/roll` 기본값, navigation launch 하드코딩 |
| base_link 위치 | 차륜 축 중심 가정 | 재정의 시 static TF와 footprint 기준점이 함께 변경됨 |

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
  - SLAM(rf2o + slam_toolbox)은 `/scan`만 사용 — IMU 불필요
  - (hector 시절의 `hector_imu_attitude_to_tf` 연동은 hector 제거로 소멸.
    이 드라이버는 orientation을 발행하지 않으므로 attitude TF가 필요해지면
    별도 publisher를 붙여야 함)
  - 필요해지는 시점(기울임 감시 / robot_localization EKF 등):
    `sudo apt install ros-jazzy-imu-filter-madgwick` 후
    `/imu/mpu6050` → `imu_filter_madgwick` → orientation 있는 `imu/data` 순으로
    연결. MAG 없는 MPU6050 특성상 yaw는 드리프트 (roll/pitch는 accel 보정되어 실용적)
- ⚠️ **빌드·launch 검증 완료 / MPU6050 본체 미연결 — 실물 테스트 남아있음**

## 5. 통합 — `r2d2_bringup`

```bash
ros2 launch r2d2_bringup r2d2_bringup.launch.py                      # motor+lidar+slam
ros2 launch r2d2_bringup r2d2_bringup.launch.py mock:=true enable_lidar:=false
ros2 launch r2d2_bringup r2d2_bringup.launch.py lidar_version:=4
ros2 launch r2d2_bringup r2d2_bringup.launch.py enable_imu:=true imu_device:=/dev/i2c-1
```

인자: `enable_motor`/`enable_lidar`/`enable_slam`/`enable_imu`, `mock`,
`lidar_port`(ttyAMA1), `lidar_version`(coin_d4_driver protocol),
`laser_tf_x`(0.18 전방), `laser_tf_z`(0.083 높이),
`laser_tf_roll`(π — 라이다 뒤집힘 장착 보정), `rf2o_freq`(20.0),
`slam_params`(yaml 경로), `imu_device`(/dev/i2c-1), `imu_frame`(base_link)

## 6. 내비게이션 — `r2d2_navigation`

linorobot2_navigation 구조(launch/config/maps/rviz)를 참고하되, **차동 + 레이저
오도메트리(rf2o)** 사양에 맞춰 재설계했습니다.

```
r2d2_navigation/
├── config/nav2_params.yaml             # nav2 1.3.13 — odom 프레임 반영 (rf2o 이후)
├── config/slam_toolbox_localization.yaml  # localization 모드 (posegraph 로드)
├── launch/navigation.launch.py         # motor+lidar+rf2o+slam_toolbox(loc)+nav2 core
├── maps/                               # save_map.sh 출력 (posegraph+pgm/yaml, git 추적)
├── rviz/nav2_config.rviz
└── scripts/save_map.sh                 # serialize_map + map_saver_cli
```

### 구동/추정 아키텍처 (핵심 결정)

| 계층 | 선택 | 이유 |
|---|---|---|
| 모션 | `r2d2_motor mapping_mode:=tank` | MCU (power, angle) = (v, ω) 극좌표 (운영자 확인, SE q18048) |
| 컨트롤러 | MPPI `motion_model: DiffDrive` (wz_max 0.8) | 기본 critics 전체 유효 (회전 가능) |
| 플래너 | NavFn | diff 로봇 표준, 경로에 heading 포함 |
| 위치추정 | **rf2o(odom→base_link) + slam_toolbox localization(map→odom)** | wheel odom 없음 → scan 기반 레이저 odom. nav2 표준 TF 체인이 완성되어 local costmap(odom)/velocity_smoother(/odom)/MPPI가 정상 동작 |
| 자기위치 보정 | RViz **2D Pose Estimate** → `/initialpose` (slam_toolbox 네이티브) | 출발 기본값 `map_start_pose: [0,0,0]` (매핑 시작 지점), 어딘가에 출발하면 클릭으로 보정 |
| 지도 | slam_toolbox가 posegraph 로드 후 `/map` 직접 발행 (map_server 폐지) | 저장: `save_map.sh` → `_posegraph.{posegraph,data}` + `.pgm/.yaml` |
| goal checker | xy 0.15 / **yaw 0.25** (회전 유효) | — |
| BT | nav2 기본 recovery tree (**Spin 포함** 가능) | 커스텀 no-spin 트리 폐기됨 |

### 지도 워크플로 (slam_toolbox)

```bash
# 1) 매핑: Pi에서 bringup(async slam_toolbox) 구동, 원격 PC RViz로 확인하며 **주행** (§7)
#    ⚠️ 정지 상태 매핑은 1-노드 포즈그래그를 만드는데, 이 slam_toolbox(2.8.5)는
#    그 파일 로드 시 SIGSEGV — 반드시 0.5m 이상 움직인 뒤 저장할 것 (§3 참조)
ros2 launch r2d2_bringup r2d2_bringup.launch.py            # Pi
ros2 launch r2d2_bringup remote.launch.py                   # 원격 PC (rviz_slam.rviz)

# 2) 저장: posegraph(내비용) + pgm/yaml(열람용) 동시 출력 — save_map.sh 헤더 주석 참고
ros2 run r2d2_navigation save_map.sh                        # → maps/r2d2_home_posegraph.* + r2d2_home.*
#           커스텀 경로: save_map.sh /tmp/room 등. 저장 후 install 사본 갱신:
colcon build --packages-select r2d2_navigation

# 3) 내비: posegraph 로드 + localization(자기위치) + nav2 core
ros2 launch r2d2_navigation navigation.launch.py \
    posegraph:=$(ros2 pkg prefix r2d2_navigation)/share/r2d2_navigation/maps/r2d2_home_posegraph
#    원격 RViz: ros2 launch r2d2_bringup remote.launch.py \
#        rviz_config:=$(ros2 pkg prefix r2d2_navigation)/share/r2d2_navigation/rviz/nav2_config.rviz
#    ① 2D Pose Estimate로 실제 위치 지정 → ② 2D Goal Pose로 목표 발행
```

(구 `maps/placeholder.*` + map_server 방식은 hector 제거와 함께 폐지 — navigation의
지도 입력은 posegraph 전용. headless 스택 검증에는 `posegraph:=`에 실제 지도 필요)

### 검증 상태 (nav2)
- 1.3.13 파라미터 실측 통과: SmacPlanner→NavFn 교체, MPPI DiffDrive,
  NavigateToPoseNavigator, behavior spin복원, 전 서버 configure (headless)
- ✅ 2026-10-19 (rf2o+toolbox 전환): LiDAR+모의 원운동 odom TF로 매핑→serialize→
  navigation localization 로드까지 headless e2e 통과 — `/odom` ~10Hz,
  `map→base_link` TF, `/map`(123×102 grid) 발행
- ⬜ 실물 e2e (MCU 연결 후): 주행 매핑→/odom 품질→costmap→경로추종

## 7. 원격 PC 세팅 (RViz — slam_toolbox 매핑/내비 모니터링)

Pi에는 디스플레이가 없으므로 `/map` 시각화·지도 저장·2D Pose Estimate는 원격 PC의
RViz 2가 담당한다. **원격 PC는 노드를 직접 실행하지 않는다** — 같은 DDS 도메인으로
Pi의 토픽/TF를 구독만 하면 되므로 노드(coin_d4_driver, rf2o, slam_toolbox, motor)를
원격에 빌드·실행할 필요가 없다.

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
- **지도 저장**: 매핑 세션(Pi)에서 `ros2 run r2d2_navigation save_map.sh` —
  serialize_map은 Pi의 slam_toolbox 서비스 호출이라 **Pi에서 실행**해야 한다
  (pgm/yaml 부분만 원격 실행 가능). 출력 `_posegraph.{posegraph,data}` + `.pgm/.yaml`은
  git 추적 위치 `src/r2d2_navigation/maps/`에 저장 — 원격에서 저장했다면 Pi의 같은
  디렉터리로 복사(rsync/git) 후 Pi에서 `colcon build --packages-select r2d2_navigation`
- **내비**: `remote.launch.py rviz_config:=…/nav2_config.rviz` → 2D Pose Estimate/Goal 사용

#### 트러블슈팅 — RViz에 "이전 맵"이 남는 경우 (2026-10-18 실증)

세션 지도는 메모리에 있고(슬램 자체는 디스크 영속성 없음 — 지도를 떠나는 건
serialize 뿐), 그런데도 이전 세션 지도가 뜨는 원인은 둘뿐:

1. **이전 bringup/nav 세션이 안 죽고 좀비로 살아있음** — 중복 `slam_toolbox`가
   자기 `/map`(TRANSIENT_LOCAL 래치)을 계속 뿌려 RViz에 이전/신규 지도가 섞이고,
   `lidar_node` 둘이 `/dev/ttyAMA1`을 잡아먹는다. Ctrl+C를連打하면 launch가
   자식을 SIGKILL까지 못 끝낸 이력이 있으므로 확인:
   `pgrep -af "slam_toolbox|rf2o_laser_odometry|single_coin_d4|motor_node|static_transform_publisher"`
   → bringup/navigation launch가 **기동 앞에 중복 노드를 감지하면 아예 중단**하도록
   가드를 넣었다 (`_guard_against_stale_session`)
2. **RViz의 Map 디스플레이는 타임아웃이 없어** 퍼블리셔가 사라져도 마지막 래치
   지도를 계속 보여준다 → RViz 재시작(또는 display 껐다 켜기)이 필요

## 빌드

```bash
cd ~/workspace/Neo-R2D2/ros2
source /opt/ros/jazzy/setup.bash
git clone -b jazzy https://github.com/ROBOTIS-GIT/coin_d4_driver.git src/coin_d4_driver   # 최초 1회
git clone -b ros2 https://github.com/MAPIRlab/rf2o_laser_odometry.git src/rf2o_laser_odometry  # 최초 1회
#   ※ 클론 후: rm -rf src/rf2o_laser_odometry/.git + package.xml 패치(§3 — 이미 반영됨)
git clone https://github.com/kimsniper/ros2_mpu6050.git src/ros2_mpu6050  # 최초 1회
sudo apt install libi2c-dev i2c-tools                     # IMU 드라이버에 필요 (설치 완료)
sudo apt install ros-jazzy-slam-toolbox                   # SLAM (설치 완료: 2.8.5)
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
| bringup 기동 | ✅ motor(mock)+LiDAR+rf2o+slam_toolbox — `/odom` 10Hz, `/map`+TF 체인 실동작 (2026-10-19, hector 제거 후) |
| nav2 스택 headless 검증 | ✅ NavFn/MPPI-DiffDrive/BT(기본 트리)/behavior/velocity_smoother 전 서버 configure (mock). localization 로드 e2e: posegraph→`map→base_link` TF 확인 |
| nav2 실물 e2e | ⬜ MCU 연결 후 주행 (§6 검증 상태) |
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
5. SLAM: rviz2(원격 PC)에서 `/map` + `/scan` + `/odom` + TF 표시
   (`ros2 topic hz /odom` ≈ 스캔률, `view_frames`로 map→odom→base_link→base_laser 확인)
6. 충전 독에 물리면 move가 차단되는지 `gin` 응답으로 확인
7. IMU(MPU6050): VCC→3.3V, GND, SDA/SCL을 쓰는 버스의 GPIO에 (i2c-1 = GPIO2/3),
   AD0는 GND(0x68) 또는 VCC(0x69 — `i2c_address` 파라미터로 지원).
   `i2cdetect -y 1`에 68 표시 확인 →
   `ros2 run ros2_mpu6050 ros2_mpu6050_calibrate`로 offset 측정 →
   `config/params.yaml` 기입 → `ros2 launch ros2_mpu6050 ros2_mpu6050.launch.py`
   → `ros2 topic echo /imu/mpu6050 --once`

## 알려진 사항

- `src/coin_d4_driver`, `src/rf2o_laser_odometry`은 vendoring 완료(네스트 .git 제거,
  본 리포에 커밋됨). `src/ros2_mpu6050`도 커밋 시 `.git` 제거 필요.
  (구 `src/ldlidar_ros2`는 COIN-D3 교체로, `src/hector_slam`은 rf2o+slam_toolbox
  교체로 2026-10-19 삭제)
- `r2d2_link.py`는 `r2d2` 패키지의 벤더본 — 원본 프로토콜 변경 시 동기화 필요
- `build/`, `install/`, `log/`은 `ros2/.gitignore`로 제외


## 구동팁
- ros2 run teleop_twist_keyboard teleop_twist_keyboard 를 이용하여 모터제어