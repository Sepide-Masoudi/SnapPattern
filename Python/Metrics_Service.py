from flask import Flask, request, jsonify
import matplotlib.pyplot as plt
import os

app = Flask(__name__)

# Folder to save plots
RESULTS_FOLDER = "Python/results"
os.makedirs(RESULTS_FOLDER, exist_ok=True)


@app.route('/metrics', methods=['POST'])
def receive_metrics():
    try:
        # Parse the incoming JSON data
        metrics = request.json
        print("Received metrics:", metrics)

        # Generate and display plots
        generate_plots(metrics)

        return jsonify({"message": "Metrics processed and plots created."}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


def generate_plots(metrics):
    for metric_name, metric_value in metrics.items():
        try:
            # Assuming metrics values are strings that can be converted to floats
            values = [float(v) for v in metric_value.split(",")] if "," in metric_value else [float(metric_value)]

            # Create a simple bar chart
            plt.figure(figsize=(8, 5))
            plt.bar(range(len(values)), values, color="skyblue", edgecolor="black")
            plt.title(f"{metric_name}")
            plt.xlabel("Index" if len(values) > 1 else "Metric")
            plt.ylabel(metric_name)
            plt.grid(axis="y", linestyle="--", alpha=0.7)

            # Save the plot
            plot_path = os.path.join(RESULTS_FOLDER, f"{metric_name}.png")
            plt.savefig(plot_path)
            plt.close()

            print(f"Plot saved for {metric_name} at {plot_path}")
        except Exception as e:
            print(f"Error generating plot for {metric_name}: {e}")


if __name__ == '__main__':
    app.run(port=5000, debug=True)
