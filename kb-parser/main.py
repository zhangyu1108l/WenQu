import logging
import time

import uvicorn
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from chunker.smart_chunker import SmartChunker
from models.schema import ParseResponse
from parser.pdf_parser import PdfParser
from parser.word_parser import WordParser

logger = logging.getLogger(__name__)

app = FastAPI(title="kb-parser")


def _error_response(status_code: int, error: str) -> JSONResponse:
    return JSONResponse(
        status_code=status_code,
        content={"success": False, "error": error, "chunks": []},
    )


@app.exception_handler(StarletteHTTPException)
async def http_exception_handler(_, exc: StarletteHTTPException) -> JSONResponse:
    # Java ParserClient uses this unified response shape to decide whether
    # parsing succeeded, so every error path must preserve these fields.
    return _error_response(exc.status_code, str(exc.detail))


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(_, exc: RequestValidationError) -> JSONResponse:
    # Keep validation errors in the same shape for Java ParserClient.
    return _error_response(400, str(exc))


@app.exception_handler(Exception)
async def global_exception_handler(_, exc: Exception) -> JSONResponse:
    # Keep unexpected parser/chunker failures in the same shape for Java ParserClient.
    logger.exception("Unhandled parser service error")
    return _error_response(500, str(exc))


@app.get("/health")
async def health() -> dict:
    # Java app calls this endpoint during startup to confirm the parser sidecar is ready.
    return {"status": "ok", "service": "kb-parser"}


@app.post("/parse", response_model=ParseResponse)
async def parse_document(
    file: UploadFile = File(...),
    file_type: str = Form(...),
) -> ParseResponse:
    # Step 1: record start time for parse latency returned to Java.
    start_time = time.perf_counter()

    # Step 2: UploadFile must be read in the async request context.
    file_bytes = await file.read()
    if not file_bytes:
        raise HTTPException(status_code=400, detail="文件内容为空")

    # Step 3: normalize and route to the parser matching the uploaded file type.
    normalized_file_type = file_type.strip().lower()
    if normalized_file_type == "pdf":
        parsed = PdfParser().parse(file_bytes)
    elif normalized_file_type == "docx":
        parsed = WordParser().parse(file_bytes)
    else:
        raise HTTPException(status_code=400, detail="只支持 pdf / docx")

    # Step 4: convert parser output into retrieval-ready chunks.
    chunks = SmartChunker().chunk(
        parsed.get("text_blocks", []),
        parsed.get("tables", []),
        normalized_file_type,
    )

    # Step 5: calculate elapsed time in milliseconds.
    parse_time_ms = int((time.perf_counter() - start_time) * 1000)

    # Step 6: return the successful parser response expected by the Java service.
    return ParseResponse(
        chunks=chunks,
        total_chunks=len(chunks),
        file_type=normalized_file_type,
        parse_time_ms=parse_time_ms,
    )


if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8090, reload=False)
