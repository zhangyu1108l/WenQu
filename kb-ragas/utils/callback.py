"""Java callback helper for posting completed Ragas evaluation results."""

import asyncio

import httpx

from models.schema import BatchEvalResult
from utils.logger import get_logger

logger = get_logger(__name__)

MAX_CALLBACK_ATTEMPTS = 3
RETRY_INTERVAL_SECONDS = 5
CALLBACK_TIMEOUT_SECONDS = 30


async def post_callback(callback_url: str, result: BatchEvalResult) -> bool:
    """Post a completed batch evaluation result back to the Java service.

    callback_url format:
    http://app:8081/internal/eval/callback

    The Java endpoint writes callback payloads into the MySQL eval_result table
    and updates the eval_batch aggregate status and metric averages.
    """

    # Serialize before sending so every retry posts the exact same immutable
    # payload to Java.
    try:
        payload = result.model_dump_json()
    except Exception:
        # Callback failure is logged instead of raised because evaluation itself
        # has already finished; raising here would interrupt the whole async
        # flow without recovering the result.
        logger.exception("Failed to serialize callback payload batch_id=%s", result.batch_id)
        return False

    headers = {"Content-Type": "application/json"}

    async with httpx.AsyncClient(timeout=CALLBACK_TIMEOUT_SECONDS) as client:
        for attempt in range(1, MAX_CALLBACK_ATTEMPTS + 1):
            try:
                response = await client.post(
                    callback_url,
                    content=payload,
                    headers=headers,
                )
                if response.status_code == 200:
                    return True

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
                # Network jitter can make a single callback fail, but evaluation
                # results must not be lost. The 5-second interval gives the
                # Docker network or Java service a short recovery window.
                await asyncio.sleep(RETRY_INTERVAL_SECONDS)

    # After all retries fail, only record the error. Callback failure should not
    # change the evaluation outcome or raise an exception that stops the worker.
    logger.error(
        "Callback failed after %s attempts batch_id=%s callback_url=%s",
        MAX_CALLBACK_ATTEMPTS,
        result.batch_id,
        callback_url,
    )
    return False
