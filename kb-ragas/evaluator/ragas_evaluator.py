"""Core Ragas evaluator for computing semantic RAG quality metrics."""

import asyncio
import math
import os
from typing import List

from datasets import Dataset
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from ragas import evaluate
from ragas.metrics import (
    answer_relevancy,
    context_precision,
    context_recall,
    faithfulness,
)

from models.schema import SingleEvalResult
from utils.logger import get_logger

logger = get_logger(__name__)

DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com"
DEFAULT_ZHIPU_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
DEFAULT_SINGLE_CASE_TIMEOUT_SECONDS = 180


class RagasEvaluator:
    # Ragas 四项指标计算流程：
    #
    # question + answer + contexts + ground_truth
    #   ↓
    # Faithfulness:      LLM判断 answer 每句话能否从 contexts 推断
    # Answer Relevancy:  LLM从answer逆向生成问题，与原question算向量相似度
    # Context Recall:    LLM判断 ground_truth 每句话是否在 contexts 中有支撑
    # Context Precision: LLM判断每个 context 是否对 ground_truth 有贡献
    #
    # Ragas 需要 LLM 和 Embedding，因为它不是简单规则打分，而是用 LLM 做语义判断；
    # 其中 answer_relevancy 还需要向量相似度来衡量“回答反推问题”和原问题的接近程度。

    def __init__(self):
        # DeepSeek 用于 Ragas 内部语义判断。temperature=0 是为了让指标判断尽量确定，
        # 避免同一评估样本因为随机采样得到不同分数。
        self.llm = ChatOpenAI(
            model="deepseek-chat",
            base_url=os.getenv("DEEPSEEK_BASE_URL") or DEFAULT_DEEPSEEK_BASE_URL,
            api_key=os.getenv("DEEPSEEK_API_KEY"),
            temperature=0,
        )

        # 智谱 embedding-3 用于 Ragas 内部向量化；answer_relevancy 会先用 LLM
        # 从 answer 逆向生成问题，再与原始 question 计算向量相似度。
        self.embeddings = OpenAIEmbeddings(
            model="embedding-3",
            base_url=os.getenv("ZHIPU_BASE_URL") or DEFAULT_ZHIPU_BASE_URL,
            api_key=os.getenv("ZHIPU_API_KEY"),
        )

        # Faithfulness: LLM 判断 answer 中的陈述是否能被 contexts 支撑。
        # Answer Relevancy: LLM 从 answer 反推问题，再用 Embedding 与 question 算相似度。
        # Context Recall: LLM 判断 ground_truth 的信息是否被 contexts 覆盖。
        # Context Precision: LLM 判断检索到的每个 context 是否对 ground_truth 有贡献。
        self.metrics = [
            faithfulness,
            answer_relevancy,
            context_recall,
            context_precision,
        ]

    async def evaluate_single(
        self,
        question: str,
        ground_truth: str,
        model_answer: str,
        contexts: List[str],
    ) -> dict:
        """Evaluate one RAG answer with four Ragas metrics."""

        # Ragas 内部会多次调用 DeepSeek：每个指标都可能需要多轮 LLM 判断，
        # 单个用例通常会消耗约 10~20 次 API 调用。
        try:
            # Ragas 要求使用 HuggingFace datasets.Dataset 格式输入。
            # question: 原始问题，用于 answer_relevancy 的相似度基准。
            # answer: RAG 系统实际回答，是 faithfulness 和 answer_relevancy 的待评估对象。
            # contexts: 检索到的段落列表，是 RAG 回答所依赖的证据。
            # ground_truth: 人工标注标准答案，用于 context_recall 和 context_precision。
            data = {
                "question": [question],
                "answer": [model_answer],
                "contexts": [contexts],
                "ground_truth": [ground_truth],
            }
            dataset = Dataset.from_dict(data)

            # raise_exceptions=False 表示单个指标计算失败时不直接抛异常，
            # Ragas 会继续尝试其他指标，并把失败指标记录为 NaN。
            result = await asyncio.wait_for(
                asyncio.to_thread(
                    evaluate,
                    dataset=dataset,
                    metrics=self.metrics,
                    llm=self.llm,
                    embeddings=self.embeddings,
                    raise_exceptions=False,
                ),
                timeout=self._single_case_timeout_seconds(),
            )

            result_df = result.to_pandas()
            faithfulness_score = result_df["faithfulness"].iloc[0]
            answer_relevancy_score = result_df["answer_relevancy"].iloc[0]
            context_recall_score = result_df["context_recall"].iloc[0]
            context_precision_score = result_df["context_precision"].iloc[0]

            def safe_score(val) -> float | None:
                # JSON 不支持 NaN，直接序列化会失败，所以评估失败的 NaN 统一转为 None。
                # 指标用于趋势观察和批次均值，保留 4 位小数已经有足够精度。
                if val is None:
                    return None
                try:
                    numeric = float(val)
                except (TypeError, ValueError):
                    return None
                if not math.isfinite(numeric):
                    return None
                return round(numeric, 4)

            return {
                "faithfulness": safe_score(faithfulness_score),
                "answer_relevancy": safe_score(answer_relevancy_score),
                "context_recall": safe_score(context_recall_score),
                "context_precision": safe_score(context_precision_score),
            }
        except asyncio.TimeoutError:
            logger.exception("Ragas metrics timed out for one case")
            return {
                "faithfulness": None,
                "answer_relevancy": None,
                "context_recall": None,
                "context_precision": None,
            }
        except Exception:
            # 单个用例评估失败不应中断整批评估；上层仍可回传该用例的空指标。
            logger.exception("Failed to evaluate Ragas metrics for one case")
            return {
                "faithfulness": None,
                "answer_relevancy": None,
                "context_recall": None,
                "context_precision": None,
            }

    def _single_case_timeout_seconds(self) -> int:
        raw_value = os.getenv("RAGAS_SINGLE_CASE_TIMEOUT_SECONDS", "").strip()
        if not raw_value:
            return DEFAULT_SINGLE_CASE_TIMEOUT_SECONDS
        try:
            return max(1, int(raw_value))
        except ValueError:
            logger.warning(
                "Invalid RAGAS_SINGLE_CASE_TIMEOUT_SECONDS=%s, using default=%s",
                raw_value,
                DEFAULT_SINGLE_CASE_TIMEOUT_SECONDS,
            )
            return DEFAULT_SINGLE_CASE_TIMEOUT_SECONDS

    def calculate_averages(self, results: List[SingleEvalResult]) -> dict:
        """Calculate batch averages while ignoring failed metric values."""

        def average(metric_name: str) -> float | None:
            # 过滤 None 是为了避免少数用例评估失败拉低整体均值。
            # 示例：faithfulness 得分 [0.9, None, 0.8, 0.85]
            # 过滤后 [0.9, 0.8, 0.85]，均值为 0.85。
            values = [
                getattr(result, metric_name)
                for result in results
                if getattr(result, metric_name) is not None
            ]
            if not values:
                return None
            return round(sum(values) / len(values), 4)

        return {
            "avg_faithfulness": average("faithfulness"),
            "avg_answer_relevancy": average("answer_relevancy"),
            "avg_context_recall": average("context_recall"),
            "avg_context_precision": average("context_precision"),
        }
