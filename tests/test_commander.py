from __future__ import annotations

import unittest

from support import build_robot


class CommanderWireTest(unittest.TestCase):
    """Every host->MCU frame must match ``Commander.java`` byte for byte."""

    def setUp(self):
        stack = build_robot()
        self.transport = stack["transport"]
        self.commander = stack["commander"]
        self.state = stack["state"]

    def tearDown(self):
        self.transport.close()

    def test_simple_frames(self):
        self.commander.software_ready()
        self.commander.gin()
        self.commander.reset()
        self.commander.debug()
        self.commander.power_off()
        self.assertEqual(
            self.transport.raw,
            [
                '{"cmd":"ready"}',
                '{"cmd":"gin"}',
                '{"cmd":"reset-wdt"}',
                '{"cmd":"debug"}',
                '{"cmd":"shut-down"}',
            ],
        )

    def test_motion_frame_field_order(self):
        self.commander.move(50, 120)
        self.commander.move_head_angle(-45)
        self.commander.move_head_direction(2)
        self.commander.head_shift(5)
        self.assertEqual(
            self.transport.raw,
            [
                '{"cmd":"move","power":50,"angle":120}',
                '{"cmd":"head-angle","angle":-45}',
                '{"cmd":"head-dir","dir":2}',
                '{"cmd":"head-shift","angle":5}',
            ],
        )

    def test_led_omits_unchanged_channels(self):
        self.commander.led(2, 1, -1, -1)
        self.commander.led(-1, -1, -1, -1)
        self.commander.lcd(2, -1)
        self.assertEqual(
            self.transport.raw,
            ['{"cmd":"led","r":2,"b":1}', '{"cmd":"led"}', '{"cmd":"lcd","s":2}'],
        )

    def test_power_commands(self):
        self.commander.change_head_power(80)
        self.commander.change_leg_power(60)
        self.commander.reset_mcu()
        self.assertEqual(
            self.transport.raw,
            ['{"cmd":"d-head-power","power":80}', '{"cmd":"d-leg-power","power":60}', "''"],
        )

    def test_motion_is_silently_dropped_while_charging(self):
        self.state.charging = 1
        self.assertFalse(self.commander.move(50, 0))
        self.assertFalse(self.commander.move_head_angle(30))
        self.assertEqual(self.transport.raw, [])

    def test_animated_modes_are_refused_while_charging(self):
        self.state.charging = 2
        for mode in (1, 2, 3, 4, 5, 9, 10, 12, 15):
            self.assertFalse(self.commander.mode(mode), f"mode {mode} must be refused")
        self.assertEqual(self.transport.raw, [])
        # 0/6/7/8/11/13/14/16..20 stay available on the dock.
        for mode in (0, 6, 7, 8, 11, 13, 14, 16, 17, 18, 19, 20):
            self.assertTrue(self.commander.mode(mode), f"mode {mode} must be allowed")

    def test_accessories_ignore_the_charging_guard(self):
        self.state.charging = 1
        self.assertTrue(self.commander.extend_arm(1))
        self.assertTrue(self.commander.lightsaber(1))
        self.assertTrue(self.commander.projector_mode(2))
        self.assertEqual(
            self.transport.raw,
            ['{"cmd":"arm","power":1}', '{"cmd":"lightsaber","power":1}', '{"cmd":"projector","mode":2}'],
        )


if __name__ == "__main__":
    unittest.main()
