from flask import Flask, request, jsonify
import pika
import json
import os
import logging
import uuid
import threading
import time

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)

# Configuration parameters
RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq.rabbitmq.svc.cluster.local')
RABBITMQ_PORT = int(os.getenv('RABBITMQ_PORT', '5672'))
REPLY_QUEUE = f"reply-{correlation_id}"
RESPONSE_TTL = 60  # seconds

# In-memory response store with timestamp
response_store = {}

def get_rabbitmq_connection():
    return pika.BlockingConnection(
        pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT)
    )

@app.route('/messages/<queue_name>', methods=['POST'])
def handle_async_request(queue_name):
    original_data = request.json

    try:
        # Generate correlation ID
        correlation_id = str(uuid.uuid4())
        wrapped_message = {
            "correlationId": correlation_id,
            "payload": original_data
        }

        connection = get_rabbitmq_connection()
        channel = connection.channel()
        channel.queue_declare(queue=queue_name, durable=True)
        channel.basic_publish(
            exchange='',
            routing_key=queue_name,
            body=json.dumps(wrapped_message),
            properties=pika.BasicProperties(
                delivery_mode=2,
                correlation_id=correlation_id
            )
        )
        connection.close()

        logging.info(f"Enqueued to '{queue_name}' with correlation ID '{correlation_id}'")
        return jsonify({"status": "Accepted", "correlationId": correlation_id}), 202

    except Exception as e:
        logging.error(f"Failed to publish message: {e}")
        return jsonify({"status": "Error", "error": str(e)}), 500

@app.route('/response/<correlation_id>', methods=['GET'])
def get_response(correlation_id):
    entry = response_store.get(correlation_id)
    if not entry:
        return jsonify({"status": "NotReady"}), 202

    # Remove and return the response
    del response_store[correlation_id]
    return jsonify({"status": "OK", "response": entry["response"]}), 200

def start_reply_listener():
    def listener():
        while True:
            try:
                connection = get_rabbitmq_connection()
                channel = connection.channel()
                channel.queue_declare(queue=REPLY_QUEUE, durable=True)

                def callback(ch, method, properties, body):
                    try:
                        message = json.loads(body)
                        correlation_id = message.get("correlationId")
                        response_data = message.get("response")

                        if correlation_id and response_data:
                            response_store[correlation_id] = {
                                "response": response_data,
                                "timestamp": time.time()
                            }
                            logging.info(f"Stored response for ID: {correlation_id}")
                        ch.basic_ack(delivery_tag=method.delivery_tag)
                    except Exception as e:
                        logging.error(f"Failed to process reply: {e}")

                channel.basic_consume(queue=REPLY_QUEUE, on_message_callback=callback)
                logging.info("Reply listener started.")
                channel.start_consuming()
            except Exception as e:
                logging.error(f"Reply listener failed, retrying in 5s: {e}")
                time.sleep(5)

    thread = threading.Thread(target=listener, daemon=True)
    thread.start()

def start_cleanup():
    def cleaner():
        while True:
            time.sleep(10)
            now = time.time()
            expired = [cid for cid, v in response_store.items() if now - v["timestamp"] > RESPONSE_TTL]
            for cid in expired:
                del response_store[cid]
                logging.info(f"Expired response for ID: {cid}")
    thread = threading.Thread(target=cleaner, daemon=True)
    thread.start()

if __name__ == "__main__":
    start_reply_listener()
    start_cleanup()
    app.run(host="0.0.0.0", port=80)
    logging.info(f"Starting Flask app on 0.0.0.0:80")
