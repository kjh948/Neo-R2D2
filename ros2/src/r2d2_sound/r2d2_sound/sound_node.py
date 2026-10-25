"""ROS 2 node: play the R2-D2 sound effects on the Pi speaker.

Separate from ``r2d2_motor`` on purpose: playback is pure host audio (no
UART), and the engine manages killer subprocesses/watcher threads whose bugs
must not take the locomotion bridge down. Sources funnel into one node so
the app's LAST-WINS rule serialises cleanly:

  service  /r2d2/sound/play         (PlaySound: sound_id or clip name)
  service  /r2d2/sound/stop         (Trigger)
  service  /r2d2/sound/set_mute     (SetBool)
  topic in /r2d2/sound/request      (SoundRequest — MCU play_sound frames,
                                     republished by r2d2_motor)
  topic out /r2d2/sound/finished    (SoundFinished — correlator for the
                                     projector auto-off sequence)

The engine's watcher thread fires ``on_finished`` off-thread; this node only
appends to a deque there and publishes from its drain timer (rclpy-safety).
"""
from __future__ import annotations

import collections
import logging

import rclpy
from rcl_interfaces.msg import SetParametersResult
from rclpy.node import Node
from std_srvs.srv import SetBool, Trigger

from r2d2_msgs.msg import SoundFinished, SoundRequest
from r2d2_msgs.srv import PlaySound

from .sound_engine import SoundPlayer, sound_name_for_id


class SoundNode(Node):
    def __init__(self) -> None:
        super().__init__("r2d2_sound")

        self.declare_parameter("sound_dir", "/home/kjh948/workspace/Neo-R2D2/sound_effects")
        self.declare_parameter("volume", 100)
        self.declare_parameter("mute", False)
        self.declare_parameter("mock", False)

        self._engine = SoundPlayer(
            sound_dir=str(self.get_parameter("sound_dir").value),
            volume=int(self.get_parameter("volume").value),
            mute=bool(self.get_parameter("mute").value),
            mock=bool(self.get_parameter("mock").value),
        )
        # generation counter: an incremented gen means the previous clip was
        # clobbered (new play / stop / mute) -> reported interrupted=True
        self._gen = 0
        self._events: collections.deque = collections.deque(maxlen=64)

        self._finished_pub = self.create_publisher(SoundFinished, "/r2d2/sound/finished", 10)
        self.create_service(PlaySound, "/r2d2/sound/play", self._on_play)
        self.create_service(Trigger, "/r2d2/sound/stop", self._on_stop)
        self.create_service(SetBool, "/r2d2/sound/set_mute", self._on_mute)
        self.create_subscription(SoundRequest, "/r2d2/sound/request", self._on_request, 10)
        self.add_on_set_parameters_callback(self._on_set_param)
        self.create_timer(0.02, self._drain)

        self.get_logger().info(
            f"r2d2_sound ready: dir={self._engine.sound_dir} volume={self._engine.volume}"
            + (" [MOCK]" if self._engine.mock else "")
            + (" MUTED" if self._engine.muted else "")
        )

    # -- dynamic parameters -----------------------------------------------------
    def _on_set_param(self, params) -> SetParametersResult:
        for p in params:
            if p.name == "volume":
                self._engine.set_volume(int(p.value))
            elif p.name == "mute":
                self._engine.set_muted(bool(p.value))
                if bool(p.value):
                    self._gen += 1  # any running clip is being killed
        return SetParametersResult(successful=True)

    # -- play paths ---------------------------------------------------------------
    def _play(self, name: str, sound_id: int, interrupt: bool, request_id: str,
              response=None) -> bool:
        self._gen += 1
        gen = self._gen

        def on_finished() -> None:
            # watcher/timer thread — queue only, never publish here
            self._events.append({
                "sound_id": sound_id,
                "clip": name,
                "request_id": request_id,
                "interrupted": gen != self._gen,
            })

        ok = self._engine.play(name, interrupt=interrupt, on_finished=on_finished)
        if response is not None:
            response.clip_resolved = name if ok else ""
        return ok

    def _on_play(self, request: PlaySound.Request, response: PlaySound.Response):
        clip = str(request.clip).strip()
        if not clip:
            clip = sound_name_for_id(int(request.sound_id))
        ok = self._play(clip, int(request.sound_id), bool(request.interrupt),
                        str(request.request_id), response=response)
        response.success = ok
        response.message = f"playing {clip}" if ok else f"not played ({clip})"
        return response

    def _on_request(self, msg: SoundRequest) -> None:
        # MCU-originated play (wire play_sound frame routed by r2d2_motor):
        # the app mapped these to the same numeric table.
        self._play(sound_name_for_id(int(msg.sound_id)), int(msg.sound_id),
                   bool(msg.interrupt), str(msg.request_id))

    def _on_stop(self, request: Trigger.Request, response: Trigger.Response):
        self._gen += 1  # the killed clip must report interrupted=True
        was_playing = self._engine.stop()
        response.success = True
        response.message = "stopped" if was_playing else "nothing was playing"
        return response

    def _on_mute(self, request: SetBool.Request, response: SetBool.Response):
        self._engine.set_muted(bool(request.data))
        if request.data:
            self._gen += 1
        response.success = True
        response.message = "muted" if request.data else "unmuted"
        return response

    # -- outbound events -----------------------------------------------------------
    def _drain(self) -> None:
        while self._events:
            info = self._events.popleft()
            msg = SoundFinished()
            msg.header.stamp = self.get_clock().now().to_msg()
            msg.sound_id = int(info["sound_id"])
            msg.clip = str(info["clip"])
            msg.request_id = str(info["request_id"])
            msg.interrupted = bool(info["interrupted"])
            self._finished_pub.publish(msg)

    def destroy_node(self) -> bool:
        self._gen += 1
        self._engine.close()
        return super().destroy_node()


def main(args=None) -> None:
    logging.basicConfig(level=logging.INFO, format="[sound] %(message)s")
    rclpy.init(args=args)
    node = SoundNode()
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
