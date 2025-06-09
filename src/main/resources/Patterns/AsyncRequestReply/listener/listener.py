import pika
import requests
import json
import os
import time
import logging

logging.basicConfig(level=logging.INFO)

# Configuration parameters (environment variables or defaults)
RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq.rabbitmq.svc.cluster.local')
RABBITMQ_PORT = int(os.getenv('RABBITMQ_PORT', '5672'))
SERVICE_B_URL = os.getenv('SERVICE_URL', 'http://service.user.svc.cluster.local/target-endpoint')
QUEUE_NAME = os.getenv('QUEUE_NAME', 'service-queue')
EXCHANGE_NAME = os.getenv('EXCHANGE_NAME', 'service-exchange')
ROUTING_KEY = os.getenv('ROUTING_KEY', 'service-routing-key')
REPLY_QUEUE = os.getenv('REPLY_QUEUE', 'reply-queue')

def get_rabbitmq_connection():
    return pika.BlockingConnection(
        pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT)
    )

def worker_function():
    while True:
        try:
            connection = get_rabbitmq_connection()
            channel = connection.channel()

            # Declare the queues and exchange
            channel.exchange_declare(exchange=EXCHANGE_NAME, exchange_type='direct', durable=True)
            channel.queue_declare(queue=QUEUE_NAME, durable=True)
            channel.queue_declare(queue=REPLY_QUEUE, durable=True)
            channel.queue_bind(exchange=EXCHANGE_NAME, queue=QUEUE_NAME, routing_key=ROUTING_KEY)

            def callback(ch, method, properties, body):
                try:
                    # Parse and unwrap the incoming message
                    message = json.loads(body)
                    correlation_id = message.get("correlationId")
                    payload = message.get("payload")

                    if not correlation_id or not payload:
                        logging.warning("Message missing correlationId or payload")
                        ch.basic_ack(delivery_tag=method.delivery_tag)
                        return

                    # Send the payload to the backend service
                    response = requests.post(SERVICE_B_URL, json=payload, timeout=10)
                    logging.info(f"Forwarded to service, status: {response.status_code}")

                    # Build reply message
                    reply_msg = {
                        "correlationId": correlation_id,
                        "response": {
                            "status": response.status_code,
                            "body": response.json() if response.content else None
                        }
                    }

                    # Publish response to reply queue
                    channel.basic_publish(
                        exchange='',
                        routing_key=REPLY_QUEUE,
                        body=json.dumps(reply_msg),
                        properties=pika.BasicProperties(delivery_mode=2)
                    )

                    logging.info(f"Published response for correlationId {correlation_id} to reply queue.")
                    ch.basic_ack(delivery_tag=method.delivery_tag)

                except Exception as e:
                    logging.error(f"Error in callback: {e}")
                    ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)

            channel.basic_consume(queue=QUEUE_NAME, on_message_callback=callback)
            logging.info("Listener started, waiting for messages...")
            channel.start_consuming()

        except pika.exceptions.AMQPConnectionError as e:
            logging.error(f"Connection to RabbitMQ failed, retrying in 5 seconds: {e}")
            time.sleep(5)

if __name__ == "__main__":
    worker_function()
