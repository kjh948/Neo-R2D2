# UART Client

`uart_client.py`는 Android 앱이 사용하는 UART JSON 라인 프로토콜을 따르는 Python 클라이언트입니다.

## 1. 개요

- 전송 방식: UART 직렬 통신
- 디바이스: 기본 `/dev/ttyS2`
- 전송 속도: 기본 `115200`
- 데이터 비트: `8`
- 정지 비트: `1`
- 패리티: 없음
- 메시지 종료: `\n`

이 클라이언트는 JSON 객체를 직렬 포트로 보내고, 응답을 한 줄 단위로 읽어 JSON으로 파싱합니다.

## 2. 구현 내용

### 2.1 `UartCommandClient`

- `connect()`: UART 포트 열기
- `send_command(payload)`: JSON 또는 문자열 명령을 `\n`로 종료해 전송
- `read_response()`: 한 줄 응답을 읽고 JSON으로 변환
- `close()`: 포트 닫기

### 2.2 전송 형식

- Python dict 입력은 JSON으로 직렬화됨
- 문자열 입력은 그대로 전송됨
- 모든 메시지는 `\n`로 끝남
- 예: `{"cmd":"move","power":1,"angle":0}\n`

### 2.3 pyserial 지원

- `pyserial`가 설치되어 있으면 자동으로 사용합니다.
- 설치되어 있지 않으면 POSIX `termios`/`os` 기반으로 기본 UART 열기를 시도합니다.

## 3. CLI 사용법

### 3.1 기본 실행

```bash
python3 uart_client.py <COMMAND> [OPTIONS]
```

### 3.2 공통 옵션

- `--device`: UART 디바이스 경로 (기본: `/dev/ttyS2`)
- `--baudrate`: 전송 속도 (기본: `115200`)
- `--timeout`: 읽기 타임아웃 초 (기본: `1.0`)
- `--json`: 추가 JSON 필드
- `--enable`: `true`/`false` 형식 boolean
- `--power`, `--angle`, `--dir`, `--mode`, `--sound_id`, `--uuid`, `--ssid`, `--password`, `--new_name`

### 3.3 명령어 예제

```bash
python3 uart_client.py move --power 1 --angle 0
python3 uart_client.py mode --mode 2
python3 uart_client.py head-angle --angle 45
python3 uart_client.py led --json '{"r":255,"g":0,"b":128}'
python3 uart_client.py lcd --json '{"s":1,"l":3}'
python3 uart_client.py --device /dev/ttyS2 --baudrate 115200 ready
python3 uart_client.py --cmd "shut-down"
python3 uart_client.py play_sound --sound_id 4
```

### 3.4 헬프 보기

```bash
python3 uart_client.py --help
```

## 4. 명령어 목록

### 4.1 기본 UART 명령

- `ready`
- `debug`
- `gin`
- `reset-wdt`
- `shut-down`

### 4.2 모션/제어 명령

- `move`
- `head-angle`
- `head-shift`
- `head-dir`
- `mode`
- `projector`
- `arm`
- `lightsaber`
- `d-head-power`
- `d-leg-power`

### 4.3 LED/LCD 명령

- `led`
- `lcd`

### 4.4 사운드 명령

- `play_sound`

## 5. 예시

### 5.1 이동 명령

```bash
python3 uart_client.py move --power 1 --angle 0
```

전송되는 패킷:

```json
{"cmd":"move","power":1,"angle":0}
```

### 5.2 LED 변경

```bash
python3 uart_client.py led --json '{"r":255,"g":128,"b":0,"y":0}'
```

전송되는 패킷:

```json
{"cmd":"led","r":255,"g":128,"b":0,"y":0}
```

### 5.3 장치 연결 테스트

```bash
python3 uart_client.py --device /dev/ttyS2 ready
```

## 6. 테스트

```bash
python3 -m unittest discover -s tests -v
```

`tests/test_uart_client_serial.py`는 CLI 파싱과 JSON 라인 포맷을 검증합니다.

## 7. 주의점

- 이 코드는 실제 UART 디바이스가 있어야 정상 동작합니다.
- `pyserial`이 없는 환경에서는 `termios` 기반 POSIX 직렬 포트를 사용합니다.
- Windows에서는 기본 `termios` 대체 구현이 포함되어 있지 않습니다.
