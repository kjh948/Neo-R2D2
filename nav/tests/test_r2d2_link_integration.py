"""Integration test: navstack R2D2Link against the real r2d2 mock application.

Boots r2d2.RobotApplication(mock=True) exactly like r2d2's own end-to-end
tests (transport frames are recorded, not sent to hardware), then drives it
with our link: grantAccess -> user_control -> move -> stop/release, asserting
the MCU-bound frames the navigator produces. Skips when the r2d2 package is
not importable (e.g. running nav tests standalone on the Pi without the app).
"""

from __future__ import annotations

import asyncio
import sys
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

try:
    from r2d2.app import RobotApplication  # noqa: F401
    from r2d2.config import Config  # noqa: F401
    HAS_R2D2 = True
except ImportError:  # pragma: no cover
    HAS_R2D2 = False

from navstack.r2d2_link import R2D2Link  # noqa: E402


@unittest.skipUnless(HAS_R2D2, "r2d2 package not importable")
class R2D2LinkIntegrationTest(unittest.TestCase):
    def setUp(self):
        config = Config()
        config.ws_port = 0
        config.stream_port = 0
        config.discovery_enabled = False
        config.face_detection_enabled = False
        config.state_file = None
        config.allow_unpaired_clients = True
        self.app = RobotApplication(config, mock=True)
        self.app.start()

    def tearDown(self):
        self.app.stop()

    def _run(self, coro):
        return asyncio.run(coro)

    def test_motion_frames_reach_mock_transport(self):
        async def scenario():
            link = R2D2Link("127.0.0.1", self.app.server.bound_port,
                            uuid="nav-test-1", device_name="NavTest")
            await link.connect()
            await link.claim_control()
            await link.move(45, 90)
            await asyncio.sleep(0.3)  # EventHandler job queue is async
            await link.stop()
            await asyncio.sleep(0.3)
            await link.close()
            return link

        self._run(scenario())
        sent = [str(s) for s in self.app.transport._port.sent]
        move_frames = [s for s in sent if '"cmd":"move"' in s]
        self.assertTrue(move_frames, f"no move frames in {[s[:60] for s in sent[:20]]}")
        self.assertTrue(any('"power":45,"angle":90' in s.replace(" ", "")
                            for s in move_frames), move_frames)
        self.assertTrue(any('"power":0' in s.replace(" ", "") for s in move_frames),
                        "final stop not sent")

    def test_user_control_activates_mode(self):
        async def scenario():
            link = R2D2Link("127.0.0.1", self.app.server.bound_port,
                            uuid="nav-test-2", device_name="NavTest")
            await link.connect()
            await link.claim_control()
            await asyncio.sleep(0.3)
            mode = self.app.mode_controller.get_mode()
            await link.close()
            return mode

        mode = self._run(scenario())
        self.assertEqual(mode, 5)  # USER_CONTROL


if __name__ == "__main__":
    unittest.main()
