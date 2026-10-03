# Raspberry Pi 5 (8GB) 배포 가이드

Raspberry Pi OS 64-bit (Bookworm), Python 3.11. torch CPU 추론이라 GPU/llama.cpp
불필요 — NoMaD explore가 목표이고, Pi5 8GB 안에서 전부 돈다.

## 0. 시스템 패키지

```bash
sudo apt update && sudo apt install -y python3.11-venv python3-pip libgl1
```

## 1. 코드 + venv

```bash
cd ~/Neo-R2D2/nav
virtualenv venv -p python3.11     # 없으면 python3 -m venv venv
venv/bin/pip install -r requirements.txt
venv/bin/pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
git clone --depth 1 https://github.com/robodhruv/visualnav-transformer
venv/bin/pip install -e visualnav-transformer/train --no-deps
```

## 2. 가중치 (~1GB, 한 번)

```bash
./scripts/fetch_vint_weights.sh
```

디스크: 모델 1GB + venv(torch 포함) ~2GB → 여유 4GB+ 필요.

## 3. 메모리 예산 (8GB)

| 항목 | 크기 |
|---|---|
| NoMaD (effnet-b0 + UNet 3.8M + 스케줄러) 추론 | ~1.2GB |
| navserve + drive (python) | ~0.5GB |
| r2d2 앱 + 시스템 | ~1.0GB |
| **여유** | **>5GB** |

aarch64은 bf16/연산 경로가 알차서 Pi5 CPU에서 NoMaD 10 디퓨전 스텝 = 수백 ms
예상(실측 필요; Mac i9 = 189ms). `--vint-threads 4`로 서멀 여유.

## 4. 구동

r2d2 호스트가 같은 Pi에 있으므로 `--robot localhost` 기본값 유효.

```bash
# 목표 없는 탐색/회피 주행 (추천 시작점):
./scripts/run_vint.sh explore
# 골 이미지 주행:
./scripts/run_vint.sh goal photos/거실소파.jpg
```

첫 주행은 반드시:
1. `--dry-run` (기본 포함 — 드라이런 제거 후 실행)
2. `--max-power 25`, 바퀴가 바닥에 안 닿게 들어올린 상태 or 줄 묶음
3. 조향 반대로 돌면 `serve`에 `--y-sign -1`

프레임 소스는 기본 `r2d2`(:12121 비디오 WS, 단일 뷰어). 웹 콘솔과 동시 사용 불가 —
하나만 열 것. Pi5에 카메라가 직접 연결된 별도 머신에서 구동할 땐 `--camera usb:0`
(Linux V4L2는 TCC 없음, 백그라운드 구동 가능).

## 5. 원격 서빙 구성 (선택)

추론을 Mac에서, Pi5는 drive만:
```bash
# Mac: serve (방화벽 :8050 허용)
./scripts/run_vint.sh explore --camera usb:0 --dry-run   # 로컬 검증 후
# Pi5:
venv/bin/python -m navstack drive --server ws://<MAC_IP>:8050 \
    --robot localhost --camera r2d2
```

## 6. systemd (상시 구동)

```ini
# /etc/systemd/system/lightnav.service
[Unit]
Description=navstack NoMaD navigation (R2D2)
After=network-online.target r2d2.service
Wants=r2d2.service

[Service]
Type=simple
User=r2d2
WorkingDirectory=/home/r2d2/Neo-R2D2/nav
ExecStart=/home/r2d2/Neo-R2D2/nav/scripts/run_vint.sh explore --max-power 25
Restart=on-failure
TimeoutStopSec=10
KillSignal=SIGINT

[Install]
WantedBy=multi-user.target
```

`run_vint.sh`가 serve+drive를 한 프로세스 트리로 관리하고 SIGINT 경로가 정지 명령을
보장하므로 `KillSignal=SIGINT`가 안전하다.
