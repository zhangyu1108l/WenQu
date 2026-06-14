import asyncio
import os
from contextlib import asynccontextmanager
from typing import List

import uvicorn
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from evaluator.ragas_evaluator import RagasEvaluator
from evaluator.task_executor import TaskExecutor
from models.schema import EvaluateRequest, EvaluateResponse, HealthResponse
from rag.rag_client import RagClient
from utils.logger import get_logger

load_dotenv()

logger = get_logger(__name__)


class SingleEvaluateRequest(BaseModel):
    question: str = Field(..., description="评估问题")
    ground_truth: str = Field(..., description="标准答案")
    model_answer: str = Field(..., description="RAG 模型回答")
    contexts: List[str] = Field(..., description="检索上下文")


def _require_env(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"{name} is not configured")
    return value


def _validate_ragas_api_keys() -> None:
    _require_env("DEEPSEEK_API_KEY")
    _require_env("ZHIPU_API_KEY")


def _validate_evaluate_request(request: EvaluateRequest) -> None:
    if not request.cases:
        raise HTTPException(status_code=400, detail="cases 不能为空")

    for index, case in enumerate(request.cases, start=1):
        if not case.question or not case.question.strip():
            raise HTTPException(status_code=400, detail=f"第 {index} 个用例 question 不能为空")
        if not case.ground_truth or not case.ground_truth.strip():
            raise HTTPException(status_code=400, detail=f"第 {index} 个用例 ground_truth 不能为空")

    if not request.callback_url or not request.callback_url.strip():
        raise HTTPException(status_code=400, detail="callback_url 不能为空")


# FastAPI 推荐使用 lifespan 替代旧的 @app.on_event 钩子，
# 这样启动和关闭逻辑会集中在一个明确的生命周期块中。
@asynccontextmanager
async def lifespan(app: FastAPI):
    rag_client: RagClient | None = None

    try:
        # 步骤 1：接收请求前先校验 API Key，让配置错误尽早暴露，
        # 避免在服务异常状态下接收评估任务。
        _validate_ragas_api_keys()
        evaluator = RagasEvaluator()

        # 步骤 2：在评估器之后初始化 Java RAG 客户端；
        # 评估器配置是第一道硬校验，RAG 客户端则依赖服务路由。
        java_base_url = (os.getenv("JAVA_APP_BASE_URL") or "").strip()
        if not java_base_url:
            raise RuntimeError("JAVA_APP_BASE_URL is required")
        rag_client = RagClient(java_base_url=java_base_url)

        # 步骤 3：最后创建执行器，因为它组合了上面创建的评估器和 RAG 客户端。
        task_executor = TaskExecutor(rag_client=rag_client, evaluator=evaluator)

        # 步骤 4：把单例挂到 app.state，路由处理器可以复用客户端，
        # 避免每次请求都重新创建昂贵的 Ragas 或 httpx 资源。
        app.state.evaluator = evaluator
        app.state.rag_client = rag_client
        app.state.task_executor = task_executor
        app.state.background_tasks = set()

        logger.info("kb-ragas started, java_base_url=%s", java_base_url)
        yield
    finally:
        background_tasks = getattr(app.state, "background_tasks", set())
        if background_tasks:
            for task in list(background_tasks):
                task.cancel()
            await asyncio.gather(*background_tasks, return_exceptions=True)
            background_tasks.clear()
        if rag_client is not None:
            await rag_client.close()
        logger.info("kb-ragas shutdown complete")


app = FastAPI(title="kb-ragas", lifespan=lifespan)


@app.exception_handler(Exception)
async def global_exception_handler(_: Request, exc: Exception) -> JSONResponse:
    # 兜底处理器保证异常 500 也返回 JSON；
    # 否则 FastAPI 可能返回 Java 调用方无法解析的 HTML 错误页。
    logger.exception("Unhandled kb-ragas error")
    return JSONResponse(
        status_code=500,
        content={
            "success": False,
            "error": str(exc),
            "detail": "内部服务错误",
        },
    )


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    # Java 启动时会检查该接口确认侧车已就绪；
    # docker-compose healthcheck 也可以复用这个轻量就绪探针。
    return HealthResponse(status="ok", service="kb-ragas")


def _track_background_task(task: asyncio.Task, batch_id: int) -> None:
    app.state.background_tasks.add(task)

    def _on_done(done_task: asyncio.Task) -> None:
        app.state.background_tasks.discard(done_task)
        try:
            done_task.result()
        except asyncio.CancelledError:
            logger.warning("Evaluation task was cancelled, batch_id=%s", batch_id)
        except Exception:
            logger.exception("Evaluation task crashed, batch_id=%s", batch_id)

    task.add_done_callback(_on_done)


@app.post("/evaluate", response_model=EvaluateResponse)
async def evaluate(request: EvaluateRequest) -> EvaluateResponse:
    # 评估可能持续数分钟，因此接口立即返回，
    # Java 通过 callback_url 接收最终结果。
    _validate_evaluate_request(request)

    task_executor: TaskExecutor = app.state.task_executor

    # create_task 会把协程调度到 FastAPI 正在运行的 asyncio 事件循环并立即返回；
    # 当前请求处理器返回响应后，评估会继续在后台执行。
    # 与 threading 不同，create_task 不会创建独立的操作系统线程。
    # FastAPI 基于 asyncio，协程调度更适合这里；线程主要适用于 CPU 密集或阻塞型工作。
    task = asyncio.create_task(
        task_executor.execute(request),
        name=f"ragas-evaluate-{request.batch_id}",
    )
    _track_background_task(task, request.batch_id)

    return EvaluateResponse(
        accepted=True,
        batch_id=request.batch_id,
        message=f"评估任务已接受，共 {len(request.cases)} 个用例，评估完成后将回调通知",
    )


@app.post("/evaluate/single")
async def evaluate_single(request: SingleEvaluateRequest) -> dict:
    # 仅开发调试使用的接口，用于检查 Ragas 配置是否可用；
    # 它不属于生产批次回调流程。
    # 该请求会等待指标结果后再返回；单个用例可能需要 30-60 秒，调用时需注意客户端超时设置。
    evaluator: RagasEvaluator = app.state.evaluator
    return await evaluator.evaluate_single(
        question=request.question,
        ground_truth=request.ground_truth,
        model_answer=request.model_answer,
        contexts=request.contexts,
    )


if __name__ == "__main__":
    port = int(os.getenv("PORT", 8091))
    # 生产容器不需要热重载；在 Docker 中它只会增加额外日志和进程。
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=False)
