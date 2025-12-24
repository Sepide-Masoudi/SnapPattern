import os
import json
import uuid
import logging
from quart import Quart, request, jsonify
import aio_pika
import aioredis

app = Quart(__name__)
logging.basicConfig(level=logging.INFO)

# Environment variables
RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq.pattern.svc.cluster.local')
RABBITMQ_PORT = int(os.getenv('RABBITMQ_PORT', '5672'))
RABBITMQ_USER = os.getenv('RABBITMQ_USER', 'user')
RABBITMQ_PASSWORD = os.getenv('RABBITMQ_PASSWORD', 'bitnami')
REDIS_HOST = os.getenv('REDIS_HOST', 'redis-cache.proxy.svc.cluster.local')
REDIS_PORT = int(os.getenv('REDIS_PORT', '6379'))
BACKEND_SERVICE = os.getenv('BACKEND_SERVICE', 'service')
BACKEND_PORT = os.getenv('BACKEND_PORT', '8080')

# Shared clients
rabbitmq_connection = None
redis_pool = None

@app.before_serving
async def startup():
    global rabbitmq_connection, redis_pool

    # RabbitMQ connection
    try:
        rabbitmq_connection = await aio_pika.connect_robust(
            host=RABBITMQ_HOST,
            port=RABBITMQ_PORT,
            login=RABBITMQ_USER,
            password=RABBITMQ_PASSWORD
        )
        logging.info("Connected to RabbitMQ")
    except Exception as e:
        logging.error(f"RabbitMQ connection failed: {e}")
        rabbitmq_connection = None

    # Redis connection pool
    try:
        redis_pool = await aioredis.from_url(
            f"redis://{REDIS_HOST}:{REDIS_PORT}", encoding="utf-8", decode_responses=True
        )
        logging.info("Connected to Redis")
    except Exception as e:
        logging.error(f"Redis connection failed: {e}")
        redis_pool = None

@app.after_serving
async def shutdown():
    if rabbitmq_connection:
        await rabbitmq_connection.close()
    if redis_pool:
        await redis_pool.close()

@app.route('/', defaults={'path': ''}, methods=['POST'])
@app.route('/<path:path>', methods=['POST'])
async def async_proxy(path):
    global rabbitmq_connection
    if rabbitmq_connection is None:
        return jsonify({"status": "Error", "error": "RabbitMQ unavailable"}), 503

    full_path = path if path.startswith('/') else '/' + path
    correlation_id = str(uuid.uuid4())
    queue_name = BACKEND_SERVICE

    wrapped_message = {
        "correlationId": correlation_id,
        "payload": await request.get_json(),
        "endpoint": full_path
    }

    try:
        channel = await rabbitmq_connection.channel()
        await channel.declare_queue(queue_name, durable=True)

        await channel.default_exchange.publish(
            aio_pika.Message(
                body=json.dumps(wrapped_message).encode(),
                correlation_id=correlation_id,
                reply_to=correlation_id
            ),
            routing_key=queue_name
        )

        logging.info(f"Published async request to queue={queue_name} correlationId={correlation_id}")

        # Immediately simulate fallback to allow upstream continuation
        return jsonify({
            "status": "Error",
            "error": "Simulated async fallback",
            "correlationId": correlation_id
        }), 500

    except Exception as e:
        logging.error(f"Failed to publish message: {e}")
        return jsonify({"status": "Error", "error": str(e)}), 500

@app.route('/results/<correlation_id>', methods=['GET'])
async def get_result(correlation_id):
    result = await redis_pool.get(correlation_id)
    if result:
        return jsonify(json.loads(result)), 200
    return jsonify({"status": "Pending"}), 202