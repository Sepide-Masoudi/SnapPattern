import pika, requests, json, os, time, logging, redis
import threading
import queue

# Set up root logger
logging.basicConfig(level=logging.INFO)

# Environment configuration
RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq.pattern.svc.cluster.local')
RABBITMQ_PORT = int(os.getenv('RABBITMQ_PORT', '5672'))
RABBITMQ_USER = os.getenv('RABBITMQ_USER', 'user')
RABBITMQ_PASSWORD = os.getenv('RABBITMQ_PASSWORD', 'bitnami')
BACKEND_SERVICE = os.getenv('BACKEND_SERVICE', 'service')
BACKEND_PORT = os.getenv('BACKEND_PORT', '8080')
ENDPOINT_PATHS = os.getenv('ENDPOINT_PATHS', '')
REDIS_HOST = os.getenv('REDIS_HOST', 'redis-cache.proxy.svc.cluster.local')
REDIS_PORT = int(os.getenv('REDIS_PORT', '6379'))

SERVICE_BASE_URL = f"http://{BACKEND_SERVICE}-backend.user.svc.cluster.local:{BACKEND_PORT}"
redis_client = redis.StrictRedis(host=REDIS_HOST, port=REDIS_PORT, db=0)

# Thread-safe queue to handle ACKs from worker threads
ack_queue = queue.Queue()

def get_rabbitmq_connection():
    credentials = pika.PlainCredentials(RABBITMQ_USER, RABBITMQ_PASSWORD)
    parameters = pika.ConnectionParameters(
        host=RABBITMQ_HOST, port=RABBITMQ_PORT, credentials=credentials
    )
    return pika.BlockingConnection(parameters)

def make_callback(queue_name):
    def callback(ch, method, properties, message_body):
        def process():
            try:
                message = json.loads(message_body)
                correlation_id = message.get("correlationId")
                payload = message.get("payload")
                endpoint_path = message.get("endpoint")

                if not correlation_id or not payload or not endpoint_path:
                    logging.warning(f"[{queue_name}] Missing fields. Skipping.")
                    ack_queue.put((ch, method.delivery_tag))
                    return

                target_url = f"{SERVICE_BASE_URL}{endpoint_path if endpoint_path.startswith('/') else '/' + endpoint_path}"
                try:
                    start = time.time()
                    response = requests.post(target_url, json=payload, timeout=10)
                    response_body = response.json() if response.content else None
                    elapsed = time.time() - start
                    logging.info(f"[{queue_name}] Forwarded to {target_url} -> status {response.status_code}")
                    logging.info(f"[{queue_name}] Backend call took {elapsed:.2f}s")
                except Exception as e:
                    logging.error(f"[{queue_name}] Backend request error: {e}")
                    response = type("Response", (), {"status_code": 500})()
                    response_body = None

                reply = {"status": response.status_code, "body": response_body}
                redis_client.setex(correlation_id, 300, json.dumps(reply))
                logging.info(f"[{queue_name}] Stored Redis response for correlationId={correlation_id}")
                ack_queue.put((ch, method.delivery_tag))

            except Exception as e:
                logging.error(f"[{queue_name}] Callback processing error: {e}")
                ack_queue.put((ch, method.delivery_tag))

        threading.Thread(target=process).start()

    return callback

def run_listener():
    while True:
        try:
            logging.info("Connecting to RabbitMQ...")
            connection = get_rabbitmq_connection()
            channel = connection.channel()
            channel.basic_qos(prefetch_count=10)

            queue_name = BACKEND_SERVICE
            logging.info(f"Declaring and listening on queue: {queue_name}")
            channel.queue_declare(queue=queue_name, durable=True)
            channel.basic_consume(
                queue=queue_name,
                on_message_callback=make_callback(queue_name),
                auto_ack=False
            )

            logging.info("Starting to consume...")
            while True:
                channel.connection.process_data_events(time_limit=1)
                while not ack_queue.empty():
                    ch, tag = ack_queue.get()
                    try:
                        ch.basic_ack(delivery_tag=tag)
                    except Exception as e:
                        logging.error(f"Ack failed: {e}")

        except Exception as e:
            logging.error(f"Listener error: {e}")
            time.sleep(5)

if __name__ == "__main__":
    run_listener()