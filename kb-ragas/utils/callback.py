"""用于回传完整 Ragas 评估结果的 Java 回调助手。"""

import asyncio

import httpx

from models.schema import BatchEvalResult
from utils.logger import get_logger

logger = get_logger(__name__)

MAX_CALLBACK_ATTEMPTS = 3
RETRY_INTERVAL_SECONDS = 5
CALLBACK_TIMEOUT_SECONDS = 30


async def post_callback(callback_url: str, result: BatchEvalResult) -> bool:
    """把已完成的批次评估结果回传给 Java 服务。

    callback_url 格式：
    http://app:8081/internal/eval/callback

    Java 接口会把回调载荷写入 MySQL eval_result 表，
    并更新 eval_batch 的聚合状态和指标均值。
    """

    # 发送前先序列化，确保每次重试都向 Java 提交完全相同的不可变载荷。
    try:
        payload = result.model_dump_json()
    except Exception:
        # 回调失败只记录日志而不抛出异常，因为评估本身已经完成；
        # 此处抛出异常会中断整个异步流程，却无法恢复结果。
        logger.exception("Failed to serialize callback payload batch_id=%s", result.batch_id)
        return False

    headers = {"Content-Type": "application/json"}

    async with httpx.AsyncClient(timeout=CALLBACK_TIMEOUT_SECONDS, trust_env=False) as client:
        for attempt in range(1, MAX_CALLBACK_ATTEMPTS + 1):
            try:
                response = await client.post(
                    callback_url,
                    content=payload,
                    headers=headers,
                )
                if response.status_code == 200:
                    try:
                        body = response.json()
                    except ValueError:
                        body = {}
                    if not isinstance(body, dict) or body.get("code", 0) == 0:
                        return True

                    logger.warning(
                        "Callback attempt %s/%s rejected batch_id=%s code=%s msg=%s",
                        attempt,
                        MAX_CALLBACK_ATTEMPTS,
                        result.batch_id,
                        body.get("code"),
                        body.get("msg"),
                    )
                else:
                    logger.warning(
                        "Callback attempt %s/%s failed batch_id=%s status_code=%s",
                        attempt,
                        MAX_CALLBACK_ATTEMPTS,
                        result.batch_id,
                        response.status_code,
                    )
            except Exception as exc:
                logger.warning(
                    "Callback attempt %s/%s failed batch_id=%s error=%s",
                    attempt,
                    MAX_CALLBACK_ATTEMPTS,
                    result.batch_id,
                    exc,
                )

            if attempt < MAX_CALLBACK_ATTEMPTS:
                # 网络抖动可能导致单次回调失败，但评估结果不能丢失。
                # 5 秒间隔给 Docker 网络或 Java 服务留出短暂恢复窗口。
                await asyncio.sleep(RETRY_INTERVAL_SECONDS)

    # 所有重试失败后只记录错误。
    # 回调失败不应改变评估结果，也不应抛出会停止 worker 的异常。
    logger.error(
        "Callback failed after %s attempts batch_id=%s callback_url=%s",
        MAX_CALLBACK_ATTEMPTS,
        result.batch_id,
        callback_url,
    )
    return False
