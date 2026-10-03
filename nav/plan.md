# LightNav-0 × R2D2 — llama.cpp 기반 Mac / Raspberry Pi 5 구현 계획

> **[역사 문서 / 2026-09-26]** 이 계획으로 구현·검증까지 완료했으나, 이후 LightNav
> 경로는 프로젝트 범위에서 제외되어 코드/자산이 모두 제거되었습니다
> (대안: NoMaD/ViNT — README.md 참고). 제거 전 구현물은 git 히스토리
> (커밋 1a54c2b, f1e7336, 560b5fe) 에서 복원 가능합니다. 본문은 분석 기록으로 보존.


작성: 2026-09-26. 근거: `nav/lightnav-analysis.md`(LightNav-0パイ프라인 분석),
r2d2 코드 분석(`r2d2/`), `prithivMLmods/LightNav-0-GGUF` 확인.

---

## 1. 목표 (spec.md)

- LightNav-0(내비게이션 VLM)을 **llama.cpp(GGUF)** 추론으로 Mac과 Raspberry Pi 5(8GB)에서 구동.
- Pi5는 r2d2 API(:8887 WebSocket)로 모터를 제어, Mac/Pi5 모두 USB 카메라 연결.
- 계획 후 즉시 구현.

## 2. 분석 요약

### 2.1 LightNav-0 파이프라인
- 백본은 **stock Qwen3-VL-4B-Instruct** + 확장 어휘(~4.7k개의 `<act_l*>/<apos_*>/<opos_*>/<tpos_*>/<pos_*>` 토큰,
  임베딩은 safetensors에 평범한 어휘 행으로 학습됨). 내비게이션 전용 헤드/모듈 없음 → GGUF 전환 무난.
- 출력: greedy 디코딩 3~5 토큰 (`<tpos_k>`/`<apos_a><opos_o>` + `<act_l0..l2>`) → 정규식 파싱 →
  RVQ codebook 합/jacobian → 10개 SE(2) waypoint `[forward_m, lateral_m(+좌), yaw_rad(+ccw)]` (순수 numpy).
- 입력: FPS 4 히스토리(에피소드 전체)를 **SlowFast 티어**(current dense pool1 / fast pool2 / mid burst / long span pool4)로
  샘플링, 세그먼트별 `<X.X seconds>` 타임스탬프 + 비전 블록. 풀 에피소드라도 비전 토큰 ≈ 550~800개(256×320 기준) — 경량.
- 픽셀: [-1,1] 스케일, mean/std 정규화 없음 → llama.cpp mtmd의 Qwen 정규화((x−0.5)/0.5)와 **수학적으로 동일**.
- 공식 백엔드(vllm_local)는 외부 ViT 임베딩 주입 방식이지만, **GGUF + mmproj(bf16 비전 타워)로
  표준 mtmd 경로(이미지 → mmproj → LLM)만으로도 동일 모델 실행 가능** — prithivMLmods가 이미 변환 제공.

### 2.2 GGUF 모델 (사용자 지정: prithivMLmods/LightNav-0-GGUF, 4bit)
- `LightNav-0.Q4_K_M.gguf` 2.73GB (LLM) + `LightNav-0.mmproj-bf16.gguf` 0.84GB (비전, bf16 유지 — ViT 양자화 금지 원칙과 부합).
- 부수 자산(RVQ bundle, eval_config)은 `LightOriginsHQ/LightNav-0`(non-gated)에서 다운로드. → `nav/models/` 진행 중.

### 2.3 r2d2 API (Pi5)
- `ws://<pi>:8887`: `grantAccess`(10s 내) → `user_control enable:true`(리스 12s, ≤5s 갱신) →
  `{"cmd":"move","power":0-100,"angle":deg}` **데드맨 300ms 주기**(0=전진, 180=후진, ±90=측면). 응답 없음.
- 카메라: `CameraWorker.most_recent`(JPEG 바이트) / `:12121` binary WS(10fps, 싱글뷰어). 충전 중 `move` 무시(인터록 내장).
- 패턴 원용: Vosk 브리지(subprocess + daemon thread + graceful degrade)를 llama-server 래퍼에 재사용.

## 3. 아키텍처

```
┌─ Mac (i9/Radeon, 32GB) ──────────────────────────┐   ┌─ Pi5 (8GB) ────────────────────────────────┐
│ llama-server (llama.cpp, OpenAI API :8081)       │   │ llama-server (Q4_K_M + mmproj-bf16, :8081) │
│   ← LightNav-0.Q4_K_M.gguf + mmproj-bf16.gguf    │   │   (단독 구동 시)                            │
│ navserve (WS :8050, lightnav-serve 호환 프로토콜)  │   │ navigator ── frames/infer ──▶ (로컬 or Mac   │
│   세션 링버퍼 + SlowFast 티어 + 프롬프트 빌더       │◄──┼──────────── navserve :8050)                  │
│ navigator (USB 카메라 cv2 → navserve)             │   │ navigator: r2d2 :12121 JPEG → navserve       │
│   (데모/디버그: 기본 제어요청 대상 = Pi의 r2d2)      │   │   → move(power,angle) → r2d2 ws :8887        │
└──────────────────────────────────────────────────┘   └──────────────────────────────────────────────┘
```

- **3계층**: ① `llama-server`(표준 빌드 그대로, 패치 없음) ② `navserve`(WS 프로토콜 호환 추론 서버 — 프롬프트
  구성/토큰 파싱/RVQ 디코딩 전담) ③ `navigator`(로봇 클라이언트 — 카메라, 제어, 세이프티).
- Mac과 Pi5에서 **동일한 코드**, 설정만 다름. Pi5가 네트워크로 Mac navserve를 쓰는 구성(추천: Pi5 CPU 부담 0)과
  Pi5 단독 구성(오프라인) 모두 지원.

### 3.1 추론 경로 상세 (navserve → llama-server)
- `llama-server`의 OpenAI `/v1/chat/completions`에 **content parts 배열**(text ↔ image 교차)로 세그먼트/프레임을 보낸다:
  `"<2.5 seconds>"` 텍스트 + 해당 tubelet 이미지를 base64 image part. `temperature=0`(greedy),
  `max_tokens`=디코딩 예산(grounding 0~2 + RVQ 3 + eos = 5~6).
- 히스토리 압축(pool_spatial 2/4 post-ViT)은 **1차 근사: 대상 이미지를 pool 배율로 미리 다운스케일**해서 전송
  → mtmd가 토큰을 pool² 배 적게 생성(개수는 training과 정확히 일치, 평균 방식은 pixel-space로 근사).
  current 티어는 원 해상도(256×448).
- known fidelity delta(§6 리스크 참조): tubelet(2프레임/블록) vs 이미지/tubelet 1개, 세그먼트 단일 vision-block vs
  이미지별 vision-block. **M1에서 출력 정상성 실측 후** 필요 시 대응(2프레임 세로 스티칭 등).

## 4. 히스토리 프리셋 (지연시간 대비)

| preset | 구성 | 비전 토큰(약) | 용도 |
|---|---|---|---|
| `full` | eval_config slowfast_tiers 그대로(current+fast+mid+long+anchor, 256×448) | ~800 | accuracy 기준선 |
| `lite` | current 2(pool1) + fast 8(pool2) + long span 8개(pool4) | ~250 | Pi5 단독, Mac 고속 루프 |
| `micro` | current 2(pool1) + fast 4(pool2) | ~100 | 최후 수단(성능 저하 예상, 실험용) |
| `nano` | current 2(pool1) + fast 2(pool2) | ~140 (실측) | 초경량(CPU 호스트용, 역사 2초 — 완만한 기동만) |

프리셋은 YAML/JSON 설정. 클라이언트는 `--preset`으로 선택.

## 5. r2d2 제어 매핑 (navigator)

- Waypoint → 명령: 10개 행 중 `dt_ctrl`(0.3s) 구간을 넘나드는 행을 보간해 목표 변위 `(fwd, lat)` 획득
  → `angle = atan2(lat, fwd)`(deg, r2d2 규약: +좌) / `power = clamp(dist/dt / v_full * 100, power_min..power_max)`.
  heading(yaw) 행렬은 r2d2가 전후진 회전으로 흡수 — v1은 병진 방향만 추적(차동/오메놈 미제공).
- 데드맨: 300ms마다 `move` 재전송, 세션 alive. 추론 미완료/타임아웃(3s)/`stop:true`/배터리 임계/에러 →
  `move power=0` + `user_control:false`. 종료 시 항상 정지 발송(os._exit 안전망).
- Pi5 단독: 프레임 소스 = r2d2 `:12121` video WS(단, 웹 콘솔과 싱글뷰어 경합 — 문서화). Mac 원격: 같은 방법.
  Pi5에서 navigator가 r2d2와 같은 파이인 경우엔 추후 `most_recent` 직접 접근 옵션(본 구현에선 WS로 통일).
- walking sound/ head-straighten 등 r2d2 부수 행동은 그대로 수용(README에 명시).

## 5.1 구현 후 실측 결과 (M0~M2 통과 기록)

- **토큰 디코딩**: GGUF에서 `<act_*>/<apos_*>/<opos_*>`는 token_type=3(control) →
  llama-server `content`에서 소거됨을 실측. `logprobs.content[*].id` → GGUF 어휘
  역참조로 복원(engine.py). `top_logprobs:0`은 일부 빌드에서 logprobs를 꺼버림 → 항상 1.
- **mmproj bf16→f16 변환 필수(x86)**: bf16 ViT가 CPU에서 이미지당 40s 소요.
  f16 변환(scripts/convert_mmproj_f16.py) + 네이티브 빌드로 micro 7프레임 스텝
  100s→21.5s, full 30프레임 60.7s (i9-9880H, ngl=0).
- **brew bottle(Accelerate BLAS)은 prefill 3.6 tok/s** — 소스 빌드(BLAS off,
  NATIVE on) 27.5 tok/s의 1/8. `--llama-binary`로 소스 빌드 지정 권장.
- **검증된 출력 포맷**: `'<apos_1297><opos_0><act_l0_41><act_l1_92><act_l2_77>'` →
  RVQ decode → (10,3) waypoints 정상. 합성 회색 프레임이라 병진≈0(정지) — 정상 동작.
- **테스트**: nav/tests 24개 전부 통과 (r2d2 목 앱 연동 2개 포함: move 프레임/USER_CONTROL 모드).

## 6. 리스크 & 검증

| 리스크 | 영향 | 완화/검증 |
|---|---|---|
| GGUF 커스텀 토큰 누락(머지 토큰 처리 차이) | 치명 — 출력 파싱 불가 | **M0 게이트**: `llama-gguf` 메타데이터에서 `act_l/apos/pos` 등 존재 확인 + 이미지 넣고 greedy 출력에 `<act_*>` 나오는지 실측 |
| mtmd 레이아웃 delta(이미지별 vision block, tubelet=1프레임) | 품질 저하(무음) | M1에서 클립 기준 waypoint 시계열 매끄러움/stop 판정 확인, 저하 심하면 2프레임 스티칭 또는 llama.cpp mm_embeddings fork(범위 외, 별도 결정) |
| Q4 lm_head 양자화로 인접 액션 코드 혼동 | 수 cm~o.9m 오차 | prithivMLmods의 fp8 허용치(97% stop agreement) 유사 검사: 동일 입력에서 `<act>` 토큰 재현률/정지 판정 비교, 심각 시 Q5_K_M/Pi5 Q4_K_S로 조정 |
| Pi5 prefill 속도(1k 토큰, CPU) | 루프율 ≤0.2~0.5Hz | `lite`/`micro` 프리셋, Mac 서버 원격화 기본 권장 |
| Mac 이 Intel(x86_64, Metal AMD) | 느린 prefill | llama.cpp brew 빌드 사용, `-ngl` 가능한 한 offload + 실측, 안 되면 CPU+스레드 튜닝 |
| r2d2 각도 규약(+좌/+우 부호) | 역주행 | M3에서 정지 상태 회전 테스트로 부호 확정(설정 `angle_sign`) |

## 7. 파일 구성 (신규: `nav/navstack/`)

```
nav/
  plan.md, lightnav-analysis.md, spec.md
  models/                       # GGUF + action_tokenizer + eval_config (다운로드 중)
  navstack/
    __init__.py
    config.py                   # 프리셋/경로/모델 파라미터 (dataclass + JSON)
    history.py                  # 세션 링버퍼 + SlowFast 티어 샘플러 (lightnav.slowfast 래퍼)
    prompt_build.py             # unified_traj 프롬프트 + 타임스탬프 interleaving → chat content parts
    llama_client.py             # llama-server OpenAI 호출 (greedy, max_tokens, 이미지 b64)
    decode.py                   # 정규식 파싱 + RVQ→waypoints (lightnav.vln_utils/traj_vocab 재사용)
    server.py                   # WS :8050 lightnav-serve 호환 (login/reset/next), 세션, 워밍업, ready_file
    r2d2_link.py                # :8887 클라이언트 (grant/user_control/move/gin 구독) + failsafe
    waypoints_to_cmd.py         # waypoint→(power,angle) 매핑 ( 순수 함수, 테스트 대상)
    navigator.py                # 메인 루프: 프레임 소스(cv2/video WS)→navserve→r2d2
    __main__.py                 # `python -m navstack {serve|drive|both}` + llama-server 서브프로세스 관리
  scripts/
    download_models.sh          # (완료분 포함) 재실행 가능한 다운로드
    run_mac.sh / run_pi5.sh     # 워머: llama-server 띄우고 navserve/navigator 기동
    setup_pi5.md                # Pi5 배포 절차(systemd 포함)
  tests/                        # CPU 전용 유닛 (pytest): history 티어, 프롬프트 문자열, 디코딩 합성 토큰,
                                # waypoints_to_cmd 경계, 프로토콜 핸들러(fake llama_client)
```

재사용 원칙: torch-free LightNav 모듈(`traj_vocab/vln_utils/slowfast/prompts/serving.protocol`)은
`LightNav-0/src`를 `sys.path`에 넣어 그대로 import(Apache-2.0, 코드 복제 금지). navigator/서버는 stdlib+numpy+PIL+websockets.

## 8. 마일스톤

- **M0 (게이트)**: 모델/메타데이터 다운로드 완료 → `llama-server`로 Q4_K_M+mmproj 기동, 테스트 이미지로
  greedy 출력이 `<act_*>` 토큰 포맷을 유지하는지 확인. GGUF 어휘에 커스텀 토큰 존재 검증.
- **M1 navserve**: history/prompt_build/decode/server 구현 + CPU 테스트 통과. Mac에서 `next`(JPEG+b64) 요청에
  `{actions:[[f,l,y]×10], stop,...}` 반환. 1프레임/다중프레임 위임을 실측.
- **M2 navigator+제어**: `waypoints_to_cmd` 테스트, r2d2 클라이언트(스펙상 실물 연결 불가 — 목 서버로 프로토콜 검증),
  USB 카메라 cv2 캡처 루프.
- **M3 Mac end-to-end 데모**: Mac: USB 카메라 → navserve → (Pi 연결 시) r2d2 제어. 미연결 시 dry-run 로그.
- **M4 Pi5 준비물**: `run_pi5.sh`, `setup_pi5.md`(llama.cpp 빌드/양자화 선택/메모리 예산/systemd), 프리셋 `lite`.
  실기기 테스트는 사용자 수행(이 세션에서 Pi5에 접근 불가 assumed — 접속 정보 주시면 실행).

### 메모리 예산 (Pi5 8GB)
llama-server: 2.73GB(weights Q4_K_M) + 0.84GB(mmproj bf16) + 0.25GB(KV 8k, f16) + ACTIV ~0.4GB ≈ 4.2GB
+ r2d2 앱 ~0.2GB + navigator/navserve ~0.3GB → 여유 ~3GB. 부족 시 KV를 q8_0(`--cache-type-k q8_0`)로,
mmproj은 f16 유지 원칙.
