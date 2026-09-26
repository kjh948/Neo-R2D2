# nav — LightNav-0 × R2D2 (llama.cpp)

LightNav-0 내비게이션 VLM(Qwen3-VL-4B 파인튜닝, 커스텀 `<act_l*>`/`<apos_*>`/`<opos_*>`
액션 어휘)을 **llama.cpp(GGUF)** 로 추론하고, R2D2의 모터 API(:8887)를 구동합니다.
Mac(Apple/Intel)과 Raspberry Pi 5(8GB) 모두에서 동작하도록 설계됐습니다.

- 계획/근거: [plan.md](plan.md) · 전체 분석: [lightnav-analysis.md](lightnav-analysis.md)
- 모델: `prithivMLmods/LightNav-0-GGUF` (Q4_K_M + mmproj-bf16) +
  `LightOriginsHQ/LightNav-0` 의 RVQ bundle/eval_config — `scripts/download_models.sh`

## 빠른 시작 (Mac)

```bash
python3.11 -m venv venv && venv/bin/pip install -r requirements.txt   # virtualenv 권장(빠름)
./scripts/download_models.sh
# (권장) brew 빌드는 x86에서 매우 느림 — 네이티브 소스 빌드 사용:
cmake -S llama.cpp-src -B llama.cpp-src/build -DCMAKE_BUILD_TYPE=Release \
      -DGGML_METAL=OFF -DGGML_BLAS=OFF -DGGML_NATIVE=ON
cmake --build llama.cpp-src/build -j 8

# 터미널 1: 추론 (llama-server + navserve :8050)
LLAMA_BIN=./llama.cpp-src/build/bin/llama-server ./scripts/run_mac.sh micro
# 터미널 2: 내비게이션 (로봇 없이 드라이런)
venv/bin/python -m navstack drive --server ws://localhost:8050 \
  --robot <pi-ip> --camera usb:0 --instruction "walk to the red door" --dry-run
```

Pi5 배포: [scripts/setup_pi5.md](scripts/setup_pi5.md)

## 두 번째 백엔드: visualnav-transformer (ViNT/GNM) — 카메라 전용, CPU 실시간

언어 대신 **목표 사진 한 장**으로 point-goal 내비게이션. torch CPU 추론이라 Pi5급
하드에서도 빠르다. 상세: [scripts/setup_vint.md](scripts/setup_vint.md).

```bash
./venv/bin/pip install -r requirements-vint.txt
./venv/bin/pip install -e visualnav-transformer/train --no-deps
# 가중치는 models/vint_weights/checkpoints/vint.pth (다운 완료)
./scripts/run_vint.sh serve                       # navserve :8050 (백엔드=vint)
./scripts/run_vint.sh drive photos/door.jpg --robot <pi> --dry-run
```

실가중치 검증 (합성 루트, 목표 시선으로 접근): predicted distance 3.82→1.52m 단조 감소,
waypoint fwd>0 / lat 부호 정상, **추론 ~100ms(≈8-10Hz)** — LightNav 15-20s/스텝과 비교.
동일 `navstack drive`/`waypoints_to_cmd`/`r2d2_link` 재사용 (백엔드만 교체).
topomap 그래프 내비: `scripts/record_topomap.py`로 ROS 없이 노드 수집 → `--topomap DIR`.

### Mac 내장 웹캠 사용 노트 (macOS TCC)
- `--camera usb:0`는 **사용자 터미널에서 포그라운드로 실행**할 것: 첫 실행 시
  시스템이 카메라 허용 다이얼로그를 띄운다 (거절하면 System Settings → Privacy &
  Security → Camera에 터미널 앱 추가).
- 자동화/디택드(no­hup·SSH 비인터랙티브) 컨텍스트에서는 AVFoundation 권한 프롬프트를
  띄울 수 없어 열기가 실패할 수 있다 (설계상 open()은 메인 스레드에서 수행).
- 카메라가 실제로 흐르는지 점검: `venv/bin/python bench/vint_e2e.py`가 아니라면
  `cv2.VideoCapture(0)` 한 장 읽기 테스트로 먼저 확인.

## 구조

```
navstack/
  config.py     # eval_config.json + 프리셋(full/lite/micro/nano) 로딩
  history.py    # 세션 에피소드 버퍼 + SlowFast 티어 샘플링 (lightnav.slowfast 재사용)
  prompt.py     # 학습 프롬프트 byte-exact 재구성 + tubelet 타임스탬프 → OpenAI content parts
  frames.py     # JPEG 디코딩/리사이즈, 티어별 다운스케일(풀링 근사)
  engine.py     # llama-server 호출(greedy) + 토큰 ID 디코딩 + RVQ → (10,3) 웨이포인트
  decode.py     # lightnav serving.protocol 호환 응답 페이로드
  server.py     # navserve: lightnav-serve WS 프로토콜(login/reset/next) 호환 서버
  navigator.py  # 카메라 → navserve → r2d2 제어 (1-in-flight, 3Hz 데드맨, 페일세이프)
  vint_engine.py # [백엔드2] ViNT/GNM torch 추론 + goal-image/topomap 세션 (setup_vint.md)
  r2d2_link.py  # :8887 grantAccess/user_control 리스/move, :12121(video) 소스
  waypoints_to_cmd.py  # 웨이포인트 → (power, angle) 순수 함수
  llama_supervisor.py  # vosk_bridge 스타일 llama-server 서브프로세스 관리
  vocab.py      # GGUF 어휘 로드 (control 토큰ID → 텍스트 복원)
```

## llama.cpp 이식에서 발견한 것 (plan.md §6 대비 실측)

1. **control 토큰 소거**: LightNav 액션 토큰은 GGUF에서 token_type=3(control)이라
   llama-server의 `message.content`에서 삭제된다. `logprobs.content[*].id`로는
   남으므로 ID→어휘 역참조로 원문을 복원한다 (`engine._text_from_choice`).
   `top_logprobs: 0`은 빌드에 따라 logprobs를 꺼버리므로 항상 ≥1.
2. **bf16 mmproj + x86 CPU = 10배 저속**: 인코더를 f16으로 변환해 해결
   (`scripts/convert_mmproj_f16.py`, download_models.sh가 자동 실행). Pi(aarch64)는
   bf16 네이티브라 변환 불필요.
3. **brew bottle(Accelerate BLAS)이 네이티브 빌드보다 4~8배 느림** (prefill 3.6 vs
   27.5 tok/s). `-DGGML_BLAS=OFF -DGGML_NATIVE=ON` 소스 빌드 권장.
4. mtmd는 이미지를 tubelet(2프레임)이 아닌 프레임 단위로 처리 — 웨이포인트 토큰 생성은
   정상 동작을 실측했으나(micro 15~18s/step, Intel i9 CPU) 학습 레이아웃과의 미세
   델타는 실장면 품질 비교가 남았다(plan.md §6 리스크).

## 테스트

```bash
venv/bin/python -m pytest tests/ -q     # 24 tests, GPU/무게불필요
```

`test_r2d2_link_integration.py`는 r2d2 목 앱(mock=True)을 실제로 띄워
grantAccess/user_control/move 프레임이 MCU 트랜스포트에 도달함을 검증한다.
