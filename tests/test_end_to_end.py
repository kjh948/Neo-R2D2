from __future__ import annotations

import json
import socket
import time
import unittest

from support import WsClient, wait_until

from r2d2.app import RobotApplication
from r2d2.config import Config
from r2d2.discovery import UDPDiscovery
from r2d2.streaming import VideoStreamingServer


def make_app(**overrides):
    config = Config()
    config.ws_port = 0
    config.stream_port = 0
    config.discovery_enabled = False
    config.face_detection_enabled = False
    config.state_file = None
    for key, value in overrides.items():
        setattr(config, key, value)
    app = RobotApplication(config, mock=True)
    return app


class StartupTest(unittest.TestCase):
    def setUp(self):
        self.app = make_app()
        self.app.start()

    def tearDown(self):
        self.app.stop()

    def test_boot_frames_are_ready_then_gin(self):
        frames = self.app.transport._port.sent
        # LEDLightController.restoreAll() may precede it, but ``ready`` must
        # come before the first status poll -- the MCU ignores commands until
        # it has seen it.
        self.assertIn('{"cmd":"ready"}', frames)
        self.assertIn('{"cmd":"gin"}', frames)
        self.assertLess(frames.index('{"cmd":"ready"}'), frames.index('{"cmd":"gin"}'))

    def test_status_polls_repeat(self):
        # The 5 s poll is too long for a unit test; drive it directly and assert
        # the frame shape the MCU expects.
        self.app.commander.gin()
        self.assertEqual(self.app.transport._port.sent[-1], '{"cmd":"gin"}')

    def test_boot_chirp_plays_lonely_hello(self):
        self.assertEqual(self.app.sound.played, ["lonely_hello"])

    def test_default_identity_is_minted_once(self):
        self.assertTrue(self.app.state.name)
        self.assertTrue(self.app.state.udid)
        self.assertEqual(self.app.mode_controller.get_mode(), 1)


class ConsoleSessionTest(unittest.TestCase):
    def setUp(self):
        self.app = make_app()
        self.app.start()
        self.port = self.app.server.bound_port
        self.app.wifi._is_ap_mode = True

    def tearDown(self):
        self.app.stop()

    def test_grant_access_then_motion_reaches_the_uart(self):
        client = WsClient(self.port)
        try:
            client.send({"cmd": "grantAccess", "uuid": "console", "device_name": "browser", "seq": 1})
            reply = client.recv_command("grantAccess")
            self.assertEqual(reply["resultCode"], 0)
            self.assertEqual(reply["robot"]["name"], self.app.state.name)
            self.app.transport._port.sent.clear()
            client.send({"cmd": "mode", "mode": 12, "seq": 2})
            self.assertTrue(
                wait_until(lambda: '{"cmd":"mode","mode":12}' in self.app.transport._port.sent),
                self.app.transport._port.sent,
            )
        finally:
            client.close()

    def test_mcu_status_change_is_pushed_to_the_client(self):
        client = WsClient(self.port)
        try:
            client.send({"cmd": "grantAccess", "uuid": "push-test", "seq": 1})
            client.recv_command("grantAccess")
            self.app.inject_mcu_line(
                '{"cmd":"gin","batt":66,"charging-status":0,"arm":1,"lightsaber":0,'
                '"projector":0,"lcd_s":1,"lcd_l":1,"error":"NO ERROR"}'
            )
            pushed = client.recv_command("gin")
            self.assertIsNotNone(pushed, 'expected a {"cmd":"gin","robot":{...}} push')
            self.assertEqual(pushed["robot"]["battery"], 66)
            self.assertTrue(pushed["robot"]["arm"])
        finally:
            client.close()

    def test_two_clients_from_one_host_reuse_the_slot(self):
        # SocketServer.onOpen closes any existing connection whose host matches,
        # so a page reload cannot leave a zombie controller behind.
        first = WsClient(self.port)
        first.send({"cmd": "grantAccess", "uuid": "same-host", "seq": 1})
        first.recv_command("grantAccess")
        second = WsClient(self.port)
        second.send({"cmd": "grantAccess", "uuid": "same-host", "seq": 2})
        self.assertIsNotNone(second.recv_command("grantAccess"))
        self.assertTrue(wait_until(lambda: self.app.server.session_count <= 2))
        first.close()
        second.close()

    def test_user_control_forces_mode_five_and_releasing_restores_it(self):
        client = WsClient(self.port)
        try:
            client.send({"cmd": "grantAccess", "uuid": "joystick", "seq": 1})
            client.recv_command("grantAccess")
            client.send({"cmd": "user_control", "enable": True, "seq": 2})
            self.assertTrue(wait_until(lambda: self.app.mode_controller.get_mode() == 5))
            client.send({"cmd": "user_control", "enable": False, "seq": 3})
            self.assertTrue(wait_until(lambda: self.app.mode_controller.get_mode() == 1))
        finally:
            client.close()

    def test_unpaired_socket_is_dropped_after_the_establish_timeout(self):
        client = WsClient(self.port)
        self.assertTrue(wait_until(lambda: self.app.server.session_count == 1, timeout=2.0))
        session = next(iter(self.app.server._sessions.values()))
        session._establish_timer.cancel()
        session.start_establish_timer(timeout=0.2)
        self.assertTrue(wait_until(lambda: not session.valid and session not in self.app.server._sessions.values(),
                                   timeout=2.0))
        client.close()


class VideoStreamTest(unittest.TestCase):
    def setUp(self):
        self.frames = [b"\xff\xd8frame-a", b"\xff\xd8frame-b"]
        self.index = {"n": 0}
        self.starts = []
        self.stops = []
        self.server = VideoStreamingServer(
            frame_source=lambda: self.frames[min(self.index["n"], len(self.frames) - 1)],
            host="127.0.0.1",
            port=0,
            on_viewer_start=lambda: self.starts.append(1),
            on_viewer_stop=lambda: self.stops.append(1),
            frame_interval=0.05,
        )
        self.server.start()
        self.port = self.server.bound_port

    def tearDown(self):
        self.server.stop()

    def test_viewer_receives_a_greeting_then_binary_frames(self):
        client = WsClient(self.port)
        try:
            greeting = client.recv_frame()
            self.assertEqual(greeting, "enter video socket")
            # ``has_viewer`` flips before the start callback runs (same order as
            # StreamingServer.onOpen), so wait on the callback itself.
            self.assertTrue(wait_until(lambda: self.starts, timeout=2.0))
            self.assertTrue(self.server.has_viewer)
            self.index["n"] = 1
            received = []
            deadline = time.monotonic() + 2.0
            while time.monotonic() < deadline and not received:
                try:
                    client.socket.settimeout(0.5)
                    payload = client.recv_frame()
                except Exception:
                    payload = None
                if payload:
                    received.append(payload)
            self.assertTrue(received, "no frame reached the viewer")
        finally:
            client.close()

    def test_a_second_viewer_is_rejected_with_421(self):
        first = WsClient(self.port)
        self.assertTrue(wait_until(lambda: self.server.has_viewer, timeout=2.0))
        second = WsClient(self.port)
        try:
            payload = second.recv_frame()
            self.assertIsNotNone(payload)
            self.assertEqual(json.loads(payload)["resultCode"], 421)
        finally:
            first.close()
            second.close()


class DiscoveryTest(unittest.TestCase):
    def test_announce_payload_matches_the_app(self):
        app = make_app()
        discovery = UDPDiscovery(app.state, app.wifi, port=0)
        message = json.loads(discovery.build_message())
        self.assertEqual(sorted(message), ["ap_mode", "cmd", "ip", "name", "uuid"])
        self.assertEqual(message["cmd"], "updBroadcast")
        self.assertEqual(message["name"], app.state.name)

    def test_pair_key_is_advertised_only_while_pairing(self):
        app = make_app()
        discovery = UDPDiscovery(
            app.state, app.wifi, port=0,
            pair_key=lambda: "12345",
            mode=lambda: app.mode_controller.get_mode(),
        )
        self.assertNotIn("key", json.loads(discovery.build_message()))
        app.mode_controller.current_mode = 3
        self.assertEqual(json.loads(discovery.build_message())["key"], "12345")


if __name__ == "__main__":
    unittest.main()
