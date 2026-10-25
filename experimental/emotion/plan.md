# R2D2 감정 모델링 엔진 설계
## 단기 감정 + 장기 성격 + 기억 + 행동 표현을 통합하는 아키텍처

지금 개발 중인 R2D2는 단순한 대화형 로봇보다 훨씬 흥미로운 시스템을 만들 수 있는 조건을 갖추고 있습니다.

이미 다음과 같은 **감정의 입력(Input), 상태(State), 표현(Output), 행동(Action)** 요소가 있기 때문입니다.

- **감정 입력:** 마이크, 음성인식 결과, 사람 감지, LiDAR 기반 주변 환경 정보
- **행동 입력:** Nav2 자율주행 결과, 장애물, 이동 성공·실패
- **감정 표현:** LED, 사운드, 돔 회전, 휠 움직임
- **상호작용:** 사람에게 접근하기, 사람을 바라보기, 소리에 반응하기, 주변을 탐색하기

제가 제안하는 방향은 LLM에 매번 "지금 어떤 감정일까?"라고 묻는 방식이 아닙니다.

대신 **로봇 내부에 지속적으로 작동하는 감정 엔진을 만들고, 감정이 로봇의 기억과 행동 선택에 영향을 주도록 설계**하는 것입니다.

예를 들어, 사람이 R2D2를 부르면 잠깐 기뻐하면서 돔을 돌리고 다가갑니다. 사람이 떠난 뒤에도 잠시 아쉬운 상태가 남습니다. 오랫동안 상호작용이 없다면 사람의 관심을 끌려는 행동이 증가할 수 있습니다. 반대로 장애물에 반복해서 막히면 일시적으로 좌절 상태가 생기고, 장기적으로는 특정 경로를 회피하는 행동을 학습할 수도 있습니다.

여기서 중요한 것은 감정을 단순한 애니메이션이 아니라 **행동을 조절하는 내부 상태 변수**로 취급하는 것입니다.

---

## 1. 전체 아키텍처

권장하는 구조는 **Emotion Engine + Memory Engine + Behavior Policy + Expression Controller**의 4개 핵심 모듈입니다.



### 1.1 시스템 구성도

```text
┌──────────────────── R2D2 Sensors ────────────────────┐
│                                                       │
│  Microphone     Speech Recognition    Person Detection│
│  LiDAR          Nav2 Status           Robot Status    │
│                                                       │
└─────────────────────────┬─────────────────────────────┘
                          │
                          ▼
┌───────────────────────────────────────────────────────┐
│               1. PERCEPTION & EVENT ENGINE            │
│                                                       │
│  person_arrived       person_left                     │
│  human_called         human_spoke                     │
│  obstacle_detected    navigation_failed               │
│  navigation_succeeded  unexpected_sound               │
│                                                       │
│  Event Confidence + Event Importance + Context        │
└─────────────────────────┬─────────────────────────────┘
                          │
                          ▼
┌───────────────────────────────────────────────────────┐
│                  2. EMOTION ENGINE                    │
│                                                       │
│  A. Short-term emotional state                        │
│     Happiness / Curiosity / Surprise / Frustration    │
│     Fear-like caution / Loneliness-like state         │
│                                                       │
│  B. Long-term disposition                              │
│     Sociability / Curiosity / Cautiousness / Playfulness│
│                                                       │
│  C. Drives                                             │
│     Social interaction / Exploration / Rest           │
│                                                       │
│  D. Appraisal                                          │
│     Novelty / Goal success / Control / Social context │
└─────────────────────────┬─────────────────────────────┘
                          │
             ┌────────────┴─────────────┐
             ▼                          ▼
┌────────────────────────┐  ┌──────────────────────────┐
│  3. MEMORY ENGINE       │  │  4. BEHAVIOR POLICY      │
│                         │  │                          │
│ Episodic memory         │  │ Approach / Follow         │
│ Interaction history     │  │ Explore / Wait            │
│ Familiarity             │  │ Avoid / Reorient          │
│ Positive/negative       │  │ Seek attention            │
│ experience              │  │ Idle / Express emotion    │
└────────────┬────────────┘  └────────────┬─────────────┘
             │                            │
             └─────────────┬──────────────┘
                           ▼
┌───────────────────────────────────────────────────────┐
│                 5. EXPRESSION CONTROLLER              │
│                                                       │
│  LED animation       Sound / Beeps                     │
│  Dome motion         Wheel motion                      │
│  Nav2 goals          Motion priority and safety        │
└───────────────────────────────────────────────────────┘
```

이 구조에서는 감정 엔진이 직접 모터를 제어하지 않습니다.

감정 엔진은 내부 상태와 행동 선호도를 계산하고, 행동 정책은 실행할 행동을 선택합니다. 마지막으로 표현 제어기가 LED·사운드·돔·휠을 구동합니다. Nav2의 주행 안전과 장애물 회피는 별도로 유지합니다.

---

# 2. 감정을 어떻게 모델링할 것인가?

가장 중요한 설계 결정입니다.

사람의 감정을 그대로 복제하려 하기보다는, R2D2의 행동을 자연스럽게 만들 수 있는 **계산 가능한 감정 모델**을 만드는 것이 좋습니다.

저는 다음 세 가지를 조합하는 방식을 권장합니다.

1. **Appraisal model:** 사건이 로봇의 목표와 기대에 어떤 영향을 미쳤는가?
2. **Dimensional emotion model:** 현재 감정 상태가 얼마나 긍정적이고, 활성화되어 있으며, 조심스러운가?
3. **Drive-based model:** 로봇이 지금 무엇을 원하는 상태인가?

여기에 장기 기억과 성격 모델을 추가합니다.

## 2.1 단기 감정: Short-term Emotion

단기 감정은 사건에 반응하여 빠르게 변하고, 시간이 지나면 자연스럽게 약해지는 상태입니다.

처음에는 다음 6개 변수를 사용하겠습니다.

| 변수 | 범위 | 의미 | R2D2의 표현 예시 |
|---|---:|---|---|
| `joy` | 0~1 | 긍정적인 사건에 대한 반응 | 경쾌한 사운드, 돔 회전 |
| `curiosity` | 0~1 | 새롭거나 불확실한 대상에 대한 관심 | 주변 탐색, 돔 방향 전환 |
| `surprise` | 0~1 | 예상하지 못한 사건에 대한 반응 | 짧은 정지, 빠른 돔 회전 |
| `frustration` | 0~1 | 목표 실패 또는 반복적인 방해 | 짧은 불만 사운드, 재시도 |
| `caution` | 0~1 | 위험이나 불확실성에 대한 경계 | 움직임 감소, 주변 관찰 |
| `social_need` | 0~1 | 사회적 상호작용에 대한 현재 욕구 | 사람 쪽으로 방향 전환, 관심 표현 |

여기서 `social_need`는 인간의 외로움과 같은 감정을 주장하는 변수가 아닙니다. **사회적 상호작용을 선택할 가능성을 조절하는 로봇의 내부 동기 변수**입니다.

또한 `surprise`는 사건 직후 빠르게 증가했다가 감소하고, `joy`는 긍정적인 상호작용 이후 상대적으로 오래 유지되도록 설정할 수 있습니다.

### 감정 변수를 분리하는 이유

예를 들어 사람이 다가왔을 때 다음과 같은 상태가 동시에 존재할 수 있습니다.

```text
joy          = 0.80
curiosity    = 0.60
surprise     = 0.20
frustration  = 0.00
caution      = 0.10
social_need  = 0.25
```

익숙한 사람이 오랜만에 돌아온 상황이라면 기쁨과 반가움이 강해질 수 있습니다. 반대로 처음 보는 사람이 갑자기 가까이 다가오면 호기심과 경계심이 동시에 증가할 수 있습니다.

따라서 감정을 하나의 상태값으로 표현하기보다는 여러 변수를 독립적으로 유지하는 편이 유연합니다.

---

## 2.2 감정의 시간 특성: 서로 다른 변화 속도

모든 감정이 같은 속도로 증가하고 감소하면 로봇이 기계적으로 보입니다.

각 감정에 서로 다른 시간 상수를 부여해야 합니다.

감정 변수 \(e_i\)의 기본 변화 모델은 다음과 같습니다.

\[
\frac{de_i}{dt}
=
-\frac{e_i-e_{i,base}}{\tau_i}
+
u_i(t)
\]

- \(e_i\): 현재 감정값
- \(e_{i,base}\): 해당 감정의 기본 상태
- \(\tau_i\): 감정이 기본 상태로 돌아가는 시간 상수
- \(u_i(t)\): 새로운 사건이 감정에 미치는 영향

실제 구현에서는 이벤트 발생 시 감정값을 먼저 갱신하고, 그 이후에는 시간에 따라 기본 상태 쪽으로 감쇠시키면 됩니다.

예를 들어 다음과 같이 초기 설정할 수 있습니다.

| 상태 | 예시 시간 상수 | 의미 |
|---|---:|---|
| Surprise | 2초 | 놀람은 빠르게 사라짐 |
| Joy | 30초 | 즐거운 반응은 잠시 유지 |
| Curiosity | 60초 | 관심이 비교적 오래 지속 |
| Frustration | 45초 | 실패 경험의 영향이 잠시 남음 |
| Caution | 20초 | 위협이 사라져도 잠시 경계 |
| Social need | 10분 | 상호작용 욕구는 천천히 변화 |

이 숫자들은 심리학적으로 검증된 인간 감정의 시간 상수가 아니라, R2D2에서 시험해 볼 초기 파라미터입니다.

감정의 실제 지속 시간은 반복적인 로봇 테스트를 통해 조정해야 합니다.

### 기본 상태도 고정하지 않는 것이 좋습니다

`joy`의 기본값이 항상 0이고, `curiosity`의 기본값도 항상 0이라면 로봇은 이벤트가 없을 때 무표정한 상태가 됩니다.

대신 장기 성격에 따라 기본값을 달리할 수 있습니다.

예를 들어 호기심이 많은 R2D2는 별다른 이벤트가 없어도 주변을 가끔 관찰하고, 사교적인 R2D2는 사람이 나타나면 더 쉽게 반응하도록 만드는 것입니다.

---

# 3. 장기 감정과 성격: Long-term Disposition

단기 감정과 장기 상태를 구분해야 합니다.

단기 감정은 방금 일어난 사건에 대한 반응입니다. 장기 상태는 과거의 경험이 쌓여 현재의 기본적인 행동 경향을 변화시키는 요소입니다.

다만 장기 성격은 단순히 감정값을 오래 유지하는 것과 다릅니다.

예를 들어 오늘 사람이 R2D2를 불렀다고 해서 내일도 계속 기쁨이 0.8인 것은 자연스럽지 않습니다. 대신 반복적인 긍정적 상호작용을 통해 사람에게 먼저 다가가는 행동의 빈도가 증가하는 것이 더 적절합니다.

## 3.1 장기 성격 변수

| 변수 | 의미 | 행동에 미치는 영향 |
|---|---|---|
| `sociability` | 사람과 상호작용하려는 성향 | 사람에게 접근하려는 선호도 |
| `curiosity_trait` | 새로운 대상을 탐색하려는 성향 | 탐색 행동의 빈도 |
| `cautiousness` | 불확실한 상황을 신중하게 다루는 성향 | 접근 속도, 관찰 시간 |
| `playfulness` | 놀이와 과장된 표현을 선호하는 성향 | 돔 동작, 사운드의 다양성 |
| `persistence` | 실패 후 다시 시도하려는 성향 | 재시도 횟수, 대체 경로 탐색 |

각 변수는 0~1 범위로 정규화할 수 있습니다.

예를 들어 초기 성격을 다음과 같이 설정할 수 있습니다.

```yaml
personality:
  sociability: 0.70
  curiosity_trait: 0.75
  cautiousness: 0.40
  playfulness: 0.80
  persistence: 0.65
```

이 설정은 비교적 사교적이고 호기심이 많으며, 장난스러운 표현을 자주 사용하는 R2D2를 의미합니다.

중요한 점은 성격값을 매번 감정 이벤트에 따라 직접 변경하지 않는 것입니다. 장기 성격은 반복적인 경험에 따라 천천히 변화하도록 해야 합니다.

## 3.2 장기 성격은 어떻게 학습할까?

예를 들어 R2D2가 사람에게 접근했을 때 대부분 긍정적인 상호작용이 발생한다면, 시간이 지남에 따라 사람에게 먼저 다가가는 행동을 더 선호하게 만들 수 있습니다.

반대로 접근을 반복적으로 거부당하거나 경로가 막힌 경우에는 접근 행동의 선호도를 낮출 수 있습니다.

다만 **사람이 응답하지 않았다는 사실만으로 거부당했다고 판단하면 안 됩니다.** 사람이 바쁜 상황일 수도 있기 때문입니다. 명시적인 거절, 반복된 무응답, 단순한 미응답을 서로 다르게 처리해야 합니다.

초기 버전에서는 성격 전체를 학습시키기보다는 다음과 같이 특정 행동의 성공률을 추적하는 편이 안전하고 디버깅하기 쉽습니다.

```text
person_approach:
    attempts: 20
    positive_interactions: 15
    explicit_rejections: 2
    uncertain_outcomes: 3
```

이 경험을 이용해 사람에게 접근하는 행동의 선호도를 조금씩 조정합니다.

이렇게 하면 R2D2는 단순히 과거의 기분을 기억하는 것이 아니라, **자신의 행동이 어떤 결과를 가져왔는지 학습하는 로봇**이 됩니다.

---

# 4. 핵심 설계: 사건을 감정으로 변환하는 Appraisal Engine

실제 로봇에서 가장 중요한 부분입니다.

센서 데이터를 그대로 감정값에 연결하면 문제가 생깁니다.

예를 들어 LiDAR에서 장애물이 감지되었다고 무조건 `fear`를 증가시키면, R2D2는 정상적인 실내 주행에서도 계속 경계하게 됩니다. 사람을 감지했다고 무조건 `joy`가 증가하는 것도 적절하지 않습니다.

따라서 센서 이벤트를 감정으로 변환하기 전에 **사건의 의미를 평가하는 과정**이 필요합니다.

## 4.1 사건 평가 모델

각 이벤트를 다음 네 가지 기준으로 평가합니다.

| 평가 항목 | 의미 | 예시 |
|---|---|---|
| Novelty | 얼마나 새로운 사건인가? | 처음 보는 물체 |
| Goal relevance | 현재 목표와 얼마나 관련 있는가? | 기다리던 사람이 부름 |
| Goal outcome | 목표 달성에 도움이 되었는가? | 목적지 도착 성공 |
| Controllability | 상황을 얼마나 통제할 수 있는가? | 막힌 경로에서 우회 가능 |

추가로 안전 위험도와 인식 신뢰도를 계산합니다.

이 평가 결과를 바탕으로 감정 엔진이 상태를 갱신합니다.

### 예시 A: 사람이 R2D2를 부르는 경우

음성인식 결과:

```json
{
  "text": "R2D2, come here!",
  "confidence": 0.94
}
```

이 결과만으로 기쁨을 발생시키는 것은 충분하지 않습니다.

다음 정보를 함께 평가합니다.

- 사람이 실제로 감지되었는가?
- 음성인식 결과가 신뢰할 만한가?
- 현재 Nav2 주행이 가능한가?
- 사람이 접근을 허용하는 상황인가?
- 이전에 해당 사람과 긍정적인 상호작용을 했는가?

평가 결과가 긍정적이라면 다음과 같이 감정을 갱신할 수 있습니다.

```text
joy          += 0.30
curiosity    += 0.10
social_need  -= 0.20
```

수치는 예시입니다. 실제로는 신뢰도와 이벤트 중요도에 따라 조정합니다.

행동 정책은 높은 기쁨을 감지해 돔을 돌리고 경쾌한 사운드를 재생하도록 할 수 있습니다. 접근이 적절하다면 Nav2에 이동 목표를 전달합니다.

### 예시 B: LiDAR에서 장애물이 감지되는 경우

장애물이 있다는 사실 자체는 부정적인 감정의 원인이 아닙니다.

R2D2가 정상적으로 장애물을 피해 주행하고 있다면 특별한 감정 변화 없이 지나갈 수 있습니다.

하지만 다음 상황이라면 다릅니다.

```text
Nav2 navigation started
    ↓
Path blocked
    ↓
Recovery attempt 1
    ↓
Path blocked again
    ↓
Navigation failed
```

이때는 `frustration`을 증가시키고, 목표 달성 가능성이 낮다면 재시도 대신 다른 행동을 선택하도록 할 수 있습니다.

```text
frustration += failure_severity * confidence
```

단, 안전에 관한 판단은 감정이 아니라 Nav2의 안전 로직이 담당해야 합니다. `frustration`이 높더라도 장애물을 무시하거나 위험한 경로를 선택해서는 안 됩니다.

---

# 5. 장기 기억 엔진: R2D2가 경험을 축적하는 방법

단기 감정과 장기 성격 사이에는 기억이 필요합니다.

기억이 없다면 매번 같은 사람을 처음 만난 것처럼 반응하고, 반복되는 상호작용도 학습할 수 없습니다.

저는 세 가지 종류의 기억을 권장합니다.

## 5.1 Episodic Memory: 사건 기억

특정 시점에 무슨 일이 있었는지를 기록합니다.

예를 들어 다음과 같습니다.

```json
{
  "timestamp": "2026-10-25T19:20:00",
  "event": "human_called",
  "person_id": null,
  "emotion_before": {
    "joy": 0.15,
    "curiosity": 0.40
  },
  "action": "turn_dome_and_approach",
  "outcome": "interaction_successful",
  "emotion_after": {
    "joy": 0.72,
    "curiosity": 0.55
  }
}
```

실제 사용자 신원이나 개인 정보를 불필요하게 저장할 필요는 없습니다. 초기 버전에서는 사람의 신원을 식별하지 않고 상호작용 이력만 저장해도 충분합니다.

이 기억은 다음과 같은 행동을 가능하게 합니다.

- 사람이 다시 나타났을 때 과거의 상호작용을 참고한다.
- 특정 상황에서 어떤 행동이 성공했는지 확인한다.
- 반복적인 실패가 발생했을 때 원인을 추적한다.

## 5.2 Semantic Memory: 일반화된 경험

사건을 모두 저장하는 것만으로는 충분하지 않습니다.

반복되는 사건에서 일반적인 패턴을 추출해야 합니다.

예를 들어 다음과 같습니다.

```text
Experience:
- 사람에게 접근한 후 긍정적인 상호작용이 여러 번 발생함.

Learned preference:
- 사람이 먼저 부르면 접근 행동을 우선적으로 고려한다.

Confidence:
- 0.78
```

이는 특정 사람의 신원을 기억하는 것과 다릅니다. R2D2의 행동 선택에 유용한 일반적인 경험을 저장하는 것입니다.

## 5.3 Relationship Memory: 사람과의 관계 기억

원한다면 특정 사용자와의 관계도 별도 모델링할 수 있습니다.

예를 들어 사용자가 반복적으로 R2D2와 상호작용한다면 친숙도를 증가시킬 수 있습니다.

```yaml
relationship:
  familiarity: 0.85
  positive_interaction_history: 24
  explicit_rejections: 1
  last_interaction_time: "2026-10-25T19:20:00"
```

다만 `familiarity`는 신뢰나 애착을 직접 측정한 값이 아닙니다. 로봇이 해당 사람과 얼마나 자주 상호작용했는지를 나타내는 운영상의 지표입니다.

초기에는 관계 기억을 선택 사항으로 두고, 개인 식별 없이도 작동하는 구조로 만드는 것을 권장합니다.

---

# 6. 감정이 실제 행동을 바꾸도록 만드는 Behavior Policy

감정값을 계산해 LED 색상만 바꾸는 방식은 쉽게 구현할 수 있지만, 로봇이 살아 있는 것처럼 느껴지게 만드는 데는 한계가 있습니다.

중요한 것은 **감정이 다음 행동의 선택에 영향을 주는 것**입니다.

예를 들어 동일하게 사람이 감지되더라도 현재 상태에 따라 반응이 달라져야 합니다.

| 현재 상태 | 행동 정책 |
|---|---|
| 기쁨이 높음 | 돔을 빠르게 돌리고 경쾌한 사운드를 재생 |
| 호기심이 높음 | 사람 쪽으로 돔을 돌리고 잠시 관찰 |
| 경계심이 높음 | 안전한 거리에서 관찰하고 급격한 움직임을 줄임 |
| 좌절감이 높음 | 실패한 행동을 반복하기보다 다른 방법을 탐색 |
| 사회적 상호작용 욕구가 높음 | 주변 상황이 적절할 때 사람에게 관심 표현 |
| 피로에 해당하는 내부 동기가 높음 | 움직임과 표현의 빈도를 낮추고 대기 |

여기서 마지막 항목은 실제 생리적 피로가 아니라, 움직임과 상호작용을 줄이기 위한 가상의 내부 동기로 구현할 수 있습니다.

## 6.1 행동 선택 수식

초기 버전에서는 복잡한 강화학습보다 점수 기반 행동 선택을 추천합니다.

행동 \(a\)의 점수를 다음과 같이 정의할 수 있습니다.

\[
Q(a)=
w_g G(a)+
w_e E(a)+
w_p P(a)+
w_m M(a)-
w_c C(a)
\]

- \(G(a)\): 현재 목표와의 적합성
- \(E(a)\): 현재 감정과의 적합성
- \(P(a)\): 장기 성격과의 적합성
- \(M(a)\): 과거 경험에 따른 예상 결과
- \(C(a)\): 행동 비용 또는 불편함

예를 들어 사람에게 다가가는 행동은 다음과 같이 평가할 수 있습니다.

```text
Approach Person:
    goal_score       = 0.70
    emotion_score    = 0.85
    personality_score= 0.80
    memory_score     = 0.75
    cost             = 0.20
```

각 점수는 0~1로 정규화할 수 있습니다.

실제 행동 선택에서는 점수가 가장 높은 행동을 고르거나, 점수에 따라 확률적으로 선택할 수 있습니다.

초기 구현은 결정론적으로 시작하는 것이 좋습니다. 이후 같은 조건에서도 표현에 약간의 변화를 주고 싶다면 확률적인 선택을 추가하면 됩니다.

**중요:** 이 점수는 행동의 매력도를 결정하는 데만 사용합니다. 충돌 방지, 이동 제한, 정지 명령 등의 안전 조건은 별도 계층에서 강제해야 합니다.

---

# 7. R2D2의 신체를 감정 표현에 활용하기

이제 계산된 감정을 실제 로봇의 움직임과 연결해야 합니다.

LED, 사운드, 돔, 휠을 각각 독립적으로 제어하면 서로 다른 감정을 표현하는 것처럼 보일 수 있습니다. 이를 방지하려면 하나의 감정 상태를 여러 표현 장치가 공유하도록 설계해야 합니다.

## 7.1 감정 표현 프로파일

| 감정 | LED | 사운드 | 돔 | 휠 |
|---|---|---|---|---|
| 기쁨 | 밝고 활기찬 패턴 | 경쾌한 짧은 음 | 좌우로 가볍게 회전 | 짧은 접근 동작 |
| 호기심 | 천천히 변화하는 패턴 | 낮은 음량의 짧은 소리 | 특정 방향을 향함 | 천천히 방향 조정 |
| 놀람 | 순간적인 점멸 | 짧은 반응음 | 빠른 방향 전환 | 짧은 정지 |
| 좌절 | 반복적인 패턴 | 낮은 음의 짧은 소리 | 잠시 멈춘 뒤 재지향 | 재시도 또는 정지 |
| 경계 | 안정적인 저강도 패턴 | 필요할 때만 소리 | 주변을 천천히 관찰 | 안전한 속도로 이동 |
| 사회적 관심 | 상대적으로 밝은 패턴 | 반가운 반응음 | 사람을 향함 | 조건이 허용되면 접근 |

이 표현은 고정된 애니메이션 목록으로 시작하되, 감정 강도에 따라 동작의 크기와 빈도를 조절하도록 합니다.

예를 들어 기쁨이 0.2일 때는 짧은 돔 회전만 실행하고, 0.8일 때는 LED와 사운드까지 함께 사용합니다.

## 7.2 감정 강도와 표현 강도 분리

감정값을 그대로 모터 속도에 연결하면 안 됩니다.

다음과 같은 변환 단계가 필요합니다.

```text
Emotion state
    ↓
Expression profile
    ↓
Intensity mapping
    ↓
Hardware constraints
    ↓
LED / Sound / Dome / Wheels
```

예를 들어 다음과 같이 정의할 수 있습니다.

```yaml
expressions:
  joy:
    led_pattern: happy_pulse
    sound_profile: cheerful_short
    dome_motion: playful_sweep
    intensity:
      min: 0.20
      max: 0.85

  curiosity:
    led_pattern: slow_breathe
    sound_profile: curious_beep
    dome_motion: look_around

  caution:
    led_pattern: low_intensity
    sound_profile: silent
    dome_motion: slow_scan
```

표현 강도는 감정값뿐 아니라 현재 주행 상태, 다른 행동의 우선순위, 동작 제한에도 영향을 받습니다.

예를 들어 Nav2가 좁은 공간에서 주행 중이라면 돔 표현은 허용하더라도 휠을 이용한 장난스러운 동작은 금지할 수 있습니다.

---

# 8. 감정 엔진과 Nav2를 어떻게 통합할 것인가?

현재 R2D2가 LiDAR 기반 Nav2 자율주행을 지원한다는 점을 고려하면, 감정 엔진이 주행을 직접 제어하기보다는 Nav2의 목표와 상태를 활용하도록 만드는 것이 좋습니다.

권장 구조는 다음과 같습니다.

```text
Emotion Engine
      │
      ▼
Behavior Policy
      │
      ▼
Navigation Behavior Adapter
      │
      ├── Submit navigation goal
      ├── Observe navigation feedback
      ├── Receive success/failure
      └── Request cancellation when appropriate
                   │
                   ▼
                  Nav2
                   │
                   ▼
          Base Controller
                   │
                   ▼
                  Wheels
```

감정 엔진은 다음과 같은 요청을 생성할 수 있습니다.

```text
Action: APPROACH_PERSON
Target: person_position
Priority: NORMAL
Reason: social_interaction
```

Nav2 어댑터는 이 요청이 실제로 실행 가능한지 검사합니다.

- 사람의 위치가 충분히 신뢰할 만한가?
- 경로가 유효한가?
- 현재 주행 목표를 변경해도 되는가?
- 사용자가 정지 명령을 내린 것은 아닌가?
- 접근이 안전한가?

조건을 통과하면 Nav2에 목표를 전달합니다.

Nav2가 이동에 성공하면 성공 이벤트가 감정 엔진으로 돌아갑니다. 이동이 실패하면 좌절감을 높이고 다른 행동을 선택할 수 있습니다.

### 중요한 안전 원칙

감정 엔진은 다음을 할 수 없어야 합니다.

- 장애물 회피를 무력화하기
- 안전 속도 제한을 변경하기
- 긴급 정지 명령을 무시하기
- 실패한 경로를 무한 반복하기
- 사람 감지 결과만으로 무조건 접근하기

감정은 주행 목표를 선택하는 데 영향을 줄 수 있지만, **주행 안전을 결정하는 최종 권한을 가져서는 안 됩니다.**

---

# 9. ROS 2 노드 설계

R2D2의 기존 소프트웨어가 ROS 2 기반이라면, 감정 엔진을 하나의 거대한 노드로 만들기보다 책임에 따라 분리하는 것이 좋습니다.

처음부터 모든 기능을 별도 노드로 만들 필요는 없지만, 논리적으로는 다음과 같이 나누는 것을 추천합니다.

| 노드 | 역할 | 주요 입력 | 주요 출력 |
|---|---|---|---|
| `event_engine` | 센서 데이터를 이벤트로 변환 | 음성, 사람 감지, LiDAR, Nav2 상태 | 이벤트 |
| `emotion_engine` | 단기 감정 계산 | 이벤트, 시간, 현재 상태 | 감정 상태 |
| `memory_engine` | 사건 및 장기 경험 저장 | 이벤트, 행동 결과 | 기억 조회 결과, 성격 업데이트 |
| `behavior_policy` | 다음 행동 결정 | 감정, 기억, 목표, 안전 상태 | 행동 요청 |
| `expression_controller` | 감정을 LED·사운드·돔으로 표현 | 감정 상태, 행동 이벤트 | 하드웨어 명령 |
| `navigation_adapter` | Nav2 목표와 결과 관리 | 행동 요청, Nav2 상태 | 주행 요청, 결과 이벤트 |

이 중 `emotion_engine`, `behavior_policy`, `expression_controller`는 초기 버전에서 하나의 ROS 2 패키지로 개발해도 됩니다.

## 9.1 권장 메시지 구조

감정 상태는 ROS 2 토픽으로 제공하는 것이 좋습니다.

예를 들어 다음과 같은 사용자 정의 메시지를 정의할 수 있습니다.

`EmotionState.msg`

```text
std_msgs/Header header

float32 joy
float32 curiosity
float32 surprise
float32 frustration
float32 caution
float32 social_need

float32 sociability
float32 curiosity_trait
float32 cautiousness
float32 playfulness
float32 persistence
```

이 메시지는 현재 감정과 장기 성격을 모두 전달합니다.

다만 실제 구현에서는 두 상태를 별도 메시지로 분리하는 편이 더 명확합니다.

```text
/emotion/state
/personality/state
/emotion/events
/behavior/current
/behavior/result
```

권장 데이터 흐름은 다음과 같습니다.

```text
/perception/person_detected ─┐
/speech/recognized ──────────┤
/scan ───────────────────────┤
/nav2/feedback ──────────────┤
                             ▼
                       /emotion/events
                             │
                             ▼
                       /emotion/state
                             │
                  ┌──────────┴──────────┐
                  ▼                     ▼
           /behavior/current     /expression/state
                  │                     │
                  ▼                     ▼
           Navigation Adapter     LED / Sound / Dome
                  │
                  ▼
                 Nav2
```

`/scan` 같은 원시 센서 토픽을 감정 엔진에서 직접 반복 처리하는 것은 권장하지 않습니다. 센서 처리 노드에서 의미 있는 이벤트를 생성하고, 감정 엔진은 해당 이벤트를 이용하도록 구성하면 결합도가 낮아집니다.

---

# 10. 바로 구현할 수 있는 Python 감정 엔진

첫 번째 버전은 머신러닝 모델 없이 구현하는 것을 권장합니다.

감정 변수, 이벤트별 반응, 시간 감쇠, 장기 성격만으로도 충분히 복잡한 상호작용을 만들 수 있습니다.

다음 코드는 ROS 2에 연결하기 전 독립적으로 테스트할 수 있는 최소 구현입니다.

주요 기능은 다음과 같습니다.

- 감정 상태 6개 유지
- 이벤트별 감정 변화
- 시간에 따른 감정 감쇠
- 장기 성격에 따른 기본 상태 변화
- 감정값의 0~1 범위 제한

```python
from dataclasses import dataclass, field
import time


@dataclass
class Personality:
    sociability: float = 0.70
    curiosity_trait: float = 0.75
    cautiousness: float = 0.40
    playfulness: float = 0.80
    persistence: float = 0.65


@dataclass
class Emotion:
    joy: float = 0.10
    curiosity: float = 0.20
    surprise: float = 0.00
    frustration: float = 0.00
    caution: float = 0.10
    social_need: float = 0.30


@dataclass
class EmotionEngine:
    personality: Personality = field(default_factory=Personality)
    state: Emotion = field(default_factory=Emotion)

    # 초기 실험값이며 실제 로봇 테스트로 조정해야 한다.
    tau: dict = field(default_factory=lambda: {
        "joy": 30.0,
        "curiosity": 60.0,
        "surprise": 2.0,
        "frustration": 45.0,
        "caution": 20.0,
        "social_need": 600.0,
    })

    last_update: float = field(default_factory=time.monotonic)

    def baselines(self):
        p = self.personality

        return {
            "joy": 0.10,
            "curiosity": 0.10 + 0.25 * p.curiosity_trait,
            "surprise": 0.00,
            "frustration": 0.00,
            "caution": 0.05 + 0.20 * p.cautiousness,
            "social_need": 0.15 + 0.50 * p.sociability,
        }

    def update_time(self):
        now = time.monotonic()
        dt = max(0.0, now - self.last_update)
        self.last_update = now

        baseline = self.baselines()

        for name in self.tau:
            current = getattr(self.state, name)
            target = baseline[name]

            # 지수 감쇠: 프레임레이트에 관계없이 안정적으로 동작
            alpha = 1.0 - pow(2.718281828, -dt / self.tau[name])
            updated = current + alpha * (target - current)

            setattr(
                self.state,
                name,
                max(0.0, min(1.0, updated))
            )

    def apply_event(self, event, confidence=1.0):
        self.update_time()

        confidence = max(0.0, min(1.0, confidence))

        effects = {
            "human_called": {
                "joy": 0.30,
                "curiosity": 0.10,
                "social_need": -0.20,
            },
            "person_arrived": {
                "curiosity": 0.20,
                "surprise": 0.15,
            },
            "interaction_success": {
                "joy": 0.20,
                "frustration": -0.15,
            },
            "navigation_failed": {
                "frustration": 0.25,
            },
            "navigation_succeeded": {
                "joy": 0.10,
                "frustration": -0.10,
            },
            "unexpected_sound": {
                "surprise": 0.35,
                "curiosity": 0.15,
            },
            "obstacle_cleared": {
                "frustration": -0.10,
            },
        }

        if event not in effects:
            return

        for name, delta in effects[event].items():
            current = getattr(self.state, name)
            updated = current + delta * confidence

            setattr(
                self.state,
                name,
                max(0.0, min(1.0, updated))
            )

    def snapshot(self):
        self.update_time()

        return {
            name: round(getattr(self.state, name), 3)
            for name in self.tau
        }


if __name__ == "__main__":
    engine = EmotionEngine()

    print("Initial:", engine.snapshot())

    engine.apply_event("human_called", confidence=0.95)
    print("After human call:", engine.snapshot())

    engine.apply_event("interaction_success", confidence=0.90)
    print("After interaction:", engine.snapshot())

    engine.apply_event("navigation_failed", confidence=1.0)
    print("After navigation failure:", engine.snapshot())

    time.sleep(3)
    print("After 3 seconds:", engine.snapshot())
```

### 이 코드의 한계

이것은 감정 엔진의 출발점이지, 완성된 감정 시스템은 아닙니다.

현재 코드에는 다음 기능이 아직 없습니다.

- 이벤트 중복 제거 및 중요도 평가
- 이벤트의 상호작용과 감정 간 상관관계
- 장기 기억 저장
- 성격의 경험 기반 업데이트
- 행동 선택
- ROS 2 토픽 및 서비스 연결
- 동시 이벤트 처리와 상태 동기화

특히 `navigation_failed`가 발생할 때마다 무조건 좌절감이 증가하는 방식은 실제 운영에서는 개선해야 합니다. 동일한 실패 이벤트가 반복 발행되는 경우 중복을 제거하고, 실패 횟수와 심각도, 재시도 결과를 함께 평가해야 합니다.

또한 위 코드에서 `social_need`는 장기 성격을 반영한 기본값으로 돌아갑니다. 실제 장기적 상호작용 욕구를 모델링하려면 별도의 드라이브 모델을 두는 것이 좋습니다.

---

# 11. 단기 감정과 장기 성격을 연결하는 학습 알고리즘

여기까지 구현하면 감정이 반응하는 로봇을 만들 수 있습니다.

다음 단계는 경험을 통해 행동이 달라지는 로봇을 만드는 것입니다.

이를 위해 초기에는 복잡한 신경망 대신 **행동별 성공률 추정 + 점진적 선호도 업데이트**를 권장합니다.

예를 들어 `approach_person`이라는 행동을 실행했을 때 긍정적인 결과가 나왔는지 기록합니다.

행동의 성공 확률을 다음과 같이 추정할 수 있습니다.

\[
P(\text{success}\mid a)
=
\frac{\alpha_a}{\alpha_a+\beta_a}
\]

여기서 \(\alpha_a\), \(\beta_a\)는 성공과 실패에 대한 가상의 누적 횟수이며, 초기값은 1로 설정할 수 있습니다.

예를 들어 성공 8회, 실패 2회라면 초기값을 포함한 추정 성공 확률은 다음과 같습니다.

\[
P=\frac{1+8}{1+1+8+2}=0.75
\]

이 값은 해당 행동을 실행했을 때 성공할 가능성에 대한 추정치입니다. 실제 인간의 감정이나 사용자의 호감을 측정하는 값은 아닙니다.

이렇게 얻은 성공률은 행동 선택에 활용할 수 있습니다.

- 사람에게 접근해서 긍정적인 상호작용이 발생하면 접근 선호도를 조금 높입니다.
- Nav2 주행이 반복적으로 실패하면 같은 목표를 반복하는 행동의 선호도를 낮춥니다.
- 새로운 행동이 성공하면 새로운 행동의 성공률을 갱신합니다.

다만 단순한 성공·실패만으로 모든 경험을 평가하면 편향이 생길 수 있습니다. 사용자가 응답하지 않은 경우는 실패가 아니라 결과 불확실성으로 기록할 수 있어야 합니다.

**초기 버전에서는 장기 성격 자체를 자동으로 변화시키기보다, 행동별 성공률과 선호도를 먼저 학습시키는 편이 좋습니다.**

장기 성격은 이후 이 결과를 바탕으로 천천히 조정할 수 있습니다.

---

# 12. 감정 엔진을 테스트하는 시나리오

실제 R2D2에서 가장 먼저 확인해야 할 것은 감정 표현의 화려함이 아니라, 동일한 상황에서 일관성 있게 반응하면서도 경험에 따라 행동이 달라지는지입니다.

다음 6개 시나리오를 추천합니다.

| 테스트 | 상황 | 기대 결과 |
|---|---|---|
| T1 | 사람이 R2D2를 부름 | 놀람·기쁨, 돔 회전, 조건부 접근 |
| T2 | 사람이 잠시 자리를 떠남 | 사회적 상호작용 욕구 증가, 즉각적인 과잉 반응은 없음 |
| T3 | 처음 보는 소리가 발생 | 놀람 증가, 방향 탐색, 이후 상태 회복 |
| T4 | Nav2가 반복적으로 실패 | 좌절감 증가, 동일 행동의 선호도 감소 |
| T5 | 장애물이 나타났지만 정상적으로 회피 | 안전하게 주행하며 불필요한 좌절감은 발생하지 않음 |
| T6 | 익숙한 사람이 다시 나타남 | 과거의 긍정적 상호작용을 반영한 반응 |

여기에 장기 테스트를 추가합니다.

**동일한 사람 호출 이벤트를 20회 반복했을 때** 다음을 확인합니다.

- 감정값이 0~1 범위를 벗어나지 않는가?
- 감정이 장시간 누적되어 포화되지 않는가?
- 이벤트가 중복 처리되지 않는가?
- 시간이 지나면 감정이 자연스럽게 회복되는가?
- 장기 기억이 다음 행동 선택에 실제로 영향을 주는가?
- 감정 표현이 Nav2의 주행 안전을 방해하지 않는가?

마지막으로 로봇이 사람에게 접근하는 행동은 시뮬레이션과 실제 로봇 모두에서 속도 제한과 정지 조건을 먼저 검증해야 합니다.

---

# 13. 최종 권장 개발 순서

현재 R2D2의 하드웨어와 기능을 고려하면 다음 순서가 가장 현실적입니다.

### Phase 1. Reactive Emotion Engine

**목표:** 센서 이벤트에 따라 감정 상태가 변하고, LED·사운드·돔으로 표현되는 로봇.

구현 항목:
- 감정 상태 6개
- 이벤트 기반 감정 갱신
- 시간 감쇠
- 감정 표현 프로파일
- ROS 2 토픽 연동

이 단계에서는 장기 기억 없이도 작동해야 합니다.

### Phase 2. Goal-driven Behavior

**목표:** 감정이 실제 행동 선택에 영향을 주는 로봇.

구현 항목:
- 행동별 점수 계산
- 사람 접근과 탐색 행동
- Nav2 성공·실패 반영
- 행동 중복 방지
- 안전 우선순위

이 단계가 끝나면 같은 이벤트에도 현재 상태에 따라 서로 다른 반응을 보일 수 있습니다.

### Phase 3. Memory & Personality

**목표:** 경험에 따라 행동 특성이 달라지는 로봇.

구현 항목:
- 사건 기억 저장
- 반복 경험 요약
- 행동별 성공률 추정
- 사람과의 상호작용 이력
- 장기 성격 및 행동 선호도 업데이트

저장소는 초기에는 SQLite 정도로 충분합니다.

### Phase 4. Adaptive Emotion

**목표:** 경험에 따라 감정과 행동의 관계가 개선되는 로봇.

구현 항목:
- 상황별 감정 반응 강도 조정
- 장기 행동 선호도 학습
- 표현의 다양성
- 감정과 행동 결과의 연관성 분석

이 단계에서도 감정 상태 자체를 LLM이 매번 결정하도록 만들 필요는 없습니다.

### Phase 5. 선택적 LLM 통합

LLM은 감정 엔진의 핵심 루프보다는 다음 기능에 사용하는 편이 좋습니다.

- 음성인식 결과의 의도 분석
- 복잡한 상호작용의 의미 해석
- 사건 기억의 요약
- 장기 행동 패턴에 대한 설명
- 새로운 행동 시나리오 생성

LLM의 출력은 이벤트나 행동 제안으로 변환하고, 검증된 행동 정책을 거쳐 실행하도록 합니다.

---

# 14. 제가 추천하는 최종 설계

R2D2의 목표가 단순히 LED와 사운드로 기분을 표현하는 것이 아니라, 시간이 지날수록 개성이 생기는 로봇이라면 다음 구조를 추천합니다.

| 구성 요소 | 핵심 역할 | 구현 방식 |
|---|---|---|
| Emotion Engine | 단기 감정 상태 계산 | Python, 이벤트 기반 동역학 |
| Drive Model | 사회적 상호작용·탐색 욕구 관리 | 별도의 내부 동기 변수 |
| Personality Model | 장기 행동 성향 관리 | 느리게 변화하는 파라미터 |
| Memory Engine | 사건 및 경험 저장 | SQLite, 경험 요약 |
| Behavior Policy | 감정과 기억에 기반한 행동 선택 | 규칙 기반 점수 모델 |
| Expression Controller | 감정을 신체 표현으로 변환 | LED·사운드·돔 제어 |
| Navigation Adapter | Nav2와 감정 기반 행동 연결 | ROS 2 Action/Topic |
| Safety Supervisor | 이동 및 동작 안전 보장 | 감정 엔진과 독립된 안전 계층 |

여기서 특히 중요한 것은 **Emotion, Drive, Personality를 서로 다른 개념으로 분리하는 것**입니다.

- **Emotion:** 지금 어떤 상태인가?
- **Drive:** 지금 무엇을 하려는 경향이 있는가?
- **Personality:** 평소 어떤 행동을 선호하는가?
- **Memory:** 과거 경험이 현재 판단에 어떤 정보를 제공하는가?

이 네 가지가 연결되면 R2D2는 단순히 기분에 맞는 애니메이션을 재생하는 로봇을 넘어, 현재 상황과 과거 경험에 따라 행동이 달라지는 시스템이 됩니다.

그리고 처음부터 신경망 기반 감정 모델을 만들기보다는, 위 구조를 명시적인 상태 변수와 규칙으로 구현하는 것을 권장합니다. 감정값과 행동의 관계를 직접 관찰할 수 있어 디버깅하기 쉽고, 향후 실제 사용 경험이 쌓이면 학습 기반 모델을 점진적으로 추가할 수 있기 때문입니다.

**다음 개발 단계로는 `Emotion Engine + Drive Model + Behavior Policy`를 하나의 실행 가능한 ROS 2 패키지로 구현하는 것이 가장 좋습니다.** 기존 사람 감지, 음성인식, LiDAR, Nav2 노드를 그대로 활용하면서 감정 엔진을 독립적으로 추가할 수 있기 때문입니다.