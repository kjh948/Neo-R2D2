"""Static configuration for the llama.cpp nav stack: model assets, presets, ports.

Asset layout (populated by ``nav/scripts/download_models.sh``)::

    nav/models/
        LightNav-0.Q4_K_M.gguf          # LLM (4-bit, llama.cpp)
        LightNav-0.mmproj-bf16.gguf      # vision tower (bf16 -- never quantize the ViT)
        eval_config.json                 # checkpoint contract (video_size, tiers, ...)
        action_tokenizer/manifest.json   # RVQ bundle (codebooks + jacobian weights)
        action_tokenizer/codebook_l*.npy
        ...

Everything inference-facing comes from ``eval_config.json`` (written by the
checkpoint release); ``PRESETS`` only narrows the trained SlowFast tier plan for
compute-starved hosts -- the prompt contract itself never changes.
"""

from __future__ import annotations

import copy
import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

NAV_ROOT = Path(__file__).resolve().parents[1]
MODELS_DIR = NAV_ROOT / "models"

import os

LLM_GGUF = Path(os.environ.get("NAV_LLM_GGUF", MODELS_DIR / "LightNav-0.Q4_K_M.gguf"))
# x86 CPUs (no native bf16) run the ViT ~10x slower on the bf16 projector;
# scripts/download_models.sh produces the f16 twin, prefer it when present.
_DEFAULT_MMPROJ = next(
    (p for p in (MODELS_DIR / "LightNav-0.mmproj-f16.gguf",
                 MODELS_DIR / "LightNav-0.mmproj-bf16.gguf") if p.is_file()),
    MODELS_DIR / "LightNav-0.mmproj-bf16.gguf")
MMPROJ_GGUF = Path(os.environ.get("NAV_MMPROJ", _DEFAULT_MMPROJ))
EVAL_CONFIG = MODELS_DIR / "eval_config.json"
ACTION_TOKENIZER_DIR = MODELS_DIR / "action_tokenizer"

# llama-server defaults (localhost only; navserve is the outward-facing port).
LLAMA_HOST = "127.0.0.1"
LLAMA_PORT = 8081
NAVSERVE_PORT = 8050

# serve-task name -> eval_config task key (mirrors lightnav serving/ws_server).
TASK_KEYS = {"vln": "vlnce", "tracking": "trackvla"}

# Tier plans lighter than the trained full-episode SlowFast mix. Tier dicts use
# exactly the eval_config schema (lightnav.slowfast validates them), so `full`
# is just "whatever eval_config says". Ages are in frames @ video_fps (4 Hz).
PRESETS: Dict[str, Optional[List[Dict[str, Any]]]] = {
    # Trained contract: eval_config.json slowfast_tiers verbatim.
    "full": None,
    # ~14 frames: current 2 @pool1 + fast 8 @pool2 + span pairs @pool4.
    "lite": [
        {"name": "current", "age_lo": 0, "age_hi": 1, "mode": "dense", "pool_spatial": 1},
        {"name": "fast", "age_lo": 2, "age_hi": 17, "mode": "dense", "pool_spatial": 2},
        {"name": "long", "age_lo": 18, "age_hi": 1000000, "mode": "span",
         "num_pairs": 4, "pool_spatial": 4},
    ],
    # ~6 frames: current 2 @pool1 + fast 4 @pool2. Degraded; last resort.
    "micro": [
        {"name": "current", "age_lo": 0, "age_hi": 1, "mode": "dense", "pool_spatial": 1},
        {"name": "fast", "age_lo": 2, "age_hi": 11, "mode": "dense", "pool_spatial": 2},
    ],
    # ~4 frames: current 2 @pool1 + fast 2 @pool2. Ultra-light for CPU hosts;
    # ~300 vision tokens/step. History-blind (2s @4fps): gentle maneuvers only.
    "nano": [
        {"name": "current", "age_lo": 0, "age_hi": 1, "mode": "dense", "pool_spatial": 1},
        {"name": "fast", "age_lo": 2, "age_hi": 3, "mode": "dense", "pool_spatial": 2},
    ],
}


@dataclass
class NavConfig:
    """Resolved runtime config for one navserve process."""

    task: str = "vln"                        # serve-task name ("vln" | "tracking")
    preset: str = "full"                     # PRESETS key
    model_dir: Path = field(default_factory=lambda: MODELS_DIR)
    llama_url: str = f"http://{LLAMA_HOST}:{LLAMA_PORT}"
    host: str = "0.0.0.0"
    port: int = NAVSERVE_PORT
    max_new_tokens: Optional[int] = None     # None -> derived from the token budget
    max_pixels_mode: str = "stretch"         # stretch (trained) | keep (aspect, larger)
    ready_file: Optional[Path] = None
    log_level: str = "INFO"

    # ---- vint backend (visualnav-transformer GNM/ViNT) --------------------
    backend: str = "llama"                   # "llama" | "vint"
    vint_config: str = ""                    # train/config/<model>.yaml of the ckpt
    vint_ckpt: str = ""                      # *.pth
    vint_threads: int = 0                    # 0 = torch default
    goal_image: str = ""                     # optional fixed goal (server-side)
    topomap: str = ""                        # optional ordered 0.jpg..N.jpg dir
    topomap_dir: str = ""                    # base dir for named setGoal lookups
    wp_scale_m: float = 0.75                 # meters per normalized waypoint unit
    close_threshold_m: float = 0.5           # dist_pred below this => arrived
    subgoal_radius: int = 3                  # topomap sliding window (+/- nodes)
    y_sign: float = 1.0                      # -1 if the robot circles the wrong way

    # Populated by load():
    eval_config: Dict[str, Any] = field(default_factory=dict, repr=False)
    video_size: List[int] = field(default_factory=list, repr=False)   # [H, W]
    fps: float = 4.0
    tiers: List[Dict[str, Any]] = field(default_factory=list, repr=False)
    timestamp_relative: bool = False
    prompt_style: str = "unified_traj"
    horizon: int = 10
    num_history_frames: int = 64
    grounding_budget: int = 2                # pointing prefix tokens (<apos><opos>)

    @property
    def task_key(self) -> str:
        return TASK_KEYS[self.task]

    def load(self) -> "NavConfig":
        """Read the checkpoint contract from eval_config.json and apply the preset."""
        if self.backend == "vint":
            for p in (self.vint_config, self.vint_ckpt):
                if not p or not Path(p).is_file():
                    raise FileNotFoundError(f"--vint-config/--vint-ckpt required for "
                                            f"backend=vint (got {p!r})")
            return self
        cfg_path = self.model_dir / "eval_config.json"
        if not cfg_path.is_file():
            raise FileNotFoundError(
                f"{cfg_path} missing -- run nav/scripts/download_models.sh first")
        self.eval_config = json.loads(cfg_path.read_text())
        common = self.eval_config.get("common", {})
        task = self.eval_config.get("tasks", {}).get(self.task_key)
        if task is None:
            raise KeyError(
                f"eval_config has no task {self.task_key!r} "
                f"(have: {sorted(self.eval_config.get('tasks', {}))})")

        self.video_size = list(common.get("video_size", [256, 448]))
        self.fps = float(task.get("video_fps", 4))
        self.horizon = int(task.get("predict_horizon", 10))
        self.num_history_frames = int(task.get("num_history_frames", 64))
        self.prompt_style = task.get("prompt_style", "unified_traj")
        self.timestamp_relative = bool(task.get("timestamp_relative", False))

        trained = task.get("slowfast_tiers")
        if self.preset == "full":
            self.tiers = copy.deepcopy(trained) if trained else []
        else:
            self.tiers = copy.deepcopy(PRESETS[self.preset])
        if not self.tiers:
            raise RuntimeError(
                f"no slowfast_tiers for task {self.task_key!r} and preset {self.preset!r}")

        self._validate()
        return self

    def _validate(self) -> None:
        # Fail loudly on asset presence; the serving layer cannot recover.
        for p in (LLM_GGUF, MMPROJ_GGUF):
            if not p.is_file():
                raise FileNotFoundError(f"{p} missing -- run download_models.sh")
        if (self.model_dir / "action_tokenizer" / "manifest.json").is_file() is False:
            raise FileNotFoundError(f"RVQ manifest missing under {self.model_dir}")

    @property
    def rvq_bundle_dir(self) -> Path:
        return self.model_dir / "action_tokenizer"
