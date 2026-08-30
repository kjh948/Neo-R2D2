from __future__ import annotations

import shutil
import subprocess
import threading
import time
from typing import Optional

from .api import RobotApi
from .central import CentralController
from .commander import Commander
from .config import Config
from .discovery import UDPDiscovery
from .events import EventHandler
from .leds import LEDLightController, LightContext
from .log import get_logger
from .mcu_commands import McuCommandReceiver, McuHooks
from .modes import ModeController
from .server import RobotServer
from .sound import LONELY_HELLO, SoundPlayer
from .state import RobotState
from .streaming import VideoStreamingServer
from .transport import JsonLineTransport
from .updater import Updater
from .vision import CameraWorker, FaceDetector
from .voice import VoiceRecognizer, VoiceToEventHandler
from .wifi import WifiService

LOG = get_logger("app")

GIN_INTERVAL = 5.0
DEFAULT_ROBOT_NAME = "R2-D2"


class RobotApplication:
    """Wires the ported subsystems into one process, in the app's startup order.

    ``MainActivity.onCreate`` opens the serial port and starts the reader
    service *before* anything else, then restores the lights, starts the
    behaviour queue, brings up the websocket server, announces ``ready`` to the
    MCU and finally kicks off the 5 s ``gin`` poll. The order matters: the MCU
    ignores commands issued before it has seen ``ready``, and the first ``gin``
    reply is what populates battery/charging state that gates locomotion.
    """

    def __init__(self, config: Config, mock: bool = False) -> None:
        self.config = config
        self.mock = mock or config.mock_serial
        self.started_at = time.time()

        self.state = RobotState(config.state_file)
        if not self.state.name:
            self.state.name = DEFAULT_ROBOT_NAME
        if not self.state.udid:
            import uuid as uuid_module

            self.state.udid = str(uuid_module.uuid4())

        self.transport = JsonLineTransport(
            device=config.serial_port,
            baudrate=config.baudrate,
            read_timeout=config.serial_read_timeout,
            on_line=self._on_serial_line,
            mock=self.mock,
        )
        self.commander = Commander(self.transport, self.state)
        self.sound = SoundPlayer(
            sound_dir=config.sound_dir,
            mute=config.mute,
            mock=mock,
        )
        self.lights = LEDLightController(self.commander, self.state)
        self.events = EventHandler(
            commander=self.commander,
            sound_player=self.sound,
            led_controller=self.lights,
            state=self.state,
            notify=self._notify,
            shutdown_hook=self._shutdown_host,
        )
        self.wifi = WifiService(self.state, mock=mock)
        self.wifi.events = self.events
        self.updater = Updater(self.state, mock=mock)

        self.camera = CameraWorker(
            index=config.camera_index,
            width=config.camera_width,
            height=config.camera_height,
            mock=mock or config.mock_camera,
        )
        self.detector = FaceDetector(
            commander=self.commander,
            sound_player=self.sound,
            lights=self.lights,
            cascade_dir=config.cascade_dir,
            on_face_detected=self._on_face_seen,
        )
        self.camera.on_frame = self._handle_frame
        self.voice_handler = VoiceToEventHandler(self.events)
        self.voice = VoiceRecognizer(self.voice_handler)
        self.mode_controller = ModeController(
            events=self.events,
            lights=self.lights,
            central=None,
            wifi=self.wifi,
            notify=self._notify,
            controlling_num=lambda: self.server.get_controlling_num() if self.server else 0,
        )
        self.central = CentralController(self.state, self.mode_controller, self.detector, self.camera)
        self.central.voice = self.voice
        self.mode_controller.central = self.central
        self.lights.ctx = LightContext(
            mode=self.mode_controller.get_mode,
            is_ap_mode=self.wifi.is_ap_mode,
            is_ap_connecting=self.wifi.is_ap_mode_connecting,
            network_connected=self.wifi.network_connected,
            voice_recognition_active=lambda: self.voice.is_voice_recognition_mode,
            face_detected=self.central.is_face_detected,
        )
        self.api = RobotApi(
            events=self.events,
            state=self.state,
            mode_controller=self.mode_controller,
            wifi=self.wifi,
            central=self.central,
            updater=self.updater,
        )
        self.server: Optional[RobotServer] = None
        self.streaming: Optional[VideoStreamingServer] = None
        self.mcu = McuCommandReceiver(
            event_handler=self.events,
            state=self.state,
            lights=self.lights,
            mode_controller=self.mode_controller,
            hooks=McuHooks(
                ap_mode_toggle=self.wifi.ap_mode_toggle,
                start_pair_mode=self.mode_controller.start_pair_mode,
                stop_pair_mode=self.mode_controller.stop_pair_mode,
                notify=self._notify,
            ),
        )
        self.discovery = UDPDiscovery(
            self.state,
            self.wifi,
            port=config.discovery_port,
            pair_key=self.mode_controller.get_pair_key,
            mode=self.mode_controller.get_mode,
        )
        self._gin_timer: Optional[threading.Timer] = None
        self._stop = threading.Event()
        self._stopped = False

    # -- lifecycle ------------------------------------------------------------
    @property
    def stopping(self) -> bool:
        return self._stop.is_set()

    def request_stop(self) -> None:
        """Non-blocking shutdown request, safe to call from a signal handler."""
        self._stop.set()

    def start(self) -> None:
        LOG.info("starting r2d2 host: serial=%s ws=%d", self.config.serial_port, self.config.ws_port)
        self.transport.open()
        self.events.start()
        self.lights.restore_all()

        self.server = RobotServer(
            api=self.api,
            host=self.config.ws_host,
            port=self.config.ws_port,
            path=self.config.ws_path,
        )
        self.server.start()
        self.streaming = VideoStreamingServer(
            frame_source=lambda: self.camera.most_recent,
            host=self.config.stream_host,
            port=self.config.stream_port,
            on_viewer_start=self._viewer_start,
            on_viewer_stop=self._viewer_stop,
        )
        self.streaming.start()

        self.events.software_ready()
        self._start_gin_poll()

        if self.config.discovery_enabled:
            self.discovery.start()
        if self.config.face_detection_enabled:
            self.central.start_face_detection(force=True)
        if self.config.voice_recognition_enabled:
            self.central.start_voice_recognition()

        self.sound.play_id(LONELY_HELLO, True)
        LOG.info("robot %s (%s) ready", self.state.name, self.state.udid)

    def stop(self) -> None:
        if self._stopped:
            return
        self._stopped = True
        LOG.info("stopping r2d2 host")
        self._stop.set()
        if self._gin_timer is not None:
            self._gin_timer.cancel()
        self.discovery.stop()
        if self.streaming is not None:
            self.streaming.stop()
        if self.server is not None:
            self.server.stop()
        self.central.stop_all_control()
        self.mode_controller.close()
        self.events.close()
        self.camera.release()
        self.sound.close()
        self.lights.close()
        self.transport.close()

    # -- serial plumbing ------------------------------------------------------
    def _on_serial_line(self, line: str) -> None:
        self.mcu.interpret_command(line)

    def inject_mcu_line(self, line: str) -> None:
        """Test/demo hook: pretend the MCU sent ``line``."""
        self.mcu.interpret_command(line)

    # -- timers ---------------------------------------------------------------
    def _start_gin_poll(self) -> None:
        def poll() -> None:
            if self._stop.is_set():
                return
            try:
                self.commander.gin()
            except Exception:  # pragma: no cover - transport already logs
                LOG.exception("gin poll failed")
            self._gin_timer = threading.Timer(GIN_INTERVAL, poll)
            self._gin_timer.daemon = True
            self._gin_timer.start()

        poll()

    # -- notifications --------------------------------------------------------
    def _notify(self) -> None:
        if self.server is None:
            return
        self.server.notify_robot_changed(
            ip=self.wifi.local_ip(),
            ap_mode=self.wifi.is_ap_mode(),
            ssid=self.wifi.current_ssid(),
        )

    def _on_face_seen(self) -> None:
        self.mode_controller.restart_sleep_timer()

    def _handle_frame(self, frame):
        return self.detector.process_frame(frame)

    def _viewer_start(self) -> None:
        self.central.start_video_streaming()
        self.central.stop_face_detection(force=True)
        self.mode_controller.stop_sleep_timer()

    def _viewer_stop(self) -> None:
        self.central.stop_video_streaming()
        self.central.start_face_detection()
        self.mode_controller.restart_sleep_timer()

    # -- power ----------------------------------------------------------------
    def _shutdown_host(self) -> None:
        """``MainActivity`` fires the Android shutdown intent after ``shut-down``.

        On Linux the equivalent is a poweroff, which is only attempted when the
        operator opts in — otherwise a stray client command would halt the
        board.
        """
        if not self.config.allow_host_shutdown:
            LOG.warning("MCU powered off; host shutdown disabled (set allow_host_shutdown)")
            return
        binary = shutil.which("systemctl") or shutil.which("poweroff")
        if binary is None:
            LOG.error("no shutdown command available")
            return
        argv = [binary, "poweroff"] if binary.endswith("systemctl") else [binary]
        LOG.info("powering host down: %s", " ".join(argv))
        try:
            subprocess.Popen(argv, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except OSError as exc:  # pragma: no cover
            LOG.error("shutdown failed: %s", exc)

    # -- introspection --------------------------------------------------------
    def status(self) -> dict:
        return {
            "name": self.state.name,
            "uuid": self.state.udid,
            "uptime_seconds": round(time.time() - self.started_at, 1),
            "mode": self.mode_controller.get_mode(),
            "battery": self.state.battery,
            "charging": self.state.charging,
            "clients": self.server.session_count if self.server else 0,
            "serial": self.transport.device,
            "serial_open": self.transport.is_open,
            "streaming": self.streaming.has_viewer if self.streaming else False,
            "face_detected": self.central.is_face_detected(),
            "ap_mode": self.wifi.is_ap_mode(),
        }
