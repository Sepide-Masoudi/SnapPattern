from flask import Flask, request, jsonify
import pika
import json
import os
import logging


app = Flask(__name__)

# Configuration parameters (environment variables or defaults)
RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq.rabbitmq.svc.cluster.local')
RABBITMQ_PORT = int(os.getenv('RABBITMQ_PORT', '5672'))
QUEUE_NAME = "service-queue"

# Establish a RabbitMQ connection
def get_rabbitmq_connection():
    return pika.BlockingConnection(pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT))

# Endpoint to handle async requests and enqueue them in RabbitMQ
@app.route('/messages', methods=['POST'])
def handle_async_request():
    data = request.json
    try:
        # Publish the message to RabbitMQ
        connection = get_rabbitmq_connection()
        channel = connection.channel()
        channel.queue_declare(queue=QUEUE_NAME, durable=True)
        channel.basic_publish(exchange='', routing_key=QUEUE_NAME, body=json.dumps(data))
        connection.close()
        logging.info(f"Message enqueued successfully: {data}")
        return jsonify({"status": "Request accepted"}), 202
    except Exception as e:
        logging.error(f"Failed to enqueue message: {e}")
        return jsonify({"status": "Failed to enqueue message", "error": str(e)}), 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=80)
    logging.info(f"Starting Flask app on 0.0.0.0:80")
