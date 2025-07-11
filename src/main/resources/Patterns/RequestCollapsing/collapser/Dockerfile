FROM python:3.10-slim

WORKDIR /app
COPY request_collapser.py .

RUN pip install fastapi uvicorn aiomysql uvloop

EXPOSE 80
CMD ["uvicorn", "request_collapser:app", "--host", "0.0.0.0", "--port", "80", "--workers", "8", "--loop", "uvloop"]
