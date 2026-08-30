from __future__ import annotations

import unittest

from support import build_robot, wait_until

from r2d2.modes import (
    MODE_PAIR,
    MODE_PATROL,
    MODE_READY,
    MODE_SLEEP,
    MODE_USER_CONTROL,
)


class _FakeCentral:
    def __init__(self):
        self.calls = []

    def start_face_detection(self, *a, **k):
        self.calls.append("face-start")

    def stop_face_detection(self, *a, **k):
        self.calls.append("face-stop")

    def start_voice_recognition(self, *a, **k):
        self.calls.append("voice-start")

    def stop_voice_recognition(self, *a, **k):
        self.calls.append("voice-stop")

    def start_qr_reader(self):
        self.calls.append("qr-start")

    def stop_qr_reader(self):
        self.calls.append("qr-stop")


class _FakeWifi:
    def __init__(self, connect_result=-1):
        self.connect_result = connect_result
        self.connect_calls = []
        self.scans = 0
        self.ap = False

    def connect(self, ssid, password):
        self.connect_calls.append((ssid, password))
        return self.connect_result

    def start_scan(self):
        self.scans += 1

    def await_connection_result(self, **kwargs):
        self.pending = kwargs

    def is_ap_mode(self):
        return self.ap


class SleepTest(unittest.TestCase):
    def setUp(self):
        self.stack = build_robot(with_modes=True, sleep_time=0.2)
        self.modes = self.stack["modes"]
        self.central = _FakeCentral()
        self.modes.central = self.central
        self.notified = []
        self.modes.notify = lambda: self.notified.append(1)

    def tearDown(self):
        self.modes.close()
        self.stack["events"].close()
        self.stack["transport"].close()

    def test_idle_robot_falls_asleep(self):
        self.assertEqual(self.modes.get_mode(), MODE_READY)
        self.assertTrue(wait_until(lambda: self.modes.get_mode() == MODE_SLEEP))
        self.assertIn("face-stop", self.central.calls)
        self.assertIn("voice-stop", self.central.calls)

    def test_wake_restores_detection_and_lights(self):
        wait_until(lambda: self.modes.get_mode() == MODE_SLEEP)
        self.stack["transport"].clear()
        self.modes.wake()
        self.assertEqual(self.modes.get_mode(), MODE_READY)
        self.assertIn("face-start", self.central.calls)
        self.assertIn("voice-start", self.central.calls)

    def test_activity_while_awake_does_not_reannounce_ready(self):
        notified_before = len(self.notified)
        self.modes.wake()
        self.assertEqual(len(self.notified), notified_before)

    def test_any_executed_job_arms_the_sleep_timer_again(self):
        self.stack["transport"].clear()
        self.stack["events"].move_head(30)
        self.assertTrue(wait_until(lambda: '{"cmd":"head-angle","angle":30}' in self.stack["transport"].raw))
        self.assertFalse(self.modes.sleep.is_sleep)


class PatrolTest(unittest.TestCase):
    def setUp(self):
        self.stack = build_robot(with_modes=True, patrol_time=0.3)
        self.modes = self.stack["modes"]
        self.central = _FakeCentral()
        self.modes.central = self.central
        self.transport = self.stack["transport"]

    def tearDown(self):
        self.modes.close()
        self.stack["events"].close()
        self.transport.close()

    def test_patrol_enters_mode_and_drives_the_mcu(self):
        self.transport.clear()
        self.stack["events"].patrol()
        self.assertTrue(wait_until(lambda: '{"cmd":"mode","mode":9}' in self.transport.raw))
        self.assertEqual(self.modes.get_mode(), MODE_PATROL)
        self.assertIn("face-stop", self.central.calls)
        self.assertIn("voice-stop", self.central.calls)

    def test_patrol_light_pattern(self):
        self.transport.clear()
        self.modes.start_patrol_mode()
        self.assertTrue(wait_until(lambda: any(f.startswith('{"cmd":"led","r":3,"b":4') for f in self.transport.raw)))

    def test_patrol_stops_itself_after_its_time_budget(self):
        stopped = []
        self.modes.patrol.on_stop_job = lambda: stopped.append(1) or self.stack["events"].stop_job()
        self.modes.start_patrol_mode()
        self.assertEqual(self.modes.get_mode(), MODE_PATROL)
        self.assertTrue(wait_until(lambda: self.modes.get_mode() != MODE_PATROL, timeout=2.0))
        # Patrol expiry goes through EventHandler.stopJob(), which is what puts
        # mode 0 on the wire.
        self.assertTrue(wait_until(lambda: stopped, timeout=1.0))
        self.assertTrue(wait_until(lambda: '{"cmd":"mode","mode":0}' in self.transport.raw, timeout=1.0))
        self.assertEqual(self.modes.get_mode(), MODE_READY)


class PairModeTest(unittest.TestCase):
    def setUp(self):
        self.stack = build_robot(with_modes=True, pair_timeout=0.25)
        self.modes = self.stack["modes"]
        self.central = _FakeCentral()
        self.wifi = _FakeWifi()
        self.modes.central = self.central
        self.modes.pair.wifi = self.wifi
        self.transport = self.stack["transport"]

    def tearDown(self):
        self.modes.close()
        self.stack["events"].close()
        self.transport.close()

    def test_pair_mode_swaps_camera_to_the_qr_reader(self):
        self.modes.start_pair_mode()
        self.assertEqual(self.modes.get_mode(), MODE_PAIR)
        self.assertIn("qr-start", self.central.calls)
        self.assertIn("face-stop", self.central.calls)
        self.assertIn("voice-stop", self.central.calls)
        self.assertEqual(self.wifi.scans, 1)

    def test_pair_mode_times_itself_out(self):
        self.modes.start_pair_mode()
        # Wait for the teardown itself, not merely the mode flip: stop_pair_mode
        # announces onPairStop() before it releases the camera.
        self.assertTrue(wait_until(lambda: "qr-stop" in self.central.calls, timeout=2.0),
                        self.central.calls)
        self.assertNotEqual(self.modes.get_mode(), MODE_PAIR)

    def test_qr_payload_shape_is_ssid_slash_a_password_slash_a_key(self):
        self.modes.start_pair_mode()
        self.assertTrue(self.modes.pair.process_qr_code("MyCafe/a/s3cret/a/12345"))
        self.assertEqual(self.wifi.connect_calls, [("MyCafe", "s3cret")])
        self.assertEqual(self.modes.get_pair_key(), "12345")
        self.assertIn("qr-stop", self.central.calls)

    def test_bad_qr_payloads_are_rejected_with_the_fail_sound(self):
        self.failures = []
        self.stack["events"].fail_in_pair_mode = lambda: self.failures.append("fail")
        self.stack["events"].start_wifi_connection_event = lambda: None
        self.modes.start_pair_mode()
        # ``ssid/a/<pw>/a/<key>``: a wrong field count or any empty field fails.
        for bad in ("ssid/a", "ssid/a/pw", "/a/pw/a/key", "ssid/a//a/key", "ssid/a/pw/a/"):
            self.failures.clear()
            self.assertFalse(self.modes.pair.process_qr_code(bad), bad)
            self.assertEqual(self.failures, ["fail"], bad)
        self.assertEqual(self.wifi.connect_calls, [])

    def test_wifi_connect_failure_reopens_the_qr_reader(self):
        # 414 = ERROR_NETWORK_NOT_FOUND, a real WifiService return code
        self.wifi.connect_result = 414
        self.stack["events"].fail_in_pair_mode = lambda: self.retries.append("fail")
        self.retries = []
        self.modes.start_pair_mode()
        self.central.calls.clear()
        self.modes.pair.process_qr_code("Cafe/a/pw/a/key")
        self.assertEqual(self.retries, ["fail"])
        self.assertIn("qr-start", self.central.calls)

    def test_grant_access_ends_pair_mode(self):
        self.modes.start_pair_mode()
        self.modes.success_connection_in_pair_mode()
        self.assertNotEqual(self.modes.get_mode(), MODE_PAIR)


class UserControlTest(unittest.TestCase):
    def setUp(self):
        self.stack = build_robot(with_modes=True)
        self.modes = self.stack["modes"]
        self.central = _FakeCentral()
        self.modes.central = self.central
        self.controlling = {"n": 0}
        self.modes._controlling_num = lambda: self.controlling["n"]

    def tearDown(self):
        self.modes.close()
        self.stack["events"].close()
        self.stack["transport"].close()

    def test_a_controller_taking_over_forces_ready_mode(self):
        self.controlling["n"] = 1
        self.modes.start_user_control_mode()
        self.assertEqual(self.modes.get_mode(), MODE_USER_CONTROL)
        self.assertIn("face-stop", self.central.calls)

    def test_releasing_control_returns_to_ready_and_face_detection(self):
        self.controlling["n"] = 1
        self.modes.start_user_control_mode()
        self.controlling["n"] = 0
        self.modes.stop_user_control_mode()
        self.assertEqual(self.modes.get_mode(), MODE_READY)
        self.assertIn("face-start", self.central.calls)

    def test_pair_and_patrol_mode_win_over_user_control(self):
        for mode in (MODE_PAIR, MODE_PATROL):
            self.modes.current_mode = mode
            self.controlling["n"] = 1
            self.modes.start_user_control_mode()
            self.assertEqual(self.modes.get_mode(), mode)

    def test_reset_mode_prefers_user_control_when_someone_holds_the_stick(self):
        self.controlling["n"] = 2
        self.modes.current_mode = MODE_PATROL
        self.modes.reset_mode(stop_patrol=True)
        self.assertEqual(self.modes.get_mode(), MODE_USER_CONTROL)


if __name__ == "__main__":
    unittest.main()
