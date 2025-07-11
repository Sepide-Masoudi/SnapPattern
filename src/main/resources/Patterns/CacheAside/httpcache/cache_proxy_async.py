import os
import hashlib
import logging
from fastapi import FastAPI, Request, Response
from fastapi.responses import JSONResponse
import httpx
from redis.asyncio.cluster import RedisCluster, ClusterNode

app = FastAPI()

# Logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("cache-proxy")

# Config
BACKEND_SERVICE = os.getenv("BACKEND_SERVICE")
BACKEND_PORT = os.getenv("BACKEND_PORT")
BACKEND_URL = f"{BACKEND_SERVICE}.user.svc.cluster.local:8082"
CACHE_TTL = int(os.getenv("CACHE_TTL", "300"))
MAX_CONNECTIONS = int(os.getenv("REDIS_MAX_CONNECTIONS", "5000"))
REDIS_CLUSTER_NODES = int(os.getenv("REDIS_CLUSTER_NODES", "6"))
REDIS_CLUSTER_HOST = os.getenv("REDIS_CLUSTER_HOST", "redis-cache-redis-cluster-headless.pattern.svc.cluster.local")

# Global clients
redis_client = None
http_client = None


@app.on_event("startup")
async def startup():
    global redis_client, http_client

    # Generate startup nodes for the Redis Cluster
    startup_nodes = [
        ClusterNode(
            host=f"redis-cache-redis-cluster-{i}.{REDIS_CLUSTER_HOST}",
            port=6379
        ) for i in range(REDIS_CLUSTER_NODES)
    ]

    redis_client = RedisCluster(
        startup_nodes=startup_nodes,
        decode_responses=True,
        max_connections=MAX_CONNECTIONS,
        socket_timeout=5.0,
    )

    limits = httpx.Limits(
        max_connections=2000,
        max_keepalive_connections=1000    # idle keep-alive connections
    )
    http_client = httpx.AsyncClient(
        timeout=httpx.Timeout(5.0),
        limits=limits
    )

logger.info(f"Connected to Redis Cluster via {REDIS_CLUSTER_NODES} startup nodes")


@app.on_event("shutdown")
async def shutdown():
    await redis_client.close()
    await http_client.aclose()


def make_cache_key(path: str) -> str:
    hashed = hashlib.sha256(path.encode()).hexdigest()
    return f"Backend-cache:{hashed}"


@app.get("/healthz")
async def health():
    return {"status": "ok"}


@app.get("/{path:path}")
async def proxy(request: Request, path: str):
    query_string = request.url.query
    full_path = f"/{path}"
    if query_string:
        full_path += f"?{query_string}"

    cache_key = make_cache_key(full_path)

    # Try Redis cache first
    try:
        cached = await redis_client.get(cache_key)
        if cached:
            logger.info(f"Cache HIT: {full_path}")
            return Response(content=cached, media_type="application/json")
        else:
            logger.info(f"Cache MISS: {full_path}")
    except Exception as e:
        logger.warning(f"Redis GET failed: {e}")

    # Fallback to backend
    backend_url = f"http://{BACKEND_URL}{full_path}"
    try:
        backend_response = await http_client.get(backend_url, headers=request.headers)

        if backend_response.status_code == 200:
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