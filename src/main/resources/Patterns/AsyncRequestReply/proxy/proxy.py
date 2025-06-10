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
RESPONSE_TIMEOUT = 10  # Seconds to wait for a reply

def get_rabbitmq_connection():
    return pika.BlockingConnection(
        pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT)
    )

@app.route('/messages/<queue_name>', methods=['POST'])
def handle_async_request(queue_name):
    original_data = request.json
    correlation_id = str(uuid.uuid4())
    reply_queue_name = f"reply-{correlation_id}"

    try:
        connection = get_rabbitmq_connection()
        channel = connection.channel()

        # Declare reply queue with auto-delete after disconnection
        result = channel.queue_declare(queue=reply_queue_name, exclusive=True, auto_delete=True)
        callback_queue = result.method.queue

        response_container = {}

        def on_response(ch, method, properties, body):
            if properties.correlation_id == correlation_id:
                response_container['data'] = json.loads(body)
                ch.stop_consuming()

        channel.basic_consume(queue=callback_queue, on_message_callback=on_response, auto_ack=True)

        # Send message with reply_to and correlation_id
        wrapped_message = {
            "correlationId": correlation_id,
            "payload": original_data
        }

        channel.basic_publish(
            exchange='',
            routing_key=queue_name,
            body=json.dumps(wrapped_message),
            properties=pika.BasicProperties(
                correlation_id=correlation_id,
                reply_to=callback_queue,
                delivery_mode=2
            )
        )

        logging.info(f"Sent message to {queue_name}, waiting for response on {callback_queue}")

        # Wait for response
        channel.connection.process_data_events(time_limit=RESPONSE_TIMEOUT)

        connection.close()

        if 'data' in response_container:
            return jsonify({"status": "OK", "response": response_container["data"]}), 200
        else:
            return jsonify({"status": "Timeout", "message": "No response received in time"}), 504

    except Exception as e:
        logging.error(f"Failed to publish message: {e}")
        return jsonify({"status": "Error", "error": str(e)}), 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=80)
    logging.info(f"Starting Flask app on 0.0.0.0:80")
