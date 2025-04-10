import pika
import requests
import json
import os
import time
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)

# Configuration parameters
RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq.rabbitmq.svc.cluster.local')
RABBITMQ_PORT = int(os.getenv('RABBITMQ_PORT', '5672'))
SERVICE_B_URL = os.getenv('SERVICE_URL', 'http://service.user.svc.cluster.local/target-endpoint')
QUEUE_NAME = os.getenv('QUEUE_NAME', 'service-queue')
EXCHANGE_NAME = os.getenv('EXCHANGE_NAME', 'service-exchange')
ROUTING_KEY = os.getenv('ROUTING_KEY', 'service-routing-key')

def get_rabbitmq_connection():
    return pika.BlockingConnection(
        pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT)
    )

def worker_function():
    while True:
        try:
            connection = get_rabbitmq_connection()
            channel = connection.channel()

            # Declare exchange and queue and bind them
            channel.exchange_declare(exchange=EXCHANGE_NAME, exchange_type='direct', durable=True)
            channel.queue_declare(queue=QUEUE_NAME, durable=True)
            channel.queue_bind(exchange=EXCHANGE_NAME, queue=QUEUE_NAME, routing_key=ROUTING_KEY)

            def callback(ch, method, properties, body):
                data = json.loads(body)
                try:
                    response = requests.post(SERVICE_B_URL, json=data, timeout=10)
                    logging.info(f"Forwarded to Service, response status: {response.status_code}")
                    ch.basic_ack(delivery_tag=method.delivery_tag)
                except requests.RequestException as e:
                    logging.error(f"Failed to forward message to Service: {e}")

            channel.basic_consume(queue=QUEUE_NAME, on_message_callback=callback)
            logging.info("Listener started, waiting for messages...")
            channel.start_consuming()

        except pika.exceptions.AMQPConnectionError as e:
            logging.error(f"Connection to RabbitMQ failed, retrying in 5 seconds: {e}")
            time.sleep(5)

if __name__ == "__main__":
    worker_function()
