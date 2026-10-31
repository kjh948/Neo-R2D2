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


# --- tank (differential polar) mapping: bench-calibrated gain model ----------
# angle/power convert omega with the MEASURED lever forward_gain/rotation_gain
# (leg scrub makes rotation cost ~1.63x the physical L/2 geometry) and scale
# power by forward_gain*100. T picks clean gains so numbers are exact:
#   lever = 0.002/0.004 = 0.5 m*s/rad, full-power speed = 0.2 m/s.
T = MoveMapping(max_linear_velocity=0.5, deadband=0.02, min_power=1,
                forward_gain=0.002, rotation_gain=0.004)


def test_tank_pure_forward():
    assert tank_to_move(0.1, 0.0, T) == (50, 0)       # 0.1/0.2 -> power 50


def test_tank_pure_reverse():
    power, angle = tank_to_move(-0.2, 0.0, T)
    assert (power, angle) == (100, 180)


def test_tank_pure_rotation_left():
    # wz=0.2 -> w_term=0.1 m/s -> 50% of full-power speed, angle 90
    power, angle = tank_to_move(0.0, 0.2, T)
    assert (power, angle) == (50, 90)


def test_tank_pure_rotation_right():
    power, angle = tank_to_move(0.0, -0.2, T)
    assert (power, angle) == (50, -90)


def test_tank_45_arc():
    # v and w_term equal (0.1 vs 0.2*0.5) -> true 45 deg arc
    power, angle = tank_to_move(0.1, 0.2, T)
    assert angle == 45
    assert power == 71        # hypot(0.1,0.1)/0.2 = 70.7%


def test_tank_deadband_and_invert():
    assert tank_to_move(0.001, 0.001, T)[0] == 0
    _, angle = tank_to_move(0.0, 0.2, MoveMapping(invert_strafe=True))
    assert angle == -90


def test_tank_bench_regression_2026_10_26():
    # Default M carries the measured gains (fwd 0.001525, rot 0.00797,
    # lever 0.1914, full-power 0.1525 m/s). These are the exact open-loop
    # bench points: (0, 0.3) turned a full circle in 38 s at power ~21 under
    # the OLD scale; the calibrated mapping must now send ~38 so the MCU
    # actually executes 0.3 rad/s.
    power, angle = tank_to_move(0.0, 0.3, M)
    assert (power, angle) == (38, 90)
    assert tank_to_move(0.1, 0.0, M) == (66, 0)     # bench: 0.1 cmd -> ~0.104 real
    assert tank_to_move(0.15, 0.0, M) == (98, 0)    # near full power
    power, angle = tank_to_move(0.15, 0.3, M)       # hypot clamps at full power
    assert (power, angle) == (100, 21)
