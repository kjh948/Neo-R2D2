# VO/VIO — 모터 odom 없는 SLAM·주행을 위한 자기운동 추정 계획 (v2)

작성: 2026-09-27. **v2 개정 사유**(사용자 지시): ① 휠 인코더 등 **모터 odom은 쓰지 않는다**
(펌웨어 확장 없음), ② SLAM/주행의 본체는 **LD19 + hector_slam**, ③ ViNT/NoMaD 류는 매핑이
불가해 **보조(로컬 정책)** 역할만, ④ IMU는 **Pi5 I2C 직결** 가능(버스 경합 걱정 없음).
근거: `nav/vo/spec.md`, `info/mcu/pinout.md`, `nav/README.md`, `nav/navstack/`.

---

## 1. 목표와 역할 분담

**핵심 전제**: 바퀴 계보(encoder/motor odom)가 없으므로, 로봇의 "내가 얼마나 움직였나"는
카메라·IMU·레이저로만 확보한다.

| 계층 | 수단 | 시점 | 역할 |
|---|---|---|---|
| **본체 odom/매핑** | LD19 + hector_slam | Pi5, LiDAR 도착 후 | 2D occupancy map + `map→base_link` 자세. **hector는 odom 없이 scan-matching 자체로 motion을 추정하는 설계**(PR2/드론 전례) — 모터 odom 배제 전제와 정확히 맞음 |
| **VIO (이 계획의 main)** | mono USB cam + Pi5 I2C 직결 IMU | 지금부터 | (a) LiDAR 도입 전 유일한 odom, (b) hector 스캔률(≈10Hz) 사이를 메우는 고율(20~30Hz) odom, (c) geometrically-degraded 영역(긴 복도·개방공간)에서 hector 표류 감지·대리, (d) LD19 장애 시 failsafe odom |
| **순수 visual VO** | 위와 동일 하드웨어, IMU 없이 | Mac 개발·디버그 | Mac은 IMU 없음 → 스케일 근사 모드(§6). 알고리즘 검증은 주로 여기서 |
| **주행 정책(보조)** | NoMaD/ViNT (navstack) | 현재 구동 | 미터법 웨이포인트 생성만 담당. 매핑 불가(topomap은 이미지 그래프 우회로, 메트릭 occupancy 아님 — 사용자 판단 확인). odom 위에서 동작 |

**ViNT 계열이 매핑을 못 하는 이유(기록)**: 모델 입력이 이미지 히스토리뿐이고 메트릭
self-motion이 없어, 궤적 누적에 미터 스케일과 시간 축 일관성이 없다. topomap 모드도
`record_topomap.py`가 고정 dt로 노드를 찍는 open-loop 기록 — VIO가 붙어야 노드에
신뢰할 메트릭 좌표를 부여할 수 있다(§8.2).

## 2. 센서 하드웨어

### 2.1 카메라
- Pi5: USB(UVC) 직접 캡처, MJPEG 640×480@30 목표. r2d2 `:12121` WS(10fps·싱글뷰어)는 VO 부적합 — 계속 기각.
- **강성 마운트 필수**: IMU와 카메라를 같은 견고 브래킷에(상대 회전 고정). 유격 있으면 VIO 품질 붕괴.
- 캘립: V1에서 체크보드 pinhole+왜곡(어안이면 fisheye). 카메라-IMU 외재(회전) 행렬은 §2.2-B 기준 정적 가정 + 실측 1회 교정.

### 2.2 IMU — Pi5 I2C 직결 (사용자 확인: 가능)
- **옵션 A(권장): 별도 모듈 부착** — I2C 3.3V 브레이크아웃(LSM6DS3/ICM-42688 급, 자기는 불요).
  배선·프로토콜 자유, ESP32 무관. 6-axis(가속+자이로)만. Pi5 I2C1(헤더 GPIO2/3)은 r2d2 MCU
  링크와 무관한 버스에서 동작 확인 필요(i2cdetect, V0).
- **옵션 B: 보드 위 QMI8658 재사용** — pinout상 Pi I2C1과 ESP32 I2C가 병렬 배선. ESP32
  펌웨어가 이 센서를 폴링 중이면 **버스 경합** → 펌웨어 수정 없는 한 기피. V0에서 실측 판단.
- 요구 스펙: 200~400Hz 폴링(C 데몬 또는 python-periphery, data-ready GPIO 사용 시 지터 최소화),
  CLOCK_MONOTONIC_RAW 타임스탬프, 지터 <5ms. 저가 모듈의 자이로 바이어스 불안정은 §9 리스크.
- USB 캠과 IMU의 **시각 동기**: 같은 머신 로컬 클록이라 §6(구버전)의 하드한 동기 문제 대부분 소멸 —
  노출~드래인 지연 상수(측정)만 보정.

### 2.3 LD19 (나중, 규약만 지금 확정)
- Pi5 USB 시리얼 직결 예정(ESP32 UART2 경유 아님), `rplidar` ROS 드라이버 호환 계열.
- 스펙(≈360°, ~10Hz, ≤12m, ±1%) — 도착 시 V0 재확인.
- ROS 환경: Pi5에 Noetic(arm64 프리빌드 이미지) 또는 ROS2 포티(hector_slam ros2 포트 존재;
  신규 설치라면 **ROS2+slam_toolbox vs ROS1+hector** 비교를 M4 진입 게이트로 두되, 사용자 지정 hector 기본 유지).

## 3. 방식 조사 (spec 2 갱신 답변)

| 후보 | 유형 | Pi5 CPU | IMU fusion | 의존 | 판단 |
|---|---|---|---|---|---|
| OpenVINS | MSCKF VIO | 가능 | ✓(스케일 해결) | **ROS1 필요** | LiDAR/ROS 도입 후(M4) VIO에도 ROS를 태울지 함께 결정 — 그 전엔 비ROS 유지 |
| Basalt | VIO(비ROS C++) | 가능 | ✓ | 빌드 리스크 | DIY 코어 품질 부족 시 승격 후보 |
| **DIY: KLT+5pt 코어 + IMU EKF(느슨결합)** | 고전 | ✓(15~30Hz) | ✓(별도 계층) | numpy/opencv | **채택(기본)** |
| DPVO/DROID/TartanVO 등 AI VO | 심층 | ✗(GPU/실시간 미달) | ✗(대부분 스케일 별도) | torch GPU | Pi onboard 기각(조사 결과 기록) |

AI 계열은 spec 2 요구("찾아야 함")에 대한 답으로 기각 사유를 남긴다. 단 Mac에서 오프라인
스모크 비교만 허용. **hector_slam은 VO가 아니지만** "레이저 scan-matching odom"으로 본
계획의 최종 본체이므로, VIO는 그것과 **경쟁이 아니라 보완** 관계로 설계한다.

## 4. 아키텍처

```
┌ 계층① vo/core.py — 순수 비전 코어 (Mac/Pi 공통) ──────────────────────────────┐
│  KLT(Fast+PyrLK) → findEssentialMat → recoverPose → 삼각측량 → 국소 윈도우     │
│  출력: 프레임 간 상대 pose + 공분산 추정, 스케일 미정(normalized translation)   │
└────────────────────────────────────────────────────────────────────────────────┘
┌ 계층② vo/imu/ — Pi5 (I2C 직결, §2.2) ─────────────────────────────────────────┐
│  daemon: 400Hz 가속/자이로 읽기 + 타임스탬프 품질(지터) 실측                     │
└────────────────────────────────────────────────────────────────────────────────┘
┌ 계층③ vo/fusion.py — 스케일+필터 ─────────────────────────────────────────────┐
│  Pi: EKF(pose,twist,imu-bias,scale) — predict=IMU, update=①증분 → 메트릭 odom  │
│  Mac: 같은 EKF의 축약 모드(IMU 없음) — predict=지면평면 근사, update=①증분       │
│  → odom_server.py: WS pub (nav_msgs/Odometry 필드 JSON, REP-103)              │
└────────────────────────────────────────────────────────────────────────────────┘
              │ (나중) hector_slam: map→odom        │ VIO: odom→base_link
              ▼                                     ▼
        navstack(policy)·record_topomap·viewer 가 단일 odom 스키마 구독
```

- LiDAR 도입 후 최종 tf 체인: `map --(hector)--> odom --(VIO)--> base_link`.
  VIO가 base→odom을 계속 갱신하고 hector가 map→odom을 잡는 표준 분업 → LiDAR 표류 구간에서
  VIO가 버퍼 역할을 자연히 수행. hector에 odom을 넘길지(use_odom prior)는 M4 실험 결정.

## 5. 스케일 (모터 odom 배제 버전)

| 소스 | 원리 | 적용 | 비고 |
|---|---|---|---|
| **S2 IMU(정본)** | 가속계 이중적분/중력 정렬로 메트릭 관측 — VIO 표준 | Pi5 | 모터 odom 없이 스케일을 얻는 **주 경로**. 초기 스케일 수렴은 1~2m 직선 왕복 후 |
| S3 지면평면 | 카메라 높이 h 고정보유 → ground homography | Pi+Mac | 바닥 요철/융단 열화. VIO 수렴 전 과도기 |
| S4 ArUco 앵커 | 크기 기지 마커 검출 시 절대 거리 1회 제공 | Pi+Mac | 평가 하네스 겸용(§9). 상시 소스는 아님 |
| S5 모터 lookup | v≈f(power) 캘립 (r2d2 API로 가능, 펌웨어 불요) | 실로봇 접속 시만 | 참고값. 슬립/배터리 열화 — odom에는 안 씀(신뢰도), 스케일 초기화에만 |

기각된 S1(휠 인코더)은 더 이상 검토 대상 아님.

## 6. 시각 동기 (v1에서 간소화)

- IMU 직결 + 동일 머신 → 남은 문제는 **USB 프레임 지연 분포**뿐: 본체 고정 1분 녹화로
  타임스탬프 지터 실측(V0), 상수 지연 캘립(예상 30~80ms)은 IMU 시계열로 자체 추정
  (캠 흔들임 이벤트와 자이로 스파이크 상관).
- 요회전(≪60°/s) 실주행에서는 지터 영향 미미 — 리스크 강등.

## 7. hector_slam 연동 규약 (지금 확정, 실행은 M4)

- 발행 스키마: `nav_msgs/Odometry` 필드명 그대로(JSON over WS) → ROS 브리지 = 어댑터 한 장.
- 프레임/단위: `base_link`/`camera_link`/`imu_link`, x-전진 y-좌 z-상, rad·m·s, quaternion wxyz (REP-103),
  timestamp = 부동소수 초(CLOCK_MONOTONIC_RAW 기준, ROS `use_sim_time` 대비 wall-offset 변환 제공).
- LD19 데이터는 hector가 직접 소비 — 본 계획은 레이저 파싱 안 함(범위 분리).
- 검증 항목(M4): hector 단독 odom과 VIO의 동시 주행 교차비교(상대 요우/병진 코히어런스),
  스캔매칭 실패 구간에서 VIO 승격 스위치(hector covariance 임계).

## 8. 정책 계층과의 관계 (spec 4/ 보조 역할)

### 8.1 navstack 소비자
- 정지/스테일 판정: open-loop 추정 → VIO twist 관측으로 교체(모터 odom 무관).
- `navigator.py` 데드맨 루프에 "명령 vs 실제 twist" 어긋남 감시(막힘/들림 감지).
- NoMaD velocity 조건부 입력: 현재 `vint_engine`은 이미지 전용 — 공식 저장소 학습 포맷에
  velocity obs가 있는지 확인 후 적용(별소 태스크, M5). 있으면 VIO가 공급.

### 8.2 topomap 업그레이드 (연계 효과)
- `record_topomap.py`의 고정-dt 노드 → **VIO pose 태그 노드**(미터 좌표+자세)로 교체 가능:
  노드 간 거리/방향 기반 선택지 로직, 주행 중 노드 리로케이션(인접 노드 스캔 투사와 VIO 정합).
- 이것은 "ViNT류는 보조" 전제의 구체적 수익: 매핑은 hector, 이미지 그래프는 그 위에 annotate.

## 9. 평가

| 항목 | 방법 | 기준 |
|---|---|---|
| 코어 정확성 | 합성 궤적(기지 planar 렌더) + EuRoC 시퀀스(Mac) | trans <1%, rot <0.5°(회귀) |
| 메트릭 스케일(Pi) | 줄자 2m 왕복×5 (VIO 수렴 후) | 오차 <10% |
| 요우 | 그자리 360° / ArUco 절대 앵커 | <10° |
| 드리프트 | 복도로 왕복, ArUco 귀환 오차 시계열 | 선형률 기록(고정값 강제 없음) |
| 성능 | Pi5: VO+fusion 20Hz, navstack 동시, CPU 총계 | VO ≤25%, 합계 ≤80% |
| (M4) | hector vs VIO 동시 주행 교차비교 | 상관계수/편차 보고 |

## 10. 리스크 (v2 갱신)

| 리스크 | 완화 |
|---|---|
| 저가 IMU 모듈 바이어스·진동 민감 | V0에서 Allan-ish 간이 측정(고정 1분 분산), 품질 부족 시 ICM-42688 급으로 승격 |
| 카메라-IMU 외재 오차(견고 마운트인데 캘립 오차) | 정적 외재 가정 + 요회전 정렬 1회 캘립 절차(캠 흔들임 상관) |
| 모노 VIO 초기 스케일 미수렴(가정 정지/저가속 구간) | S3/S5로 초기화 후 S2 전환, 수렴 판정 지표(스케일 분산) 노출 |
| 긴 복도·개방공간 hector 표류(LiDAR 이후) | M7(tf 체인 분업)에서 VIO가 odom 층 유지 — 구조적 완화 |
| Pi5 CPU: hector+ROS+torch+VIO 공존 | VIO 20Hz→12Hz 강등 옵션, ROS 배포 시 노드 리소스 실측 후 배분 |
| ROS 버전 결정 연기(hector1 vs ros2 포트) | M4 게이트로 명시, 그 전까지 비ROS 인터페이스 견지 |
| I2C 버스에 r2d2 직렬콘솔 등 공유 | V0 i2cdetect·지터 실측 게이트 |

## 11. 파일 구성 (v1에서 소폭)

```
nav/vo/
  spec.md  plan.md
  vo/
    core.py          # 계층① (순수, IMU 무관)
    imu/             # §2.2: driver.py(i2c) daemon.py sync.py
    fusion.py        # 계층③ EKF(Pi:IMU / Mac:근사 소스 스위치)
    scale.py         # S2..S5 플러그인
    odom_server.py   # WS pub (Odometry-JSON), ready_file
    recorder.py      # 프레임+IMU+odom bag 기록/리플레이
    evaluate.py      # §9 지표
  scripts/  v0_hwcheck.py  calibrate_camera.py  tape_test.py(ArUco 병행)  run_vo.sh
  tests/    합성 회귀·EKF 수렴·스키마 스냅샷·sync 상관 추정
  data/     (gitignore)
```

## 12. 마일스톤

- **M0 하드웨어 게이트**: IMU 직결 구성 확정(A/B), 400Hz 지터·타임스탬프 품질, 캠 캘립,
  USB 지연 분포 실측. **통과 기준 없으면 진행 없음.**
- **M1 순수 VO 코어 (Mac)**: `core.py` + 합성/EuRoC. 스케일 S3+S4로 근사. 게이트: 핸드헬드
  산책에서 방향·요우 정상 추적.
- **M2 odom_server + 내구성**: 스키마/재발행/스테일. Mac에서 navstack과 동시 가동 확인.
- **M3 Pi5 포팅 + VIO fusion**: IMU 직결 EKF. 줄자/그자리회전 §9 통과 → **LiDAR 없는 지금
  기준 최종 산출물**(메트릭 odom 20~30Hz).
- **M4 (LiDAR 도착 후) hector 연동**: ROS 설치 게이트(버전 결정) → tf 분업 §7, 교차비교 §9.
  hector+VIO 통합 odom 소비자 확정.
- **M5 정책 통합**: 정지판정/어긋남 감시, NoMaD velocity(포맷 확인 후), topomap pose 태그.
- 자원 예산(Pi5, M4 이후): VIO 20% + navstack 30~60% + hector/ROS ~20% ≈ 8GB 메모리 대비 OK,
  CPU는 소진 시 VIO 강등.

### v1 대비 개정 내역
- S1 휠 인코더 전량 제거(펌웨어 확장 금지) → 스케일 정본은 IMU.
- IMU: ESP32 버스 경합 리스크 → **Pi 직결**로 전제 교체(동기 문제 대폭 완화).
- hector_slam을 "나중 항목"에서 **본체 odom/매핑**으로 승격, VIO는 보완 계층으로 재정의.
- ViNT/NoMaD는 매핑 불가 확인, 보조 정책 + topomap pose 태그 수익으로 역할 명문화.
