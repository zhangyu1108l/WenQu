"""Shared logging setup for the Ragas sidecar."""

import logging
from typing import Optional

LOG_FORMAT = "[%(asctime)s] [%(levelname)s] [%(name)s] %(message)s"
DATE_FORMAT = "%Y-%m-%d %H:%M:%S"
LOG_LEVEL = logging.INFO


def configure_logging() -> None:
    """Configure the root logger with the service-wide format."""
    # A consistent format makes Docker stdout logs easier to parse by time,
    # level, and module name when logs are collected centrally.
    logging.basicConfig(
        level=LOG_LEVEL,
        format=LOG_FORMAT,
        datefmt=DATE_FORMAT,
    )


def get_logger(name: Optional[str] = None) -> logging.Logger:
    """Return a logger after ensuring the shared format is configured."""
    configure_logging()
    return logging.getLogger(name)
