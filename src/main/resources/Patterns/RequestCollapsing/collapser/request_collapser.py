from fastapi import FastAPI
import asyncio
import os
import logging
from typing import Dict, Any
from collections import defaultdict
import aiomysql

app = FastAPI()

# Environment variables
DB_HOST = os.environ.get("DB_HOST", "teastore-db.user.svc.cluster.local")
DB_PORT = int(os.environ.get("DB_PORT", 3306))
DB_NAME = os.environ.get("DB_NAME", "teadb")
DB_USER = os.environ.get("DB_USER", "teauser")
DB_PASS = os.environ.get("DB_PASS", "teapassword")
BATCH_QUERY = os.environ.get(
    "BATCH_QUERY",
    "SELECT ID, NAME, DESCRIPTION, LISTPRICEINCENTS, CATEGORY_ID FROM PERSISTENCEPRODUCT WHERE ID IN (%s)"
)
TIMEOUT = float(os.environ.get("TIMEOUT", 0.5))
BATCH_SIZE = int(os.environ.get("BATCH_SIZE", 50))

# Logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s [%(name)s] %(message)s")
logger = logging.getLogger("request-collapser")

# Queues and state
request_queue = asyncio.Queue()
pending_requests: Dict[int, list[asyncio.Event]] = defaultdict(list)
results: Dict[int, Any] = {}

# DB pool will be set on startup
db_pool = None

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
    global db_pool
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
        query = BATCH_QUERY % ','.join(['%s'] * len(batch_ids))

        try:
            async with db_pool.acquire() as conn:
                async with conn.cursor(aiomysql.DictCursor) as cursor:
                    await cursor.execute(query, list(batch_ids))
                    rows = await cursor.fetchall()
        except Exception as e:
            logger.error(f"DB query failed: {e}")
            rows = []

        response_map = {row["ID"]: normalize_product(row) for row in rows}
        for id_, data in response_map.items():
            results[id_] = data
            for ev in pending_requests[id_]:
                ev.set()
            pending_requests.pop(id_, None)

@app.on_event("startup")
async def startup_event():
    global db_pool
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
    asyncio.create_task(batch_processor())