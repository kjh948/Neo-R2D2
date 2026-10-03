# ros2 — R2-D2 모터 제어 (ROS 2 Jazzy)

`geometry_msgs/Twist` (`/cmd_vel`)를 받아 R2-D2 MCU의 UART
`{"cmd":"move","power":P,"angle":A}` 프레임으로 변환하는 ROS 2 패키지 모음.

## 구조

- `src/r2d2_motor/` — 모터 제어 노드 (`rclpy`)
  - `motor_node.py` — `/cmd_vel` 구독, 300ms 주기 전송, 타임아웃 자동 정지
  - `twist_mapping.py` — Twist → power/angle 매핑 (순수 함수, 단위 테스트 포함)
  - `r2d2_link.py` — `r2d2/transport.py`+`commander.py`의 최소 벤더본(move/ready/gin)
  - `mcu_client.py` — 노드 ↔ UART 링크 브리지 (gin 응답 charging 상태 추적)

## 전제

- **r2d2 앱과 동시에 실행 불가**: 노드가 UART(`/dev/ttyS2`)를 직접 점유한다.
  실행 전에 r2d2 앱/서버를 종료할 것.
- 매핑 방식(차동조향식):
  - `linear.x` 우세 → 전진(`angle 0`) / 후진(`angle 180`), `|linear.x|/max` → power 0~100
  - `angular.z` 우세 → 좌측 strafing(`+90`) / 우측 strafing(`-90`) — 이 MCU는 제자리 회전을 못 함
  - 방향이 뒤바뀌면 `invert_strafe:=true`
- 안전: `cmd_vel_timeout`(기본 0.5s) 동안 Twist 없으면 `power:0` 정지 프레임 전송.
  충전 중(`gin` 응답의 `charging-status != 0`)이면 `Commander`가 이동을 자동 차단.

## 빌드 & 실행

```bash
cd ~/workspace/Neo-R2D2/ros2
source /opt/ros/jazzy/setup.bash
colcon build --packages-select r2d2_motor
source install/setup.bash

# 실물
ros2 launch r2d2_motor motor.launch.py

# 하드웨어 없이 프레임 로그로 확인
ros2 launch r2d2_motor motor.launch.py mock:=true
ros2 topic pub -r 10 /cmd_vel geometry_msgs/msg/Twist "{linear: {x: 0.3}, angular: {z: 0.0}}"
```

튜닝 파라미터: `max_linear_velocity`(0.5), `max_angular_velocity`(1.0),
`cmd_vel_timeout`(0.5), `repeat_period`(0.3), `serial_port`, `invert_strafe`.

## 테스트

```bash
colcon test --packages-select r2d2_motor && colcon test-result --verbose
# 또는 맵핑만 빨리
PYTHONPATH=src/r2d2_motor python3 -m pytest src/r2d2_motor/test -q
```
