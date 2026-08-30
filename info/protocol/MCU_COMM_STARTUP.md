# MCU 통신 — 시작(Startup) 시 플로우 요약

다음 문서는 `apk` 폴더의 소스 코드를 분석해 정리한, 앱이 시작될 때 MCU와 어떻게 통신하는지에 대한 요약이다.

## 개요
- 통신 방식: 시리얼 포트(로컬 장치 파일, newline-terminated JSON 메시지)
- 장치 파일: 기본값 `/dev/ttyAMA0`
- 기본 전송 속도: 115200 bps
- 네이티브 라이브러리: `libr2d2` (SerialPortOrangePi), `libfriendlyarm-hardware` (HardwareControler 네이티브 API)

## 초기화 및 런타임 플로우 (요약)
1. `MainActivity.onCreate()`
   - 앱 초기화 중 `SerialPort.getInstance(context)` 호출로 `SerialPort` 싱글턴 생성.
   - `SerialPort.startService()` 호출 → `SerialPortService` 시작.
   - `SerialPort.setSerialPortSendCallback(...)` 등록.
   - `EventHandler.softwareReady()` 호출 → 내부에서 `Commander.softwareReady()` 호출.
2. `Commander.softwareReady()`
   - MCU에 준비 메시지 전송: `{"cmd":"ready"}` (newline 추가되어 전송).
3. `SerialPort` 초기화
   - 내부적으로 `SerialPortOrangePi` 생성: new File(`/dev/ttyAMA0`), baud 115200.
   - `SerialPortOrangePi`는 네이티브 메서드로 파일 디스크립터를 얻어 `InputStream`/`OutputStream`을 만든다.
4. `SerialPortService` (백그라운드 스레드)
   - 서비스가 시작되면 읽기 전용 스레드가 생성되어 시리얼 입력을 한 바이트씩 읽음.
   - 개행문자(`\n`, LF, value 10)를 만나면 그동안 읽은 바이트를 문자열로 변환.
   - 읽은 문자열 중 JSON 객체(첫 문자 '{' && 길이>2)인 경우 `LocalBroadcast`로 액션 `serial_port_receiver`에 `msg` 엑스트라로 전송.
5. `MainActivity`의 수신 처리
   - `LocalBroadcastManager`로 `serial_port_receiver`를 수신하도록 등록.
   - 수신 시 `SerialPortCommandReceiver.interpretCommand(msg)` 호출.
6. `SerialPortCommandReceiver`
   - 수신된 JSON을 `Command` 또는 `GinResponse` 등으로 파싱하여 처리.
   - 예: `cmd=="play_sound"` → `EventHandler.playSound(...)`, `cmd=="ready"` → `eventHandler.cancelReady()` 등.
   - `GinResponse`(배터리, 충전상태, arm/lightsaber 상태 등)를 받아 `RobotPreference`를 갱신하고 UI/LED 등 상태 업데이트를 트리거.
7. 주기적 상태 요청
   - `MainActivity`에서 타이머로 5초마다 `commander.gin()` 호출 → MCU에 `{"cmd":"gin"}` 전송하여 상태를 요청.

## 메시지 포맷 예시
- 앱(안드로이드) → MCU: JSON 객체, newline으로 종료
   - 예: `{"cmd":"gin"}\n`
   - 예: `{"cmd":"move","power":50,"angle":120}\n`
   - 예: `{"cmd":"play_sound","soundId":9}\n`
- MCU → 앱: JSON 객체(라인 단위)
  - `GinResponse` 예: `{"cmd":"gin","batt":85,"charging-status":0,"arm":1,...}`

## 관련 파일(분석 출처)
- `apk/sources/com/bullb/r2d2_nanopisystem/MainActivity.java` — 앱 시작, 서비스 시작, 브로드캐스트 수신 등록
- `apk/sources/com/bullb/r2d2_nanopisystem/Commander.java` — 앱 → MCU 전송(명령 생성)
- `apk/sources/com/bullb/r2d2_nanopisystem/SerialPort/SerialPort.java` — 시리얼 포트 추상화, `send()` 구현
- `apk/sources/com/bullb/r2d2_nanopisystem/SerialPort/SerialPortOrangePi.java` — 네이티브 오픈/스트림 생성 (`System.loadLibrary("r2d2")`)
- `apk/sources/com/bullb/r2d2_nanopisystem/SerialPort/SerialPortService.java` — 수신 루프(개행 단위), `serial_port_receiver` 브로드캐스트
- `apk/sources/com/bullb/r2d2_nanopisystem/SerialPort/SerialPortCommandReceiver.java` — 수신 JSON 파싱 및 처리
- `apk/sources/com/bullb/r2d2_nanopisystem/Model/GinResponse.java` — MCU→앱 응답 필드
- `apk/sources/com/friendlyarm/AndroidSDK/HardwareControler.java` — 네이티브 하드웨어 API (`openSerialPort`, `read`, `write` 등)

## 요약
- 앱은 `/dev/ttyAMA0`(115200) 시리얼을 통해 MCU와 JSON 기반 텍스트 프로토콜로 통신한다.
- 앱 시작 시 `ready` 메시지를 보내고 주기적으로 `gin` 요청을 보내 MCU 상태를 갱신한다.
- MCU로부터 수신된 JSON은 브로드캐스트로 전달되어 `SerialPortCommandReceiver`가 처리하고 앱 상태/UI를 갱신한다.

필요하면 문서에 예시 메시지 더 추가하거나 시퀀스 다이어그램으로 시각화해 드리겠습니다.
