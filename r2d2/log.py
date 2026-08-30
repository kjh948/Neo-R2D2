from __future__ import annotations

import logging
import sys

_CONFIGURED = False

TRACE = 5


def _add_trace_level() -> None:
    if logging.getLevelName(TRACE) == "Level %d" % TRACE:
        logging.addLevelName(TRACE, "TRACE")
    if not hasattr(logging.Logger, "trace"):

        def trace(self, msg, *args, **kwargs):
            if self.isEnabledFor(TRACE):
                self._log(TRACE, msg, args, **kwargs)

        logging.Logger.trace = trace


_add_trace_level()


def get_logger(name: str) -> logging.Logger:
    return logging.getLogger(f"r2d2.{name}")


def configure_logging(level: int = logging.INFO, stream=None) -> None:
    global _CONFIGURED
    if not _CONFIGURED:
        logging.basicConfig(
            level=level,
            stream=stream or sys.stderr,
            format="%(asctime)s %(levelname)-7s %(name)-24s %(message)s",
            datefmt="%H:%M:%S",
            force=True,
        )
        _CONFIGURED = True
    else:
        logging.getLogger("r2d2").setLevel(level)


def level_from_name(name: str) -> int:
    mapping = {
        "trace": TRACE,
        "debug": logging.DEBUG,
        "info": logging.INFO,
        "warning": logging.WARNING,
        "error": logging.ERROR,
    }
    return mapping.get(str(name).strip().lower(), logging.INFO)
