"""다중 앱(ROS 노드 등) 동시 구동 시 YOLO 프로세스의 CPU 점유를 줄이는 정책 모듈.

사용법 — 데모 스크립트 최상단, torch/cv2/ncnn **import 전에**:

    from perf_policy import apply
    apply()          # 기본 ON (--no-eco 로 끄려면 apply() 호출 자체를 건너뛰면 됨)

효과(실측 근거는 README 참조):
  - OMP_WAIT_POLICY=passive : libgomp(ncnn 번들) 워커의 busy-wait 스핀 제거.
    Pi5 4코어에서 다른 프로세스를 말 그대로 잡아먹던 주된 요인.
  - OMP_NUM_THREADS=2       : ncnn nano 모델은 2스레드가 4스레드보다 빠름(15 vs 21ms)
                              + runnable 스레드 수 감소로 스케줄러 압박 완화.
  - cv2.setNumThreads(1)    : OpenCV 병렬 풀(전처리 resize/cvtColor) 상한.
  - nice(10)                : 시스템 오버로드 시 slam_toolbox/rf2o 같은 주기 제어
                              노드에 CPU를 양보. root 없이 올리기만 가능.
"""

import os

_APPLIED = False

# ncnn가 쓸 스레드 수 (nano 모델 최적치, cpu_pin_bench 실측 기반)
NCNN_THREADS = 2


def apply(nice_value: int = 10, ncnn_threads: int = NCNN_THREADS) -> None:
    """env 정책을 적용한다. idempotent — 두 번 호출해도 안전."""
    global _APPLIED
    if _APPLIED:
        return

    # libgomp/torch는 공유 라이브러리 로드(= import) 시점에 이 env를 읽는다.
    # 반드시 torch/cv2/ncnn import 전에 호출되어야 한다.
    os.environ.setdefault("OMP_WAIT_POLICY", "passive")
    os.environ.setdefault("OMP_NUM_THREADS", str(ncnn_threads))

    # 이미 import된 상태라면(데모가 실수한 경우) 최대한 사후 반영
    try:
        import cv2
        cv2.setNumThreads(1)
    except ImportError:
        pass

    try:
        os.nice(nice_value)
    except (PermissionError, OSError):
        pass  # nice lower bound 정책 등 — 실패해도 치명적이지 않음

    _APPLIED = True


def tune_after_imports() -> None:
    """import 완료 후 호출: cv2 스레드 상한 등 런타임 반영 항목."""
    try:
        import cv2
        cv2.setNumThreads(1)
    except ImportError:
        pass


def status() -> dict:
    """현재 정책 상태 (벤치/디버그용)."""
    st = {
        "applied": _APPLIED,
        "OMP_WAIT_POLICY": os.environ.get("OMP_WAIT_POLICY"),
        "OMP_NUM_THREADS": os.environ.get("OMP_NUM_THREADS"),
        "nice": os.nice(0),
    }
    try:
        import cv2
        st["cv2_threads"] = cv2.getNumThreads()
    except ImportError:
        pass
    return st
