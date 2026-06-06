# 使用 python:3.11-slim 而不是 python:3.11，因为 slim 版本体积更小，
# 同时保留这里需要的 Python 运行时并移除了不必要的系统工具。
FROM python:3.11-slim

WORKDIR /app

# 安装 OCR 引擎、中文 OCR 语言包，以及 Pillow/OpenCV 所需的图像依赖。
# tesseract-ocr-chi-sim 是必需依赖；缺少简体中文语言包时，中文 OCR 会失败。
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-chi-sim \
        tesseract-ocr-eng \
        libgl1-mesa-glx \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .

# 安装 FastAPI 解析服务所需的 Python 依赖。
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8090

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8090"]
