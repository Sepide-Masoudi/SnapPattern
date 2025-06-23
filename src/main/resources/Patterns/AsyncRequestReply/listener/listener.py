import pika, requests, json, os, time, logging, redis

logging.basicConfig(level=logging.INFO)

RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq.rabbitmq.svc.cluster.local')
RABBITMQ_PORT = int(os.getenv('RABBITMQ_PORT', '5672'))
SERVICE_URL = os.getenv('SERVICE_URL', 'http://service.user.svc.cluster.local/target-endpoint')
QUEUE_NAME = os.getenv('QUEUE_NAME', 'service-queue')
REDIS_HOST = os.getenv('REDIS_HOST', 'redis.pattern.svc.cluster.local')
REDIS_PORT = int(os.getenv('REDIS_PORT', '6379'))

redis_client = redis.StrictRedis(host=REDIS_HOST, port=REDIS_PORT, db=0)

def get_rabbitmq_connection():
    return pika.BlockingConnection(
        pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT)
    )

def worker_function():
    while True:
        try:
            connection = get_rabbitmq_connection()
            channel = connection.channel()

            # Declare queue and bind it
            channel.queue_declare(queue=QUEUE_NAME, durable=True)

            def callback(ch, method, properties, body):
                try:
                    message = json.loads(body)
                    correlation_id = message.get("correlationId")
                    payload = message.get("payload")

                    if not correlation_id or not payload:
                        logging.warning("Missing correlationId or payload. Skipping.")
                        ch.basic_ack(delivery_tag=method.delivery_tag)
                        return

                    # Send the payload to the backend service
                    response = requests.post(SERVICE_URL, json=payload, timeout=10)
                    logging.info(f"Forwarded request, got {response.status_code}")

                    reply = {
                        "status": response.status_code,
                        "body": response.json() if response.content else None
                    }

                    # Cache result in Redis under correlationId
                    redis_client.setex(correlation_id, 300, json.dumps(reply))  # TTL: 5 minutes
                    logging.info(f"Cached result in Redis for correlationId={correlation_id}")
                    ch.basic_ack(delivery_tag=method.delivery_tag)

                except Exception as e:
                    logging.error(f"Error in listener callback: {e}")
                    ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

            channel.basic_consume(queue=QUEUE_NAME, on_message_callback=callback)
            logging.info("Listener started, waiting for messages...")
            channel.start_consuming()

        except pika.exceptions.AMQPConnectionError as e:
            logging.error(f"RabbitMQ connection error: {e}")
            time.sleep(5)

if __name__ == "__main__":
    worker_function()