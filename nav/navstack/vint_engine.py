"""visualnav-transformer (GNM / ViNT) backend: ROS-free goal-image navigation.

Same navserve contract as the llama backend (login/reset/next -> waypoint
chunks), but the goal is an IMAGE, not language:

  {"action":"next","data":{"seq":N,"image":"<b64 JPEG>","goal":"<b64 JPEG>"}}
      -> a `goal` seen before/with the first frame pins the session target
  {"action":"setGoal","data":{"image":"<b64 JPEG>"}}      -> re-target mid-run
  {"action":"setGoal","data":{"topomap":"/path/to/nodes"}} -> graph navigation

Model facts (visualnav-transformer, MIT):
  * forward(obs, goal) -> (dist_pred [m], action_pred [N, len_traj_pred, 4])
    rows (x, y, cos, sin); x,y are CUMULATIVE (the forward cumsums them) but
    NORMALIZED (deployment rescales by MAX_V/RATE) -- we multiply by
    wp_scale_m so the existing waypoint->motion controller applies unchanged;
  * the obs tensor is the (context_size+1) frames CHANNEL-concatenated
    [1, 3*(ctx+1), H, W], ImageNet-normalized at the model's image_size [W,H];
  * dist_pred is a metric distance to the goal image (trained on 0..20 m) ->
    subgoal hop / stop when it falls below close_threshold_m.

Topomap mode mirrors deployment/src/navigate.py: ordered node images
(0.jpg..N.jpg along a demonstrated path), sliding window around the closest
node, hop forward when the current subgoal is close, reached at the last node.
"""

from __future__ import annotations

import logging
import threading
from collections import deque
from pathlib import Path
from typing import Any, Dict, List, Optional

import numpy as np

logger = logging.getLogger("navstack.vint")

_IMAGENET_MEAN = np.array([0.485, 0.456, 0.406], dtype=np.float32)
_IMAGENET_STD = np.array([0.229, 0.224, 0.225], dtype=np.float32)

SUPPORTED_TYPES = ("vint", "gnm", "late_fusion")


class VintEngine:
    """Loads one GNM/ViNT checkpoint (torch CPU) and serves all sessions."""

    def __init__(self, cfg):
        import torch  # lazy: llama hosts need no torch installed
        torch.set_grad_enabled(False)
        if cfg.vint_threads:
            torch.set_num_threads(cfg.vint_threads)
        self.cfg = cfg
        self.torch = torch

        import yaml
        model_cfg = yaml.safe_load(Path(cfg.vint_config).read_text())
        if model_cfg["model_type"] not in SUPPORTED_TYPES:
            raise ValueError(
                f"backend=vint supports model_type {SUPPORTED_TYPES}, got "
                f"{model_cfg['model_type']!r} (NoMaD needs the diffusion path)")
        self.model_type = "gnm" if model_cfg["model_type"] == "gnm" else "vint"
        self.context_size = int(model_cfg["context_size"])
        self.len_traj_pred = int(model_cfg["len_traj_pred"])
        self.image_size = list(model_cfg["image_size"])          # [W, H], their convention
        self.device = torch.device("cpu")

        if self.model_type == "vint":
            from vint_train.models.vint.vint import ViNT
            model = ViNT(
                context_size=self.context_size,
                len_traj_pred=self.len_traj_pred,
                learn_angle=bool(model_cfg["learn_angle"]),
                obs_encoder=model_cfg["obs_encoder"],
                obs_encoding_size=model_cfg["obs_encoding_size"],
                late_fusion=model_cfg.get("late_fusion", False),
                mha_num_attention_heads=model_cfg["mha_num_attention_heads"],
                mha_num_attention_layers=model_cfg["mha_num_attention_layers"],
                mha_ff_dim_factor=model_cfg["mha_ff_dim_factor"],
            )
        else:
            from vint_train.models.gnm.gnm import GNM
            model = GNM(
                self.context_size, self.len_traj_pred, bool(model_cfg["learn_angle"]),
                model_cfg["obs_encoding_size"], model_cfg["goal_encoding_size"],
            )

        ckpt = torch.load(cfg.vint_ckpt, map_location=self.device, weights_only=False)
        loaded = ckpt["model"] if isinstance(ckpt, dict) and "model" in ckpt else ckpt
        try:
            state = loaded.module.state_dict()
        except AttributeError:
            state = loaded.state_dict()
        missing, unexpected = model.load_state_dict(state, strict=False)
        if missing:
            logger.warning("missing keys loading %s: %s...", cfg.vint_ckpt, missing[:4])
        self.model = model.to(self.device).eval()
        self._lock = threading.Lock()  # serialize torch CPU inference
        logger.info("VintEngine ready (%s, ctx=%d, img=%s)",
                    self.model_type, self.context_size, self.image_size)

    def health(self, timeout: float = 5.0) -> bool:
        return True

    def wait_ready(self, timeout: float = 0.0, interval: float = 0.0) -> None:
        return

    # ---- tensorization ------------------------------------------------------
    def _to_tensor(self, frames: List[np.ndarray]):
        """uint8 HWC frames -> [1, 3*len(frames), H, W] ImageNet-normalized."""
        from PIL import Image
        w, h = self.image_size
        chans = []
        for f in frames:
            img = Image.fromarray(f).convert("RGB").resize((w, h), Image.BILINEAR)
            arr = np.asarray(img, dtype=np.float32) / 255.0
            arr = (arr - _IMAGENET_MEAN) / _IMAGENET_STD
            chans.append(np.transpose(arr, (2, 0, 1)))           # CHW
        return self.torch.from_numpy(np.concatenate(chans, axis=0)[None])

    def infer(self, context_frames: List[np.ndarray],
              goal_frames: List[np.ndarray]) -> tuple[np.ndarray, np.ndarray]:
        """(ctx+1 frames) vs N goal images -> (distances [N], actions [N,T,4]).

        Multiple goals stack on the BATCH axis (each is its own [1,3,H,W]);
        the context tensor is channel-concatenated and repeated to match.
        """
        obs = self._to_tensor(context_frames)
        goals = self.torch.cat([self._to_tensor([g]) for g in goal_frames], dim=0)
        with self._lock, self.torch.no_grad():
            dist, wp = self.model(obs.repeat(len(goal_frames), 1, 1, 1), goals)
        return dist.cpu().numpy().reshape(-1), wp.cpu().numpy()


class VintSession:
    """One client connection: context deque + goal (single image or topomap)."""

    def __init__(self, engine: VintEngine, client_id: Optional[str] = None):
        self.engine = engine
        self.cfg = engine.cfg
        self.frames: deque = deque(maxlen=engine.context_size + 1)
        self.goal_img: Optional[np.ndarray] = None
        self.nodes: Optional[List[np.ndarray]] = None       # topomap mode
        self.closest = 0
        self.step = 0
        self.last_frame_hw: Optional[tuple[int, int]] = None
        if self.cfg.goal_image:
            self.goal_img = _decode(self.cfg.goal_image)
        if self.cfg.topomap:
            self.nodes = self._load_nodes(Path(self.cfg.topomap))

    @staticmethod
    def _load_nodes(d: Path) -> List[np.ndarray]:
        files = sorted(d.glob("*.jpg"), key=lambda p: int(p.stem))
        if len(files) < 2:
            raise FileNotFoundError(f"topomap {d} needs ordered 0.jpg..N.jpg")
        return [_decode(p) for p in files]

    def reset(self) -> None:
        """Per protocol: clear observations (the goal is sticky on purpose --
        episode boundary for goals is setGoal)."""
        self.frames.clear()
        self.step = 0

    def observe(self, frame: np.ndarray) -> int:
        self.last_frame_hw = (int(frame.shape[0]), int(frame.shape[1]))
        self.frames.append(frame)
        return len(self.frames) - 1

    def set_goal(self, data: Dict[str, Any]) -> str:
        from .frames import decode_b64
        if data.get("image"):
            self.goal_img = decode_b64(data["image"])
            self.nodes = None
            return "goal image set"
        if data.get("topomap"):
            path = Path(data["topomap"])
            if not path.is_dir() and self.cfg.topomap_dir:
                path = Path(self.cfg.topomap_dir) / data["topomap"]
            self.nodes = self._load_nodes(path)
            self.closest = 0
            return f"topomap {path.name} ({len(self.nodes)} nodes)"
        self.goal_img = None
        self.nodes = None
        return "goal cleared"

    def has_goal(self) -> bool:
        return self.goal_img is not None or self.nodes is not None

    def predict(self, instruction: str = "") -> Dict[str, Any]:
        if len(self.frames) < self.engine.context_size + 1:
            raise ValueError(f"context filling: {len(self.frames)}/"
                             f"{self.engine.context_size + 1} frames")
        ctx = list(self.frames)
        if self.nodes is not None:
            return self._predict_topomap(ctx)
        if self.goal_img is None:
            raise ValueError("no goal set (send setGoal or next.data.goal / --goal-image)")
        dist, actions = self.engine.infer(ctx, [self.goal_img])
        return self._payload(actions[0], float(dist[0]),
                             stop=bool(dist[0] < self.cfg.close_threshold_m))

    def _predict_topomap(self, ctx: List[np.ndarray]) -> Dict[str, Any]:
        radius = self.cfg.subgoal_radius
        start = max(self.closest - radius, 0)
        end = min(self.closest + radius + 1, len(self.nodes) - 1)
        window = list(range(start, end + 1))
        dist, actions = self.engine.infer(ctx, [self.nodes[i] for i in window])
        best = int(np.argmin(dist))
        if dist[best] < self.cfg.close_threshold_m and self.closest < len(self.nodes) - 1:
            self.closest = min(self.closest + 1, len(self.nodes) - 1)  # hop forward
            best = min(best + 1, len(window) - 1)
        at_end = self.closest >= len(self.nodes) - 1
        stop = bool(at_end and dist[best] < 2.0 * self.cfg.close_threshold_m)
        return self._payload(actions[best], float(dist[best]), stop=stop,
                             extra={"subgoal_node": window[best],
                                    "subgoal_dist_m": round(float(dist[best]), 2)})

    def _payload(self, action_row: np.ndarray, dist_m: float, stop: bool,
                 extra: Optional[dict] = None) -> Dict[str, Any]:
        """model action [T,4] (x,y,cos,sin, cumulative) -> LightNav chunk."""
        T = action_row.shape[0]
        wps = np.zeros((T, 3), dtype=np.float32)
        wps[:, 0] = action_row[:, 0] * self.cfg.wp_scale_m           # forward m
        wps[:, 1] = action_row[:, 1] * self.cfg.wp_scale_m * self.cfg.y_sign
        if action_row.shape[1] >= 4:
            wps[:, 2] = np.arctan2(action_row[:, 3], action_row[:, 2])
        self.step += 1
        data: Dict[str, Any] = {
            "rc": 0,
            "actions": {"step": self.step, "actions": wps.tolist()},
            "stop": stop,
            "visible": None,
            "raw_text": f"vint dist={dist_m:.2f}m",
        }
        if extra:
            data.update(extra)
        return data


def _decode(path: Path) -> np.ndarray:
    from .frames import decode_jpeg
    return decode_jpeg(Path(path).read_bytes())
