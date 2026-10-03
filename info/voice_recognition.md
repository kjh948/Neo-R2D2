# 음성 인식 (Voice Recognition) 사용법과 명령어 목록

Python 호스트(`r2d2/`) 기준, 2026-10-03 현재 구현 상태 문서.
관련 코드: `r2d2/voice.py`, `r2d2/central.py`, `r2d2/api.py`, `scripts/test_vosk.py`.

---

## 1. 동작 구조 요약

```
마이크/STT(Vosk 등) ──인식 문장──▶ VoiceRecognizer.feed_keyword(phrase)
                                        │
                     3상태 기계 (DISABLE / WAIT / WAKE) 로 게이트
                                        │
                              VoiceToEventHandler.voice_to_event
                                        │
                              events.* → MCU 모드/소리/LED 실행
```

- **인식기는 3단계 상태 기계** (`VoiceRecognizer`, `r2d2/voice.py:215`):
  | 상태 | 값 | 의미 |
  |---|---|---|
  | `MODE_DISABLE` | -1 | 초기값이자 `stop()` 결과. 모든 문장 무시 |
  | `MODE_WAIT` | 0 | `start()` 후 대기. **웨업(word)만 반응**, 그 외 문장은 무시되고 대기 유지 |
  | `MODE_WAKE` | 1 | 웨업 인식 후 **15초**(LISTEN_TIMEOUT) 창. 창 안의 모든 문장이 실행되고 창 갱신 |

- 15초 창이 끝나면 `MODE_WAIT`로 돌아가는 것이지 **인식이 꺼지지 않는다** (LED만 복원).
  완전 종료(`stop`)는 수면/정찰/페어링 모드 전환이나 `voice_recognition` 토글에서만.
- 문장 매칭은 **소문자화 후 접두사(prefix) 일치, 최장 구 우선** (`startswith`).
  예: `rotate right please` → `rotate right` 매칭, `move arms`는 `arms`보다 먼저(길기 때문) 판단.
- 명령이 실행될 때마다 `mode_controller.wake()`가 호출되어 **대기(수면) 카운터가 초기화**된다.
- WAKE 상태에서 매칭 실패 → `MODE_NOT_RECOGNIZE`(8) → 부정 소리(`SHORT_RASPBERRY`).

---

## 2. 켜기 / 끄기

### 설정 (config / 환경변수)

`r2d2/config.py:82-83`

```json
"voice_recognition_enabled": true,
"voice_language": "en"
```

- 환경변수: `R2D2_VOICE_RECOGNITION` (bool, 기본 `false`), `R2D2_VOICE_LANGUAGE` (기본 `en`).
- 현재 이 머신의 `r2d2/config_local.json`은 `voice_recognition_enabled: true`, `voice_language: "en"`.
- 앱 기동 시 `voice_recognition_enabled`면 자동으로 `start_voice_recognition()` 호출 (`r2d2/app.py:211`).
- `voice_language`가 `en`(또는 `english`)이면 **영어 세트만** 사용. 그 외 값(예: `all`)이면
  프랑스어/중국어까지 포함된 다국어 통합표 `VOICE_PHRASES`를 사용.

### 런타임 토글

- **웹 콘솔**(`http://<robot-ip>:8080/`)의 `voice_recognition` 버튼.
- WebSocket(8887) API: `{"cmd": "voice_recognition", "enable": true|false}` (`r2d2/api.py:300`).
- 상태 플래그는 `state.json`의 `voiceRecognition`에 저장(기본 `true`).

### 시작이 차단되는 조건

`CentralController.start_voice_recognition()` (`r2d2/central.py:71`)는 다음 경우 시작하지 않는다:

- `state.voice_recognition`이 꺼려 있을 때
- 본체가 **모드 3(페어링)** 또는 **모드 4(정찰)** 일 때

---

## 3. 사용 방법 (웨업 → 명령)

1. 인식이 켜진 뒤 본체는 `MODE_WAIT` — 평상시는 **웨업 단어만** 듣는다.
2. 영어 웨업 단어 발성: **"r two d two"** (또는 "two d two", "hey r two d two",
   "good morning", "how are you").
3. 반응: LED 복원 + 세 번 째는 소리(`HAPPY_THREE_CHIRP`) + 고개 가로/세로 인사 → `MODE_WAKE`,
   **15초 명령 창** 열림.
4. 창 안에서 명령 발성 → 즉시 실행, 실행될 때마다 창이 15초로 갱신 (연속 명령 가능).
5. 15초 동안 아무 말 없으면 자동으로 `MODE_WAIT` 복귀(다시 웨업 필요), 인식은 계속 켜짐.

주의: `MODE_WAIT`에서 웨업이 아닌 명령을 말하면 **무시**된다. 반드시 먼저 웨업.

---

## 4. 지원 명령어 (영어 세트 `ENGLISH_VOICE_PHRASES`)

`voice_language: "en"`에서 실제로 인식되는 전체 목록. 문장 중 **맨 앞에** 나오면 매칭.

| 명령 (VoiceCommand) | 인식 문장 | 동작 |
|---|---|---|
| `WAKE_UP` | r two d two / two d two / hey r two d two / good morning / how are you | 웨업: 소리+고개 인사, 15초 창 개방 |
| `TURN_LEFT` | turn left / left turn / rotate left | 왼쪽 회전 (mode 3) |
| `TURN_RIGHT` | turn right / right turn / rotate right | 오른쪽 회전 (mode 4) |
| `GO_FORWARD` | go forward / go straight / go ahead / move forward | 직진 (mode 5) |
| `SHAKE_HEAD` | shake your head / say no / negative | 고개 저음 (소리+좌우 45° 흔들기) |
| `WALK_A_CIRCLE` | walk a circle / give me a circle / round round round | 한 바퀴 돌기 (mode 12 경로) |
| `DANCE` | dance now / dancing dancing / go dance / dance please | 춤 (mode 10) |
| `WHO_ARE_YOU` | who are you | 자기소개 모드 (mode 7) |
| `LIGHT_SABER` | lightsaber / lightsaber action | 광선검 토글 (프로젝터/소리/동작) |
| `ARMS` | move arms / arms / spacecraft linkage | 팔 토글 |
| `PATROL` | patrol / go patrol / check around | 정찰 모드 시작 |
| `STOP` | stop / stop here / rest for a while | 정지 (mode_stop) |

### 영어 세트에 없는 명령 (음성으로 못 부름)

| 명령 | 상태 | 대체 경로 |
|---|---|---|
| `TURN_AROUND` (180° 회전) | 영어 표 없음 | 소켓 `mode` 2 |
| `MAKE_SOME_NOISE` | 영어 표 없음 (중국어표에만 존재) | 소리 명령 |
| `SKY_WALKER` / `PRINCESS_LEIA` | 페이즈표 비어있음 (원본 앱도 동일) | 소켓 `mode` 20 / 19 |
| `ANGLE_SECRET` / `STARK_SECRET` | 인식만 되고 동작 없는 이스터에그 | 클라이언트 측 처리 |

### 다국어 세트 (`voice_language` ≠ en)

프랑스어(원본 앱의 실동작 로케일)·중국어·영어 혼재 표: `VOICE_PHRASES` (`r2d2/voice.py:55`).
예: `salut`, `bonjour`, `你 好` (웨업) / `tourne à gauche`, `左 转` (좌회전) /
`看 后面`, `后 转` (U턴 — 영어에 없는 중국어 전용) / `停止` (정지) 등.

---

## 5. STT 연결 상태 (중요)

**현재 Python 호스트 자체에는 마이크 수집 루프가 내장되어 있지 않다.**
`feed_keyword()`가 유일한 입구(seam)이며, 외부 STT가 인식 문장을 밀어넣아야 한다.

### Vosk 테스트 스크립트 (독립 실행, 명령은 실행하지 않음)

영어 Vosk 모델이 저장소에 들어 있다: `r2d2/model/vosk-model-small-en-us-0.15/`

```bash
python3 -m pip install vosk           # alsa-utils(already: arecord) 필요

python3 scripts/test_vosk.py \
    --model r2d2/model/vosk-model-small-en-us-0.15 \
    --device default                  # 마이크 장치 (예: plughw:1,0)

# 옵션
#   --seconds 30     N초 후 자동 종료 (0=Ctrl-C까지)
#   --free-speech    명령 문법 대신 자유 인식
#   --partial        발화 중 부분 결과 출력
```

- 기본 모드는 **명령 문법 제한**: 위 영어 웨업/명령 + `[unk]`만 인식 대상으로 함
  (`scripts/test_vosk.py:22` `DEFAULT_PHRASES`).
- 이 스크립트는 인식 결과를 **출력만** 하고 로봇과는 연결되지 않는다 (docstring 명시).
  실동작을 원하면 스크립트의 FINAL 텍스트를 `app.voice.feed_keyword(text)`로 전달하는
  브리지를 붙이면 된다. (참고: 문법 모드의 결과 문자열이 표의 문장과 접두사 매칭됨)
- 16 kHz mono S16_LE PCM (`arecord` 파이프), CHUNK 4000바이트.

---

## 6. 테스트 / 검증

```bash
python3 -m unittest tests.test_voice -v     # 구문→동작 표, 15초 자동복귀, 최장접두 매칭
python3 -m r2d2 --mock --log-level debug    # 하드웨어 없이 전체 루프
```

`--mock` 실행 중 WS 8887로 `{"cmd":"voice_recognition","enable":true}` 토글 후,
Python REPL이나 브리지를 통해 `feed_keyword("r two d two")`, `feed_keyword("turn left")`
경로를 시뮬레이션할 수 있다.

---

## 7. 구현 참고 (원본과의 차이)

- 어구표의 원본은 Android 리소스(`R.array.voice_*`). Python 포팅에서는 명시적으로
  영어 표를 선택(`voice_language`)해 사용한다.
- 원본 앱의 HashMap 순서 비결정성("arms" vs "move arms")은 포팅에서 **길이 내림차순
  정렬**로 확정 (`VoiceToEventHandler.__init__`).
- 15초 타임아웃 시 `stop()`이 아니라 `MODE_WAIT` 복귀 + `endVoiceEvent`(LED 복원) —
  원본 `VoiceRecognizer` 상태 기계를 그대로 재현 (`r2d2/voice.py:227-235` docstring).
