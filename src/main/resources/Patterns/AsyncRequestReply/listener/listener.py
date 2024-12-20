import pika
import requests
import json
import os
import time
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)

# Configuration parameters (environment variables or defaults)
RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq.rabbitmq.svc.cluster.local')
RABBITMQ_PORT = int(os.getenv('RABBITMQ_PORT', '5672'))
SERVICE_B_URL = os.getenv('SERVICE_URL', 'http://service.user.svc.cluster.local/target-endpoint')
QUEUE_NAME = "service-queue"

# Establish a RabbitMQ connection
def get_rabbitmq_connection():
    connection = pika.BlockingConnection(pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT))
    return connection


# Function to listen to RabbitMQ and forward messages to receiving Service
def worker_function():
    while True:
        try:
            connection = get_rabbitmq_connection()
            channel = connection.channel()
            channel.queue_declare(queue=QUEUE_NAME, durable=True)

            def callback(ch, method, properties, body):
                data = json.loads(body)
                try:
                    response = requests.post(SERVICE_B_URL, json=data, timeout=10)
                    logging.info(f"Forwarded to Service, response status: {response.status_code}")
                    ch.basic_ack(delivery_tag=method.delivery_tag)
                except requests.RequestException as e:
                    logging.error(f"Failed to forward message to Service: {e}")

            # Set up consumption
            channel.basic_consume(queue=QUEUE_NAME, on_message_callback=callback)
            logging.info("Listener started, waiting for messages...")
            channel.start_consuming()

        except pika.exceptions.AMQPConnectionError as e:
            logging.error(f"Connection to RabbitMQ failed, retrying in 5 seconds: {e}")
            time.sleep(5)

if __name__ == "__main__":
    worker_function()
