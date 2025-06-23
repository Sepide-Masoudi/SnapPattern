import os
import hashlib
import logging
from fastapi import FastAPI, Request, Response
from fastapi.responses import JSONResponse
import httpx
import aioredis
import asyncio

app = FastAPI()

# Logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("cache-proxy")

# Configuration
REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379")
BACKEND_URL = os.getenv("BACKEND_URL", "http://localhost:8080")
CACHE_TTL = int(os.getenv("CACHE_TTL", "300"))
CACHED_ENDPOINTS = [e.strip() for e in os.getenv("CACHED_ENDPOINTS", "/rest/products").split(",") if e.strip()]

# Redis client
redis = None

@app.on_event("startup")
async def startup():
    global redis
    redis = await aioredis.from_url(REDIS_URL, decode_responses=True)
    logger.info(f"Connected to Redis at {REDIS_URL}")

@app.on_event("shutdown")
async def shutdown():
    await redis.close()

def should_cache(path: str) -> bool:
    return any(path.startswith(ep) for ep in CACHED_ENDPOINTS)

def make_cache_key(path: str) -> str:
    hashed = hashlib.sha256(path.encode()).hexdigest()
    return f"teastore-cache:{hashed}"

@app.get("/{path:path}")
async def proxy(request: Request, path: str):
    full_path = "/" + path
    url = f"{BACKEND_URL}{full_path}"
    cache_key = make_cache_key(full_path)

    if should_cache(full_path):
        cached = await redis.get(cache_key)
        if cached:
            logger.info(f"Cache HIT: {full_path}")
            return Response(content=cached, media_type="application/json")

        logger.info(f"Cache MISS: {full_path}")

    try:
        async with httpx.AsyncClient(timeout=5.0) as client:
            backend_response = await client.get(url, headers=request.headers)

        if backend_response.status_code == 200 and should_cache(full_path):
            await redis.setex(cache_key, CACHE_TTL, backend_response.text)
            logger.info(f"Cached response for {full_path}")

        return Response(
            content=backend_response.content,
            status_code=backend_response.status_code,
            media_type=backend_response.headers.get("Content-Type", "application/json")
        )

    except httpx.RequestError as e:
        logger.error(f"Request to backend failed: {e}")
        return JSONResponse(status_code=502, content={"error": "Backend unavailable"})