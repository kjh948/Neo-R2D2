from r2d2_app.person_tracker import PersonDetection, PersonTrackerController


def detection(track_id, center_x, area=1000.0, score=0.8):
    return PersonDetection(track_id, center_x, area, score)


def test_selects_largest_person_and_keeps_track_id():
    controller = PersonTrackerController()
    controller.update([detection("small", 300), detection("large", 500, area=2000)], 0.0)

    assert controller.step(0.0).target_id == "large"

    controller.update([detection("small", 500, area=3000), detection("large", 400)], 0.1)
    assert controller.step(0.1).target_id == "large"


def test_reacquires_largest_person_after_selected_track_expires():
    controller = PersonTrackerController(target_lost_timeout=0.5)
    controller.update([detection("one", 320)], 0.0)
    controller.step(0.0)
    controller.update([detection("two", 400, area=2000)], 0.6)

    assert controller.step(0.6).target_id == "two"


def test_missing_locked_track_is_held_until_loss_timeout():
    controller = PersonTrackerController(target_lost_timeout=0.5)
    controller.update([detection("one", 320)], 0.0)
    controller.step(0.0)
    controller.update([detection("other", 500)], 0.2)

    assert controller.step(0.3).target_id == "one"
    lost = controller.step(0.6)
    assert lost.state == "lost"
    assert lost.center_head


def test_head_follow_is_deadbanded_and_bounded():
    controller = PersonTrackerController(
        head_deadband_deg=4.0, head_limit_deg=10.0, head_recenter_deg=5.0)
    controller.update([detection("one", 340)], 0.0)
    assert controller.step(0.0).head_shift == 0

    controller.update([detection("one", 640)], 0.1)
    command = controller.step(0.1)
    assert command.head_shift == 5
    controller.record_head_shift(command.head_shift)
    controller.update([detection("one", 640)], 0.2)
    command = controller.step(0.2)
    assert command.head_shift == 5
    controller.record_head_shift(command.head_shift)
    controller.update([detection("one", 640)], 0.3)
    command = controller.step(0.3)
    assert command.head_shift == 0
    # person out on the RIGHT (center 640 > 320) -> rotate toward them = CW = wz < 0
    assert command.angular_z < 0.0
    assert command.state == "align"


def test_base_alignment_is_pure_rotation_and_stops_after_head_recenters():
    controller = PersonTrackerController(head_recenter_deg=10.0)
    controller.update([detection("one", 600)], 0.0)
    controller.step(0.0)
    controller.record_head_shift(30)
    # stay OUTSIDE the 10 deg centre margin (480 -> error 20 deg) so align runs
    controller.update([detection("one", 480)], 0.1)
    command = controller.step(0.1)

    assert command.state == "align"
    assert command.angular_z < 0.0  # head at +30 (right limit) -> turn right
    controller.record_head_shift(-20)
    controller.update([detection("one", 480)], 0.2)
    command = controller.step(0.2)
    assert command.state == "follow"
    assert command.angular_z == 0.0


def test_person_inside_center_margin_freezes_head_and_base():
    controller = PersonTrackerController(head_deadband_deg=10.0, head_recenter_deg=10.0)
    controller.update([detection("one", 600)], 0.0)
    controller.step(0.0)
    controller.record_head_shift(30)  # head parked at the right limit, align pending
    controller.update([detection("one", 360)], 0.1)  # error 40 px = 5 deg < 10 deg

    command = controller.step(0.1)
    assert command.head_shift == 0
    assert command.angular_z == 0.0


def test_lost_target_stops_and_requests_center_once():
    controller = PersonTrackerController(target_lost_timeout=0.5)
    controller.update([detection("one", 500)], 0.0)
    controller.step(0.0)
    controller.record_head_shift(10)

    lost = controller.step(0.6)
    assert lost.state == "lost"
    assert lost.angular_z == 0.0
    assert lost.center_head
    assert not controller.step(0.7).center_head


def test_rejects_invalid_tuning_values():
    try:
        PersonTrackerController(head_recenter_deg=30.0, head_limit_deg=30.0)
    except ValueError:
        pass
    else:
        raise AssertionError("invalid head hysteresis should be rejected")
    try:
        PersonTrackerController(head_deadband_deg=45.0)  # >= camera_hfov_deg / 2
    except ValueError:
        return
    raise AssertionError("centre margin wider than the frame should be rejected")
