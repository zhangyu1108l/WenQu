import httpx


class RagClientException(Exception):
    """Custom exception for separating RAG invocation failures from other errors."""


class RagClient:
    """Client for reusing the Java RAG flow during Ragas evaluation.

    Python does not call Milvus or DeepSeek directly here because the Java service
    already owns the complete RAG implementation. Reusing it avoids maintaining
    two divergent retrieval and generation pipelines.
    """

    def __init__(self, java_base_url: str):
        self.java_base_url = java_base_url.rstrip("/")
        timeout = httpx.Timeout(connect=10.0, read=120.0, write=10.0, pool=10.0)
        # Use httpx.AsyncClient instead of requests because FastAPI is async;
        # a synchronous requests call would block the event loop.
        # The 120s read timeout gives enough room for embedding, vector search,
        # and LLM generation, which can be slower than ordinary HTTP calls.
        self.session = httpx.AsyncClient(timeout=timeout)

    async def get_rag_answer(self, question: str, tenant_id: int) -> dict:
        """Call the Java internal RAG endpoint and return answer plus contexts."""

        # /internal/rag/answer is an evaluation-only internal endpoint. It does
        # not pass through Gateway authentication and should only be called on
        # the private Docker network.
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
        model_answer = data.get("model_answer", "")
        # contexts is the list of original retrieved chunk texts. Ragas needs it
        # to compute context_recall and context_precision; without this input,
        # those metrics cannot be calculated.
        contexts = data.get("contexts", [])

        return {
            "model_answer": model_answer,
            "contexts": contexts,
        }

    async def close(self) -> None:
        # Call this from FastAPI lifespan shutdown to close the HTTP connection pool.
        await self.session.aclose()
