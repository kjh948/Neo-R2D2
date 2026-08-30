# 웹서버/명령 처리 매핑표

## 1. 처리 흐름

클라이언트가 JSON 메시지를 보내면 다음 흐름으로 처리됩니다.

1. 메시지를 한 줄씩 파싱합니다.
2. `cmd` 필드를 기준으로 분기합니다.
3. 각 명령은 전용 핸들러로 라우팅됩니다.
4. 일부 명령은 UART로 다시 전달됩니다.

## 2. 명령별 처리 매핑

| 명령 | 분류 | 처리 방식 | 응답 예시 |
|---|---|---|---|
| `grantAccess` | 인증 | 클라이언트 UUID 등록 | `{"resultCode":0,"cmd":"grantAccess","robot":...}` |
| `getWifiList` | API | 저장된 Wi-Fi 목록 반환 | `{"resultCode":0,"cmd":"getWifiList","wifi_list":...}` |
| `connectWifi` | API | Wi-Fi 연결 요청 처리 | `{"resultCode":0,"cmd":"connectWifi","connected":true}` |
| `face_detection` | 설정 | 얼굴 인식 on/off | `{"resultCode":0,"cmd":"face_detection","enabled":true}` |
| `mute` | 설정 | 음소거 on/off | `{"resultCode":0,"cmd":"mute","enabled":true}` |
| `power` | 제어 | 전원 종료 명령을 UART로 전달 | `{"resultCode":0,"cmd":"power","poweredOff":true}` |
| `voice_recognition` | 설정 | 음성 인식 on/off | `{"resultCode":0,"cmd":"voice_recognition","enabled":true}` |
| `user_control` | 제어 | 사용자 제어 권한 상태 변경 | `{"resultCode":0,"cmd":"user_control","controlling":true}` |
| `change_name` | 설정 | 로봇 이름 변경 | `{"resultCode":0,"cmd":"change_name","robotName":"..."}` |
| `paired_list` | API | 등록된 클라이언트 목록 반환 | `{"resultCode":0,"cmd":"paired_list","clients":...}` |
| `unpair` | API | 클라이언트 제거 | `{"resultCode":0,"cmd":"unpair","clients":...}` |
| `move` | 모션 | 이동/모션 상태 저장 및 UART 전송 | `{"resultCode":0,"cmd":"move","motion":...}` |
| `head-angle` | 모션 | 머리 각도 상태 저장 및 UART 전송 | `{"resultCode":0,"cmd":"head-angle","angle":...}` |
| `head-shift` | 모션 | 머리 이동 상태 저장 및 UART 전송 | `{"resultCode":0,"cmd":"head-shift","angle":...}` |
| `head-dir` | 모션 | 머리 방향 저장 및 UART 전송 | `{"resultCode":0,"cmd":"head-dir","dir":...}` |
| `mode` | 모션 | 모드 변경 및 UART 전송 | `{"resultCode":0,"cmd":"mode","mode":...}` |
| `projector` | 제어 | 프로젝터 명령 전달 | `{"resultCode":0,"cmd":"projector","mode":...}` |
| `arm` | 제어 | 팔 제어 명령 전달 | `{"resultCode":0,"cmd":"arm","power":...}` |
| `lightsaber` | 제어 | 라이트세이버 제어 | `{"resultCode":0,"cmd":"lightsaber","power":...}` |
| `led` | 제어 | LED 색상 상태 저장 및 UART 전송 | `{"resultCode":0,"cmd":"led","led":...}` |
| `lcd` | 제어 | LCD 상태 저장 및 UART 전송 | `{"resultCode":0,"cmd":"lcd","lcd":...}` |
| `debug` | 디버그 | 디버그 명령 전달 | `{"resultCode":0,"cmd":"debug","debug":true}` |
| `ready` | 시그널 | 준비 완료 신호 전달 | `{"resultCode":0,"cmd":"ready","ready":true}` |
| `reset-wdt` | 제어 | watchdog reset 전달 | `{"resultCode":0,"cmd":"reset-wdt","reset":true}` |
| `gin` | 상태 | 로봇 상태 업데이트 반영 | `{"resultCode":0,"cmd":"gin","battery":...}` |
| `play_sound` | 제어 | 사운드 재생 요청 전달 | `{"resultCode":0,"cmd":"play_sound","soundId":...}` |
| `self_update` | 업데이트 | 업데이트 요청 처리 | `{"resultCode":0,"cmd":"self_update","updateStarted":true}` |
| `self_update_unsafe` | 업데이트 | 안전하지 않은 업데이트 요청 처리 | `{"resultCode":0,"cmd":"self_update_unsafe","updateStarted":true}` |
| `reset_mcu` | 제어 | MCU reset 명령 전달 | `{"resultCode":0,"cmd":"reset_mcu","resetMcu":true}` |

## 3. 구현 파일

- Python 구현: [robot_command_handler.py](robot_command_handler.py)
- 테스트: [tests/test_robot_command_handler.py](tests/test_robot_command_handler.py)
