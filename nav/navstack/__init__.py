"""navstack: visualnav-transformer (ViNT/GNM/NoMaD) navigation for the R2D2 robot.

Serves a lightnav-serve-style WebSocket protocol (navserve) over in-process
torch CPU inference and drives the robot through the R2D2 host command API on
port 8887. See README.md and scripts/setup_vint.md.

The vendored ``diffusion_policy`` subpackage (navstack/vendor/, MIT, files from
real-stanford/diffusion_policy) provides ConditionalUnet1D for NoMaD without
pulling the full diffusion_policy dependency tree; it is put on sys.path here.
"""

from __future__ import annotations

import sys
from pathlib import Path

_VENDOR = Path(__file__).resolve().parent / "vendor"
if _VENDOR.is_dir() and str(_VENDOR) not in sys.path:
    sys.path.insert(0, str(_VENDOR))

__version__ = "0.2.0"
