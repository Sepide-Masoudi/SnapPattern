from flask import Flask, request, jsonify, Response
import pika, json, os, logging, uuid, redis, time

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)

RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq.pattern.svc.cluster.local')
RABBITMQ_PORT = int(os.getenv('RABBITMQ_PORT', '5672'))
RABBITMQ_USER = os.getenv('RABBITMQ_USER', 'user')
RABBITMQ_PASSWORD = os.getenv('RABBITMQ_PASSWORD', 'bitnami')
REDIS_HOST = os.getenv('REDIS_HOST', 'redis-cache.proxy.svc.cluster.local')
REDIS_PORT = int(os.getenv('REDIS_PORT', '6379'))
BACKEND_SERVICE = os.getenv('BACKEND_SERVICE', 'service')
BACKEND_PORT = os.getenv('BACKEND_PORT', '8080')
ENDPOINT_PATHS = os.getenv('ENDPOINT_PATHS', '')

redis_client = redis.StrictRedis(host=REDIS_HOST, port=REDIS_PORT, db=0)

rabbitmq_connection = None
rabbitmq_channel = None

def init_rabbitmq():
    global rabbitmq_connection, rabbitmq_channel
    try:
        credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASSWORD)
        parameters = pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT, credentials=credentials)
        rabbitmq_connection = pika.BlockingConnection(parameters)
        rabbitmq_channel = rabbitmq_connection.channel()
        logging.info("RabbitMQ connection established.")
    except Exception as e:
        logging.error(f"Failed to connect to RabbitMQ: {e}")
        rabbitmq_connection = None
        rabbitmq_channel = None

def ensure_rabbitmq_connection():
    global rabbitmq_connection, rabbitmq_channel
    if rabbitmq_connection is None or rabbitmq_connection.is_closed:
        logging.warning("Reinitializing RabbitMQ connection...")
        init_rabbitmq()


@app.route('/', defaults={'path': ''}, methods=['POST'])
@app.route('/<path:path>', methods=['POST'])
def async_proxy(path):
    ensure_rabbitmq_connection()
    if rabbitmq_channel is None:
        return jsonify({"status": "Error", "error": "RabbitMQ unavailable"}), 503

    full_path = path if path.startswith('/') else '/' + path
    correlation_id = str(uuid.uuid4())
    queue_name = BACKEND_SERVICE

    logging.info(f"Incoming request: queue={queue_name}, path={full_path}")

    wrapped_message = {
        "correlationId": correlation_id,
        "payload": request.json,
        "endpoint": full_path
    }

    try:
        rabbitmq_channel.queue_declare(queue=queue_name, durable=True)
        rabbitmq_channel.basic_publish(
            exchange='',
            routing_key=queue_name,
            body=json.dumps(wrapped_message),
            properties=pika.BasicProperties(
                correlation_id=correlation_id,
                reply_to=correlation_id
            )
        )
        logging.info(f"Published async request to queue={queue_name} correlationId={correlation_id}")

        poll_interval = 0.1
        max_wait_time = 10.0
        elapsed = 0.0

        while elapsed < max_wait_time:
            result = redis_client.get(correlation_id)
            if result:
                parsed = json.loads(result)
                logging.info(f"Returning result for correlationId={correlation_id}")
                return Response(
                    response=json.dumps(parsed.get("body", {})),
                    status=parsed.get("status", 200),
                    content_type="application/json"
                )
            time.sleep(poll_interval)
            elapsed += poll_interval

        logging.error(f"Timeout waiting for result for correlationId={correlation_id}")
        return jsonify({"status": "Error", "error": "Timeout waiting for backend response"}), 500

    except Exception as e:
        logging.error(f"Failed to publish or handle request: {e}")
        return jsonify({"status": "Error", "error": str(e)}), 500

# Polling Endpoint, if services implement polling
@app.route('/results/<correlation_id>', methods=['GET'])
def get_result(correlation_id):
    result = redis_client.get(correlation_id)
    if result:
        return jsonify(json.loads(result)), 200
    else:
        return jsonify({"status": "Pending"}), 202


if __name__ == "__main__":
    logging.info("Starting Flask proxy app on 0.0.0.0:80")
    init_rabbitmq()
    app.run(host="0.0.0.0", port=80, threaded=True)