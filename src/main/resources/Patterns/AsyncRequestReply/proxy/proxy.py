from flask import Flask, request, jsonify
import pika, json, os, logging, uuid, redis

app = Flask(__name__)
logging.basicConfig(level=logging.INFO)

# Configuration parameters
RABBITMQ_HOST = os.getenv('RABBITMQ_HOST', 'rabbitmq.rabbitmq.svc.cluster.local')
RABBITMQ_PORT = int(os.getenv('RABBITMQ_PORT', '5672'))
REDIS_HOST = os.getenv('REDIS_HOST', 'redis.pattern.svc.cluster.local')
REDIS_PORT = int(os.getenv('REDIS_PORT', '6379'))

# Redis client
redis_client = redis.StrictRedis(host=REDIS_HOST, port=REDIS_PORT, db=0)

def get_rabbitmq_connection():
    return pika.BlockingConnection(
        pika.ConnectionParameters(host=RABBITMQ_HOST, port=RABBITMQ_PORT)
    )

@app.route('/messages/<queue_name>', methods=['POST'])
def handle_async_request(queue_name):
    original_data = request.json
    correlation_id = str(uuid.uuid4())

    try:
        connection = get_rabbitmq_connection()
        channel = connection.channel()

        # Publish message to backend queue with reply_to set to correlation ID (used as key)
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
                reply_to=correlation_id  # treated as Redis key by listener
            )
        )

        logging.info(f"Published async request to queue={queue_name} correlationId={correlation_id}")
        connection.close()

        return jsonify({"status": "Accepted", "correlationId": correlation_id}), 202

    except Exception as e:
        logging.error(f"Proxy error: {e}")
        return jsonify({"status": "Error", "error": str(e)}), 500

@app.route('/results/<correlation_id>', methods=['GET'])
def get_result(correlation_id):
    try:
        result = redis_client.get(correlation_id)
        if result:
            return jsonify({"status": "OK", "response": json.loads(result)}), 200
        else:
            return jsonify({"status": "Pending", "message": "No result yet"}), 202
    except Exception as e:
        logging.error(f"Redis fetch failed: {e}")
        return jsonify({"status": "Error", "error": str(e)}), 500

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=80)
    logging.info(f"Starting Flask app on 0.0.0.0:80")
