"""Python port of the R2D2 NanoPi system app (``com.bullb.r2d2_nanopisystem``).

The Android app is the robot's host-side brain: it owns the UART link to the
MCU, serves a JSON command protocol to paired clients, plays sounds, runs face
detection and drives the autonomous behaviour modes. This package reproduces
that role on a Linux single-board computer.
"""

__version__ = "1.0.0"

__all__ = ["__version__"]
