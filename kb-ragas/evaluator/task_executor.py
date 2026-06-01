"""Async executor for complete Ragas evaluation batches."""

import asyncio
from typing import Any

from evaluator.ragas_evaluator import RagasEvaluator
from models.schema import BatchEvalResult, EvaluateRequest, SingleEvalResult
from rag.rag_client import RagClient
from utils import callback
from utils.logger import get_logger

logger = get_logger(__name__)

CASE_INTERVAL_SECONDS = 2


class TaskExecutor:
    """Run one evaluation batch in the background and report results to Java."""

    def __init__(self, rag_client: RagClient, evaluator: RagasEvaluator):
        self.rag_client = rag_client
        self.evaluator = evaluator

    async def execute(self, request: EvaluateRequest) -> None:
        """Execute the full evaluation flow for one accepted batch."""

        single_results: list[SingleEvalResult] = []

        try:
            logger.info(
                "Start evaluating batch_id=%s, case_count=%s",
                request.batch_id,
                len(request.cases),
            )

            # Cases run serially instead of asyncio.gather concurrency because
            # concurrent DeepSeek/Ragas calls can trigger provider rate limits.
            # Resume-project batches are small, so serial execution is stable
            # and fast enough for this sidecar.
            for case in request.cases:
                try:
                    # Long evaluations need detailed milestone logs; they make
                    # it clear which case is currently blocked or failed.
                    logger.info(
                        "Evaluating case_id=%s batch_id=%s",
                        case.case_id,
                        request.batch_id,
                    )
                    rag_result = await self.rag_client.get_rag_answer(
                        case.question,
                        request.tenant_id,
                    )
                    logger.info(
                        "RAG answer ready case_id=%s batch_id=%s",
                        case.case_id,
                        request.batch_id,
                    )

                    scores = await self.evaluator.evaluate_single(
                        question=case.question,
                        ground_truth=case.ground_truth,
                        model_answer=rag_result["model_answer"],
                        contexts=rag_result["contexts"],
                    )

                    single_result = SingleEvalResult(
                        case_id=case.case_id,
                        model_answer=rag_result["model_answer"],
                        contexts=rag_result["contexts"],
                        faithfulness=scores["faithfulness"],
                        answer_relevancy=scores["answer_relevancy"],
                        context_recall=scores["context_recall"],
                        context_precision=scores["context_precision"],
                    )
                    single_results.append(single_result)

                    logger.info(
                        "Case %s evaluated: faithfulness=%s",
                        case.case_id,
                        scores["faithfulness"],
                    )
                except Exception as exc:
                    single_results.append(
                        SingleEvalResult(
                            case_id=case.case_id,
                            model_answer="",
                            contexts=[],
                            faithfulness=None,
                            answer_relevancy=None,
                            context_recall=None,
                            context_precision=None,
                            error=str(exc),
                        )
                    )
                    logger.exception(
                        "Case %s evaluation failed batch_id=%s",
                        case.case_id,
                        request.batch_id,
                    )
                finally:
                    # Sleep after each case so the API rate-limit window has
                    # breathing room before the next DeepSeek/Ragas call burst.
                    await asyncio.sleep(CASE_INTERVAL_SECONDS)

            averages = self.evaluator.calculate_averages(single_results)

            batch_result = BatchEvalResult(
                batch_id=request.batch_id,
                status="DONE",
                results=single_results,
                avg_faithfulness=self._average_value(averages, "faithfulness"),
                avg_answer_relevancy=self._average_value(averages, "answer_relevancy"),
                avg_context_recall=self._average_value(averages, "context_recall"),
                avg_context_precision=self._average_value(averages, "context_precision"),
            )

            await callback.post_callback(request.callback_url, batch_result)

            logger.info(
                "batch_id=%s evaluation completed, avg_faithfulness=%s",
                request.batch_id,
                batch_result.avg_faithfulness,
            )
        except Exception as exc:
            # Even when the whole batch fails, Java still needs a callback so it
            # can mark eval_batch as FAILED instead of leaving it RUNNING forever.
            failed_result = BatchEvalResult(
                batch_id=request.batch_id,
                status="FAILED",
                results=single_results,
                avg_faithfulness=None,
                avg_answer_relevancy=None,
                avg_context_recall=None,
                avg_context_precision=None,
                error=str(exc),
            )
            await callback.post_callback(request.callback_url, failed_result)
            logger.exception("batch_id=%s evaluation failed", request.batch_id)

    @staticmethod
    def _average_value(averages: dict[str, Any], metric_name: str) -> float | None:
        """Read averages from either avg_* or plain metric keys."""

        avg_key = f"avg_{metric_name}"
        if avg_key in averages:
            return averages[avg_key]
        return averages.get(metric_name)
