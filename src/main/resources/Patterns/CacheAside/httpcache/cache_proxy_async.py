import os
import hashlib
import logging
from fastapi import FastAPI, Request, Response
from fastapi.responses import JSONResponse
import httpx
import redis.asyncio as redis

app = FastAPI()

# Logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("cache-proxy")

# Config
REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379")
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080")
CACHE_TTL = int(os.getenv("CACHE_TTL", "300"))
CACHED_ENDPOINTS = [e.strip() for e in os.getenv("CACHED_ENDPOINTS", "/rest/products").split(",") if e.strip()]

# Global clients
redis_client = None
http_client = None

@app.on_event("startup")
async def startup():
    global redis_client, http_client

    redis_client = redis.RedisCluster.from_url(
        REDIS_URL,
        max_connections=5000,
        decode_responses=True
    )

    http_client = httpx.AsyncClient(timeout=5.0)
    logger.info(f"Connected to Redis Cluster at {REDIS_URL} with connection pooling")


@app.on_event("shutdown")
async def shutdown():
    await redis_client.close()
    await http_client.aclose()

def should_cache(path: str) -> bool:
    return any(path.startswith(ep) for ep in CACHED_ENDPOINTS)

def make_cache_key(path: str) -> str:
    hashed = hashlib.sha256(path.encode()).hexdigest()
    return f"teastore-cache:{hashed}"

@app.get("/healthz")
async def health():
    return {"status": "ok"}

@app.get("/{path:path}")
async def proxy(request: Request, path: str):
    query_string = request.url.query
    full_path = f"/{path}"
    if query_string:
        full_path += f"?{query_string}"

    url = f"{BACKEND_URL}{full_path}"
    cache_key = make_cache_key(full_path)

    if should_cache(full_path):
        try:
            cached = await redis_client.get(cache_key)
            if cached:
                logger.info(f"Cache HIT: {full_path}")
                return Response(content=cached, media_type="application/json")
            logger.info(f"Cache MISS: {full_path}")
        except Exception as e:
            logger.warning(f"Redis GET failed: {e}")

    try:
        backend_response = await http_client.get(url, headers=request.headers)

        if backend_response.status_code == 200 and should_cache(full_path):
            try:
                await redis_client.setex(cache_key, CACHE_TTL, backend_response.text)
                logger.info(f"Cached response for {full_path}")
            except Exception as e:
                logger.warning(f"Redis SETEX failed: {e}")

        return Response(
            content=backend_response.content,
            status_code=backend_response.status_code,
            media_type=backend_response.headers.get("Content-Type", "application/json")
        )

    except httpx.RequestError as e:
        logger.error(f"Request to backend failed: {e}")
        return JSONResponse(status_code=502, content={"error": "Backend unavailable"})