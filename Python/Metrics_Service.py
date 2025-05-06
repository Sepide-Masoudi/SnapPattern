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

EXCEL_FILE = os.path.join(RESULTS_FOLDER, "metrics_test.xlsx")

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

            # Enforce correct workload order
            workload_order = ['Low', 'Medium', 'High']
            workload_levels = [w for w in workload_order if w in plot_df['Workload Level'].unique()]


            # Set up figure with 1 row, 3 columns
            fig, axes = plt.subplots(1, len(workload_levels), figsize=(5 * len(workload_levels), 6), sharey=True)

            if len(workload_levels) == 1:
                axes = [axes]  # Make sure it's iterable

            for ax, workload in zip(axes, workload_levels):
                workload_df = plot_df[plot_df['Workload Level'] == workload]
                baseline_value = baseline_df[baseline_df['Workload Level'] == workload][metric_name].mean()

                sns.barplot(
                    data=workload_df,
                    x='Pattern',
                    y=metric_name,
                    palette='colorblind',
                    errorbar='sd',
                    edgecolor='black',
                    ax=ax
                )

                if not np.isnan(baseline_value):
                    ax.axhline(y=baseline_value, color='red', linestyle='--', label='Baseline')

                ax.set_title(f"{workload} Workload", fontsize=12)
                ax.set_xlabel("Pattern", fontsize=10)
                ax.set_ylabel(metric_name, fontsize=10)
                ax.tick_params(axis='x', rotation=45)
                ax.grid(axis='y', linestyle='--', alpha=0.7)

                if not np.isnan(baseline_value):
                    ax.legend(fontsize=8)

            plt.suptitle(f"{metric_name} Across Workloads", fontsize=16)
            plt.tight_layout(rect=[0, 0, 1, 0.95])

            plot_path = os.path.join(RESULTS_FOLDER, f"{metric_name}_combined.png")
            plt.savefig(plot_path)
            plt.close()

            print(f"Combined plot saved for {metric_name} at {plot_path}")

        except Exception as e:
            print(f"Error generating bar plot for {metric_name}: {e}")

if __name__ == '__main__':
    app.run(port=5000, debug=True)
    # generate_metrics_standalone()