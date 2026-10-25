"""ROS 2 node: ``/cmd_vel`` -> MCU ``move`` frames **and the MCU function
bridge** (head/mode/projector/arm/lightsaber/led/lcd/sound/safety services +
status topics).

Behaviour (agreed with the operator):
* UART direct — the node owns ``/dev/ttyS2`` via the repo's ``r2d2`` package;
  the r2d2 app must NOT run at the same time.
* Differential-style mapping: dominant ``linear.x`` -> forward/reverse,
  dominant ``angular.z`` -> lateral strafe (see ``twist_mapping``).
* While driving, the frame is repeated every ``repeat_period`` (the Android
  app drives its joystick the same way, 300 ms).
* Safety: if no Twist arrives for ``cmd_vel_timeout`` seconds a
  ``{"cmd":"move","power":0}`` stop frame is sent exactly once.

MCU bridge surface (namespaced ``/r2d2/``; wire commands mirror
``r2d2/commander.py`` exactly, interlocks included):

  services  set_head_angle set_head_dir set_head_power set_leg_power set_mode
            set_projector set_arm set_lightsaber set_led set_lcd
            mcu_play_sound reset_watchdog mcu_debug reset_mcu request_status
            power_off (gated by allow_power_off, default false)
  topics    /r2d2/status (McuStatus, transient_local — gin frame + ACK)
            /r2d2/button (ButtonEvent — panel presses, NO auto-action here)
            /r2d2/sound/request (SoundRequest — MCU play_sound frames routed
                                 to the r2d2_sound node)
            /r2d2/head_shift (subscribe, Int32 — high-rate face-tracking path,
                              throttled by head_shift_min_interval)

The UART reader thread never touches rclpy publishers: inbound frames are
parsed in ``mcu_client`` and queued; the 20 ms drain timer here republishes
them on the executor thread.
"""
from __future__ import annotations

import logging
import threading
import time
import uuid

import rclpy
from geometry_msgs.msg import Twist
from rclpy.node import Node
from rclpy.qos import DurabilityPolicy, QoSProfile, ReliabilityPolicy
from std_msgs.msg import Int32
from std_srvs.srv import SetBool, Trigger

from r2d2_msgs.msg import ButtonEvent, McuStatus as McuStatusMsg, SoundRequest
from r2d2_msgs.msg import SoundFinished
from r2d2_msgs.srv import (
    PlayMcuSound, PlaySound, SetHeadAngle, SetHeadDir, SetLcd, SetLed,
    SetMode, SetPower, SetProjector,
)

from .mcu_client import McuClient
from .mcu_config import load_mcu_config
from .twist_mapping import MoveMapping, holonomic_to_move, tank_to_move, twist_to_move

# events.py projector sequence: mode 1/2 plays STARWAR01/02 then auto-off.
_PROJECTOR_SOUND = {1: 100, 2: 101}
_PROJECTOR_OFF_TIMEOUT = 30.0  # s — guarantee off even if r2d2_sound is absent


class MotorNode(Node):
    def __init__(self) -> None:
        super().__init__("r2d2_motor")

        # "" / -1 mean "take it from the r2d2 config file (config_local.json),
        # falling back to the built-in default" — explicit params still win.
        self.declare_parameter("config_file", "")
        self.declare_parameter("serial_port", "")
        self.declare_parameter("baudrate", -1)
        self.declare_parameter("serial_read_timeout", -1.0)
        self.declare_parameter("mock", False)
        self.declare_parameter("cmd_vel_topic", "/cmd_vel")
        self.declare_parameter("max_linear_velocity", 0.5)
        self.declare_parameter("max_angular_velocity", 1.0)
        self.declare_parameter("deadband", 0.02)
        self.declare_parameter("min_power", 1)
        # True by default: the MCU's polar angle sign is mirrored vs the ROS
        # twist convention (hardware check 2026-10-18: +wz turned the robot right).
        self.declare_parameter("invert_strafe", True)
        self.declare_parameter("mapping_mode", "differential")
        self.declare_parameter("cmd_vel_timeout", 0.5)
        self.declare_parameter("repeat_period", 0.3)
        self.declare_parameter("gin_period", 5.0)
        # -- MCU bridge (see module docstring) --------------------------------
        self.declare_parameter("allow_power_off", False)
        self.declare_parameter("head_shift_min_interval", 0.05)
        self.declare_parameter("status_transient", True)
        self.declare_parameter("sound_enabled", True)
        # mock only: canned MCU ready/gin replies so /r2d2/status flows on the bench
        self.declare_parameter("mock_responds", True)

        cfg = load_mcu_config(self.get_parameter("config_file").value)
        if "_config_path" in cfg:
            self.get_logger().info(f"using MCU defaults from {cfg['_config_path']}")
        else:
            self.get_logger().warn("no r2d2 config found; falling back to built-in defaults")

        def resolve(name: str, key: str, fallback):
            """Explicit ROS param wins; "" / -1 defer to the config file."""
            value = self.get_parameter(name).value
            if isinstance(value, str) and value == "":
                return cfg.get(key, fallback)
            if isinstance(value, (int, float)) and not isinstance(value, bool) and value < 0:
                return cfg.get(key, fallback)
            return value

        serial_port = resolve("serial_port", "serial_port", "/dev/ttyS2")
        baudrate = int(resolve("baudrate", "baudrate", 115200))
        read_timeout = float(resolve("serial_read_timeout", "serial_read_timeout", 1.0))
        mock = bool(self.get_parameter("mock").value or cfg.get("mock_serial", False))

        mode = str(self.get_parameter("mapping_mode").value).strip().lower()
        if mode not in ("differential", "holonomic", "tank"):
            raise ValueError(f"unknown mapping_mode {mode!r} (differential|holonomic|tank)")
        self._mode = mode

        self._mapping = MoveMapping(
            max_linear_velocity=self.get_parameter("max_linear_velocity").value,
            max_angular_velocity=self.get_parameter("max_angular_velocity").value,
            deadband=self.get_parameter("deadband").value,
            min_power=int(self.get_parameter("min_power").value),
            invert_strafe=self.get_parameter("invert_strafe").value,
        )
        self._timeout = float(self.get_parameter("cmd_vel_timeout").value)
        repeat_period = float(self.get_parameter("repeat_period").value)

        self._client = McuClient(
            device=serial_port,
            mock=mock,
            baudrate=baudrate,
            read_timeout=read_timeout,
            logger=self.get_logger(),
            mock_responds=bool(self.get_parameter("mock_responds").value),
        )
        try:
            self._client.open()
        except Exception as exc:
            self.get_logger().error(f"cannot open {self._client.transport.device}: {exc}")
            raise

        self._lock = threading.Lock()
        self._last_twist: Twist | None = None
        self._last_rx = self.get_clock().now()
        self._driving = False          # a non-zero move is live on the MCU

        topic = self.get_parameter("cmd_vel_topic").value
        self.create_subscription(Twist, topic, self._on_twist, 10)
        self.create_timer(repeat_period, self._tick)
        gin_period = float(self.get_parameter("gin_period").value)
        if gin_period > 0.0:
            self.create_timer(gin_period, self._poll_gin)

        # -- MCU bridge: publishers/services/drain ----------------------------
        self._allow_power_off = bool(self.get_parameter("allow_power_off").value)
        self._sound_enabled = bool(self.get_parameter("sound_enabled").value)
        self._head_shift_min = float(self.get_parameter("head_shift_min_interval").value)
        self._last_head_shift = 0.0
        status_qos = QoSProfile(
            reliability=ReliabilityPolicy.RELIABLE,
            durability=DurabilityPolicy.TRANSIENT_LOCAL
            if bool(self.get_parameter("status_transient").value)
            else DurabilityPolicy.VOLATILE,
            depth=1,
        )
        self._status_pub = self.create_publisher(McuStatusMsg, "/r2d2/status", status_qos)
        self._button_pub = self.create_publisher(ButtonEvent, "/r2d2/button", 10)
        self._sound_req_pub = self.create_publisher(SoundRequest, "/r2d2/sound/request", 10)
        self.create_subscription(Int32, "/r2d2/head_shift", self._on_head_shift, 10)

        cmd = self._client.commander
        srv = lambda Type, name, fn: self.create_service(Type, f"/r2d2/{name}", fn)  # noqa: E731
        srv(SetHeadAngle, "set_head_angle",
            lambda r, s: self._simple(r, s, lambda: cmd.move_head_angle(r.angle)))
        srv(SetHeadDir, "set_head_dir",
            lambda r, s: self._simple(r, s, lambda: cmd.move_head_direction(int(r.dir))))
        srv(SetPower, "set_head_power",
            lambda r, s: self._simple(r, s, lambda: cmd.change_head_power(r.power)))
        srv(SetPower, "set_leg_power",
            lambda r, s: self._simple(r, s, lambda: cmd.change_leg_power(r.power)))
        srv(SetMode, "set_mode",
            lambda r, s: self._simple(r, s, lambda: cmd.mode(int(r.mode))))
        srv(SetProjector, "set_projector", self._on_set_projector)
        srv(SetBool, "set_arm",
            lambda r, s: self._simple(r, s, lambda: cmd.extend_arm(1 if r.data else 0)))
        srv(SetBool, "set_lightsaber",
            lambda r, s: self._simple(r, s, lambda: cmd.lightsaber(1 if r.data else 0)))
        srv(SetLed, "set_led",
            lambda r, s: self._simple(
                r, s, lambda: cmd.led(r.led_r, r.led_b, r.led_y, r.led_g)))
        srv(SetLcd, "set_lcd",
            lambda r, s: self._simple(r, s, lambda: cmd.lcd(r.lcd_s, r.lcd_l)))
        srv(PlayMcuSound, "mcu_play_sound",
            lambda r, s: self._simple(
                r, s, lambda: cmd.play_sound(int(r.sound_id), 1 if r.interrupt else 0)))
        srv(Trigger, "reset_watchdog", lambda r, s: self._simple(r, s, cmd.reset))
        srv(Trigger, "mcu_debug", lambda r, s: self._simple(r, s, cmd.debug))
        srv(Trigger, "reset_mcu", lambda r, s: self._simple(r, s, cmd.reset_mcu))
        srv(Trigger, "request_status", lambda r, s: self._simple(r, s, cmd.gin))
        srv(Trigger, "power_off", self._on_power_off)

        if self._sound_enabled:
            self._sound_cli = self.create_client(PlaySound, "/r2d2/sound/play")
            self.create_subscription(
                SoundFinished, "/r2d2/sound/finished", self._on_sound_finished, 10)
        else:
            self._sound_cli = None
        # projector 1/2 -> pending sound completion -> projector 0
        self._pending_projector: str | None = None
        self._projector_off_timer = None

        self.create_timer(0.02, self._drain_mcu_events)

        self.get_logger().info(
            f"r2d2_motor ready: {topic} -> MCU move over {self._client.transport.device}"
            + (" [MOCK]" if self.get_parameter("mock").value else "")
            + ("" if self._allow_power_off else " (power_off gated)")
        )

    # -- inbound ROS ----------------------------------------------------------
    def _on_twist(self, msg: Twist) -> None:
        with self._lock:
            self._last_twist = msg
            self._last_rx = self.get_clock().now()

    # -- periodic send --------------------------------------------------------
    def _tick(self) -> None:
        now = self.get_clock().now()
        with self._lock:
            twist = self._last_twist
            stale = (now - self._last_rx).nanoseconds > self._timeout * 1e9

        if twist is None:
            return

        if self._mode == "holonomic":
            power, angle = holonomic_to_move(twist.linear.x, twist.linear.y, self._mapping)
        elif self._mode == "tank":
            power, angle = tank_to_move(twist.linear.x, twist.angular.z, self._mapping)
        else:
            power, angle = twist_to_move(twist.linear.x, twist.angular.z, self._mapping)

        if power == 0 or stale:
            if self._driving:
                reason = "cmd_vel timed out" if stale else "twist is zero"
                self.get_logger().info(f"stopping ({reason})")
                self._send_move(0, angle=0)
                self._driving = False
            return

        # Repeat while driving: matches the Android app's 300 ms joystick tick
        # and keeps the MCU fed even if the teleop publisher goes quiet.
        self._send_move(power, angle)
        self._driving = True

    def _poll_gin(self) -> None:
        self._client.request_gin()

    def _send_move(self, power: int, angle: int) -> None:
        if self._client.move(power, angle):
            return
        if self._client.status.charging != 0:
            # Charging interlock (Commander.move drops frames while docked) —
            # intentional suppression, not a UART failure. mcu_client already
            # logged the transition; stay quiet through the 0.3 s repeat ticks.
            return
        self.get_logger().warn(f"move(power={power}, angle={angle}) failed to send")

    # -- MCU bridge -------------------------------------------------------------
    def _simple(self, request, response, fn):
        """Run a Commander call, map bool -> (success, message) reply."""
        try:
            ok = bool(fn())
        except Exception as exc:  # transport bugs already logged by r2d2_link
            response.success = False
            response.message = f"{type(exc).__name__}: {exc}"
            return response
        response.success = ok
        response.message = "sent" if ok else "suppressed or write failed (see logs)"
        return response

    def _on_power_off(self, request, response):
        if not self._allow_power_off:
            response.success = False
            response.message = "power_off gated: start with allow_power_off:=true"
            self.get_logger().warn("refused /r2d2/power_off (allow_power_off=false)")
            return response
        return self._simple(request, response, self._client.commander.power_off)

    def _on_head_shift(self, msg: Int32) -> None:
        now = time.monotonic()
        if now - self._last_head_shift < self._head_shift_min:
            return  # face tracking can queue faster than the MCU cares to read
        self._last_head_shift = now
        self._client.commander.head_shift(int(msg.data))

    def _on_set_projector(self, request, response):
        mode = int(request.mode)
        ok = bool(self._client.commander.projector_mode(mode))
        response.success = ok
        response.message = "sent" if ok else "write failed (see logs)"
        if ok and mode in _PROJECTOR_SOUND and self._sound_cli is not None:
            self._start_projector_sequence(mode)
        return response

    def _start_projector_sequence(self, mode: int) -> None:
        """Mirror events.py projector 1/2: play the star-war cue, auto-off on
        finish. Completion is guaranteed: the 30 s watchdog timer sends
        projector 0 even if r2d2_sound is down or never answers."""
        self._pending_projector = uuid.uuid4().hex
        req = PlaySound.Request()
        req.sound_id = _PROJECTOR_SOUND[mode]
        req.request_id = self._pending_projector
        try:
            self._sound_cli.call_async(req)
        except Exception as exc:
            self.get_logger().warn(f"projector sound cue send failed: {exc}")
        if self._projector_off_timer is not None:
            self._projector_off_timer.cancel()
        # repeating timer, but _finish/_watchdog cancel it on the first pass
        self._projector_off_timer = self.create_timer(
            _PROJECTOR_OFF_TIMEOUT, lambda: self._projector_watchdog(mode))

    def _on_sound_finished(self, msg: SoundFinished) -> None:
        if not self._pending_projector or msg.request_id != self._pending_projector:
            return
        self._finish_projector_sequence("sound finished")

    def _projector_watchdog(self, mode: int) -> None:
        if self._pending_projector is None:
            return
        self.get_logger().warn(
            f"projector sound cue {mode} never completed — forcing projector off")
        self._finish_projector_sequence("watchdog timeout")

    def _finish_projector_sequence(self, reason: str) -> None:
        self._pending_projector = None
        if self._projector_off_timer is not None:
            self._projector_off_timer.cancel()
            self._projector_off_timer = None
        self._client.commander.projector_mode(0)
        self.get_logger().info(f"projector auto-off ({reason})")

    def _drain_mcu_events(self) -> None:
        """Republish frames parsed on the UART reader thread (rclpy-safe)."""
        st = self._client.status
        for kind, payload in self._client.drain_events():
            if kind in ("status", "ready_ack"):
                msg = McuStatusMsg()
                msg.header.stamp = self.get_clock().now().to_msg()
                msg.battery = int(st.battery)
                msg.charging = int(st.charging)
                msg.arm = bool(st.arm)
                msg.lightsaber = bool(st.lightsaber)
                msg.projector = int(st.projector)
                msg.lcd_short_open = bool(st.lcd_short_open)
                msg.lcd_long_open = bool(st.lcd_long_open)
                msg.mode = int(st.mode)
                msg.head = int(st.head)
                msg.error = str(st.error)
                msg.mcu_ready = bool(st.mcu_ready)
                self._status_pub.publish(msg)
            elif kind == "button":
                msg = ButtonEvent()
                msg.header.stamp = self.get_clock().now().to_msg()
                msg.value = int(payload)
                self._button_pub.publish(msg)
            elif kind == "sound_request":
                msg = SoundRequest()
                msg.header.stamp = self.get_clock().now().to_msg()
                msg.sound_id = int(payload["sound_id"])
                msg.interrupt = bool(payload["interrupt"])
                self._sound_req_pub.publish(msg)

    # -- shutdown ---------------------------------------------------------------
    def destroy_node(self) -> bool:
        if self._driving:
            self._client.stop()
            self._driving = False
        if self._projector_off_timer is not None:
            self._projector_off_timer.cancel()
            self._projector_off_timer = None
        self._client.close()
        return super().destroy_node()


def main(args=None) -> None:
    # Surface the vendored r2d2_link logger (UART tx in mock mode, write errors).
    logging.basicConfig(level=logging.INFO, format="[uart] %(message)s")
    rclpy.init(args=args)
    node = MotorNode()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        if rclpy.ok():
            rclpy.shutdown()


if __name__ == "__main__":
    main()
