# Robot WebSocket Client

이 문서는 `robot_client.py`를 기반으로 한 로봇 제어용 Python 클라이언트 구현과 사용법을 정리합니다.

## 1. 목적

`robot_client.py`는 Android 앱에서 사용하는 로봇 명령 프로토콜에 맞춰 WebSocket을 통해 JSON 명령을 전송하는 간단한 Python 클라이언트입니다.

## 2. 주요 기능

- WebSocket 서버에 연결
- JSON 명령을 텍스트 프레임으로 전송
- 서버 응답을 받아 JSON으로 파싱
- 명령어별 옵션 지원
- CLI 기반 실행 및 도움말 지원

## 3. 주요 파일

- `robot_client.py`: 클라이언트 구현 파일
- `tests/test_robot_client.py`: 클라이언트 기능 검증용 테스트 파일

## 4. CLI 사용법

### 기본 명령 구문

```bash
python3 robot_client.py <COMMAND> [OPTIONS]
```

### 지원 명령 목록

- `grantAccess`
- `getWifiList`
- `connectWifi`
- `face_detection`
- `mute`
- `power`
- `voice_recognition`
- `user_control`
- `change_name`
- `paired_list`
- `unpair`
- `move`
- `head-angle`
- `head-shift`
- `head-dir`
- `mode`
- `projector`
- `arm`
- `lightsaber`
- `led`
- `lcd`
- `debug`
- `ready`
- `reset-wdt`
- `gin`
- `play_sound`
- `self_update`
- `self_update_unsafe`
- `reset_mcu`

### 기본 옵션

- `--host`: WebSocket 호스트 (기본값: `127.0.0.1`)
- `--port`: WebSocket 포트 (기본값: `8887`)
- `--path`: WebSocket 경로 (기본값: `/`)
- `--json`: 추가 JSON 필드 객체
- `--enable`: `true/false` 스타일 플래그
- `--power`: 정수값
- `--angle`: 정수값
- `--dir`: 정수값
- `--mode`: 정수값
- `--sound_id`: 정수값
- `--uuid`: 문자열 UUID
- `--ssid`: Wi-Fi SSID
- `--password`: Wi-Fi 비밀번호
- `--new_name`: 로봇 이름

### 명령 실행 예제

```bash
python3 robot_client.py move --power 1 --angle 0
python3 robot_client.py mode --mode 2
python3 robot_client.py head-angle --angle 45
python3 robot_client.py led --json '{"r":255,"g":0,"b":128}'
python3 robot_client.py lcd --json '{"s":1,"l":3}'
python3 robot_client.py grantAccess --uuid demo-uuid --new_name Robot
python3 robot_client.py connectWifi --ssid MyWiFi --password secret
python3 robot_client.py --cmd play_sound --sound_id 10
```

### 도움말 확인

```bash
python3 robot_client.py --help
```

## 5. 내부 구현 요약

- `RobotWebSocketClient.connect()`: WebSocket 핸드셰이크 수행
- `RobotWebSocketClient.send_command()`: JSON 페이로드를 웹소켓 프레임으로 전송하고 응답 대기
- `_encode_frame()`, `_recv_frame()`: 간단한 WebSocket 프레임 인코딩/디코딩
- `build_parser()`: CLI 파서 생성
- `build_payload_from_args()`: CLI 인자를 JSON 페이로드로 변환

## 6. 테스트

```bash
python3 -m unittest discover -s tests -v
```

`tests/test_robot_client.py`는 다음을 검증합니다.

- WebSocket 클라이언트 기본 동작
- 서브커맨드 CLI 파싱
- 명령 송수신 라운드트립

## 7. 주의사항

- 현재 구현은 간단한 WebSocket 클라이언트로, SSL/TLS나 복잡한 핸드쉐이크 검증은 지원하지 않습니다.
- 서버 측이 표준 WebSocket 핸드셰이크와 텍스트 프레임을 지원해야 정상 동작합니다.
