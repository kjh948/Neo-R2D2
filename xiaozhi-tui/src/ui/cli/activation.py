"""CLI模式设备激活流程."""

from datetime import datetime

from src.constants.system import SystemConstants
from src.logging import get_logger
from src.ui.shared.activation import BaseActivation

logger = get_logger()


class CliActivation(BaseActivation):
    """CLI模式设备激活处理器.

    继承 BaseActivation，仅覆盖终端输出的展示方法。
    """

    def __init__(self, activation_service, init_result: dict):
        super().__init__(activation_service, init_result)

    async def run(self) -> bool:
        self._print_header()

        if not self.needs_activation():
            self._log("Device already activated, nothing to do")
            self._print_success()
            return True

        self._print_device_info()
        try:
            return await self._core_activate()
        except KeyboardInterrupt:
            self._log("\nActivation cancelled by user")
            return False

    # ---- BaseActivation 展示方法 ----

    def _show_code(self, data: dict) -> None:
        self._print_activation_info(data)

    def _show_result(self, success: bool) -> None:
        if success:
            self._print_success()
        else:
            self._print_failure()

    def _show_error(self, msg: str) -> None:
        self._log(msg)

    # ---- 终端展示辅助方法 ----

    def _print_header(self):
        print("\n" + "=" * 60)
        print(f"{SystemConstants.APP_DISPLAY_NAME_EN} - Device Activation")
        print("=" * 60)

    def _print_device_info(self):
        """打印设备信息."""
        serial = self._service.get_serial_number() or "--"
        mac = self._service.get_mac_address() or "--"
        status = self._service.get_activation_status()

        print("\nDevice info:")
        print(f"  Serial: {serial}")
        print(f"  MAC:    {mac}")

        local = status.get("local_activated", False)
        server = status.get("server_activated", False)
        consistent = status.get("status_consistent", True)

        if not consistent:
            status_text = (
                "needs re-activation" if local and not server else "auto-repaired"
            )
        else:
            status_text = "activated" if local else "not activated"

        print(f"  Status: {status_text}")

    def _print_activation_info(self, data: dict):
        """打印激活信息."""
        code = data.get("code", "------")
        message = data.get("message", "Visit xiaozhi.me and enter the code")

        print("\n" + "-" * 60)
        print("Activation")
        print("-" * 60)
        print(f"Code: {' '.join(code)}")
        print(f"Note: {message}")
        print("-" * 60)
        print("\nSteps:")
        print("  1. Open a browser and go to xiaozhi.me")
        print("  2. Sign in to your account")
        print("  3. Choose Add Device")
        print(f"  4. Enter the code: {code}")
        print("  5. Confirm adding the device")

    def _print_success(self):
        print("\n" + "=" * 60)
        print("Device activated!")
        print("=" * 60)
        print("The device has been added to your account")
        print(f"Starting {SystemConstants.APP_DISPLAY_NAME_EN}...")
        print("=" * 60 + "\n")

    def _print_failure(self):
        print("\n" + "=" * 60)
        print("Device activation failed")
        print("=" * 60)
        print("Possible causes:")
        print("  - Unstable network connection")
        print("  - Wrong or expired activation code")
        print("  - Server temporarily unavailable")
        print("\nWhat to try:")
        print("  - Check your network connection")
        print("  - Run the program again to get a new code")
        print("  - Make sure the code is entered correctly")
        print("=" * 60 + "\n")

    def _log(self, message: str):
        """打印带时间戳的日志."""
        timestamp = datetime.now().strftime("%H:%M:%S")
        print(f"[{timestamp}] {message}")
        logger.info(message)
