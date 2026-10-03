"""Unit tests for the Twist -> MCU move mapping (no ROS runtime needed)."""
from r2d2_motor.twist_mapping import MoveMapping, holonomic_to_move, tank_to_move, twist_to_move

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


# --- holonomic (nav2) mapping -------------------------------------------------

def test_holo_forward():
    assert holonomic_to_move(0.5, 0.0, M) == (100, 0)


def test_holo_backward():
    power, angle = holonomic_to_move(-0.25, 0.0, M)
    assert (power, angle) == (50, 180)


def test_holo_strafe_left():
    power, angle = holonomic_to_move(0.0, 0.5, M)
    assert (power, angle) == (100, 90)


def test_holo_diagonal():
    import math

    power, angle = holonomic_to_move(0.3, 0.3, M)
    assert power == round(math.hypot(0.3, 0.3) / 0.5 * 100)  # 84.85 -> 85
    assert angle == 45


def test_holo_zero_and_deadband():
    assert holonomic_to_move(0.0, 0.0, M) == (0, 0)
    assert holonomic_to_move(0.004, 0.004, M)[0] == 0


def test_holo_speed_clamped():
    power, _ = holonomic_to_move(2.0, 0.0, M)
    assert power == 100


def test_holo_invert_strafe_flips_angle():
    _, angle = holonomic_to_move(0.0, 0.5, MoveMapping(invert_strafe=True))
    assert angle == -90


# --- tank (differential polar) mapping: operator-confirmed MCU semantics -----

def test_tank_pure_forward():
    assert tank_to_move(0.25, 0.0, M) == (50, 0)      # 0.25/0.5 -> power 50


def test_tank_pure_reverse():
    power, angle = tank_to_move(-0.5, 0.0, M)
    assert (power, angle) == (100, 180)


def test_tank_pure_rotation_left():
    power, angle = tank_to_move(0.0, 1.0, M)
    assert (power, angle) == (100, 90)


def test_tank_pure_rotation_right():
    power, angle = tank_to_move(0.0, -1.0, M)
    assert (power, angle) == (100, -90)


def test_tank_45_arc():
    import math

    # v and omega both at 100% -> angle 45 (polar of (1, 1))
    power, angle = tank_to_move(0.5, 1.0, M)
    assert angle == 45
    # hypot(1.0, 1.0) = 141% of the normalised frame -> clamped to 100
    assert power == 100


def test_tank_deadband_and_invert():
    assert tank_to_move(0.005, 0.005, M)[0] == 0
    _, angle = tank_to_move(0.0, 1.0, MoveMapping(invert_strafe=True))
    assert angle == -90
