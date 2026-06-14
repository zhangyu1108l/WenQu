"""Ragas 侧车的共享日志配置。"""

import logging
from typing import Optional

LOG_FORMAT = "[%(asctime)s] [%(levelname)s] [%(name)s] %(message)s"
DATE_FORMAT = "%Y-%m-%d %H:%M:%S"
LOG_LEVEL = logging.INFO


def configure_logging() -> None:
    """使用服务级统一格式配置根日志器。"""
    # 统一格式能让 Docker 标准输出日志在集中采集后，
    # 更容易按时间、级别和模块名解析。
    logging.basicConfig(
        level=LOG_LEVEL,
        format=LOG_FORMAT,
        datefmt=DATE_FORMAT,
    )


def get_logger(name: Optional[str] = None) -> logging.Logger:
    """确保共享格式已配置后返回日志器。"""
    configure_logging()
    return logging.getLogger(name)
