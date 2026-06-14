from typing import List, Literal

from pydantic import BaseModel, Field


class EvalCaseItem(BaseModel):
    """Java 服务发送的单个评估用例。"""

    # MySQL eval_case.id，用于把指标映射回来源用例。
    case_id: int = Field(..., description="MySQL eval_case.id")
    # 面向用户的评估问题，将由 RAG 流程回答。
    question: str = Field(..., description="评估问题")
    # Ragas 用作 context_recall 和 context_precision 基准的标准答案。
    ground_truth: str = Field(
        ...,
        description="Ragas 计算 context_recall 和 context_precision 的标准答案基准",
    )


class EvaluateRequest(BaseModel):
    """Java 服务用于启动异步评估批次的请求体。"""

    # MySQL eval_batch.id，用作任务执行和回调的关联 ID。
    batch_id: int = Field(..., description="MySQL eval_batch.id")
    # RAG 检索流程使用的租户 ID，用于选择 Milvus Collection：tenant_{tenant_id}_docs。
    tenant_id: int = Field(
        ...,
        description="用于选择租户专属检索 Collection 的租户 ID",
    )
    # 本批次包含的评估用例。
    cases: List[EvalCaseItem] = Field(..., description="评估用例列表")
    # 接收完成结果的 Java 回调地址，例如：http://app:8081/internal/eval/callback。
    callback_url: str = Field(
        ...,
        description="Java 回调地址，例如 http://app:8081/internal/eval/callback",
    )


class SingleEvalResult(BaseModel):
    """批次回调载荷中单个用例的评估结果。"""

    # 该结果对应的来源 eval_case.id。
    case_id: int = Field(..., description="来源评估用例 ID")
    # RAG 系统针对该用例问题生成的实际回答。
    model_answer: str = Field(..., description="RAG 系统生成的实际回答")
    # RAG 系统检索到、用于指标计算的上下文段落。
    contexts: List[str] = Field(..., description="检索到的上下文段落")
    # Faithfulness 分数范围为 0 到 1；None 表示只有该指标计算失败。
    faithfulness: float | None = Field(
        None,
        description="Faithfulness 分数，范围 0 到 1；None 表示该指标失败",
    )
    # Answer relevancy 分数范围为 0 到 1；None 表示只有该指标计算失败。
    answer_relevancy: float | None = Field(
        None,
        description="Answer relevancy 分数，范围 0 到 1；None 表示该指标失败",
    )
    # Context recall 分数范围为 0 到 1；None 表示只有该指标计算失败。
    context_recall: float | None = Field(
        None,
        description="Context recall 分数，范围 0 到 1；None 表示该指标失败",
    )
    # Context precision 分数范围为 0 到 1；None 表示只有该指标计算失败。
    context_precision: float | None = Field(
        None,
        description="Context precision 分数，范围 0 到 1；None 表示该指标失败",
    )
    # 用例级错误信息；None 表示该用例整体未失败。
    error: str | None = Field(
        None,
        description="用例级错误信息；None 表示该用例未失败",
    )


class BatchEvalResult(BaseModel):
    """回传给 Java 回调接口的完整批次评估结果。"""

    # 该回调载荷对应的 MySQL eval_batch.id。
    batch_id: int = Field(..., description="MySQL eval_batch.id")
    # 批次状态。DONE 表示回调包含完成结果；FAILED 表示整个批次失败。
    status: Literal["DONE", "FAILED"] = Field(..., description="批次状态：DONE 或 FAILED")
    # 每个用例的评估结果。失败用例可能包含 None 指标值和错误信息。
    results: List[SingleEvalResult] = Field(..., description="逐用例评估结果")
    # 所有非 None 用例值的 faithfulness 均值；无可用值时为 None。
    avg_faithfulness: float | None = Field(
        None,
        description="非 None faithfulness 值的均值；不可用时为 None",
    )
    # 所有非 None 用例值的 answer relevancy 均值；无可用值时为 None。
    avg_answer_relevancy: float | None = Field(
        None,
        description="非 None answer relevancy 值的均值；不可用时为 None",
    )
    # 所有非 None 用例值的 context recall 均值；无可用值时为 None。
    avg_context_recall: float | None = Field(
        None,
        description="非 None context recall 值的均值；不可用时为 None",
    )
    # 所有非 None 用例值的 context precision 均值；无可用值时为 None。
    avg_context_precision: float | None = Field(
        None,
        description="非 None context precision 值的均值；不可用时为 None",
    )
    # 批次级错误信息；None 表示该批次整体未失败。
    error: str | None = Field(
        None,
        description="批次级错误信息；None 表示该批次未失败",
    )


class EvaluateResponse(BaseModel):
    """评估任务被接收后立即返回的同步响应。"""

    # 异步任务是否已被接收；True 表示异步处理已开始。
    accepted: bool = Field(..., description="异步评估任务是否已被接收")
    # 已接收并进入异步处理的 MySQL eval_batch.id。
    batch_id: int = Field(..., description="已接收的 eval_batch ID")
    # 可读的接收提示。它不是评估结果；结果会通过 callback_url 发送。
    message: str = Field(
        ...,
        description="立即响应消息；评估结果会通过 callback_url 异步发送",
    )


class HealthResponse(BaseModel):
    """Ragas 侧车返回的健康检查响应。"""

    # 固定的健康状态值。
    status: Literal["ok"] = Field("ok", description='固定值 "ok"')
    # 固定的服务名称值。
    service: Literal["kb-ragas"] = Field("kb-ragas", description='固定值 "kb-ragas"')
