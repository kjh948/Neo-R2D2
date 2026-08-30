from __future__ import annotations

import unittest

from support import build_robot, wait_until

from r2d2.leds import (
    BACK_BASE_MODE_LAN_WITHOUT_WIFI,
    BACK_BASE_MODE_LAN_WITH_WIFI,
    BACK_BASE_SLEEP_IN_AP,
    BACK_BASE_SLEEP_IN_LOCAL_NETWORK,
    FRONT_BASE_BATTERY_LOW,
    FRONT_BASE_MODE_PAIR,
    FRONT_BASE_MODE_READY,
    FRONT_BASE_SLEEP,
    FRONT_SPECIAL_CHARGED,
    FRONT_SPECIAL_CHARGING,
    FRONT_SPECIAL_CONNECT_WIFI,
    FRONT_SPECIAL_FACE_DETECTION,
    FRONT_SPECIAL_MODE_PATROL,
    FRONT_SPECIAL_PAIR_FAIL,
    LightContext,
    MODE_FF1,
    MODE_OFF,
)


class PatternTableTest(unittest.TestCase):
    def setUp(self):
        stack = build_robot()
        self.stack = stack
        self.lights = stack["lights"]
        self.transport = stack["transport"]
        self.state = stack["state"]

    def tearDown(self):
        self.transport.close()

    def test_ready_in_lan_sends_both_front_on(self):
        self.lights.change_light(FRONT_BASE_MODE_READY, BACK_BASE_MODE_LAN_WITH_WIFI)
        self.assertEqual(self.transport.raw, ['{"cmd":"led","r":2,"b":2,"y":1,"g":2}'])

    def test_no_network_switches_the_back_channel(self):
        self.lights.change_light(FRONT_BASE_MODE_READY, BACK_BASE_MODE_LAN_WITHOUT_WIFI)
        self.assertEqual(self.transport.raw, ['{"cmd":"led","r":2,"b":2,"y":1,"g":5}'])

    def test_unchanged_channels_are_not_resent(self):
        self.lights.change_light(FRONT_BASE_MODE_READY, BACK_BASE_MODE_LAN_WITH_WIFI)
        self.transport.clear()
        # Nothing differs, so the app emits no frame at all.
        self.lights.change_light(FRONT_BASE_MODE_READY, BACK_BASE_MODE_LAN_WITH_WIFI)
        self.assertEqual(self.transport.raw, [])
        # Only the front half differs, and the untouched back half is sent as
        # MODE_NONE (0) exactly like changeLight()'s sentinel handling.
        self.lights.change_light(FRONT_SPECIAL_FACE_DETECTION, -1)
        self.assertEqual(self.transport.raw, ['{"cmd":"led","r":1,"b":2,"y":0,"g":0}'])

    def test_power_off_latches_every_later_change(self):
        self.lights.power_off_light()
        self.assertEqual(self.transport.raw, ['{"cmd":"led","r":5,"b":1,"y":1,"g":1}'])
        self.transport.clear()
        self.assertFalse(self.lights.change_light(FRONT_BASE_MODE_READY, BACK_BASE_MODE_LAN_WITH_WIFI))
        self.assertEqual(self.transport.raw, [])

    def test_patrol_and_pair_fail_patterns(self):
        self.lights.change_light(FRONT_SPECIAL_MODE_PATROL, -1)
        self.assertEqual(self.transport.raw[-1], '{"cmd":"led","r":3,"b":4,"y":0,"g":0}')
        self.transport.clear()
        self.lights.change_light(FRONT_SPECIAL_PAIR_FAIL, -1)
        self.assertEqual(self.transport.raw[-1], '{"cmd":"led","r":3,"b":1,"y":0,"g":0}')


class BaseModeLadderTest(unittest.TestCase):
    """The priority order in getFrontBaseMode/getBackBaseMode is load bearing."""

    def _lights(self, mode=1, ap=False, connecting=False, network=True, voice=False, face=False,
                charging=0, battery=80):
        stack = build_robot()
        state = stack["state"]
        state.charging = charging
        state.battery = battery
        stack["lights"].ctx = LightContext(
            mode=lambda: mode,
            is_ap_mode=lambda: ap,
            is_ap_connecting=lambda: connecting,
            network_connected=lambda: network,
            voice_recognition_active=lambda: voice,
            face_detected=lambda: face,
        )
        return stack

    def test_patrol_beats_face_detection(self):
        stack = self._lights(mode=4, face=True)
        self.assertEqual(stack["lights"].get_front_base_mode(), FRONT_SPECIAL_MODE_PATROL)
        stack["transport"].close()

    def test_face_detection_beats_pair_and_charging(self):
        stack = self._lights(mode=1, face=True, charging=1, battery=5)
        self.assertEqual(stack["lights"].get_front_base_mode(), FRONT_SPECIAL_FACE_DETECTION)
        stack["transport"].close()

    def test_pair_mode_shows_connecting_or_pair(self):
        stack = self._lights(mode=3, connecting=True)
        self.assertEqual(stack["lights"].get_front_base_mode(), FRONT_SPECIAL_CONNECT_WIFI)
        stack["transport"].close()
        stack = self._lights(mode=3)
        self.assertEqual(stack["lights"].get_front_base_mode(), FRONT_BASE_MODE_PAIR)
        stack["transport"].close()

    def test_charging_then_low_battery_then_ready(self):
        stack = self._lights(charging=1)
        self.assertEqual(stack["lights"].get_front_base_mode(), FRONT_SPECIAL_CHARGING)
        stack["transport"].close()
        stack = self._lights(charging=2)
        self.assertEqual(stack["lights"].get_front_base_mode(), FRONT_SPECIAL_CHARGED)
        stack["transport"].close()
        stack = self._lights(charging=0, battery=19)
        self.assertEqual(stack["lights"].get_front_base_mode(), FRONT_BASE_BATTERY_LOW)
        stack["transport"].close()
        # The gate is ``< 20``, so exactly 20 is not low.
        stack = self._lights(charging=0, battery=20)
        self.assertEqual(stack["lights"].get_front_base_mode(), FRONT_BASE_MODE_READY)
        stack["transport"].close()
        stack = self._lights(charging=0, battery=21)
        self.assertEqual(stack["lights"].get_front_base_mode(), FRONT_BASE_MODE_READY)
        stack["transport"].close()

    def test_sleep_mode_selects_the_sleep_patterns(self):
        stack = self._lights(mode=2)
        self.assertEqual(stack["lights"].get_front_base_mode(), FRONT_BASE_SLEEP)
        self.assertEqual(stack["lights"].get_back_base_mode(), BACK_BASE_SLEEP_IN_LOCAL_NETWORK)
        stack["transport"].close()

    def test_ap_mode_back_ladder(self):
        stack = self._lights(ap=True)
        self.assertEqual(stack["lights"].get_back_base_mode(), 23)
        stack["transport"].close()
        stack = self._lights(ap=True, connecting=True)
        self.assertEqual(stack["lights"].get_back_base_mode(), 24)
        stack["transport"].close()
        stack = self._lights(mode=2, ap=True)
        self.assertEqual(stack["lights"].get_back_base_mode(), BACK_BASE_SLEEP_IN_AP)
        stack["transport"].close()

    def test_voice_recognition_outranks_network_state(self):
        stack = self._lights(voice=True, network=True)
        self.assertEqual(stack["lights"].get_back_base_mode(), 202)
        stack["transport"].close()

    def test_restore_all_emits_the_computed_ladder(self):
        stack = self._lights(mode=1, network=False)
        stack["transport"].clear()
        stack["lights"].restore_all()
        self.assertEqual(
            stack["transport"].raw,
            ['{"cmd":"led","r":2,"b":2,"y":%d,"g":%d}' % (MODE_OFF, MODE_FF1)],
        )
        stack["transport"].close()


class TimedRevertTest(unittest.TestCase):
    def test_pair_failure_reverts_to_the_base_mode_after_two_seconds(self):
        stack = build_robot()
        lights = stack["lights"]
        stack["state"].battery = 80
        lights.front_mode = FRONT_BASE_MODE_PAIR
        lights.back_mode = BACK_BASE_MODE_LAN_WITH_WIFI
        stack["transport"].clear()
        lights.fail_in_pair_mode()
        self.assertEqual(stack["transport"].raw[-1], '{"cmd":"led","r":3,"b":1,"y":0,"g":0}')
        self.assertEqual(lights.front_mode, FRONT_SPECIAL_PAIR_FAIL)
        # The pair-error timer calls restoreFrontBaseMode(), which re-runs the
        # ladder and lands on READY rather than back on PAIR.
        reverted = wait_until(lambda: lights.front_mode == FRONT_BASE_MODE_READY, timeout=3.5)
        self.assertTrue(reverted, f"front mode stuck at {lights.front_mode}")
        self.assertEqual(stack["transport"].raw[-1], '{"cmd":"led","r":2,"b":2,"y":0,"g":0}')
        lights.close()
        stack["transport"].close()


if __name__ == "__main__":
    unittest.main()
