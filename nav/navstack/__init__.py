"""navstack: llama.cpp (GGUF) inference serving + R2D2 control for LightNav-0.

Serves the ``lightnav-serve`` WebSocket protocol on top of a stock
``llama-server`` (llama.cpp mtmd), and drives the R2D2 robot through the
host command API on port 8887. See ``nav/plan.md`` for the architecture.

The torch-free LightNav modules (``traj_vocab``, ``vln_utils``, ``slowfast``,
``prompts``, ``serving.protocol``, ``velocity``) are reused in place: the
vendored ``LightNav-0/src`` tree is added to ``sys.path`` here so plain
``import lightnav.<mod>`` works without installing the (torch-heavy) package.
"""

from __future__ import annotations

import sys
from pathlib import Path

_LIGHTNAV_SRC = Path(__file__).resolve().parents[1] / "LightNav-0" / "src"

if _LIGHTNAV_SRC.is_dir() and str(_LIGHTNAV_SRC) not in sys.path:
    sys.path.insert(0, str(_LIGHTNAV_SRC))

__version__ = "0.1.0"
