"""Runtime configuration for the navstack vint backend.

Asset layout (see scripts/setup_vint.md)::

    nav/visualnav-transformer/          # cloned upstream (MIT), train/ + configs
    nav/models/vint_weights/checkpoints/{vint,gnm,nomad}.pth

Everything model-facing comes from the upstream training config yaml
(context_size, image_size, len_traj_pred, num_diffusion_iters, ...);
NavConfig only carries serving/behaviour knobs.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

NAV_ROOT = Path(__file__).resolve().parents[1]
MODELS_DIR = NAV_ROOT / "models"
VINT_CONFIGS = NAV_ROOT / "visualnav-transformer" / "train" / "config"

DEFAULT_PORT = 8050


@dataclass
class NavConfig:
    host: str = "0.0.0.0"
    port: int = DEFAULT_PORT
    ready_file: Optional[Path] = None
    log_level: str = "INFO"

    # model selection
    vint_config: str = str(VINT_CONFIGS / "nomad.yaml")   # nomad = goal-less
    vint_ckpt: str = str(MODELS_DIR / "vint_weights" / "checkpoints" / "nomad.pth")
    vint_threads: int = 0                                 # 0 = torch default

    # goals (optional at start; setGoal over the wire also works)
    goal_image: str = ""
    topomap: str = ""
    topomap_dir: str = ""

    # behaviour tuning
    wp_scale_m: float = 0.75      # gain for ViNT/GNM normalized outputs
    close_threshold_m: float = 0.5
    subgoal_radius: int = 3
    y_sign: float = 1.0           # -1 if the robot steers the wrong way

    def load(self) -> "NavConfig":
        for p in (self.vint_config, self.vint_ckpt):
            if not p or not Path(p).is_file():
                raise FileNotFoundError(
                    f"missing {p!r} -- see scripts/setup_vint.md (weights via "
                    f"scripts/fetch_vint_weights.sh)")
        return self
