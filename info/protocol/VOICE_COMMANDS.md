# Voice Commands

This document lists the English and Korean phrases currently supported by the Vosk voice integration.

## Language Configuration

Set `voice_language` in the configuration file:

```json
{
  "voice_language": "en-ko",
  "vosk_english_model_path": "r2d2/model/vosk-model-small-en-us-0.15",
  "vosk_korean_model_path": "r2d2/model/vosk-model-small-ko-0.22",
  "audio_device": "default"
}
```

Supported values:

| Value | Recognition |
|---|---|
| `en` | English only |
| `ko` | Korean only |
| `en-ko` | English and Korean |
| `ko-en` | English and Korean |
| `bilingual` | English and Korean |

## Usage

The recognizer starts in wake-word mode. Say a wake phrase first, then say a command within 15 seconds.

```text
r two d two
turn left
```

The command window is renewed after each recognized command. When it expires, say a wake phrase again.

## English Commands

| Action | Phrases |
|---|---|
| Wake up | `r two d two`, `two d two`, `hey r two d two`, `good morning`, `how are you` |
| Turn left | `turn left`, `left turn`, `rotate left` |
| Turn right | `turn right`, `right turn`, `rotate right` |
| Go forward | `go forward`, `go straight`, `go ahead`, `move forward` |
| Shake head | `shake your head`, `say no`, `negative` |
| Walk a circle | `walk a circle`, `give me a circle`, `round round round` |
| Dance | `dance now`, `dancing dancing`, `go dance`, `dance please` |
| Identify robot | `who are you` |
| Lightsaber | `lightsaber`, `lightsaber action` |
| Arms | `move arms`, `arms`, `spacecraft linkage` |
| Patrol | `patrol`, `go patrol`, `check around` |
| Stop | `stop`, `stop here`, `rest for a while` |

## Korean Commands

| 동작 | 음성 명령 |
|---|---|
| 깨우기 | `알투디투`, `알 투 디 투`, `좋은 아침` |
| 왼쪽 회전 | `왼쪽으로 돌아`, `왼쪽 돌아` |
| 오른쪽 회전 | `오른쪽으로 돌아`, `오른쪽 돌아` |
| 전진 | `앞으로 가`, `직진` |
| 머리 흔들기 | `고개 흔들어`, `고개를 흔들어` |
| 원 그리기 | `한 바퀴 돌아`, `원을 그려` |
| 춤 | `춤춰`, `춤을 춰` |
| 로봇 확인 | `너 누구야`, `누구야` |
| 라이트세이버 | `광선검`, `광선검 꺼내` |
| 팔 동작 | `팔 움직여`, `팔을 움직여` |
| 순찰 | `순찰`, `순찰해` |
| 정지 | `멈춰`, `정지`, `그만` |

## Recognition Flow

1. Start the application with `voice_recognition_enabled: true`.
2. Say an English or Korean wake phrase.
3. Say one command from the selected language table within 15 seconds.
4. The recognized phrase is passed to `VoiceRecognizer` and then to the event handler.

The bilingual mode loads both Vosk models and processes the same microphone stream with both recognizers.

## Standalone Vosk Test

The standalone test prints recognition results and does not execute robot commands.

```bash
python3 scripts/test_vosk.py \
  --model r2d2/model/vosk-model-small-en-us-0.15 \
  --free-speech \
  --partial
```

Use the Korean model separately when testing Korean recognition:

```bash
python3 scripts/test_vosk.py \
  --model r2d2/model/vosk-model-small-ko-0.22 \
  --free-speech \
  --partial
```

For an ALSA microphone other than the default device:

```bash
arecord -l
python3 scripts/test_vosk.py \
  --model r2d2/model/vosk-model-small-ko-0.22 \
  --device plughw:1,0 \
  --free-speech \
  --partial
```
