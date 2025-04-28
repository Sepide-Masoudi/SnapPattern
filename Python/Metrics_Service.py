from flask import Flask, jsonify
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import os
import seaborn as sns
import openpyxl
import traceback

app = Flask(__name__)

# Folder to save plots
RESULTS_FOLDER = "Python/results"
os.makedirs(RESULTS_FOLDER, exist_ok=True)

EXCEL_FILE = os.path.join(RESULTS_FOLDER, "metrics_agg.xlsx")

@app.route('/generate_metrics', methods=['POST'])
def generate_metrics():
    try:
        print("Generating metrics from default Excel file...")

        if not os.path.exists(EXCEL_FILE):
            print(f"File not found: {EXCEL_FILE}")
            return jsonify({"error": f"File does not exist: {EXCEL_FILE}"}), 404

        df = pd.read_excel(EXCEL_FILE, engine='openpyxl')
        print(f"Loaded Excel file: {EXCEL_FILE}")
        print("Columns:", df.columns.tolist())

        generate_plots(df)

        return jsonify({"message": "Plots generated successfully"}), 200

    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


def generate_metrics_standalone():
    try:
        print("Generating metrics from default Excel file...")

        if not os.path.exists(EXCEL_FILE):
            print(f"File not found: {EXCEL_FILE}")
            return

        df = pd.read_excel(EXCEL_FILE, engine='openpyxl')
        print(f"Loaded Excel file: {EXCEL_FILE}")
        print("Columns:", df.columns.tolist())

        generate_plots(df)

        print("Plots generated successfully.")
    except Exception as e:
        traceback.print_exc()



def generate_plots(df):
    # Remove non-metric columns
    metric_columns = df.columns.difference(['Pattern', 'Workload Level', 'Timestamp'])

    for metric_name in metric_columns:
        try:
            # Prepare data for plotting
            plot_df = df[['Pattern', 'Workload Level', metric_name]].dropna()
            baseline_df = plot_df[plot_df['Pattern'] == 'Baseline']
            plot_df = plot_df[
                (plot_df['Pattern'] != 'Baseline') &
                (~plot_df['Pattern'].str.contains('Internal', na=False))
                ]

            workload_levels = plot_df['Workload Level'].unique()

            for workload in workload_levels:
                workload_df = plot_df[plot_df['Workload Level'] == workload]
                baseline_value = baseline_df[baseline_df['Workload Level'] == workload][metric_name].mean()

                plt.figure(figsize=(10, 6))
                sns.barplot(
                    data=workload_df,
                    x='Pattern',
                    y=metric_name,
                    palette='colorblind',
                    errorbar='sd',
                    edgecolor='black'
                )

                if not np.isnan(baseline_value):
                    plt.axhline(y=baseline_value, color='red', linestyle='--', label='Baseline')

                plt.title(f"{metric_name} - {workload} Workload", fontsize=14)
                plt.xlabel("Pattern", fontsize=12)
                plt.ylabel(metric_name, fontsize=12)
                plt.xticks(rotation=45, ha="right", fontsize=10)

                handles, labels = plt.gca().get_legend_handles_labels()
                if handles:
                    plt.legend(fontsize=10)

                plt.grid(axis="y", linestyle="--", alpha=0.7)

                plot_path = os.path.join(RESULTS_FOLDER, f"{metric_name}_{workload}_separate.png")
                plt.tight_layout()
                plt.savefig(plot_path)
                plt.close()

                print(f"Plot saved for {metric_name} - {workload} at {plot_path}")
        except Exception as e:
            print(f"Error generating bar plot for {metric_name}: {e}")

if __name__ == '__main__':
    app.run(port=5000, debug=True)
    # generate_metrics_standalone()