from fastapi import FastAPI
import asyncio
import os
import logging
from typing import Dict, Any
from collections import defaultdict
import aiomysql
import aioredis
import json

app = FastAPI()

# Environment variables
DB_HOST = os.environ.get("DB_HOST", "teastore-db.user.svc.cluster.local")
DB_PORT = int(os.environ.get("DB_PORT", 3306))
DB_NAME = os.environ.get("DB_NAME", "teadb")
DB_USER = os.environ.get("DB_USER", "teauser")
DB_PASS = os.environ.get("DB_PASS", "teapassword")
REDIS_POOL_SIZE = int(os.getenv("REDIS_POOL_SIZE", "1000"))
BATCH_QUERY = os.environ.get(
    "BATCH_QUERY",
    "SELECT ID, NAME, DESCRIPTION, LISTPRICEINCENTS, CATEGORY_ID FROM PERSISTENCEPRODUCT WHERE ID IN (%s)"
)
TIMEOUT = float(os.environ.get("TIMEOUT", 1))
BATCH_SIZE = int(os.environ.get("BATCH_SIZE", 50))
REDIS_HOST = os.environ.get("REDIS_HOST", "redis-master.pattern.svc.cluster.local")
REDIS_PORT = int(os.environ.get("REDIS_PORT", 6379))
REDIS_PASS = os.environ.get("REDIS_PASSWORD", "")  # Added
CACHE_TTL = int(os.environ.get("CACHE_TTL", 300))  # Cache time in seconds

# Logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s [%(name)s] %(message)s")
logger = logging.getLogger("request-collapser")

# Queues and state
request_queue = asyncio.Queue()
pending_requests: Dict[int, list[asyncio.Event]] = defaultdict(list)
results: Dict[int, Any] = {}

# Connections
db_pool = None
redis = None


def normalize_product(row: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "id": row["ID"],
        "name": row["NAME"],
        "description": row["DESCRIPTION"],
        "listPriceInCents": row["LISTPRICEINCENTS"],
        "categoryId": row["CATEGORY_ID"]
    }

@app.get("/tools.descartes.teastore.persistence/rest/products/{id}")
async def get_product(id: int):
    logger.info(f"Received request for ID: {id}")

    cached = await redis.get(f"product:{id}")
    if cached:
        logger.info(f"Cache hit for ID {id}")
        return json.loads(cached)

    # Only queue if not cached
    event = asyncio.Event()
    pending_requests[id].append(event)
    await request_queue.put(id)

    try:
        await asyncio.wait_for(event.wait(), timeout=TIMEOUT)
        return results.get(id, {"error": f"No product found for ID {id}"})
    except asyncio.TimeoutError:
        logger.warning(f"Timeout for ID {id}")
        return {"error": f"Timeout waiting for ID {id}"}

async def batch_processor():
    global db_pool, redis
    while True:
        batch_ids = set()
        while len(batch_ids) < BATCH_SIZE:
            try:
                id = await asyncio.wait_for(request_queue.get(), timeout=0.01)
                batch_ids.add(id)
            except asyncio.TimeoutError:
                break

        if not batch_ids:
            await asyncio.sleep(0.01)
            continue

        logger.info(f"Batching IDs: {batch_ids}")

        # Filter IDs that are already in cache
        ids_to_query = []
        for id_ in batch_ids:
            if not await redis.exists(f"product:{id_}"):
                ids_to_query.append(id_)

        if not ids_to_query:
            for id_ in batch_ids:
                cached = await redis.get(f"product:{id_}")
                if cached:
                    results[id_] = json.loads(cached)
                    for ev in pending_requests[id_]:
                        ev.set()
                    pending_requests.pop(id_, None)
            continue

        query = BATCH_QUERY % ','.join(['%s'] * len(ids_to_query))
        try:
            async with db_pool.acquire() as conn:
                async with conn.cursor(aiomysql.DictCursor) as cursor:
                    await cursor.execute(query, ids_to_query)
                    rows = await cursor.fetchall()
        except Exception as e:
            logger.error(f"DB query failed: {e}")
            rows = []

        # Normalize, cache, and dispatch
        response_map = {row["ID"]: normalize_product(row) for row in rows}
        for id_, data in response_map.items():
            results[id_] = data
            await redis.setex(f"product:{id_}", CACHE_TTL, json.dumps(data))
            for ev in pending_requests[id_]:
                ev.set()
            pending_requests.pop(id_, None)

        # Send error for not-found
        not_found = set(ids_to_query) - set(response_map.keys())
        for id_ in not_found:
            results[id_] = {"error": f"No product found for ID {id_}"}
            for ev in pending_requests[id_]:
                ev.set()
            pending_requests.pop(id_, None)


@app.on_event("startup")
async def startup_event():
    global db_pool, redis

    # Create MySQL connection pool
    db_pool = await aiomysql.create_pool(
        host=DB_HOST,
        port=DB_PORT,
        user=DB_USER,
        password=DB_PASS,
        db=DB_NAME,
        minsize=10,
        maxsize=500,
        autocommit=True
    )

    redis_url = (
        f"redis://:{REDIS_PASS}@{REDIS_HOST}:{REDIS_PORT}"
        if REDIS_PASS else f"redis://{REDIS_HOST}:{REDIS_PORT}"
    )

    pool = aioredis.ConnectionPool.from_url(
        redis_url,
        max_connections=REDIS_POOL_SIZE,
        encoding="utf-8",
        decode_responses=True
    )

    redis = aioredis.Redis(connection_pool=pool)
    logger.info(f"Connected to Redis with pool size {REDIS_POOL_SIZE}")

    asyncio.create_task(batch_processor())