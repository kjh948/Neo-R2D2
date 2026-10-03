# nav — R2D2 자율 내비게이션 (visualnav-transformer)

R2D2 로봇을 **카메라 하나로** 자율 주행시키는 스택입니다. 추론은
[visualnav-transformer](https://github.com/robodhruv/visualnav-transformer)
(Berkeley, MIT)의 GNM / ViNT / **NoMaD** 모델로 수행하며, **torch CPU면 충분**
해서 Mac과 Raspberry Pi 5(8GB) 모두에서 실시간으로 돕니다.
모터 제어는 r2d2 호스트 API(:8887 WebSocket)를 사용합니다.

> 과거 LightNav-0(Qwen3-VL) + llama.cpp 경로는 검증까지 완료 후 프로젝트
> 범위 축소로 제거했습니다. 기록: [plan.md](plan.md),
> [lightnav-analysis.md](lightnav-analysis.md) / 복원 필요 시 git 히스토리 참조.

## 세 가지 주행 모드

| 모드 | 모델 | 목표 지정 | 설명 |
|---|---|---|---|
| **explore** (기본) | NoMaD | **불필요** | 목표 없이 돌아다니며 장애물 회피 (goal masking) |
| **goal-image** | ViNT/GNM/NoMaD | 사진 1장 | "이 사진 찍은 곳으로 가" |
| **topomap** | 위 모델들 | teleop 시연 경로 | 시연 경로를 노드 사진시로 기록 → 같은 경로 자율 주행, 노드 선택지(分岐) 지원 |

실측 (Intel i9 CPU, NoMaD): **189ms/스텝 ≈ 5Hz**, ViNT 골 모드 ~100ms.
Pi5는 이보다 느리지만 explore 실용 범위 예상(실측 필요).

## 빠른 시작

```bash
cd nav
python3 -m venv venv && venv/bin/pip install -r requirements.txt   # virtualenv 권장
venv/bin/pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu  # x86 macOS는 torch==2.2.2+numpy<2
git clone --depth 1 https://github.com/robodhruv/visualnav-transformer
venv/bin/pip install -e visualnav-transformer/train --no-deps
./scripts/fetch_vint_weights.sh            # nomad/vint/gnm.pth (Google Drive ~1GB)

# 목표 없는 탐색 (로봇 없이 먼저 드라이런):
./scripts/run_vint.sh explore --camera usb:0 --dry-run --show
# 목표 사진 주행:
./scripts/run_vint.sh goal photos/소파.jpg --robot <pi-ip> --max-power 30
# 시연 경로 기록 후 그래프 주행:
./scripts/record_topomap.py --robot <pi-ip> --dt 1.0 models/topomaps/kitchen
./scripts/run_vint.sh map models/topomaps/kitchen --robot <pi-ip>
```

자세한 설정·튜닝·안전 체크리스트: [scripts/setup_vint.md](scripts/setup_vint.md),
Pi5 배포: [scripts/setup_pi5.md](scripts/setup_pi5.md)

## 아키텍처

```
카메라 ──▶ navserve (:8050 WS) ──▶ 추론(torch CPU) ──▶ (10×3) 웨이포인트
  USB(cv2)                        vint_engine.py        waypoints_to_cmd
  r2d2 :12121/video WS            explore | goal | map        │
  dir: (벤치)                                               ▼
                                          r2d2 호스트 API (:8887 WS)
                                          grantAccess → user_control(리스 5s)
                                          → move(power,angle) 데드맨 3Hz + 페일세이프
```

```
navstack/
  config.py     # 런타임 설정(모델 yaml/pth, 게이트, 스케일)
  vint_engine.py # 추론 엔진+세션: ViNT/GNM forward, NoMaD diffusion(explore/goal),
                 #          topomap 노드 윈도우·호핑 상태기계, payload 변환
  server.py     # navserve: login/reset/setGoal/next WS 프로토콜, 워밍업
  navigator.py  # 프레임→서버→로봇 루프: 1-in-flight, 3Hz 데드맨, 스테일 정지,
  #          SIGINT 시 move 0/0 + user_control false 보장
  r2d2_link.py  # :8887(명령)/:12121(비디오) WS 클라이언트, gin 배터리 구독, 충전 인터록
  camera.py     # 소스: r2d2 | usb[:N](메인스레드 open) | dir:PATH
  waypoints_to_cmd.py # 웨이포인트→(power,angle) 순수함수 (+y_sign 각도 부호 교정)
  viewer.py     # --show: 카메라+오버레이 OpenCV 창 (GUI build 필요)
  frames.py     # JPEG↔numpy
  vendor/diffusion_policy/  # ConditionalUnet1D 3파일만 벤더링(MIT, NoMaD용)
scripts/        # fetch_vint_weights / record_topomap / run_vint + 문서
tests/          # 27개 (NoMaD 실가중치 테스트 포함, r2d2 목 앱 통합)
bench/          # vint_latency / vint_e2e
```

## 안전한 첫 실주행 순서 (반드시)

1. `--dry-run`: 명령 로그만 확인 (로봇 무반응)
2. `--max-power 25` + 저속에서 짧은 목표, 줄/장애물 유도
3. 조향 부호가 반대로 틀면 `serve --y-sign -1`
4. 카메라 화면 확인은 `--show` (GUI opencv 필요)
5. 종료/Ctrl-C 시 정지 명령 보장 — systemd `KillSignal=SIGINT`
