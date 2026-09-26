# navstack 설정·운용 가이드 (visualnav-transformer 백엔드)

가중치·모델 코드 출처: https://github.com/robodhruv/visualnav-transformer (MIT)
论文: GNM(ICRA23) / ViNT(CoRL23) / NoMaD(2023) — **NoMaD는 목표 사진이 필요 없다.**

## 1. 설치 (Mac / Pi5 공통)

```bash
cd nav
virtualenv venv -p python3.11        # venv 대비 압도적으로 빠른 virtualenv 권장
venv/bin/pip install -r requirements.txt
# torch CPU (Intel macOS만 고정이 필요함 — torch 2.2.2가 마지막 x86 휠):
venv/bin/pip install "torch==2.2.2" "numpy<2"            # Intel Mac
venv/bin/pip install torch --index-url https://download.pytorch.org/whl/cpu   # Pi5/Apple Silicon
git clone --depth 1 https://github.com/robodhruv/visualnav-transformer
venv/bin/pip install -e visualnav-transformer/train --no-deps
./scripts/fetch_vint_weights.sh      # nomad.pth / vint.pth / gnm.pth (약 1GB)
```

주의 (Intel Mac):
- `diffusers==0.27.2` + `huggingface_hub<0.26` 조합이 torch 2.2에서 살아남는 마지막 조합
  (requirements.txt에 이미 고정)
- `vint.pth` 로드에는 `warmup_scheduler` 필요 (requirements 포함)

## 2. 서빙

```bash
# 목표 없는 탐색 (기본 = NoMaD explore):
venv/bin/python -m navstack serve
# 골 이미지는 다른 체크포인트로:
venv/bin/python -m navstack serve --vint-config visualnav-transformer/train/config/vint.yaml \
    --vint-ckpt models/vint_weights/checkpoints/vint.pth
# 서버 상수 고정 목표/그래프도 가능 (drive 쪽 플래그가 있으면 우선: setGoal)
venv/bin/python -m navstack serve --topomap models/topomaps/kitchen
```

| serve flag | 뜻 |
|---|---|
| `--vint-config/--vint-ckpt` | 모델 yaml + .pth (nomad/vint/gnm) |
| `--vint-threads N` | torch CPU 스레드 (Pi5 발열 시 4) |
| `--goal-image / --topomap` | 세션 시작 시 고정 목표 |
| `--close-threshold-m 0.5` | dist_pred 이하면 도착/호핑 (NoMaD explore엔 무관) |
| `--subgoal-radius 3` | topomap 윈도우 반경 |
| `--wp-scale-m 0.75` | ViNT/GNM 정규화 출력→미터 이득 (NoMaD는 이미 미터, 1.0 고정) |
| `--y-sign -1` | 좌우 반전 교정 |

## 3. 주행

```bash
# 드라이런 (로봇 무반응, 로그만)
venv/bin/python -m navstack drive --server ws://<host>:8050 --robot <pi> \
    --camera usb:0 --dry-run --show
# explore 실주행 — 목표 불필요:
venv/bin/python -m navstack drive --server ws://<host>:8050 --robot <pi> \
    --camera r2d2 --max-power 30
# 골 이미지 / topomap:
... --goal-image photos/door.jpg
... --topomap models/topomaps/kitchen        # setGoal topomap 디렉터리
```

프로토콜 (lightnav-serve 호환 + 확장):
`login` → `setGoal {image|topomap}` (선택) → `next {seq,image,instruction?}`
→ `{actions:{step,[[fwd,lat,yaw]×H]}, stop, raw_text, latency_ms, subgoal_*?}`.
instruction 없이도 NoMaD는 매 프레임 예측(탐험), ViNT/GNM은 목표 설정 전엔 버퍼만.

topomap 만들기 (ROS 없이): `./scripts/record_topomap.py --robot <pi> --dt 1.0 models/topomaps/<name>`
— teleop으로 목표 경로를 천천히 주행하면 1초마다 노드 jpg가 쌓임. 파일명은 0.jpg..N.jpg 순서.

## 4. 안전 (무엇을 보장하고 무엇을 보장 안 하는가)

보장:
- 종료/Ctrl-C 시 `move 0/0` + `user_control false` 전송
- 추론 응답 없으면(스테일 > max_wp_age) 자동 정지 명령
- 충전 중 `move` 차단(r2d2 펌웨어 인터록 + 클라이언트 경고)

보장 못 함 (첫 실주행 전 필수 점검):
- **explore는 멈추는 법을 모른다** — `stop`이 절대 뜨지 않으므로 배터리·공간 관리,
  저속(max-power 25~30) 시작
- dist_pred가 없는 NoMaD explore는 "도착" 개념 없음; 목표 주행은 ViNT/topomap 사용
- R2D2 키 높이(~35cm) 시점은 학습 데이터(성인 허리 높이)와 domain gap → 서행+감독
- 조향 부호(`--y-sign`), `--v-full`(power100 실속도) 현장 보정 필요

## 5. 문제해결

| 증상 | 확인 |
|---|---|
| serve 워밍업 실패 로그 | ckpt/yaml 조합(`model_type`과 .pth 일치?), missing keys 경고 |
| 카메라 못 열림(Mac) | 포그라운드 실행, 터미널에 카메라 권한 (시스템설정→개인정보 보호→카메라) |
| `--show` 창 없음 | headless opencv 사용 중 → GUI build로 교체 (README 참조) |
| 한 곳으로만 회전 | `--y-sign -1` |
| Pi5 발열 | `--vint-threads 4`, 케이스 히트싱크 |

## 6. 검증 상태

- 단위/통합 27 테스트 통과 (NoMaD 실가중치 explore 포함)
- Mac i9: explore 189ms/step(≈5.3Hz), ViNT 골 ~100ms
- r2d2 목 앱 연동: move 프레임/USER_CONTROL 모드 확인
- 실로봇 주행: 미수행 (사용자 현장 테스트 필요)
