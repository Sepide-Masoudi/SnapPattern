FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# System dependencies
RUN apt-get update && apt-get install -y gcc libffi-dev build-essential && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY cache_proxy_async.py .

# Use Gunicorn with Uvicorn worker and multiple async workers
#CMD ["uvicorn", "cache_proxy_async:app", "--host", "0.0.0.0", "--port", "80"]
CMD ["gunicorn", "cache_proxy_async:app", "-k", "uvicorn.workers.UvicornWorker", "--bind", "0.0.0.0:80", "--workers", "6"]