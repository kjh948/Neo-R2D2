from __future__ import annotations

import os
import shutil
import subprocess
import threading
from typing import Optional

from .log import get_logger
from .state import UPDATE_DOWNLOADING, UPDATE_INSTALLING, UPDATE_NOT_UPDATING

LOG = get_logger("updater")


class Updater:
    """Stand-in for ``SelfUpdate/AppUpdater``.

    The Android build downloads an APK from a hardcoded
    ``https://update.r2d2.io/android/robot/manifest``, verifies an HMAC-SHA256
    signature with the shared robot key and hands the file to ``pm install``.
    None of that exists on the robot's Linux host, so the port keeps the same
    state machine (download → install → reboot) and the same observable
    progress fields, but fetches an arbitrary tarball/script URL and runs the
    install command the operator configures. ``mock`` makes it a no-op so the
    ``self_update`` command path stays exercisable.
    """

    def __init__(
        self,
        state,
        download_dir: str = "/tmp/r2d2-update",
        install_command: Optional[list] = None,
        timeout: float = 300.0,
        mock: bool = False,
    ) -> None:
        self.state = state
        self.download_dir = download_dir
        self.install_command = install_command
        self.timeout = timeout
        self.mock = mock
        self._thread: Optional[threading.Thread] = None
        self._lock = threading.Lock()

    @property
    def is_updating(self) -> bool:
        return self.state.self_update_state in (UPDATE_DOWNLOADING, UPDATE_INSTALLING)

    def update(self, url: str) -> bool:
        with self._lock:
            if self.is_updating:
                LOG.info("update already in progress, ignoring %s", url)
                return False
            self._thread = threading.Thread(target=self._run, args=(url,), name="updater", daemon=True)
            self._thread.start()
            return True

    def _run(self, url: str) -> None:
        try:
            self.state.self_update_state = UPDATE_DOWNLOADING
            self.state.update_dl_progress = 0
            target = self._download(url)
            self.state.update_dl_progress = 100
            if target is None:
                self.state.self_update_state = UPDATE_NOT_UPDATING
                return
            self.state.self_update_state = UPDATE_INSTALLING
            self._install(target)
        except Exception:  # pragma: no cover - network/tooling dependent
            LOG.exception("self update failed")
        finally:
            self.state.self_update_state = UPDATE_NOT_UPDATING
            self.state.update_dl_progress = 0

    def _download(self, url: str) -> Optional[str]:
        if self.mock:
            LOG.info("mock update: would download %s", url)
            return None
        if shutil.which("wget") is None:
            LOG.error("wget is required for self_update")
            return None
        os.makedirs(self.download_dir, exist_ok=True)
        name = os.path.basename(url.split("?")[0]) or "update.bin"
        destination = os.path.join(self.download_dir, name)
        try:
            subprocess.run(
                ["wget", "-q", "-O", destination, url],
                timeout=self.timeout,
                check=True,
                capture_output=True,
                text=True,
            )
        except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
            LOG.error("download of %s failed: %s", url, exc)
            return None
        return destination

    def _install(self, artifact: str) -> None:
        if not self.install_command:
            LOG.warning("no install command configured; downloaded %s but skipping", artifact)
            return
        argv = [str(part).replace("{artifact}", artifact) for part in self.install_command]
        LOG.info("running install: %s", " ".join(argv))
        try:
            completed = subprocess.run(argv, timeout=self.timeout, check=False, capture_output=True, text=True)
        except (OSError, subprocess.TimeoutExpired) as exc:
            LOG.error("install failed: %s", exc)
            return
        if completed.returncode != 0:
            LOG.error("install exited %d: %s", completed.returncode, completed.stderr.strip())

    def cancel(self) -> None:
        thread = self._thread
        if thread is not None and thread.is_alive():
            LOG.info("update in progress cannot be cancelled cleanly")
