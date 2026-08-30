from __future__ import annotations

import json
import unittest

from support import build_robot, wait_until

import r2d2.mcu_commands as mcu


class InboundCommandTest(unittest.TestCase):
    def setUp(self):
        self.stack = build_robot(with_modes=True)
        self.mcu = self.stack["mcu"]
        self.state = self.stack["state"]
        self.transport = self.stack["transport"]
        self.sound = self.stack["sound"]
        self.notified = []
        self.mcu.hooks.notify = lambda: self.notified.append(1)

    def tearDown(self):
        self.stack["events"].close()
        self.transport.close()

    def test_play_sound_is_local_not_forwarded(self):
        self.transport.clear()
        self.played = []
        self.sound.play_id = lambda sid, interrupt=True, on_finished=None: self.played.append((sid, interrupt)) or True
        self.mcu.interpret_command('{"cmd":"play_sound","sound_id":4,"interrupt":1}')
        self.assertEqual(self.played, [(4, True)])
        self.assertEqual(self.transport.raw, [])

    def test_ready_is_tolerated(self):
        # The app's cancelReady() NPEs on an uninitialised timer; the port logs
        # the ack instead.
        self.assertEqual(self.mcu.interpret_command('{"cmd":"ready"}'), "ready")

    def test_unknown_cmd_is_ignored(self):
        self.assertIsNone(self.mcu.interpret_command('{"cmd":"status","value":1}'))
        self.assertIsNone(self.mcu.interpret_command("not json at all"))
        self.assertIsNone(self.mcu.interpret_command("{}"))

    def test_button_power_off(self):
        self.transport.clear()
        self.mcu.interpret_command('{"cmd":"btn","value":1}')
        self.assertTrue(wait_until(lambda: '{"cmd":"shut-down"}' in self.transport.raw))
        self.assertIn('{"cmd":"led","r":5,"b":1,"y":1,"g":1}', self.transport.raw)

    def test_button_dispatch_table(self):
        calls = []
        self.mcu.hooks.ap_mode_toggle = lambda: calls.append("ap")
        self.mcu.hooks.start_pair_mode = lambda: calls.append("pair-start")
        self.mcu.hooks.stop_pair_mode = lambda: calls.append("pair-stop")
        self.stack["events"].lightsaber = lambda *a: calls.append("saber")
        self.stack["events"].arm = lambda *a: calls.append("arm")
        self.stack["events"].patrol = lambda: calls.append("patrol")
        for value, expected in ((2, "ap"), (3, "pair-start"), (4, "saber"), (5, "arm"), (6, "patrol")):
            calls.clear()
            self.mcu.interpret_command(json.dumps({"cmd": "btn", "value": value}))
            self.assertEqual(calls[0], expected, f"btn {value}")
        calls.clear()
        self.mcu.interpret_command('{"cmd":"btn","value":0}')
        self.mcu.interpret_command('{"cmd":"btn","value":7}')
        self.assertEqual(calls, [])

    def test_button_three_toggles_pair_mode_off_when_paired(self):
        calls = []
        self.mcu.hooks.stop_pair_mode = lambda: calls.append("pair-stop")
        self.stack["modes"].current_mode = 3
        self.mcu.interpret_command('{"cmd":"btn","value":3}')
        self.assertEqual(calls, ["pair-stop"])


class GinResponseTest(unittest.TestCase):
    def setUp(self):
        self.stack = build_robot(with_modes=True)
        self.mcu = self.stack["mcu"]
        self.state = self.stack["state"]
        self.transport = self.stack["transport"]
        self.notified = []
        self.mcu.hooks.notify = lambda: self.notified.append(1)

    def tearDown(self):
        self.stack["events"].close()
        self.transport.close()

    def _gin(self, **fields):
        payload = {"cmd": "gin", "batt": 80, "charging-status": 0, "arm": 0,
                   "lightsaber": 0, "projector": 0, "lcd_s": 1, "lcd_l": 1, "error": "NO ERROR"}
        payload.update(fields)
        self.mcu.interpret_command(json.dumps(payload))

    def test_state_is_mirrored_and_pushed(self):
        self._gin(batt=77, arm=1, lightsaber=1, projector=2)
        self.assertEqual(self.state.battery, 77)
        self.assertTrue(self.state.arm)
        self.assertTrue(self.state.lightsaber)
        self.assertEqual(self.state.projector, 2)
        self.assertEqual(len(self.notified), 1)

    def test_identical_report_does_not_notify(self):
        self._gin(batt=50)
        self.notified.clear()
        self._gin(batt=50)
        self.assertEqual(self.notified, [])

    def test_lcd_open_threshold_is_two_not_one(self):
        self._gin(lcd_s=1, lcd_l=1)
        self.assertFalse(self.state.short_lcd)
        self.assertFalse(self.state.long_lcd)
        self._gin(lcd_s=2, lcd_l=3)
        self.assertTrue(self.state.short_lcd)
        self.assertTrue(self.state.long_lcd)

    def test_crossing_low_battery_reresolves_the_lights(self):
        self._gin(batt=80)
        self.transport.clear()
        self._gin(batt=10)
        self.assertTrue(wait_until(lambda: any(f.startswith('{"cmd":"led"') for f in self.transport.raw)))

    def test_error_field_defaults(self):
        self.mcu.interpret_command('{"cmd":"gin","batt":80}')
        self.assertEqual(self.state.error, "NO ERROR")
        self._gin(error="MOTOR STALL")
        self.assertEqual(self.state.error, "MOTOR STALL")

    def test_charging_is_applied_immediately_when_docked(self):
        self._gin(**{"charging-status": 1})
        self.assertEqual(self.state.charging, 1)
        self.assertTrue(any('"y":3' in f or '"r":3' in f for f in self.transport.raw))

    def test_leaving_the_dock_is_debounced(self):
        original = mcu.CHARGING_DEBOUNCE_SECONDS
        mcu.CHARGING_DEBOUNCE_SECONDS = 0.15
        try:
            self._gin(**{"charging-status": 1})
            self._gin(**{"charging-status": 0})
            self.assertEqual(self.state.charging, 1, "must still be charging before the debounce elapses")
            self.assertTrue(wait_until(lambda: self.state.charging == 0))
        finally:
            mcu.CHARGING_DEBOUNCE_SECONDS = original

    def test_superseded_debounce_is_dropped(self):
        original = mcu.CHARGING_DEBOUNCE_SECONDS
        mcu.CHARGING_DEBOUNCE_SECONDS = 0.3
        try:
            self._gin(**{"charging-status": 0})
            self._gin(**{"charging-status": 1})
            self._gin(**{"charging-status": 0})  # arrives last, wins the debounce race
            self.assertTrue(wait_until(lambda: self.state.charging == 0))
        finally:
            mcu.CHARGING_DEBOUNCE_SECONDS = original


if __name__ == "__main__":
    unittest.main()
