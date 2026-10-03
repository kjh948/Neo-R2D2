"""Unit tests for the Twist -> MCU move mapping (no ROS runtime needed)."""
from r2d2_motor.twist_mapping import MoveMapping, twist_to_move

M = MoveMapping(max_linear_velocity=0.5, max_angular_velocity=1.0, deadband=0.02, min_power=1)


def test_stationary_is_zero_power():
    assert twist_to_move(0.0, 0.0, M) == (0, 0)


def test_deadband():
    assert twist_to_move(0.005, 0.01, M)[0] == 0


def test_forward_full_speed():
    assert twist_to_move(0.5, 0.0, M) == (100, 0)


def test_forward_scaled():
    assert twist_to_move(0.25, 0.0, M) == (50, 0)


def test_reverse():
    power, angle = twist_to_move(-0.5, 0.0, M)
    assert (power, angle) == (100, 180)


def test_linear_dominates_over_angular():
    power, angle = twist_to_move(0.5, 0.4, M)
    assert angle == 0
    assert power == 100


def test_pure_rotation_strafes_left():
    power, angle = twist_to_move(0.0, 1.0, M)
    assert (power, angle) == (100, 90)


def test_pure_rotation_strafes_right():
    power, angle = twist_to_move(0.0, -1.0, M)
    assert (power, angle) == (100, -90)


def test_invert_strafe():
    assert twist_to_move(0.0, 1.0, MoveMapping(invert_strafe=True)) == (100, -90)


def test_power_is_clamped():
    power, _ = twist_to_move(5.0, 0.0, M)
    assert power == 100


def test_min_power_floor():
    m = MoveMapping(min_power=20)
    power, _ = twist_to_move(0.03 * 0.5, 0.0, m)  # normalised 0.03, just above deadband
    assert power == 20
