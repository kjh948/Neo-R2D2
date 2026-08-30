from __future__ import annotations

import json
import os
import re
import unittest
import urllib.error
import urllib.request

from support import WsClient, wait_until

from r2d2.api import ALL_COMMANDS
from r2d2.app import RobotApplication
from r2d2.config import Config
from r2d2.web import WEB_ROOT, WebConsoleServer

PAGE = os.path.join(WEB_ROOT, "index.html")


def fetch(url: str):
    with urllib.request.urlopen(url, timeout=5) as response:
        return response.status, response.headers, response.read()


class StaticServingTest(unittest.TestCase):
    def setUp(self):
        self.server = WebConsoleServer(host="127.0.0.1", port=0, status_provider=lambda: {"mode": 1})
        self.server.start()
        self.base = f"http://127.0.0.1:{self.server.bound_port}"

    def tearDown(self):
        self.server.stop()

    def test_index_is_served_as_html(self):
        status, headers, body = fetch(self.base + "/")
        self.assertEqual(status, 200)
        self.assertTrue(headers.get_content_type().startswith("text/html"))
        self.assertIn(b"R2-D2 CONSOLE", body)

    def test_responses_are_not_cachable(self):
        _status, headers, _body = fetch(self.base + "/index.html")
        self.assertIn("no-store", headers.get("Cache-Control", ""))

    def test_unknown_path_is_404(self):
        with self.assertRaises(urllib.error.HTTPError) as ctx:
            fetch(self.base + "/nope.js")
        self.assertEqual(ctx.exception.code, 404)

    def test_path_traversal_is_refused(self):
        # ../ must not escape the web root, however it is spelled.
        for target in ("/../config.py", "/%2e%2e/config.py", "/..%2fconfig.py"):
            with self.assertRaises(urllib.error.HTTPError) as ctx:
                fetch(self.base + target)
            self.assertIn(ctx.exception.code, (403, 404), target)
        self.assertFalse(os.path.exists(os.path.join(WEB_ROOT, "config.py")))

    def test_health_reports_runtime_status(self):
        status, headers, body = fetch(self.base + "/health")
        self.assertEqual(status, 200)
        self.assertEqual(headers.get_content_type(), "application/json")
        self.assertEqual(json.loads(body)["mode"], 1)

    def test_head_matches_get_without_a_body(self):
        request = urllib.request.Request(self.base + "/", method="HEAD")
        with urllib.request.urlopen(request, timeout=5) as response:
            self.assertEqual(response.status, 200)
            self.assertEqual(response.read(), b"")


class ConsoleProtocolContractTest(unittest.TestCase):
    """Guard the page against drifting away from the command surface."""

    def page(self) -> str:
        with open(PAGE, "r", encoding="utf-8") as handle:
            return handle.read()

    def test_every_command_the_page_sends_is_accepted_by_the_server(self):
        sent = set(re.findall(r"""cmd\s*:\s*['"]([A-Za-z_\-]+)['"]""", self.page()))
        self.assertTrue(sent, "no commands found in the page — did the literals change?")
        # `btn` and `gin` appear in prose/hints; gin is a real inbound-only name
        # that the page also polls with, so allow it explicitly.
        self.assertLessEqual(sent, ALL_COMMANDS | {"gin"}, f"page sends unknown: {sent - ALL_COMMANDS}")

    def test_page_sends_the_wire_field_names(self):
        # Assert on the payload keys inside send()/classic() calls, not raw
        # substrings: element ids such as ``deviceNameInput`` would otherwise
        # look like the Gson name ``deviceName``.
        payloads = re.findall(r"(?:classic|send)\(\{(.+?)\}\)", self.page(), re.S)
        blob = "\n".join(payloads)
        required = {
            "change_name": "new_name",       # ChangeNameRequest
            "connectWifi": "wifi_pw",        # ConnectWifiRequest
            "grantAccess": "device_name",    # GrantAccessRequest
            "play_sound": "sound_id",        # Command
        }
        for cmd, field in required.items():
            self.assertIn(cmd, blob, cmd)
            self.assertIn(f"{field}:", blob, f"{cmd} must send {field}")
        # The names the older docs used are not what the server parses.
        for wrong in ("newName:", "deviceName:", "soundId:", "password:"):
            self.assertNotIn(wrong, blob, f"{wrong} is not a wire key")

    def test_page_targets_the_verified_ports(self):
        self.assertIn("8887", self.page())
        self.assertIn("12121", self.page())

    def test_page_sends_grantaccess_immediately_on_open(self):
        # The server drops an unvalidated socket after 10 s.
        block = self.page().split("S.ws.onopen")[1][:400]
        self.assertIn("grantAccess", block)

    def test_every_element_the_script_touches_exists(self):
        # A stale $('#btnX') after a markup edit fails silently in the browser,
        # so bind the two halves of the page together here.
        html_ids = set(re.findall(r'id="([^"]+)"', self.page()))
        referenced = set(re.findall(r"""\$\(['"]#([A-Za-z0-9_\-]+)['"]\)""", self.page()))
        missing = sorted(referenced - html_ids)
        self.assertEqual(missing, [], f"script looks up ids that are not in the markup: {missing}")

    def test_every_query_selector_target_is_declared(self):
        # data-* driven wiring (mode pad, LED pickers, presets): each attribute
        # needs at least one markup site and one script site.
        html = self.page()
        for attr in ("data-mode", "data-projector", "data-headdir", "data-led", "data-preset"):
            self.assertGreaterEqual(html.count(attr), 2, f"{attr} is declared but never wired (or vice versa)")

    def test_no_handler_references_a_removed_element(self):
        html = self.page()
        # Triple quotes: the character class contains a double quote, which
        # would otherwise terminate a plain r"..." literal.
        handlers = re.findall(r"""#([A-Za-z0-9_\-]+)'\)\.(?:onclick|onchange|oninput|addEventListener)""", html)
        declared = set(re.findall(r'id="([^"]+)"', html))
        self.assertEqual(sorted(set(handlers) - declared), [])

    def test_classic_commands_are_not_awaited(self):
        # The app never answers mode/move/led; the page must not block on them.
        self.assertIn("REPLYING", self.page())
        for name in ("move", "mode", "led", "play_sound"):
            self.assertNotIn(f"recv_command('{name}')", self.page())


class ConsoleEndToEndTest(unittest.TestCase):
    """Drive the app over the real socket exactly the way the page does."""

    def setUp(self):
        config = Config()
        config.ws_port = 0
        config.stream_port = 0
        config.discovery_enabled = False
        config.face_detection_enabled = False
        config.web_enabled = False
        config.state_file = None
        self.app = RobotApplication(config, mock=True)
        self.app.start()
        self.app.wifi._is_ap_mode = True
        self.port = self.app.server.bound_port

    def tearDown(self):
        self.app.stop()

    def test_console_flow_grant_then_mode_then_state_push(self):
        client = WsClient(self.port)
        try:
            client.send({"cmd": "grantAccess", "uuid": "browser-1", "device_name": "web-console", "seq": 1})
            reply = client.recv_command("grantAccess")
            self.assertEqual(reply["resultCode"], 0)
            self.assertEqual(reply["seq"], 1)

            client.send({"cmd": "mode", "mode": 7, "seq": 2})
            self.assertTrue(
                wait_until(lambda: '{"cmd":"mode","mode":0}' in self.app.transport._port.sent, timeout=2.0)
            )

            self.app.inject_mcu_line('{"cmd":"gin","batt":42,"charging-status":0,"arm":0,"lightsaber":0,'
                                      '"projector":0,"lcd_s":1,"lcd_l":1,"error":"NO ERROR"}')
            pushed = client.recv_command("gin")
            self.assertEqual(pushed["robot"]["battery"], 42)
        finally:
            client.close()

    def test_led_panel_omission_semantics_reach_the_uart(self):
        client = WsClient(self.port)
        try:
            client.send({"cmd": "grantAccess", "uuid": "browser-2", "seq": 1})
            client.recv_command("grantAccess")
            self.app.transport._port.sent.clear()
            # The page sends -1 for channels left on "생략".
            client.send({"cmd": "led", "r": 3, "b": -1, "y": -1, "g": -1, "seq": 2})
            self.assertTrue(
                wait_until(lambda: '{"cmd":"led","r":3}' in self.app.transport._port.sent, timeout=2.0),
                self.app.transport._port.sent,
            )
        finally:
            client.close()

    def test_user_control_hold_pattern(self):
        client = WsClient(self.port)
        try:
            client.send({"cmd": "grantAccess", "uuid": "browser-3", "seq": 1})
            client.recv_command("grantAccess")
            client.send({"cmd": "user_control", "enable": True, "seq": 2})
            self.assertTrue(wait_until(lambda: self.app.mode_controller.get_mode() == 5, timeout=2.0))
            # No reply is expected, and the server releases control after 12 s.
            client.send({"cmd": "user_control", "enable": False, "seq": 3})
            self.assertTrue(wait_until(lambda: self.app.mode_controller.get_mode() == 1, timeout=2.0))
        finally:
            client.close()

    def test_rejected_grant_reports_and_closes(self):
        self.app.wifi._is_ap_mode = False
        self.app.state.clear_clients()
        client = WsClient(self.port)
        try:
            client.send({"cmd": "grantAccess", "uuid": "stranger", "seq": 9})
            reply = client.recv_command("grantAccess")
            self.assertEqual(reply["resultCode"], 401)
            self.assertEqual(reply["seq"], 9)
        finally:
            client.close()


if __name__ == "__main__":
    unittest.main()
