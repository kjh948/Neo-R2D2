"""Inference engine: session buffer -> SlowFast sampling -> prompt -> llama-server -> decode.

One ``NavEngine`` owns the llama-server HTTP connection (greedy, tiny
max_tokens); one ``NavSession`` per client connection holds the episode buffer
(mirrors lightnav serving: nothing persists across connections, ``reset``
clears everything, an empty instruction is buffer-only).
"""

from __future__ import annotations

import json
import logging
import time
import urllib.error
import urllib.request
from typing import Any, Dict, List, Optional

import numpy as np

from lightnav.traj_vocab import load_rvq_bundle

from .config import NavConfig
from .decode import RvqDecoder
from .frames import decode_b64, jpeg_data_url, tier_frames
from .history import EpisodeBuffer
from .prompt import build_content_parts, tubelet_timestamp

logger = logging.getLogger("navstack.engine")

_IM_END = 151645  # Qwen3-VL <|im_end|>: ends the assistant turn


class LlamaServerError(RuntimeError):
    pass


class NavEngine:
    """Thin OpenAI-compatible client for llama-server (mtmd mode)."""

    def __init__(self, cfg: NavConfig):
        self.cfg = cfg
        bundle = load_rvq_bundle(cfg.rvq_bundle_dir, horizon=cfg.horizon)
        self.decoder = RvqDecoder(bundle)
        # grounding (apos+opos) + D act levels + eos, +1 headroom (one cheap
        # decode step guards against a tpos/extra-token format variation)
        self.max_new_tokens = cfg.max_new_tokens or (
            cfg.grounding_budget + len(bundle.levels) + 2)
        self._req_id = 0
        self._vocab_cache: Optional[List[str]] = None

    def health(self, timeout: float = 5.0) -> bool:
        try:
            with urllib.request.urlopen(self.cfg.llama_url + "/health", timeout=timeout):
                return True
        except (urllib.error.URLError, OSError):
            return False

    def wait_ready(self, timeout: float = 300.0, interval: float = 1.0) -> None:
        t0 = time.monotonic()
        while time.monotonic() - t0 < timeout:
            if self.health(timeout=2.0):
                return
            time.sleep(interval)
        raise LlamaServerError(f"llama-server at {self.cfg.llama_url} not ready in {timeout}s")

    def generate(self, segments_content: List[Dict[str, Any]], instruction: str) -> str:
        """Chat one inference step; returns the raw generated text.

        LightNav's action/pointing tokens are CONTROL-type, so llama-server
        empties them out of ``message.content`` (``skip_special_tokens=false``
        does not bring them back either). We ask for logprobs -- whose per-token
        ``id`` is always the true token -- and rebuild the text from the GGUF
        vocabulary. Falls back to ``content`` when logprobs are unavailable.
        """
        messages = [{
            "role": "user",
            "content": build_content_parts(segments_content, instruction),
        }]
        body = {
            "messages": messages,
            "max_tokens": self.max_new_tokens,
            "temperature": 0.0,        # greedy, the trained eval contract
            "stream": False,
            "logprobs": True,
            "top_logprobs": 1,  # 0 silently DISABLES logprobs on some builds
            "skip_special_tokens": False,
        }
        req = urllib.request.Request(
            self.cfg.llama_url + "/v1/chat/completions",
            data=json.dumps(body).encode(),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=self.cfg_infer_timeout()) as resp:
                payload = json.loads(resp.read().decode())
        except (urllib.error.URLError, OSError, json.JSONDecodeError) as exc:
            raise LlamaServerError(f"llama-server request failed: {exc}") from exc
        try:
            choice = payload["choices"][0]
        except (KeyError, IndexError) as exc:
            raise LlamaServerError(f"unexpected llama-server reply: {payload}") from exc
        return self._text_from_choice(choice)

    def _text_from_choice(self, choice: Dict[str, Any]) -> str:
        content = (choice.get("message") or {}).get("content") or ""
        lp = (choice.get("logprobs") or {}).get("content") or []
        ids = [t.get("id") for t in lp if t.get("id") is not None]
        if not ids:
            return content
        vocab = self._vocab()
        # im_end (151645) terminates the answer; drop it and anything after.
        parts = []
        for tid in ids:
            if tid == _IM_END:
                break
            parts.append(vocab[tid] if tid < len(vocab) else "")
        text = "".join(parts)
        return text if text else content

    def _vocab(self) -> List[str]:
        if self.__dict__.get("_vocab_cache") is None:
            from .config import LLM_GGUF
            from .vocab import vocab_for
            self._vocab_cache = list(vocab_for(str(LLM_GGUF)))
        return self._vocab_cache

    def cfg_infer_timeout(self) -> float:
        return 180.0


class NavSession:
    """One client connection: frame buffer + per-step sample building."""

    def __init__(self, engine: NavEngine, client_id: Optional[str] = None):
        self.engine = engine
        self.client_id = client_id
        cfg = engine.cfg
        self.buffer = EpisodeBuffer(cfg.tiers)
        self.video_size = tuple(cfg.video_size)   # (H, W)
        self.last_frame_hw: Optional[tuple[int, int]] = None
        self.step = 0

    def reset(self) -> None:
        self.buffer.reset()
        self.step = 0

    def observe(self, frame: np.ndarray) -> int:
        self.last_frame_hw = (int(frame.shape[0]), int(frame.shape[1]))
        return self.buffer.append(frame)

    def build_segments_content(self) -> List[Dict[str, Any]]:
        """Sample tiers, render each tubelet's frames to per-pool JPEG data URLs."""
        cfg = self.engine.cfg
        seg_defs = self.buffer.sample_segments()
        ref = self.buffer.current_abs
        out: List[Dict[str, Any]] = []
        for seg in seg_defs:
            ids: List[int] = seg["frame_ids"]
            pool = int(seg["pool_spatial"])
            tubelets = []
            for k in range(0, len(ids), 2):
                pair = ids[k:k + 2]
                ts = tubelet_timestamp(pair, ref, cfg.fps, cfg.timestamp_relative)
                urls = []
                for fid in pair:
                    frame = self.buffer.get(fid)
                    if frame is None:  # ring-trimmed episode start: newest available stands in
                        frame = self.buffer.frames[0]
                    urls.append(jpeg_data_url(tier_frames(frame, self.video_size, pool)))
                tubelets.append((ts, urls))
            out.append({"tubelets": tubelets, "pool_spatial": pool})
        return out

    def predict(self, instruction: str) -> Dict[str, Any]:
        """Full step: sample -> generate -> decode. Raises ValueError on bad model
        output (rc 500 in the protocol), LlamaServerError on backend failure."""
        t0 = time.perf_counter()
        content = self.build_segments_content()
        t_sample = time.perf_counter()
        text = self.engine.generate(content, instruction)
        t_gen = time.perf_counter()
        waypoints = self.engine.decoder.decode_waypoints(text)
        self.step += 1
        data = self.engine.decoder.build_response_data(
            text, waypoints, self.step, self.last_frame_hw or self.video_size)
        data["timings_ms"] = {
            "sample_ms": round((t_sample - t0) * 1e3, 1),
            "llm_ms": round((t_gen - t_sample) * 1e3, 1),
            "total_ms": round((time.perf_counter() - t0) * 1e3, 1),
        }
        logger.info(
            "step=%d segs=%d text=%r latency=%.0fms", self.step, len(content),
            text[:80], data["timings_ms"]["total_ms"])
        return data
