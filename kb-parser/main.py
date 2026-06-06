import logging
import time

import uvicorn
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from chunker.smart_chunker import SmartChunker
from models.schema import ParseResponse
from parser.pdf_parser import OcrDependencyError, OcrExecutionError, PdfParser, get_ocr_status
from parser.word_parser import WordParseError, WordParser

logger = logging.getLogger(__name__)

app = FastAPI(title="kb-parser")


def _error_response(status_code: int, error: str) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={"success": False, "error": error, "chunks": []},
    )


def _contains_chinese(text: str) -> bool:
    return any("\u4e00" <= char <= "\u9fff" for char in text)


@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(_, exc: StarletteHTTPException) -> JSONResponse:
    # Java ParserClient 会通过统一响应结构判断解析是否成功，
    # 因此所有错误路径都必须保留这些字段。
    detail = str(exc.detail)
    if not _contains_chinese(detail):
        detail = "请求路径不存在" if exc.status_code == 404 else "请求处理失败"
    return _error_response(exc.status_code, detail)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(_, exc: RequestValidationError) -> JSONResponse:
    # 校验错误也保持 Java ParserClient 期望的统一响应结构。
    return _error_response(400, "请求参数校验失败")


@app.exception_handler(OcrDependencyError)
async def ocr_dependency_exception_handler(_, exc: OcrDependencyError) -> JSONResponse:
    logger.exception("OCR runtime dependency is unavailable")
    return _error_response(500, str(exc))


@app.exception_handler(OcrExecutionError)
async def ocr_execution_exception_handler(_, exc: OcrExecutionError) -> JSONResponse:
    logger.exception("OCR execution failed")
    return _error_response(500, str(exc))


@app.exception_handler(Exception)
async def global_exception_handler(_, exc: Exception) -> JSONResponse:
    # 解析器或分块器的非预期异常也保持统一响应结构。
    logger.exception("解析服务发生未处理异常")
    return _error_response(500, "解析服务内部错误")


@app.get("/health")
async def health() -> dict:
    # Java 主服务启动时调用此接口，确认解析侧车是否就绪。
    return {"status": "ok", "service": "kb-parser", "ocr": get_ocr_status()}


@app.post("/parse", response_model=ParseResponse)
async def parse_document(
    file: UploadFile = File(...),
    file_type: str = Form(...),
) -> ParseResponse:
    # 步骤 1：记录开始时间，用于返回给 Java 主服务的解析耗时。
    start_time = time.perf_counter()
    filename = file.filename or "<unknown>"
    raw_file_type = file_type or ""
    normalized_file_type = raw_file_type.strip().lower()
    file_bytes = b""

    try:
        # 步骤 2：UploadFile 必须在异步请求上下文中读取。
        file_bytes = await file.read()
        if not file_bytes:
            raise HTTPException(status_code=400, detail="文件内容为空")

        logger.info(
            "收到解析请求: filename=%s, fileType=%s, size=%d bytes",
            filename,
            normalized_file_type,
            len(file_bytes),
        )

        # 步骤 3：规范化文件类型，并路由到匹配的解析器。
        if normalized_file_type == "pdf":
            parsed = PdfParser().parse(file_bytes)
        elif normalized_file_type == "docx":
            parsed = WordParser().parse(file_bytes)
        else:
            raise HTTPException(status_code=400, detail="只支持 pdf / docx")

        # 步骤 4：将解析结果转换为可用于检索的 chunk。
        chunks = SmartChunker().chunk(
            parsed.get("text_blocks", []),
            parsed.get("tables", []),
            normalized_file_type,
        )

        # 步骤 5：计算毫秒级解析耗时。
        parse_time_ms = int((time.perf_counter() - start_time) * 1000)
        logger.info(
            "解析请求完成: filename=%s, fileType=%s, chunks=%d, parseTimeMs=%d",
            filename,
            normalized_file_type,
            len(chunks),
            parse_time_ms,
        )

        # 步骤 6：返回 Java 主服务期望的成功解析响应。
        return ParseResponse(
            chunks=chunks,
            total_chunks=len(chunks),
            file_type=normalized_file_type,
            parse_time_ms=parse_time_ms,
        )
    except HTTPException:
        raise
    except WordParseError as exc:
        logger.exception(
            "Word 文档解析失败: filename=%s, fileType=%s, size=%d bytes",
            filename,
            normalized_file_type or raw_file_type,
            len(file_bytes),
        )
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception:
        logger.exception(
            "文档解析失败: filename=%s, fileType=%s, size=%d bytes",
            filename,
            normalized_file_type or raw_file_type,
            len(file_bytes),
        )
        raise


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8090, reload=False)
