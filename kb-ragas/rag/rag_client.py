import httpx


class RagClientException(Exception):
    """用于区分 RAG 调用失败和其他错误的自定义异常。"""


class RagClient:
    """Ragas 评估期间复用 Java RAG 流程的客户端。

    这里 Python 不直接调用 Milvus 或 DeepSeek，因为 Java 服务已经拥有完整的 RAG 实现。
    复用它可以避免维护两套彼此分叉的检索与生成流程。
    """

    def __init__(self, java_base_url: str):
        self.java_base_url = java_base_url.rstrip("/")
        timeout = httpx.Timeout(connect=10.0, read=120.0, write=10.0, pool=10.0)
        # 使用 httpx.AsyncClient 而不是 requests，因为 FastAPI 是异步的；
        # 同步 requests 调用会阻塞事件循环。
        # 120 秒读取超时为 embedding、向量检索和 LLM 生成留出足够时间，
        # 这些步骤可能比普通 HTTP 调用更慢。
        self.session = httpx.AsyncClient(timeout=timeout, trust_env=False)

    async def get_rag_answer(self, question: str, tenant_id: int) -> dict:
        """调用 Java 内部 RAG 接口，并返回回答与上下文。"""

        # /internal/rag/answer 是仅供评估使用的内部接口。
        # 它不经过 Gateway 鉴权，只应在私有 Docker 网络中调用。
        url = f"{self.java_base_url}/internal/rag/answer"
        try:
            response = await self.session.post(
                url,
                json={"question": question, "tenantId": tenant_id},
            )
        except httpx.ConnectError as exc:
            raise RagClientException("RAG服务不可用") from exc
        except httpx.TimeoutException as exc:
            raise RagClientException("RAG服务响应超时") from exc

        if response.status_code != 200:
            raise RagClientException(f"RAG服务返回错误: {response.status_code}")

        payload = response.json()
        data = payload.get("data", payload) if isinstance(payload, dict) else {}
        model_answer = data.get("modelAnswer") or data.get("model_answer") or ""
        # contexts 是检索到的原始 chunk 文本列表。
        # Ragas 需要它计算 context_recall 和 context_precision；
        # 没有该输入就无法计算这些指标。
        contexts = data.get("contexts") or []
        if not isinstance(contexts, list):
            contexts = []

        return {
            "model_answer": model_answer,
            "contexts": contexts,
        }

    async def close(self) -> None:
        # 在 FastAPI lifespan 关闭阶段调用，用于关闭 HTTP 连接池。
        await self.session.aclose()
