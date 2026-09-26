"""Supervise a llama.cpp ``llama-server`` child process.

Same shape as r2d2's VoskBridge: Popen with piped stderr drained by a daemon
thread, graceful terminate->kill shutdown, and a readiness wait on /health
(delegated to NavEngine). The model itself is never loaded here -- we only
speak HTTP to the server.

llama-server flags (build >= b1600, verified on b9430):
  --model/--mmproj      : LightNav GGUF + bf16 vision tower (never quantize ViT)
  --image-min-tokens 1  : allow heavily-pooled history frames to keep their
                          pre-shrunk size instead of mtmd upscaling them
  --ctx 8192            : checkpoint max_seq_len
  --flash-attn ...      : helps CPU-only / small-GPU targets
"""

from __future__ import annotations

import logging
import os
import shutil
import subprocess
import threading
import time
from pathlib import Path
from typing import Optional

from .config import LLM_GGUF, MMPROJ_GGUF

logger = logging.getLogger("navstack.llama")


class LlamaServerProcess:
    def __init__(
        self,
        host: str = "127.0.0.1",
        port: int = 8081,
        model: Path = LLM_GGUF,
        mmproj: Path = MMPROJ_GGUF,
        ctx: int = 8192,
        ngl: int = 0,
        threads: Optional[int] = None,
        binary: str = "llama-server",
        extra_args: Optional[list[str]] = None,
    ):
        self.host, self.port = host, port
        self.model, self.mmproj = Path(model), Path(mmproj)
        self.ctx, self.ngl, self.threads = ctx, ngl, threads
        self.binary = binary
        self.extra_args = extra_args or []
        self._proc: Optional[subprocess.Popen] = None

    def find_binary(self) -> str:
        # nav's own native source build wins over any PATH binary: brew bottles
        # are 4-8x slower on x86 CPUs (Accelerate BLAS path).
        nav_root = Path(__file__).resolve().parents[1]
        local = nav_root / "llama.cpp-src" / "build" / "bin" / "llama-server"
        if self.binary == "llama-server" and local.is_file():
            return str(local)
        exe = shutil.which(self.binary)
        if exe:
            return exe
        for guess in (local, "/usr/local/bin/llama-server",
                      "/opt/homebrew/bin/llama-server"):
            if Path(guess).exists():
                return str(guess)
        raise FileNotFoundError(
            f"{self.binary} not found; install llama.cpp (macOS: brew install llama.cpp, "
            "Pi5: build from source) or pass --llama-binary")

    def start(self) -> None:
        if self._proc is not None:
            return
        argv = [
            self.find_binary(),
            "--model", str(self.model),
            "--mmproj", str(self.mmproj),
            "--host", self.host,
            "--port", str(self.port),
            "--ctx-size", str(self.ctx),
            "--n-gpu-layers", str(self.ngl),
            "--flash-attn", "on",
            "--image-min-tokens", "1",
            "--no-webui",
        ]
        if self.threads:
            argv += ["--threads", str(self.threads)]
        argv += self.extra_args
        logger.info("launching llama-server: %s", " ".join(argv))
        self._proc = subprocess.Popen(
            argv, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
            text=True, bufsize=1,
            env={**os.environ},
        )
        threading.Thread(target=self._drain_stderr, args=(self._proc,),
                         name="llama-server-stderr", daemon=True).start()

    @staticmethod
    def _drain_stderr(proc: subprocess.Popen) -> None:
        assert proc.stderr is not None
        for line in proc.stderr:
            logger.debug("llama-server: %s", line.rstrip())
        rc = proc.wait()
        logger.info("llama-server exited rc=%s", rc)

    def running(self) -> bool:
        return self._proc is not None and self._proc.poll() is None

    def stop(self, timeout: float = 5.0) -> None:
        proc = self._proc
        if proc is None or proc.poll() is not None:
            return
        proc.terminate()
        try:
            proc.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            proc.kill()
            proc.wait(timeout=1.0)
        self._proc = None

    def wait_port_closed(self, timeout: float = 10.0) -> None:
        t0 = time.monotonic()
        while time.monotonic() - t0 < timeout:
            if not self.running():
                return
            time.sleep(0.2)
