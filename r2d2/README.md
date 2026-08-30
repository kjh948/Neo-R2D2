# r2d2 — 안드로이드 앱의 Python 포팅

`apk/sources/com/bullb/r2d2_nanopisystem/` 역컴파일 소스를 기준으로, R2D2
(DeAgostini) 본체에서 **MCU와 웹 콘솔 사이를 담당하는 호스트 프로그램**을
Python으로 옮긴 것입니다. 기존 Android 앱이 하던 역할을 그대로 수행합니다.

- 시리얼(`/dev/ttyS2` 115200 8N1, newline 구분 JSON)으로 MCU와 통신
- 웹 콘솔/페어링된 클라이언트용 WebSocket 명령 서버(`:8887`)
- 카메라 프레임 배포(`:12121`) 및 OpenCV 얼굴 인식·머리 추적
- 효과음 재생(사운드 ID 표), LED/LCD 패턴, 동작 큐(Animation Job)
- 앱 수준 모드 상태 머신(Ready / Sleep / Pair / Patrol / UserControl)
- UDP 디스커버리 브로드캐스트(`:8090`), Wi-Fi AP/페어링 provisioning

범위: **핵심 제어 루프 전체**. Android 전용 API에 붙어 있던 부분(블루투스는
제외)은 리눅스 등가 도구로 교체했고, 프로토콜과 값은 소스에서 직접 확인한
것만 사용했습니다.

## 빠른 시작

```bash
# 하드웨어 없이 전체 루프 확인 (시리얼/카메라/오디오 모두 mock)
python3 -m r2d2 --mock --log-level debug

# 실제 본체
pip install -r r2d2/requirements.txt
python3 -m r2d2 --port /dev/ttyS2 --config /etc/r2d2/config.json
```

`--mock`이면 송신 프레임과 재생될 효과음이 로그로 남고, 시리얼·카메라·오디오를
건드리지 않습니다. 클라이언트 검사에는 저장소의 `info/protocol/web_client.py`를
쓸 수 있습니다.

```bash
python3 info/protocol/web_client.py --port 8887 move --power 50 --angle 0
python3 info/protocol/uart_client.py --device /dev/ttyS2 gin
```

## 모듈 구성

| 파일 | 원본 | 역할 |
|---|---|---|
| `transport.py` | `SerialPort`, `SerialPortService` | 줄 단위 JSON 프레이밍, 수신 스레드, 송신 락 |
| `commander.py` | `Commander` | 호스트→MCU 명령 생성, charging 인터록 |
| `mcu_commands.py` | `SerialPortCommandReceiver` | MCU→호스트 프레임 처리(`play_sound`/`ready`/`btn`/`gin`) |
| `api.py` | `CommandReceiver`, `RobotApiHandler` | 클라이언트 명령 라우팅, 페어링/에러 코드 |
| `ws.py` | `org.java_websocket` | 스탠다드 라이브러리 RFC6455 서버(프레이밍·ping·재조립) |
| `server.py` | `SocketServer`, `SocketConnection` | WebSocket 세션 등록, 브로드캐스트, 제어권 중재 |
| `streaming.py` | `StreamingServer`, `VideoStreamer` | 단일 뷰어 영상 배포(10fps 바이너리 JPEG) |
| `events.py` | `EventHandler`, `Model/EventJob/*` | 동작 큐와 애니메이션 딜레이 |
| `leds.py` | `LEDLightController` | LED 채널 패턴과 우선순위 사다리 |
| `sound.py` | `SoundPlayer` | 사운드 ID→파일 표, 재생/정지 |
| `modes.py` | `ModeControl/*` | Sleep(180s)/Patrol(60s)/Pair(30s)/UserControl |
| `central.py` | `CentralController` | 카메라·얼굴·음성 소유, 음소거 |
| `vision.py` | `FaceDetection`, `CameraController` | LBP cascade 추적, 머리 추적 제어 |
| `voice.py` | `VoiceRecognizer`, `VoiceToEventHandler` | 어구표 → 동작 매핑, 15s 자동 중지 |
| `wifi.py` | `WifiService` | AP 모드와 provisioning(`nmcli`) |
| `discovery.py` | `UDPBroadcastService` | 3초 주기 브로드캐스트 |
| `updater.py` | `SelfUpdate/AppUpdater` | download→install 상태 머신(스테이) |
| `models.py` | `Model/Command`, `GinResponse`, `Robot`, `Client` | 프레임/응답 데이터 형 |
| `state.py` | `RobotPreference` | 상태 저장(원자적 쓰기) |
| `config.py`, `log.py` | `AndroidManifest`, `Log` | 실행 설정(파일→환경변수 우선), 로깅 |
| `app.py`, `main.py`, `__main__.py` | `MainActivity` | 시작 순서 조립과 CLI |

## 프로토콜

### 호스트 → MCU

원본과 동일하게 필드 순서와 `-1` 생략 규칙을 지킵니다.

```json
{"cmd":"ready"}
{"cmd":"gin"}
{"cmd":"reset-wdt"}
{"cmd":"shut-down"}
{"cmd":"move","power":50,"angle":120}
{"cmd":"head-angle","angle":-45}
{"cmd":"head-shift","angle":5}
{"cmd":"head-dir","dir":2}
{"cmd":"mode","mode":9}
{"cmd":"projector","mode":1}
{"cmd":"arm","power":1}
{"cmd":"lightsaber","power":1}
{"cmd":"led","r":2,"b":2,"y":1,"g":2}
{"cmd":"lcd","s":2,"l":1}
{"cmd":"d-head-power","power":80}
{"cmd":"d-leg-power","power":60}
```

`led`/`lcd`는 값이 `-1`이면 해당 키를 **전송하지 않습니다**(펌웨어가 "채널
유지"로 해석). `reset_mcu`는 JSON이 아니라 `''` 두 글자를 그대로 씁니다.

충전 중 인터록(`Commander`에 구현):

- `move`, `head-angle`, `head-shift`, `head-dir` → charging != 0이면 침묵 폐기
- `mode` → charging != 0이고 mode ∈ `{1,2,3,4,5,9,10,12,15}`이면 거부
- 그 외(`0,6,7,8,11,13,14,16..20`)와 모든 액세서리 명령은 충전 중에도 통과

### MCU → 호스트

수신 줄은 `{`로 시작하고 길이가 2보다 같아야 받아들입니다(원본 규칙).

| cmd | 처리 |
|---|---|
| `play_sound` | `{sound_id, interrupt}` → 로컬 재생. 재전송 없음 |
| `ready` | MCU生存 신호로 기록만 |
| `btn` | `1` 전원 / `2` AP 토글 / `3` 페어링 토글 / `4` 라이트세이버 / `5` 팔 / `6` 순찰 |
| `gin` | `{batt, charging-status, arm, lightsaber, projector, lcd_s, lcd_l, error}` → 상태 갱신 + push |

`lcd_s`/`lcd_l`은 플래그가 아니라 **2 이상일 때 열림**입니다. 충전 상태가
`1`(거치)로 바뀌는 즉시 적용되지만 `0`/`2`로 돌아가는 것은 3초 디바운스를
거칩니다. 배터리는 20% 경계를 넘나들 때 조명 사다리를 다시 계산합니다.

### 클라이언트 ↔ 호스트

개념적으로 한 줄(`\n` 구분)의 JSON입니다. 응답은 `resultCode`/`cmd`/`seq`를
담고, 트레일링 개행이 붙은 채 텍스트 프레임으로 나갑니다(원본 동작).

- 인증 필요 없음: `grantAccess` `{uuid, device_name}` → `{"resultCode":0,"cmd":"grantAccess","seq":N,"robot":{…}}`
  - `301` uuid 누락, `401` 미인가(AP 모드·기페어링·페어링 모드 중이 아님). 응답을 보낸 **뒤에** 소켓을 닫습니다
  - 미등록 소켓은 10초 안에 `grantAccess`가 오지 않으면 끊깁니다
- 설정/정보(유효 연결 필요): `getWifiList`, `connectWifi`(`ssid`, `wifi_pw`),
  `face_detection`, `voice_recognition`, `mute`, `power`, `user_control`,
  `change_name`(`new_name`, 16자 제한 → `422`), `paired_list`, `unpair`(`423`)
- 동작(유효 연결 + **페어링 모드(3)가 아닐 때만**): `move`, `move-head`,
  `head-dir`, `projector`, `reset-wdt`, `d-head-power`, `d-leg-power`, `lcd`,
  `led`, `mode`, `play_sound`, `self_update`, `self_update_unsafe`, `reset_mcu`
  — 원본대로 **응답을 보내지 않습니다**
- `self_update`는 배터리가 50%를 넘어야 하고, `self_update_unsafe`는 생략합니다
- 상태 변경은 모든 연결에 `{"cmd":"gin","robot":{…}}`로 푸시됩니다
- 같은 host에서 새 연결이 오면 이전 연결을 끊습니다(페이지 새로고침 대응)
- 음성 어구는 **프랑스어가 실동작 세트**입니다. `MainApplication`이
  `new Locale("fr","")`로 로케일을 고정하고 `voice_path`가 `fr`로 풀리기
  때문입니다. `turn_around`/`stop`/`make_some_noise`/`skywalker`/`leia`/`angle`/
  `stark`는 모든 로케일의 `R.array`가 비어 있어 음성으로 도달할 수 없고,
  `mode` 명령으로만 동작합니다

`seq`를 넣으면 응답에 그대로 돌아옵니다. 클라이언트가 요청한 모션 명령과 서버
푸시를 구분하는 용도입니다.

### 영상 · 디스커버리

- 영상은 **WebSocket `:12121`**(HTTP/MJPEG 아님). 뷰어는 하나만 받고, 두 번째는
  `{"cmd":"streaming","resultCode":421}` 을 받고 끊깁니다. 수락 시 텍스트
  `enter video socket` 한 줄 뒤로 **바이너리 프레임 1개 = JPEG 1장**이 10fps로
  나갑니다(원본 화질 20). 경계 문자열이나 `Content-Type`은 프로토콜 어디에도
  없습니다.
- 디스커버리는 `:8090` UDP 브로드캐스트, 3초 주기이며 페이로드는
  `{"cmd":"updBroadcast","ip","uuid","name","ap_mode"}`. **페어링 모드(3) 동안
  한해 `key`**(QR 세 번째 필드)가 함께 실려, 코드를 띄운 클라이언트가 "자기
  네트워크에 붙었다"는 것을 확인합니다.
- Wi-Fi 오류 코드는 `WifiService` 값을 그대로 씁니다: `414` SSID 미발견,
  `410` 미지원 보안, `412` 설정 추가 실패, `411` 인증 실패(async), `-1`
  "연결 시작, 브로드캐스트 대기". 나머지 응답 코드는
  `1/301/401/421/422/423`입니다(`501`는 선언만 되고 아무 데서도 안 나옴).

## 원본과 다른 점 (의도적)

| 원본 | 이 포팅 |
|---|---|
| `SerialPort.send()`가 예외 외에는 항상 `true` | 실패하면 `False`를 반환해 호출자가 인지 |
| `SoundPlayer.isPlaying`가 절대 true가 안 됨 → `interrupt` 무력화 | 동일하게 "마지막 요청 승리"로 동작(청취 경험 동일) |
| `cancelReady()`가 미초기화 타이머로 NPE | `ready` 수신을 기록으로만 처리 |
| `ModeController`의 필드명 오타(`prohabitted…`) | `MODES_PROHIBITED_WHILE_CHARGING` |
| `MainActivity`가 `ACTION_REQUEST_SHUTDOWN` 브로드캐스트 | 기본 비활성. `allow_host_shutdown: true`일 때만 `systemctl poweroff` |
| OTA: `update.r2d2.io` APK + HMAC 검증 후 설치 | `install_command`로 임의 아티팩트 실행(스테이프). 상태 필드/진행도는 유지 |
| `WifiManager` + `WifiApControl` | `nmcli`(NetworkManager). 없으면 "Wi-Fi 없음"으로 안전 동작 |
| 주머니스핑크 네이티브 KWS | 인식기 플러그형. `feed_keyword()`로 외부 STT 주입 가능 |
| `getFrontBaseMode` 사다리의 `battery < 20` | 같은 효과를 유지하되 저장 기본값 `-1`(미확인)은 low로 취급 |

버그 복원이 아니라 **동작 보존**을 우선했습니다. 무대 위에서 눈에 띄는
차이가 생기면 이 표를 기준으로 판단하세요.

## 테스트

```bash
python3 -m unittest discover -s tests -t tests        # 140개, 하드웨어 불필요
R2D2_TEST_LOG=debug python3 -m unittest discover -s tests -t tests
```

- `test_transport.py`, `test_commander.py` — 프레이밍과 모든 송신 프레임의
  바이트 단위 일치, charging 인터록
- `test_mcu_commands.py` — `btn` 분기표, `gin` 부작용(디바운스·임계값·조명 재계산)
- `test_events.py` — 큐 구조와 "딜레이는 다음 조실행 직전에 걸린다"는 실행 순서
- `test_leds.py` — 패턴 표, 우선순위 사다리, 전원 소등 래치, 페어 실패 복귀
- `test_api.py` — 라우팅과 응답 형식, 게이트(미인증·페어링 중·AP 모드)
- `test_modes.py` — 슬립/순찰/페어링 타이머, 제어권 중재
- `test_vision.py` — 얼굴 추적 수명(1.5s 획득/만료)과 머리 추적 클램프·데드존
- `test_voice.py` — 어구→동작 표, 자동 중지, 최장 매칭
- `test_end_to_end.py` — 기동 순서, WebSocket 콘솔 왕복, 상태 푸시, 뷰어 421,
  디스커버리 페이로드

## 배포

```bash
sudo cp -r r2d2 /usr/local/lib/
sudo cp sound_effects/*.mp3 sound_effects/*.xml /opt/r2d2/sound_effects/
sudo cp r2d2/config.example.json /etc/r2d2/config.json
sudo cp scripts/r2d2.service /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable --now r2d2
journalctl -fu r2d2
```

서비스 계정에 UART 권한이 필요합니다: `sudo usermod -aG dialout r2d2`.
`poweroff`까지 허용하려면 `allow_host_shutdown`을 켜고 유닛의
`NoNewPrivileges`/경로 보호를 조정하세요.

## 알려진 제약

- **시리얼 경로는 보드에 따라 다릅니다.** Android 빌드는 `SerialPort.java`에
  `/dev/ttyS2`를 하드코딩했지만, `info/mcu/pinout.md`의 배선(ESP32 GPIO1/GPIO3
  ↔ Pi GPIO15/GPIO14)은 라즈베리파이 PL011 포트, 즉 `/dev/ttyAMA0`를 가리킵니다.
  기본값은 원본과 동일하게 `/dev/ttyS2`이므로 `--port`로 실제 배선에 맞추세요.
- 블루투스 SPP(`:9100`, RFCOMM 비보안 채널, UUID `c8bb5a21-d5ab-458b-ab9a-f5b0c64637ac`)은
  옮기지 않았습니다. 같은 `CommandReceiver` 경로를 쓰므로 `ClientSession`을 하나
  더 붙이면 됩니다. 원본 APK에서도 `BluetoothService.start()`는 호출되지 않아
  사실상 점등되지 않은 코드입니다.
- 얼굴 인식 캐스케이드는 파일명이 함정입니다. 원본은
  `R.raw.haarcascade_frontalface_alt`의 **바이트**를
  `lbpcascade_frontalface.xml`이라는 이름의 임시 파일로 풀어놓습니다. 그래서 이
  포팅의 기본값도 `haarcascade_frontalface_alt.xml`입니다. LBP로 바꾸려면
  `cascade_dir` / `cascade_name`으로 지정하세요.
- 원본 얼굴 루프는 270° 회전 + 1/3 축소 조합에서 중심 계산을 `width = 640`
  하드코딩에 기대고 있습니다(`warpAffine`이 원본 캔버스 크기를 유지한 채 잘라냄).
  이 포팅은 실제 프레임 너비를 쓰므로, 정상 방향의 USB/CSI 프레임에서는
  `rotate=False`가 올바른 동작입니다.
- QR 디코딩은 `cv2.wechat_qrcode`가 있는 빌드가 필요합니다. 없으면 페어링은
  `connectWifi` 명령 경로로만 동작합니다.
- MCU 펌웨어 내부 동작(순찰 `mode 9`의 실제 보행, 애니메이션 ID 2..20)은 호스트가
  아니라 펌웨어에 있습니다. Java 쪽 순찰 루프는 60초 워치독과 조명뿐이며 센서
  읽기나 power/angle 값이 없습니다. 같은 ID를 같은 필드 순서로 보냅니다.
- GPIO로 호스트가 제어할 전원/리셋 라인은 **없습니다**(`info/mcu/pinout.md`: Pi는
  스위치 없는 5V DC-DC 레일, 여유 핀은 GPIO0 하나). 전원 차단과 MCU 리셋은 전부
  인밴드(`shut-down`, `reset-wdt`)이며, 걸린 MCU는 호스트에서 복구할 수 없습니다.
