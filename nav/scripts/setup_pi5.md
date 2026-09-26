# Pi5 (8GB) 배포 가이드

Raspberry Pi OS 64-bit (Bookworm), Python 3.11 권장. 두 가지 구성을 지원한다:

- **A. 원격 추론(권장)** — Mac(또는 다른 GPU 머신)에 `navserve`를 띄우고, Pi5의
  `drive`만 구동. Pi5는 JPEG를 WebSocket으로 보내는 것 외에는 연산을 하지 않는다.
- **B. 단독 구동(오프라인)** — Pi5에서 llama-server + navserve + drive 전부 실행.
  CPU 추론이라 스텝당 수 분(lite/micro 프리셋) 예상. 지령 단위("sofa 앞으로")
  저속 미션에만 실용적.

## 0. 시스템 패키지

```bash
sudo apt update && sudo apt install -y python3.11-venv python3-pip cmake build-essential git libgl1
```

## 1. 코드 및 가상환경

```bash
cd ~/Neo-R2D2/nav
python3.11 -m venv venv            # (venv로 충분하지만) virtualenv도 동일하게 동작
venv/bin/pip install -r requirements.txt
```

## 2. llama.cpp 빌드 (aarch64 네이티브)

```bash
git clone --depth 1 https://github.com/ggml-org/llama.cpp
cd llama.cpp
cmake -B build -DCMAKE_BUILD_TYPE=Release -DGGML_METAL=OFF -DGGML_NATIVE=ON
cmake --build build -j 4           # 발열 방지를 위해 -j 4 권장 (기본 8코어)
sudo cp build/bin/llama-server /usr/local/bin/
```

Pi5는 bf16 네이티브 지원이므로 **mmproj-bf16을 그대로 써도 된다**
(f16 변환은 x86용 최적화).

## 3. 모델 다운로드 (~3.6GB)

```bash
cd ~/Neo-R2D2/nav
./scripts/download_models.sh       # Q4_K_M + mmproj + RVQ bundle + eval_config
```

디스크 여유: 모델 3.6GB + 빌드 2GB → 최소 8GB 필요.

## 4. 메모리 예산 (8GB)

| 항목 | 크기 |
|---|---|
| LightNav-0.Q4_K_M (LLM) | 2.7GB |
| mmproj-bf16 (비전) | 0.8GB |
| KV 캐시 4096 (f16) | ~0.5GB (--ctx-size 4096 기본) |
| llama-server 활성/오버헤드 | ~0.7GB |
| navserve+drive (python) | ~0.4GB |
| r2d2 앱 + 시스템 | ~0.8GB |
| **합계** | **~5.9GB / 8GB** |

스왑 방지: `--ctx-size 4096` 유지, 부담스러우면 `--cache-type-k q8_0 --cache-type-v q8_0`.

## 5. A안: 원격 추론 (Mac에서)

```bash
# Mac
cd ~/workspace/Neo-R2D2/nav
./scripts/run_mac.sh lite

# Pi5 (r2d2 앱이 :8887/:12121 으로 이미 떠 있어야 함)
cd ~/Neo-R2D2/nav
venv/bin/python -m navstack drive \
  --server ws://<MAC_IP>:8050 --robot localhost --camera r2d2 \
  --instruction "walk to the table and stop"
```

## 6. B안: Pi5 단독

```bash
cd ~/Neo-R2D2/nav
LLAMA_BIN=/usr/local/bin/llama-server ./scripts/run_pi5.sh "walk to the table" lite
```

r2d2 host가 같은 Pi에 있으므로 `--robot localhost` 기본값이 유효.
동일 Pi에서 navigator를 돌릴 때도 프레임 소스는 기본 `r2d2`(:12121 뷰어 1개)를 쓴다.
웹 콘솔(`http://<pi>:8080`)과 동시에 열면 421(두 번째 뷰어) 거부되므로 하나만 사용할 것.

## 7. 안전 체크리스트

- `--dry-run` 으로 먼저 명령 로그만 확인 (로봇 구동 없음).
- 첫 실주행은 `--max-power 30` 과 줄로 로봇을 묶거나 바퀴가 바닥에 닿지 않게.
- `angle_sign` 부호 확인: 경사로 회전 후 같은 방향으로 선회하면 정상, 반대라면
  `MotionParams(angle_sign=-1)` (drive CLI 확장 전이면 코드의 기본값 변경 필요 — §매핑 문서).
- 충전 독 위에서는 r2d2가 `move`를 무시한다(인터록). navigator 로그에
  "robot is charging" 경고가 뜬다.
- navigator 종료/Ctrl-C 시 항상 `move 0/0` + `user_control:false`를 보낸다.

## 8. systemd (B안 상시 구동)

```ini
# /etc/systemd/system/lightnav.service
[Unit]
Description=LightNav navserve+drive (Pi5 stand-alone)
After=network-online.target r2d2.service
Wants=r2d2.service

[Service]
Type=simple
User=r2d2
WorkingDirectory=/home/r2d2/Neo-R2D2/nav
ExecStart=/home/r2d2/Neo-R2D2/nav/scripts/run_pi5.sh "patrol to the sofa" lite
Restart=on-failure
TimeoutStopSec=10
KillSignal=SIGINT

[Install]
WantedBy=multi-user.target
```

`Ctrl-C` 경로가 정지 명령을 보장하므로 `KillSignal=SIGINT`가 안전하다.
