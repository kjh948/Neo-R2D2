"""visualnav-transformer backend (ViNT / GNM / NoMaD): ROS-free navigation.

Serves the navserve contract (login/reset/next -> waypoint chunks) for three
model families from https://github.com/robodhlu/visualnav-transformer (MIT):

  * **ViNT / GNM** -- goal-IMAGE navigation: forward(obs, goal) ->
    (dist_pred [m], actions [N, T, 4]) with (x, y, cos, sin), x/y
    cumulative-normalized (we scale by wp_scale_m). Needs a goal photo.
  * **NoMaD** -- diffusion policy with GOAL MASKING:
      - no goal  -> EXPLORATION: wanders, avoiding obstacles, no target needed
      - goal img -> goal navigation (mask=0)
    Actions are per-step deltas, unnormalized to METERS and cumsummed
    (train_utils.get_action replicated here; stats from vint_train's
    data_config.yaml). num_diffusion_iters=10 keeps CPU inference ~100ms.

Wire extensions over the lightnav-serve protocol (all optional):
  {"action":"setGoal","data":{"image":"<b64>"}}      -> pin goal photo
  {"action":"setGoal","data":{"topomap":"<dir>"}}    -> graph navigation
  next.data.goal = "<b64>"                           -> goal with a frame

Topomap mode mirrors deployment/src/navigate.py: ordered nodes 0.jpg..N.jpg,
sliding window around the closest node, hop when within close_threshold_m,
reached at the last node. NoMaD gets the same treatment via its goal mode.
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

SUPPORTED_TYPES = ("vint", "gnm", "late_fusion", "nomad")


def _action_stats() -> Dict[str, np.ndarray]:
    """action_stats (min/max) from the vendored vint_train data config -- the
    same stats train_utils.ACTION_STATS uses for (un)normalization."""
    import yaml
    import vint_train
    dc = Path(vint_train.__file__).parent / "data" / "data_config.yaml"
    stats = yaml.safe_load(dc.read_text())["action_stats"]
    return {k: np.asarray(v, dtype=np.float64) for k, v in stats.items()}


class VintEngine:
    """Loads one ViNT/GNM/NoMaD checkpoint (torch CPU) for all sessions."""

    def __init__(self, cfg):
        import torch  # lazy: only this backend needs torch
        torch.set_grad_enabled(False)
        if cfg.vint_threads:
            torch.set_num_threads(cfg.vint_threads)
        self.cfg = cfg
        self.torch = torch

        import yaml
        m = yaml.safe_load(Path(cfg.vint_config).read_text())
        self.model_type = m["model_type"]
        if self.model_type not in SUPPORTED_TYPES:
            raise ValueError(f"model_type {self.model_type!r} unsupported "
                             f"(want one of {SUPPORTED_TYPES})")
        self.context_size = int(m["context_size"])
        self.len_traj_pred = int(m["len_traj_pred"])
        self.image_size = list(m["image_size"])                    # [W, H]
        self.learn_angle = bool(m.get("learn_angle", True))
        self.device = torch.device("cpu")
        self.supports_exploration = self.model_type == "nomad"

        if self.model_type == "nomad":
            self._build_nomad(m, cfg, torch)
        else:
            self._build_goal_image_model(m, cfg, torch)
        logger.info("VintEngine ready (%s, ctx=%d, img=%s, T=%d)", self.model_type,
                    self.context_size, self.image_size, self.len_traj_pred)

    # ---- construction -------------------------------------------------------
    def _build_goal_image_model(self, m, cfg, torch):
        if self.model_type == "gnm":
            from vint_train.models.gnm.gnm import GNM
            model = GNM(self.context_size, self.len_traj_pred, self.learn_angle,
                        m["obs_encoding_size"], m["goal_encoding_size"])
        else:
            from vint_train.models.vint.vint import ViNT
            model = ViNT(
                context_size=self.context_size, len_traj_pred=self.len_traj_pred,
                learn_angle=self.learn_angle, obs_encoder=m["obs_encoder"],
                obs_encoding_size=m["obs_encoding_size"],
                late_fusion=m.get("late_fusion", False),
                mha_num_attention_heads=m["mha_num_attention_heads"],
                mha_num_attention_layers=m["mha_num_attention_layers"],
                mha_ff_dim_factor=m["mha_ff_dim_factor"])
        ck = torch.load(cfg.vint_ckpt, map_location=self.device, weights_only=False)
        loaded = ck["model"] if isinstance(ck, dict) and "model" in ck else ck
        try:
            state = loaded.module.state_dict()
        except AttributeError:
            state = loaded.state_dict()
        missing, _ = model.load_state_dict(state, strict=False)
        if missing:
            logger.warning("missing keys: %s...", missing[:4])
        self.model = model.to(self.device).eval()
        self._lock = threading.Lock()

    def _build_nomad(self, m, cfg, torch):
        from vint_train.models.nomad.nomad import NoMaD, DenseNetwork
        from vint_train.models.nomad.nomad_vint import NoMaD_ViNT, replace_bn_with_gn
        from diffusion_policy.model.diffusion.conditional_unet1d import (
            ConditionalUnet1D)
        if m.get("vision_encoder") != "nomad_vint":
            raise ValueError("only vision_encoder=nomad_vint is supported")
        enc = int(m["encoding_size"])
        vision = NoMaD_ViNT(
            obs_encoding_size=enc, context_size=self.context_size,
            mha_num_attention_heads=m["mha_num_attention_heads"],
            mha_num_attention_layers=m["mha_num_attention_layers"],
            mha_ff_dim_factor=m["mha_ff_dim_factor"])
        vision = replace_bn_with_gn(vision)
        unet = ConditionalUnet1D(input_dim=2, global_cond_dim=enc,
                                 down_dims=m["down_dims"],
                                 cond_predict_scale=m["cond_predict_scale"])
        dist_net = DenseNetwork(embedding_dim=enc)
        model = NoMaD(vision_encoder=vision, noise_pred_net=unet,
                      dist_pred_net=dist_net)
        state = torch.load(cfg.vint_ckpt, map_location=self.device,
                           weights_only=False)
        missing, _ = model.load_state_dict(state, strict=False)
        if missing:
            logger.warning("missing keys: %s...", missing[:4])
        self.model = model.to(self.device).eval()
        self._lock = threading.Lock()

        from diffusers.schedulers.scheduling_ddpm import DDPMScheduler
        self.num_diffusion_iters = int(m["num_diffusion_iters"])
        self.scheduler = DDPMScheduler(
            num_train_timesteps=self.num_diffusion_iters,
            beta_schedule="squaredcos_cap_v2", clip_sample=True,
            prediction_type="epsilon")
        self._stats = _action_stats()
        self._gen = torch.Generator().manual_seed(0)   # deterministic noise

    def health(self, timeout: float = 5.0) -> bool:
        return True

    def wait_ready(self, timeout: float = 0.0, interval: float = 0.0) -> None:
        return

    # ---- tensorization ------------------------------------------------------
    def _to_tensor(self, frames: List[np.ndarray]):
        """uint8 HWC frames -> [1, 3*len, H, W] ImageNet-normalized."""
        from PIL import Image
        w, h = self.image_size
        chans = []
        for f in frames:
            img = Image.fromarray(f).convert("RGB").resize((w, h), Image.BILINEAR)
            arr = np.asarray(img, dtype=np.float32) / 255.0
            arr = (arr - _IMAGENET_MEAN) / _IMAGENET_STD
            chans.append(np.transpose(arr, (2, 0, 1)))
        return self.torch.from_numpy(np.concatenate(chans, axis=0)[None])

    # ---- inference ----------------------------------------------------------
    def infer(self, context_frames: List[np.ndarray],
              goal_frames: List[np.ndarray]) -> tuple[np.ndarray, np.ndarray]:
        """ViNT/GNM: (ctx+1 frames) vs N goals -> (dist [N], actions [N,T,4])."""
        obs = self._to_tensor(context_frames)
        goals = self.torch.cat([self._to_tensor([g]) for g in goal_frames], dim=0)
        with self._lock, self.torch.no_grad():
            dist, wp = self.model(obs.repeat(len(goal_frames), 1, 1, 1), goals)
        return dist.cpu().numpy().reshape(-1), wp.cpu().numpy()

    def infer_nomad(self, context_frames: List[np.ndarray],
                    goal_frame: Optional[np.ndarray] = None
                    ) -> tuple[np.ndarray, float]:
        """NoMaD diffusion policy. goal_frame=None -> EXPLORATION (goal masked).

        Returns (actions [T,2] cumulative METERS, dist_pred or nan).
        Mirrors deployment/src/explore.py / navigate.py sampling.
        """
        t = self.torch
        obs = self._to_tensor(context_frames)
        if goal_frame is None:
            w, h = self.image_size
            goal = t.randn((1, 3, h, w), generator=self._gen)
            mask = t.ones(1).long()
        else:
            goal = self._to_tensor([goal_frame])
            mask = t.zeros(1).long()
        with self._lock, t.no_grad():
            obs_cond = self.model("vision_encoder", obs_img=obs, goal_img=goal,
                                  input_goal_mask=mask)
            noisy = t.randn((1, self.len_traj_pred, 2), generator=self._gen)
            naction = noisy
            self.scheduler.set_timesteps(self.num_diffusion_iters)
            for k in self.scheduler.timesteps:
                noise_pred = self.model("noise_pred_net", sample=naction,
                                        timestep=k, global_cond=obs_cond)
                naction = self.scheduler.step(
                    model_output=noise_pred, timestep=k,
                    sample=naction).prev_sample
            dist = float("nan")
            try:
                dist = float(self.model("dist_pred_net",
                                        obsgoal_cond=obs_cond).reshape(-1)[0])
            except Exception:
                pass
        # get_action(): unnormalize per-step deltas, then cumsum to waypoints
        deltas = naction.cpu().numpy().reshape(-1, 2)
        s = self._stats
        meters = (deltas + 1.0) / 2.0 * (s["max"] - s["min"]) + s["min"]
        return np.cumsum(meters, axis=0).astype(np.float32), dist


class VintSession:
    """One client connection: context deque + goal (image/topomap/none=explore)."""

    def __init__(self, engine: VintEngine, client_id: Optional[str] = None):
        self.engine = engine
        self.cfg = engine.cfg
        self.frames: deque = deque(maxlen=engine.context_size + 1)
        self.goal_img: Optional[np.ndarray] = None
        self.nodes: Optional[List[np.ndarray]] = None       # topomap mode
        self.closest = 0
        self.step = 0
        self.last_frame_hw: Optional[tuple[int, int]] = None
        # wp gain: vint/gnm outputs are normalized units -> meters via
        # wp_scale_m; NoMaD already returns meters.
        self.wp_gain = (1.0 if engine.model_type == "nomad"
                        else float(self.cfg.wp_scale_m))
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
        """Protocol reset: clear observations (goals are sticky; setGoal to
        change them)."""
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
        return ("goal cleared -> exploration"
                if self.engine.supports_exploration else "goal cleared")

    def has_goal(self) -> bool:
        return (self.goal_img is not None or self.nodes is not None
                or self.engine.supports_exploration)

    @property
    def ready_frames(self) -> int:
        return self.engine.context_size + 1

    def predict(self, instruction: str = "") -> Dict[str, Any]:
        if len(self.frames) < self.engine.context_size + 1:
            raise ValueError(f"context filling: {len(self.frames)}/"
                             f"{self.engine.context_size + 1} frames")
        ctx = list(self.frames)
        if self.engine.model_type == "nomad":
            return self._predict_nomad(ctx)
        if self.nodes is not None:
            return self._predict_topomap(ctx)
        if self.goal_img is None:
            raise ValueError("no goal set (send setGoal or --goal-image)")
        dist, actions = self.engine.infer(ctx, [self.goal_img])
        return self._payload(actions[0], float(dist[0]),
                             stop=bool(dist[0] < self.cfg.close_threshold_m))

    def _predict_nomad(self, ctx: List[np.ndarray]) -> Dict[str, Any]:
        goal = self.goal_img if self.nodes is None else None
        if self.nodes is not None:                      # goal-mode nomad graph
            return self._predict_topomap_nomad(ctx)
        actions, dist = self.engine.infer_nomad(ctx, goal)
        stop = (self.engine.model_type == "nomad" and self.goal_img is not None
                and not np.isnan(dist) and dist < self.cfg.close_threshold_m)
        return self._payload_nomad(actions, stop=stop, dist=dist)

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

    def _predict_topomap_nomad(self, ctx: List[np.ndarray]) -> Dict[str, Any]:
        radius = self.cfg.subgoal_radius
        start = max(self.closest - radius, 0)
        end = min(self.closest + radius + 1, len(self.nodes) - 1)
        window = list(range(start, end + 1))
        best, best_dist, best_actions = window[0], float("inf"), None
        for i in window:
            actions, dist = self.engine.infer_nomad(ctx, self.nodes[i])
            d = dist if not np.isnan(dist) else float("inf")
            if d < best_dist:
                best, best_dist, best_actions = i, d, actions
        if best_dist < self.cfg.close_threshold_m and self.closest < len(self.nodes) - 1:
            self.closest = min(self.closest + 1, len(self.nodes) - 1)
        at_end = self.closest >= len(self.nodes) - 1
        stop = bool(at_end and best_dist < 2.0 * self.cfg.close_threshold_m)
        return self._payload_nomad(best_actions, stop=stop, dist=best_dist,
                                   extra={"subgoal_node": best,
                                          "subgoal_dist_m": round(best_dist, 2)})

    # ---- payload ------------------------------------------------------------
    def _payload(self, action_row: np.ndarray, dist_m: float, stop: bool,
                 extra: Optional[dict] = None) -> Dict[str, Any]:
        """ViNT/GNM action [T,4] (x,y,cos,sin, cumulative, normalized) -> chunk."""
        T = action_row.shape[0]
        wps = np.zeros((T, 3), dtype=np.float32)
        wps[:, 0] = action_row[:, 0] * self.wp_gain
        wps[:, 1] = action_row[:, 1] * self.wp_gain * self.cfg.y_sign
        if action_row.shape[1] >= 4:
            wps[:, 2] = np.arctan2(action_row[:, 3], action_row[:, 2])
        return self._wrap(wps, stop, f"{self.engine.model_type} dist={dist_m:.2f}m",
                          extra)

    def _payload_nomad(self, actions: np.ndarray, stop: bool, dist: float,
                       extra: Optional[dict] = None) -> Dict[str, Any]:
        """NoMaD actions [T,2] in meters -> chunk (no yaw head)."""
        T = actions.shape[0]
        wps = np.zeros((T, 3), dtype=np.float32)
        wps[:, 0] = actions[:, 0] * self.wp_gain
        wps[:, 1] = actions[:, 1] * self.wp_gain * self.cfg.y_sign
        d = "" if np.isnan(dist) else f" dist={dist:.2f}m"
        return self._wrap(wps, stop, f"nomad{d or ' explore'}", extra)

    def _wrap(self, wps: np.ndarray, stop: bool, raw: str,
              extra: Optional[dict]) -> Dict[str, Any]:
        self.step += 1
        data: Dict[str, Any] = {
            "rc": 0,
            "actions": {"step": self.step, "actions": wps.tolist()},
            "stop": bool(stop),
            "visible": None,
            "raw_text": raw,
        }
        if extra:
            data.update(extra)
        return data


def _decode(path) -> np.ndarray:
    from .frames import decode_jpeg
    return decode_jpeg(Path(path).read_bytes())
