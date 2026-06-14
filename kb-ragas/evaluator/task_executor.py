"""完整 Ragas 评估批次的异步执行器。"""

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
    """在后台运行一个评估批次，并将结果回传给 Java。"""

    def __init__(self, rag_client: RagClient, evaluator: RagasEvaluator):
        self.rag_client = rag_client
        self.evaluator = evaluator

    async def execute(self, request: EvaluateRequest) -> None:
        """执行一个已接收批次的完整评估流程。"""

        single_results: list[SingleEvalResult] = []

        try:
            logger.info(
                "Start evaluating batch_id=%s, case_count=%s",
                request.batch_id,
                len(request.cases),
            )

            # 用例串行执行而不是通过 asyncio.gather 并发执行，
            # 因为并发的 DeepSeek/Ragas 调用可能触发供应商限流。
            # 当前项目的评估批次规模较小，串行执行对该侧车来说足够稳定且速度可接受。
            case_count = len(request.cases)
            for case_index, case in enumerate(request.cases, start=1):
                try:
                    # 长耗时评估需要详细里程碑日志，
                    # 便于确认当前阻塞或失败的是哪个用例。
                    logger.info(
                        "Evaluating case %s/%s case_id=%s batch_id=%s",
                        case_index,
                        case_count,
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
                        "Case %s/%s evaluated case_id=%s batch_id=%s faithfulness=%s",
                        case_index,
                        case_count,
                        case.case_id,
                        request.batch_id,
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
                        "Case %s/%s evaluation failed case_id=%s batch_id=%s",
                        case_index,
                        case_count,
                        case.case_id,
                        request.batch_id,
                    )
                finally:
                    # 每个用例结束后暂停一小段时间，
                    # 给 API 限流窗口留出恢复空间，再发起下一轮 DeepSeek/Ragas 调用。
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

            if not await callback.post_callback(request.callback_url, batch_result):
                raise RuntimeError("Callback failed after evaluation completed")

            logger.info(
                "batch_id=%s evaluation completed, avg_faithfulness=%s",
                request.batch_id,
                batch_result.avg_faithfulness,
            )
        except Exception as exc:
            # 即使整个批次失败，也要回调 Java，
            # 让 eval_batch 标记为 FAILED，避免一直停留在 RUNNING。
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
        """从 avg_* 或普通指标键中读取均值。"""

        avg_key = f"avg_{metric_name}"
        if avg_key in averages:
            return averages[avg_key]
        return averages.get(metric_name)
