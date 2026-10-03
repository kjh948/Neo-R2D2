# ros2 — Neo-R2D2 ROS 2 워크스페이스 (Jazzy)

ROS 2 Jazzy 위에서 R2-D2를 구동하는 colcon 워크스페이스입니다.
`/cmd_vel`(Twist) → MCU 모터 제어, LDROBOT LiDAR, hector SLAM까지 한 번에 올립니다.

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
```

## 패키지 구성

| 패키지 | 종류 | 설명 |
|---|---|---|
| `src/r2d2_motor/` | 직접 개발 (ament_python) | Twist → MCU `move` 프레임 변환 노드 |
| `src/r2d2_bringup/` | 직접 개발 (ament_python) | motor + lidar + slam 통합 launch |
| `src/ldlidar_ros2/` | 외부 클론 | LDROBOT LiDAR 드라이버 (sdk 서브모듈 포함) |
| `src/hector_slam_humble/` | 외부 클론 | hector SLAM (15개 패키지) |

## 1. 모터 제어 — `r2d2_motor`

`geometry_msgs/Twist`를 구독해 R2-D2 MCU의 UART JSON 프로토콜
(`{"cmd":"move","power":P,"angle":A}`, power 0~100)로 변환합니다.
MCU는 홀로노믹(방향각) 구동이라 전방 0 / 후방 180 / 좌우 ±90만 사용 (차동조향식 매핑).

### 파일 구조

- `motor_node.py` — `/cmd_vel` 구독, 300ms 주기 move 반복 전송, 타임아웃 자동 정지
- `twist_mapping.py` — Twist → power/angle 순수 함수 (단위 테스트 11개)
- `r2d2_link.py` — 리포의 `r2d2/transport.py` + `commander.py` 최소 **벤더본**
  (move/ready/gin). UART 프로토콜이 바뀌면 이 파일도 동기화 필요
- `mcu_client.py` — 링크 브리지, `gin` 응답의 `charging-status` 추적
- `mcu_config.py` — 앱 설정(`r2d2/config_local.json`)에서 직렬 포트 기본값 로드

### 동작 규칙

- **시작 handshake**: `{"cmd":"ready"}` 전송 (Android 앱과 동일), 5초마다 `{"cmd":"gin"}` 상태 요청
- **매핑**: `|linear.x|/(max)` 또는 `|angular.z|/(max)` 중 우세 성분 → power 0~100.
  전진/후진/좌strafe/우strafe 4방향. 제자리 회전은 불가(strafe로 대체)
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

## 3. SLAM — `hector_slam_humble`

- 출처: `https://github.com/KiiiLin/hector_slam_humble` (Humble용, 15개 패키지)
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

## 4. 통합 — `r2d2_bringup`

```bash
ros2 launch r2d2_bringup r2d2_bringup.launch.py                      # motor+lidar+slam
ros2 launch r2d2_bringup r2d2_bringup.launch.py mock:=true enable_lidar:=false
ros2 launch r2d2_bringup r2d2_bringup.launch.py lidar_product:=LDLiDAR_LD14P
```

인자: `enable_motor`/`enable_lidar`/`enable_slam`, `mock`,
`lidar_port`(ttyAMA1), `lidar_product`, `lidar_baudrate`, `hector_params`(yaml 경로)

## 빌드

```bash
cd ~/workspace/Neo-R2D2/ros2
source /opt/ros/jazzy/setup.bash
git clone --recursive https://github.com/ldrobotSensorTeam/ldlidar_ros2.git src/   # 최초 1회
git clone https://github.com/KiiiLin/hector_slam_humble.git src/                   # 최초 1회
colcon build                    # python은 --symlink-install 권장
source install/setup.bash
```

## 검증 상태

| 항목 | 상태 |
|---|---|
| Twist→move 매핑 단위 테스트 | ✅ 11/11 (`colcon test`) |
| motor 노드 mock 스모크 | ✅ ready/gin/0.3→`power60 angle0` 300ms 반복/타임아웃 정지 확인 |
| config 반영(ttyAMA0) | ✅ `config_local.json` 자동 로드, 파라미터 오버라이드 확인 |
| 전체 패키지 빌드 | ✅ 17 packages, 0 failures (Pi5, Jazzy) |
| bringup 기동 | ✅ motor(mock)+hector 파라미터 반영 확인 (LiDAR 제외) |
| ldlidar 실물 | ⬜ 미연결 (빌드/launch 인자만 검증) |
| MCU 실물 UART | ⬜ 미검증 |

## 실물 연결 시 체크리스트

1. r2d2 앱 종료 (ttyAMA0 점유 확인: `fuser /dev/ttyAMA0`)
2. strafe 방향 확인: `ros2 topic pub /cmd_vel ... angular:{z:0.5}` → 로봇이 왼쪽으로
   밀려야 정상. 반대면 `invert_strafe:=true`
3. 속도 체감 보고 `max_linear_velocity` 조정 (power 스케일)
4. LiDAR: `/dev/ttyAMA1` 없으면 Pi UART 오버레이(`config.txt`의 `enable_uart`/
   `dtoverlay=uart1`) 확인, `ros2 topic echo /scan --once`로 데이터 수신 확인
5. SLAM: rviz2(원격 PC)에서 `/map` + `/scan` + TF 표시
6. 충전 독에 물리면 move가 차단되는지 `gin` 응답으로 확인

## 알려진 사항

- `src/ldlidar_ros2`, `src/hector_slam_humble`는 별도 git 저장소(클론)로 리포에
  미추적. 부모 리포에 커밋하려면 git submodule 등록이나 `.git` 제거 후 vendoring 결정 필요
- `r2d2_link.py`는 `r2d2` 패키지의 벤더본 — 원본 프로토콜 변경 시 동기화 필요
- `build/`, `install/`, `log/`은 `ros2/.gitignore`로 제외
