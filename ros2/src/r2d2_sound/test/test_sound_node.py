"""Mock-audio tests for r2d2_sound (engine vendoring contracts + node flow)."""
import os
import time

import pytest
import rclpy

from r2d2_msgs.msg import SoundFinished, SoundRequest
from r2d2_msgs.srv import PlaySound
from std_srvs.srv import SetBool, Trigger

from r2d2_sound.sound_engine import SOUND_EFFECTS, SoundPlayer, sound_name_for_id
from r2d2_sound.sound_node import SoundNode


@pytest.fixture(scope="module")
def rclpy_ctx():
    rclpy.init()
    yield
    rclpy.shutdown()


@pytest.fixture()
def clip_dir(tmp_path):
    (tmp_path / "abrupt_burst.mp3").write_bytes(b"not-really-audio")
    (tmp_path / "happiness_confirmation.mp3").write_bytes(b"not-really-audio")
    return str(tmp_path)


class FakePublisher:
    def __init__(self):
        self.messages = []

    def publish(self, msg):
        self.messages.append(msg)


def test_sound_table_and_fallback():
    assert sound_name_for_id(2) == "abrupt_burst"
    assert sound_name_for_id(100) == "starwar01_right_02"
    assert sound_name_for_id(999) == SOUND_EFFECTS[8]  # fallback clip


def test_engine_mock_play_resolve_and_mute(clip_dir):
    eng = SoundPlayer(sound_dir=clip_dir, mock=True)
    assert eng.resolve("abrupt_burst").endswith("abrupt_burst.mp3")
    done = []
    assert eng.play("abrupt_burst", on_finished=lambda: done.append(1)) is True
    time.sleep(0.05)
    assert eng.played == ["abrupt_burst"]
    assert done == [1]                      # mock completion callback fired
    eng.set_muted(True)
    assert eng.play("abrupt_burst") is False


def test_node_play_service_finished_flow(rclpy_ctx, clip_dir):
    node = SoundNode()
    node._engine.sound_dir = clip_dir
    node._engine.mock = True
    captured = FakePublisher()
    node._finished_pub = captured

    req = PlaySound.Request()
    req.clip = "abrupt_burst"
    req.sound_id = 2
    req.request_id = "proj-42"
    resp = node._on_play(req, PlaySound.Response())
    assert resp.success and resp.clip_resolved == "abrupt_burst"

    deadline = time.time() + 2.0
    while not captured.messages and time.time() < deadline:
        node._drain()
        time.sleep(0.02)
    assert captured.messages, "no SoundFinished published"
    msg = captured.messages[0]
    assert isinstance(msg, SoundFinished)
    assert (msg.sound_id, msg.clip, msg.request_id, msg.interrupted) == (
        2, "abrupt_burst", "proj-42", False)
    node.destroy_node()


def test_last_wins_interrupted_flag(rclpy_ctx, clip_dir):
    node = SoundNode()
    node._engine.sound_dir = clip_dir
    node._engine.mock = True
    captured = FakePublisher()
    node._finished_pub = captured

    r1 = PlaySound.Request()
    r1.clip = "abrupt_burst"
    r1.request_id = "first"
    r2 = PlaySound.Request()
    r2.clip = "abrupt_burst"
    r2.request_id = "second"
    node._on_play(r1, PlaySound.Response())
    node._on_play(r2, PlaySound.Response())   # clobbers the first (last-wins)

    deadline = time.time() + 2.0
    while len(captured.messages) < 2 and time.time() < deadline:
        node._drain()
        time.sleep(0.02)
    by_id = {m.request_id: m for m in captured.messages}
    assert by_id["first"].interrupted is True
    assert by_id["second"].interrupted is False
    node.destroy_node()


def test_mcu_frame_routing_and_stop(rclpy_ctx, clip_dir):
    node = SoundNode()
    node._engine.sound_dir = clip_dir
    node._engine.mock = True
    captured = FakePublisher()
    node._finished_pub = captured

    frame = SoundRequest()
    frame.sound_id = 7                      # happiness_confirmation
    node._on_request(frame)                 # MCU-originated play by id
    deadline = time.time() + 2.0
    while not captured.messages and time.time() < deadline:
        node._drain()
        time.sleep(0.02)
    assert captured.messages[0].clip == "happiness_confirmation"

    stop_resp = node._on_stop(Trigger.Request(), Trigger.Response())
    assert stop_resp.success
    mute_resp = node._on_mute(SetBool.Request(data=True), SetBool.Response())
    assert mute_resp.success and node._engine.muted
    node.destroy_node()
