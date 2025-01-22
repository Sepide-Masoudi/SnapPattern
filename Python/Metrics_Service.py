from flask import Flask, request, jsonify
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import os

app = Flask(__name__)

# Folder to save plots
RESULTS_FOLDER = "Python/results"
os.makedirs(RESULTS_FOLDER, exist_ok=True)

EXCEL_FILE = os.path.join(RESULTS_FOLDER, "metrics.xlsx")


@app.route('/generate_metrics', methods=['POST'])
def generate_metrics():
    try:
        # Parse incoming request
        data = request.json
        file_path = data.get('file_path')
        if not file_path or not os.path.exists(file_path):
            return jsonify({"error": "Invalid or missing file_path"}), 400

        # Read Excel file
        df = pd.read_excel(file_path)

        # Generate plots for each metric
        generate_plots(df)

        return jsonify({"message": "Plots generated successfully"}), 200
    except Exception as e:
        return jsonify({"error": str(e)}), 500


def generate_plots(df):
    metric_columns = df.columns[3:]

    for metric_name in metric_columns:
        try:
            # Pivot the data for grouped bar plotting
            pivot_table = df.pivot_table(
                index="Pattern",
                columns="Workload Level",
                values=metric_name,
                aggfunc=np.mean
            )

            # Plot grouped bar chart
            plt.figure(figsize=(10, 6))
            pivot_table.plot(kind="bar", width=0.7, edgecolor="black", colormap="viridis")

            plt.title(f"{metric_name} by Pattern and Workload Level", fontsize=14)
            plt.xlabel("Pattern", fontsize=12)
            plt.ylabel(metric_name, fontsize=12)
            plt.xticks(rotation=45, ha="right", fontsize=10)
            plt.legend(title="Workload Level", fontsize=10)
            plt.grid(axis="y", linestyle="--", alpha=0.7)

            # Save the plot
            plot_path = os.path.join(RESULTS_FOLDER, f"{metric_name}_grouped.png")
            plt.tight_layout()
            plt.savefig(plot_path)
            plt.close()

            print(f"Grouped bar plot saved for {metric_name} at {plot_path}")
        except Exception as e:
            print(f"Error generating grouped bar plot for {metric_name}: {e}")


if __name__ == '__main__':
    app.run(port=5000, debug=True)
