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
    question: str = Field(..., description="Evaluation question")
    ground_truth: str = Field(..., description="Reference answer")
    model_answer: str = Field(..., description="RAG model answer")
    contexts: List[str] = Field(..., description="Retrieved contexts")


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


# FastAPI recommends lifespan instead of the older @app.on_event hooks because
# startup and shutdown wiring stays in one explicit lifecycle block.
@asynccontextmanager
async def lifespan(app: FastAPI):
    rag_client: RagClient | None = None

    try:
        # Step 1: validate API keys before accepting traffic so configuration
        # mistakes fail fast and no evaluation task is accepted in a broken state.
        _validate_ragas_api_keys()
        evaluator = RagasEvaluator()

        # Step 2: initialize the Java RAG client after the evaluator because it
        # depends on service routing, while evaluator config is the first hard gate.
        java_base_url = (
            os.getenv("JAVA_BASE_URL")
            or os.getenv("JAVA_APP_BASE_URL")
            or "http://localhost:8082"
        ).strip()
        if not java_base_url:
            raise RuntimeError("JAVA_BASE_URL is empty")
        rag_client = RagClient(java_base_url=java_base_url)

        # Step 3: build the executor last because it composes the evaluator and
        # RAG client created above.
        task_executor = TaskExecutor(rag_client=rag_client, evaluator=evaluator)

        # Step 4: attach singletons to app.state so route handlers reuse clients
        # and do not recreate expensive Ragas or httpx resources per request.
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
    # A fallback handler keeps unexpected 500 errors JSON-shaped; otherwise
    # FastAPI may return an HTML error page that the Java caller cannot parse.
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
    # Java checks this endpoint during startup to confirm the sidecar is ready;
    # docker-compose healthcheck can use the same lightweight readiness probe.
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
    # Evaluation can take several minutes, so the API returns immediately and
    # Java receives the final result through callback_url.
    _validate_evaluate_request(request)

    task_executor: TaskExecutor = app.state.task_executor

    # create_task schedules the coroutine on FastAPI's running asyncio event loop
    # and returns immediately; the evaluation continues in the background after
    # this request handler sends its response.
    # Unlike threading, create_task does not create an independent OS thread.
    # FastAPI is asyncio-based, so coroutine scheduling is the right fit here;
    # threads are mainly useful for CPU-bound or blocking work.
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
    # Development-only debug endpoint for checking whether Ragas configuration
    # works. It is not part of the production batch callback flow.
    # This request waits for the metric result before returning; one case can
    # take about 30-60 seconds, so use it with client timeouts in mind.
    evaluator: RagasEvaluator = app.state.evaluator
    return await evaluator.evaluate_single(
        question=request.question,
        ground_truth=request.ground_truth,
        model_answer=request.model_answer,
        contexts=request.contexts,
    )


if __name__ == "__main__":
    port = int(os.getenv("PORT", 8091))
    # Production containers do not need hot reload; in Docker it adds noise and
    # extra processes without value.
    uvicorn.run("main:app", host="0.0.0.0", port=port, reload=False)
