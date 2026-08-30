from __future__ import annotations

import json
import unittest

from support import build_robot, wait_until

from r2d2.api import (
    ERROR_CLIENT_NOT_FOUND,
    ERROR_INVALID_NAME,
    ERROR_NO_UUID,
    ERROR_UNAUTHORIZED,
    ClientSession,
    RobotApi,
)


class _FakeWifi:
    def __init__(self, ap_mode=False):
        self._ap = ap_mode
        self.connect_calls = []
        self.awaited = []

    def is_ap_mode(self):
        return self._ap

    def current_ssid(self):
        return "test-net"

    def local_ip(self):
        return "10.0.0.5"

    def scan_results(self):
        return [{"ssid": "test-net", "rssi": 80}]

    def connect(self, ssid, password):
        self.connect_calls.append((ssid, password))
        return -1

    def await_connection_result(self, **kwargs):
        self.awaited.append(kwargs)


class _FakeModes:
    def __init__(self, mode=1):
        self.mode = mode
        self.wakes = 0

    def get_mode(self):
        return self.mode

    def wake(self):
        self.wakes += 1

    def success_connection_in_pair_mode(self):
        self.pair_success = getattr(self, "pair_success", 0) + 1


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

    def set_mute(self, value):
        self.calls.append(f"mute-{value}")


def _session(sink):
    return ClientSession("test", send=lambda text: sink.append(text) or True, close=lambda: sink.append("<closed>"))


class ApiTestCase(unittest.TestCase):
    def setUp(self):
        self.stack = build_robot()
        self.wifi = _FakeWifi()
        self.modes = _FakeModes()
        self.central = _FakeCentral()
        self.updates = []
        updater = type("U", (), {"update": staticmethod(lambda url: self.updates.append(url))})()
        self.api = RobotApi(
            events=self.stack["events"],
            state=self.stack["state"],
            mode_controller=self.modes,
            wifi=self.wifi,
            central=self.central,
            updater=updater,
        )
        self.sent = []
        self.session = _session(self.sent)

    def tearDown(self):
        self.stack["events"].close()
        self.stack["transport"].close()

    def _send(self, payload):
        return self.api.handle_message(self.session, json.dumps(payload))

    def _reply(self, responses, cmd):
        for item in responses:
            if item.get("cmd") == cmd:
                return item
        return None


class GatingTest(ApiTestCase):
    def test_motion_from_unvalidated_session_is_dropped(self):
        self.assertEqual(self._send({"cmd": "move", "power": 1, "angle": 0, "seq": 1}), [])
        self.assertEqual(self.stack["transport"].raw, [])

    def test_settings_from_unvalidated_session_are_dropped(self):
        self.assertEqual(self._send({"cmd": "mute", "enable": True, "seq": 2}), [])
        self.assertFalse(self.stack["state"].mute)

    def test_motion_is_ignored_while_pairing(self):
        self.session.valid = True
        self.session.uuid = "u"
        self.modes.mode = 3
        self.assertEqual(self._send({"cmd": "mode", "mode": 5, "seq": 3}), [])
        self.assertEqual(self.stack["transport"].raw, [])

    def test_settings_still_work_while_pairing(self):
        self.session.valid = True
        self.modes.mode = 3
        responses = self._send({"cmd": "mute", "enable": True, "seq": 4})
        self.assertEqual(responses[0]["resultCode"], 0)


class GrantAccessTest(ApiTestCase):
    def test_missing_uuid_is_301_and_closes_after_reply(self):
        responses = self._send({"cmd": "grantAccess", "seq": 7})
        self.assertEqual(responses[0]["resultCode"], ERROR_NO_UUID)
        self.assertTrue(self.session.close_after_send)
        self.assertFalse(self.session.valid)

    def test_unknown_client_outside_ap_mode_is_401(self):
        responses = self._send({"cmd": "grantAccess", "uuid": "stranger", "seq": 8})
        self.assertEqual(responses[0]["resultCode"], ERROR_UNAUTHORIZED)
        self.assertTrue(self.session.close_after_send)

    def test_ap_mode_grants_access_and_returns_the_robot_blob(self):
        self.wifi._ap = True
        responses = self._send({"cmd": "grantAccess", "uuid": "u-1", "device_name": "phone", "seq": 9})
        reply = self._reply(responses, "grantAccess")
        self.assertEqual(reply["resultCode"], 0)
        self.assertEqual(reply["seq"], 9)
        self.assertTrue(self.session.valid)
        self.assertEqual(self.session.uuid, "u-1")
        robot = reply["robot"]
        self.assertEqual(robot["name"], "R2-D2")
        self.assertEqual(robot["version"], 15)
        self.assertEqual(robot["ip"], "10.0.0.5")
        self.assertTrue(robot["ap_mode"])
        self.assertIn("timestamp", robot)
        self.assertEqual(self.stack["state"].clients[0]["uuid"], "u-1")

    def test_already_paired_client_is_accepted_off_ap(self):
        self.stack["state"].add_client("u-2", "tablet")
        responses = self._send({"cmd": "grantAccess", "uuid": "u-2", "seq": 10})
        self.assertEqual(self._reply(responses, "grantAccess")["resultCode"], 0)
        self.assertEqual(len(self.stack["state"].clients), 1)

    def test_pair_mode_admits_a_new_client(self):
        self.modes.mode = 3
        responses = self._send({"cmd": "grantAccess", "uuid": "u-3", "seq": 11})
        self.assertEqual(self._reply(responses, "grantAccess")["resultCode"], 0)
        self.assertEqual(getattr(self.modes, "pair_success", 0), 1)


class MotionMappingTest(ApiTestCase):
    def setUp(self):
        super().setUp()
        self.session.valid = True
        self.stack["transport"].clear()

    def test_move_maps_to_events_and_resets_the_head(self):
        self._send({"cmd": "move", "power": 50, "angle": 0, "seq": 1})
        self.assertTrue(wait_until(lambda: '{"cmd":"move","power":50,"angle":0}' in self.stack["transport"].raw))
        self.assertEqual(self.stack["transport"].raw[0], '{"cmd":"mode","mode":0}')

    def test_move_head_is_a_separate_command_name(self):
        self._send({"cmd": "move-head", "angle": 40, "seq": 2})
        self.assertTrue(wait_until(lambda: '{"cmd":"head-angle","angle":40}' in self.stack["transport"].raw))

    def test_led_sentinels_survive_the_round_trip(self):
        self._send({"cmd": "led", "r": 2, "b": -1, "y": -1, "g": 5, "seq": 3})
        self.assertTrue(wait_until(lambda: any(f.startswith('{"cmd":"led"') for f in self.stack["transport"].raw)))
        frame = [f for f in self.stack["transport"].raw if f.startswith('{"cmd":"led"')][-1]
        payload = json.loads(frame)
        self.assertEqual(sorted(payload), ["cmd", "g", "r"])
        self.assertNotIn("b", payload)
        self.assertNotIn("y", payload)

    def test_lcd_and_mode_and_head_dir(self):
        self._send({"cmd": "lcd", "s": 2, "seq": 4})
        self.assertTrue(wait_until(lambda: '{"cmd":"lcd","s":2}' in self.stack["transport"].raw))
        self._send({"cmd": "head-dir", "dir": 2, "seq": 5})
        self.assertTrue(wait_until(lambda: '{"cmd":"head-dir","dir":2}' in self.stack["transport"].raw))

    def test_power_adjustments(self):
        self._send({"cmd": "d-head-power", "power": 80, "seq": 6})
        self.assertTrue(wait_until(lambda: '{"cmd":"d-head-power","power":80}' in self.stack["transport"].raw))
        self._send({"cmd": "d-leg-power", "power": 70, "seq": 7})
        self.assertTrue(wait_until(lambda: '{"cmd":"d-leg-power","power":70}' in self.stack["transport"].raw))

    def test_reset_wdt_and_reset_mcu(self):
        self._send({"cmd": "reset-wdt", "seq": 8})
        self.assertTrue(wait_until(lambda: '{"cmd":"reset-wdt"}' in self.stack["transport"].raw))
        self._send({"cmd": "reset_mcu", "seq": 9})
        self.assertTrue(wait_until(lambda: "''" in self.stack["transport"].raw))

    def test_play_sound_is_local(self):
        played = []
        self.stack["sound"].play_id = lambda sid, interrupt=True, on_finished=None: played.append((sid, interrupt)) or True
        self._send({"cmd": "play_sound", "sound_id": 12, "interrupt": 1, "seq": 10})
        self.assertEqual(played, [(12, True)])
        self.assertNotIn("play_sound", self.stack["transport"].cmds())


class SelfUpdateGateTest(ApiTestCase):
    def setUp(self):
        super().setUp()
        self.session.valid = True

    def test_self_update_needs_over_50_percent_battery(self):
        self.stack["state"].battery = 50
        self._send({"cmd": "self_update", "url": "http://x/app.apk", "seq": 1})
        self.assertEqual(self.updates, [])
        self.stack["state"].battery = 51
        self._send({"cmd": "self_update", "url": "http://x/app.apk", "seq": 2})
        self.assertEqual(self.updates, ["http://x/app.apk"])

    def test_unsafe_variant_skips_the_battery_check(self):
        self.stack["state"].battery = 5
        self._send({"cmd": "self_update_unsafe", "url": "http://x/app.apk", "seq": 3})
        self.assertEqual(self.updates, ["http://x/app.apk"])


class SettingsTest(ApiTestCase):
    def setUp(self):
        super().setUp()
        self.session.valid = True

    def test_toggles_call_central_only_on_change(self):
        # face_detection defaults to enabled, so enabling it again is a no-op.
        self.assertTrue(self.stack["state"].face_detection)
        self._send({"cmd": "face_detection", "enable": True, "seq": 1})
        self.assertEqual(self.central.calls, [])
        self.stack["state"].face_detection = False
        self._send({"cmd": "face_detection", "enable": True, "seq": 3})
        self.assertEqual(self.central.calls, ["face-start"])

    def test_mute_and_voice(self):
        self._send({"cmd": "mute", "enable": True, "seq": 4})
        self.assertTrue(self.stack["state"].mute)
        self.assertEqual(self.central.calls, ["mute-True"])
        self._send({"cmd": "voice_recognition", "enable": False, "seq": 5})
        self.assertIn("voice-stop", self.central.calls)

    def test_power_wakes_then_powers_off(self):
        self._send({"cmd": "power", "seq": 6})
        self.assertEqual(self.modes.wakes, 1)
        self.assertTrue(wait_until(lambda: '{"cmd":"shut-down"}' in self.stack["transport"].raw))

    def test_user_control_gets_no_reply_and_drives_mode(self):
        responses = self._send({"cmd": "user_control", "enable": True, "seq": 7})
        self.assertEqual(responses, [])
        self.assertTrue(self.session.controlling)
        self.session.set_controlling(False)
        self.assertFalse(self.session.controlling)

    def test_change_name_limits_and_persists(self):
        self._send({"cmd": "change_name", "new_name": "Artoo", "seq": 8})
        self.assertEqual(self.stack["state"].name, "Artoo")
        responses = self._send({"cmd": "change_name", "new_name": "x" * 17, "seq": 9})
        self.assertEqual(responses[0]["resultCode"], ERROR_INVALID_NAME)
        responses = self._send({"cmd": "change_name", "new_name": "", "seq": 10})
        self.assertEqual(responses[0]["resultCode"], ERROR_INVALID_NAME)

    def test_wifi_list_and_connect_use_the_wire_field_names(self):
        responses = self._send({"cmd": "getWifiList", "seq": 11})
        payload = responses[0]
        self.assertEqual(payload["wifi_list"], [{"ssid": "test-net", "rssi": 80}])
        self.assertEqual(payload["currentSSID"], "test-net")
        self._send({"cmd": "connectWifi", "ssid": "cafe", "wifi_pw": "secret", "seq": 12})
        self.assertEqual(self.wifi.connect_calls, [("cafe", "secret")])
        self.assertEqual(len(self.wifi.awaited), 1)

    def test_paired_list_and_unpair(self):
        self.stack["state"].add_client("c1", "one")
        responses = self._send({"cmd": "paired_list", "seq": 13})
        self.assertEqual([c["uuid"] for c in responses[0]["clients"]], ["c1"])
        responses = self._send({"cmd": "unpair", "uuid": "ghost", "seq": 14})
        self.assertEqual(responses[0]["resultCode"], ERROR_CLIENT_NOT_FOUND)
        responses = self._send({"cmd": "unpair", "uuid": "c1", "seq": 15})
        self.assertEqual(responses[0]["resultCode"], 0)
        self.assertEqual(responses[0]["clients"], [])


class FramingTest(ApiTestCase):
    def test_json_split_across_frames_is_dispatched_once(self):
        self.session.valid = True
        payload = json.dumps({"cmd": "change_name", "new_name": "split", "seq": 1})
        first, second = payload[:10], payload[10:]
        # An incomplete buffer is held, not guessed at.
        self.assertEqual(self.api.handle_message(self.session, first), [])
        self.assertNotEqual(self.stack["state"].name, "split")
        # Completing the object is enough; the app required a newline, the port
        # also accepts a frame that carries a whole object.
        responses = self.api.handle_message(self.session, second)
        self.assertEqual(responses[0]["cmd"], "change_name")
        self.assertEqual(self.stack["state"].name, "split")
        # The trailing newline must not replay the command.
        self.assertEqual(self.api.handle_message(self.session, "\n"), [])

    def test_partial_buffer_is_not_executed_twice_when_a_newline_follows(self):
        self.session.valid = True
        self.api.handle_message(self.session, '{"cmd":"mute","enable":true,"seq":1}')
        self.assertEqual(self.stack["state"].mute, True)
        self.api.handle_message(self.session, "\n")
        self.assertEqual(self.stack["state"].mute, True)

    def test_newline_delimited_batch_is_dispatched_in_order(self):
        self.session.valid = True
        batch = '{"cmd":"mute","enable":true,"seq":1}\n{"cmd":"mute","enable":false,"seq":2}\n'
        responses = self.api.handle_message(self.session, batch)
        self.assertEqual([r["seq"] for r in responses], [1, 2])
        self.assertFalse(self.stack["state"].mute)

    def test_garbage_lines_are_ignored(self):
        self.session.buffer = ""
        responses = self.api.handle_message(self.session, "not json\n")
        self.assertEqual(responses, [])

    def test_malformed_json_in_a_complete_object_is_ignored(self):
        responses = self.api.handle_message(self.session, "{oops")
        self.assertEqual(responses, [])


if __name__ == "__main__":
    unittest.main()
