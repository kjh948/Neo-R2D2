# UART 명령어 정리

## 1. UART 통신 개요

이 앱은 UART를 통해 텍스트 기반 JSON 라인 프로토콜로 통신합니다.

- 포트: `/dev/ttyS2`
- 속도: `115200`
- 데이터 비트: `8`
- 정지 비트: `1`
- 메시지 종료: `\n`
- 수신 처리: `\n` 기준으로 한 줄씩 파싱

## 2. 앱 → UART로 보내는 명령어

전송 코드는 [sources/com/bullb/r2d2_nanopisystem/Commander.java](sources/com/bullb/r2d2_nanopisystem/Commander.java)에서 구현됩니다.

### 2.1 기본 명령

| 명령 | JSON 예시 | 설명 |
|---|---|---|
| `ready` | `{"cmd":"ready"}` | 하드웨어 준비 완료 신호 |
| `debug` | `{"cmd":"debug"}` | 디버그 명령 |
| `gin` | `{"cmd":"gin"}` | 상태 요청 |
| `reset-wdt` | `{"cmd":"reset-wdt"}` | watchdog reset |
| `shut-down` | `{"cmd":"shut-down"}` | 전원 종료 |

### 2.2 모션/제어 명령

| 명령 | JSON 예시 | 설명 |
|---|---|---|
| `move` | `{"cmd":"move","power":1,"angle":0}` | 이동/모션 제어 |
| `head-angle` | `{"cmd":"head-angle","angle":30}` | 머리 각도 제어 |
| `head-shift` | `{"cmd":"head-shift","angle":20}` | 머리 좌우 이동 |
| `head-dir` | `{"cmd":"head-dir","dir":1}` | 머리 방향 제어 |
| `mode` | `{"cmd":"mode","mode":1}` | 모드 변경 |
| `projector` | `{"cmd":"projector","mode":1}` | 프로젝터 on/off |
| `arm` | `{"cmd":"arm","power":1}` | 팔 동작 |
| `lightsaber` | `{"cmd":"lightsaber","power":1}` | 라이트세이버 동작 |
| `d-head-power` | `{"cmd":"d-head-power","power":80}` | 헤드 전원 제어 |
| `d-leg-power` | `{"cmd":"d-leg-power","power":80}` | 다리 전원 제어 |

### 2.3 LED/LCD 명령

| 명령 | JSON 예시 | 설명 |
|---|---|---|
| `led` | `{"cmd":"led","r":255,"g":0,"b":0,"y":0}` | LED 제어 |
| `lcd` | `{"cmd":"lcd","s":1,"l":2}` | LCD 제어 |

## 3. UART → 앱으로 들어오는 명령어

수신 처리는 [sources/com/bullb/r2d2_nanopisystem/SerialPort/SerialPortCommandReceiver.java](sources/com/bullb/r2d2_nanopisystem/SerialPort/SerialPortCommandReceiver.java)에서 수행됩니다.

### 3.1 `play_sound`

- 예시: `{"cmd":"play_sound","sound_id":4,"interrupt":1}`
- 동작: 사운드 재생

### 3.2 `ready`

- 예시: `{"cmd":"ready"}`
- 동작: 준비 완료 상태 해제/취소

### 3.3 `btn`

- 예시: `{"cmd":"btn","value":1}`
- 동작:
  - `1`: 전원 종료
  - `2`: AP 모드 토글
  - `3`: 페어링 모드 시작/중지
  - `4`: 라이트세이버
  - `5`: 팔 동작
  - `6`: 순찰 모드

### 3.4 `gin`

- 예시: `{"cmd":"gin","batt":80,"charging-status":0,"arm":1,"lightsaber":0,"projector":1,"mode":2,"error":"..."}`
- 동작: 로봇 상태 업데이트

## 4. 공통 필드

### 4.1 공통 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `cmd` | string | 명령 종류 |
| `power` | int | 전원/출력값 |
| `angle` | int | 각도값 |
| `dir` | int | 방향값 |
| `mode` | int | 모드값 |
| `r` | int | 빨강값 |
| `g` | int | 초록값 |
| `b` | int | 파랑값 |
| `y` | int | 노랑값 |
| `s` | int | LCD short 값 |
| `l` | int | LCD long 값 |
| `sound_id` | int | 사운드 ID |
| `interrupt` | int | 인터럽트 여부 |
| `value` | int | 버튼 값 |
| `url` | string | 업데이트 URL |

## 5. 참고 소스

- [sources/com/bullb/r2d2_nanopisystem/SerialPort/SerialPort.java](sources/com/bullb/r2d2_nanopisystem/SerialPort/SerialPort.java)
- [sources/com/bullb/r2d2_nanopisystem/SerialPort/SerialPortService.java](sources/com/bullb/r2d2_nanopisystem/SerialPort/SerialPortService.java)
- [sources/com/bullb/r2d2_nanopisystem/Commander.java](sources/com/bullb/r2d2_nanopisystem/Commander.java)
- [sources/com/bullb/r2d2_nanopisystem/SerialPort/SerialPortCommandReceiver.java](sources/com/bullb/r2d2_nanopisystem/SerialPort/SerialPortCommandReceiver.java)
- [sources/com/bullb/r2d2_nanopisystem/Model/Command.java](sources/com/bullb/r2d2_nanopisystem/Model/Command.java)
- [sources/com/bullb/r2d2_nanopisystem/Model/GinResponse.java](sources/com/bullb/r2d2_nanopisystem/Model/GinResponse.java)
