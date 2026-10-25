"""Mock-transport tests for the MCU receive pipeline (mcu_client).

Runs the same path the UART reader thread uses (raw line -> _on_line) without
hardware; r2d2_link._MockPort provides the loopback. Mirrors the manual
verification done for the charging-transition crash fix (c7c9a3b).
"""
import time

import pytest

from r2d2_motor import mcu_client as mc
from r2d2_motor.mcu_client import McuClient


@pytest.fixture()
def client():
    # mock_responds=False: these tests inject frames via _on_line directly,
    # the canned ready/gin replies would race with the drain assertions.
    c = McuClient(device="/mock", mock=True, mock_responds=False)
    c.open()
    yield c
    c.close()


def drain_kinds(c):
    return [k for k, _ in c.drain_events()]


def test_gin_frame_updates_status_and_queues_event(client):
    client._on_line(
        '{"cmd":"gin","batt":83,"charging-status":0,"arm":1,"lightsaber":0,'
        '"projector":2,"lcd_s":2,"lcd_l":1,"mode":7,"head":42,"error":"NO ERROR"}')
    st = client.status
    assert (st.battery, st.arm, st.projector, st.head) == (83, True, 2, 42)
    # lcd_* are NOT flags: >= 2 means open
    assert st.lcd_short_open is True and st.lcd_long_open is False
    assert drain_kinds(client) == ["status"]


def test_ready_btn_play_sound_routing(client):
    client._on_line('{"cmd":"ready"}')
    client._on_line('{"cmd":"btn","value":4}')
    client._on_line('{"cmd":"play_sound","sound_id":7,"interrupt":1}')
    client._on_line('{"cmd":"not_a_mcu_cmd","value":1}')  # allow list rejects
    client._on_line('garbage')
    events = list(client.drain_events())
    assert [k for k, _ in events] == ["ready_ack", "button", "sound_request"]
    assert client.status.mcu_ready is True
    assert events[1][1] == 4
    assert events[2][1] == {"sound_id": 7, "interrupt": True}


def test_charging_mount_immediate_interlock(client):
    assert client.move(50, 0) is True          # off dock
    client._on_line('{"cmd":"gin","charging-status":1}')
    assert client.status.charging == 1          # mounting: applied immediately
    assert client.move(50, 0) is False          # interlock drops move
    assert drain_kinds(client) == ["status"]


def test_charging_leave_debounced(monkeypatch, client):
    monkeypatch.setattr(mc, "CHARGING_DEBOUNCE_SECONDS", 0.15)
    client._on_line('{"cmd":"gin","charging-status":1}')
    client._on_line('{"cmd":"gin","charging-status":0}')
    assert client.status.charging == 1          # still suppressed in the window
    assert client.move(50, 0) is False
    time.sleep(0.4)
    assert client.status.charging == 0          # committed after debounce
    assert client.move(50, 0) is True


def test_debounce_supersedes_rapid_transitions(monkeypatch, client):
    monkeypatch.setattr(mc, "CHARGING_DEBOUNCE_SECONDS", 0.2)
    client._on_line('{"cmd":"gin","charging-status":1}')
    client._on_line('{"cmd":"gin","charging-status":0}')   # pending leave...
    client._on_line('{"cmd":"gin","charging-status":1}')   # ...re-mounted: 1 stays
    time.sleep(0.45)                                        # stale timer fires late
    assert client.status.charging == 1                      # sequence token wins


def test_mode_interlock_while_charging(client):
    client._on_line('{"cmd":"gin","charging-status":1}')
    assert client.commander.mode(1) is False     # prohibited while charging
    assert client.commander.mode(11) is True     # not in MODES_PROHIBITED set
    assert client.commander.change_head_power(5) is True  # not a motion cmd
