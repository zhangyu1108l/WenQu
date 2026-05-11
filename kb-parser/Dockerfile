# Use python:3.11-slim instead of python:3.11 because slim is smaller
# and removes unnecessary system tools while keeping the Python runtime needed here.
FROM python:3.11-slim

WORKDIR /app

# Install OCR engine, Chinese OCR language data, and Pillow/OpenCV image dependency.
# tesseract-ocr-chi-sim is required because OCR recognition of Chinese text fails
# without the Simplified Chinese language package.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-chi-sim \
        libgl1-mesa-glx \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .

# Install Python dependencies required by the FastAPI parser service.
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8090

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8090"]
