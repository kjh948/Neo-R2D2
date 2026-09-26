# visualnav-transformer(GNM/ViNT) 백엔드 설정

llama.cpp 백엔드와 **같은 navserve 프로토콜**로 동작하는 두 번째 추론 백엔드입니다.
목표가 자연어가 아니라 **사진(goal image)** 이며, torch CPU 추론이라 Pi5에서 수 Hz가
목표입니다. (라이선스: MIT — `visualnav-transformer/`, 저들 코드는 수정하지 않고 import만 사용)

## 1. 설치

```bash
cd nav
venv/bin/pip install -r requirements-vint.txt        # torch(CPU) + efficientnet_pytorch
venv/bin/pip install -e visualnav-transformer/train --no-deps   # vint_train 패키지
```

Pi5: torch는 aarch64 CPU 휠(`pip install torch --index-url https://download.pytorch.org/whl/cpu`) 사용.

## 2. 가중치 (이 Mac에서 다운로드 완료)

공식 체크포인트(Google Drive)를 `models/vint_weights/checkpoints/` 에 받아 두었습니다
(`vint.pth` 430MB, `gnm.pth`, `nomad.pth`). 재받기:

```bash
venv/bin/gdown --folder "https://drive.google.com/drive/folders/1a9yWR2iooXFAqjQHetz263--4_2FFggg" \
  -O models/vint_weights/
```

- `vint.pth`의 unpickle이 `warmup_scheduler` 모듈을 참조 → requirements-vint.txt에 추가됨
- 학습 설정 yaml: `visualnav-transformer/train/config/{vint,gnm}.yaml`

## 3. 실행

### A. 단일 목표 사진 ("이 사진 속 장소로")
```bash
# 서빙
venv/bin/python -m navstack serve --backend vint \
  --vint-config visualnav-transformer/train/config/vint.yaml \
  --vint-ckpt models/vint_weights/vint.pth
# 주행 (goal 사진을 서버에 setGoal로 보낸다)
venv/bin/python -m navstack drive --server ws://<host>:8050 --robot <pi> \
  --camera r2d2 --goal-image photos/destination.jpg
```

### B. 위상지도 그래프 내비 ("teleop으로 한 바퀴 → 같은 경로 자율 주행")
```bash
# 1) 라우트 채보: teleop으로 천천히 운전하며 1초마다 노드 이미지 수집
venv/bin/python scripts/record_topomap.py --robot <pi> --dt 1.0 models/topomaps/kitchen_route
# 2) 서빙/주행
venv/bin/python -m navstack serve --backend vint ... --topomap models/topomaps/kitchen_route
venv/bin/python -m navstack drive --server ws://<host>:8050 --robot <pi> --camera r2d2 --topomap models/topomaps/kitchen_route
```
주행 중 `setGoal {"topomap":"<dir>"}` 로 노드 디렉터리를 교체할 수 있습니다.

### 튜닝 값 (serve flags)
- `--wp-scale-m 0.75` — 모델 출력(정규화 누적 거리)의 미터 환산. 속도감을 결정.
- `--close-threshold-m 0.5` — dist_pred가 이보다 작은 노드면 도착/호핑.
- drive 쪽 `--max-power` — R2D2 속도 상한 (첫 주행은 25~30 권장).
- 방향이 반대로 돌면 서빙에 `--backend vint` 설정의 `y_sign=-1` (config 기본값, CLI 미노출 — 코드 수정).

## 4. 알려진 한계

- **가중치 도메인**: RECON/TartanDrive/SCAND/HuRoN 등 성인의 허리 높이(~1.2m) 카메라 데이터로
  학습. R2D2(~35cm) 저시점은 domain gap → 첫 실주는 반드시 `--dry-run` + 감속 상한으로.
  나쁜하면 자체 파인튜닝(README §Train, `process_bags.py` 대신 우리 노드 포맷 작성).
- NoMaD(diffusion, 탐색/회피)는 이 백엔드에서 아직 미지원 (`model_type: nomad` 거부).
- dist_pred는 0~20m 버킷 회귀 — 근거리(0.5m 미만) 분해능 낮음. 도착 판정은 여유있게.
- ViNT forward당 원가는 efficientnet-b0 × (ctx+1+N) 이미지 — Pi5에서 ctx+1=6, 서브골
  윈도우 8장이면 스텝 200~600ms CPU 예상(실측 필요).

## 5. 검증 상태 (커밋 시점)

- 테스트 6개: 페이로드 규약 (T,3), 컨텍스트 게이트, 목표 게이트, topomap 호핑/정지, y_sign — 통과
  (가중치는 random init으로 플러밍만 검증).
- 실가중치 end-to-end: 가중치 확보 후 `--dry-run` 로그로 진행.
