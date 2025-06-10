from flask import Flask, jsonify
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import os
import seaborn as sns
import traceback

app = Flask(__name__)

# Folder to save plots
RESULTS_FOLDER = "Python/results/plots"
os.makedirs(RESULTS_FOLDER, exist_ok=True)

EXCEL_FILE = "Python/results/metrics_data.xlsx"

@app.route('/generate_metrics', methods=['POST'])
def generate_metrics():
    try:
        print("Generating metrics from Excel...")

        if not os.path.exists(EXCEL_FILE):
            return jsonify({"error": f"File not found: {EXCEL_FILE}"}), 404

        xl = pd.ExcelFile(EXCEL_FILE, engine='openpyxl')

        if "Metrics" in xl.sheet_names:
            metrics_df = xl.parse("Metrics")
            print("Loaded Metrics sheet.")
            generate_boxplots(metrics_df)
        else:
            print("No 'Metrics' sheet found.")

        if "EnergyTimeSeries" in xl.sheet_names:
            timeseries_df = xl.parse("EnergyTimeSeries")
            print("Loaded EnergyTimeSeries sheet.")
            generate_energy_timeseries_plot(timeseries_df)
        else:
            print("No 'EnergyTimeSeries' sheet found.")

        return jsonify({"message": "Plots generated successfully"}), 200

    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


def generate_metrics_standalone():
    try:
        print("Generating metrics from Excel...")

        if not os.path.exists(EXCEL_FILE):
            return jsonify({"error": f"File not found: {EXCEL_FILE}"}), 404

        xl = pd.ExcelFile(EXCEL_FILE, engine='openpyxl')

        if "Metrics" in xl.sheet_names:
            metrics_df = xl.parse("Metrics")
            print("Loaded Metrics sheet.")
            generate_boxplots(metrics_df)
        else:
            print("No 'Metrics' sheet found.")

        if "EnergyTimeSeries" in xl.sheet_names:
            timeseries_df = xl.parse("EnergyTimeSeries")
            print("Loaded EnergyTimeSeries sheet.")
            generate_energy_timeseries_plot(timeseries_df)
        else:
            print("No 'EnergyTimeSeries' sheet found.")

        return jsonify({"message": "Plots generated successfully"}), 200

    except Exception as e:
        traceback.print_exc()
        return None


def generate_boxplots(df):
    metric_columns = df.columns.difference(['Pattern', 'Workload Level', 'Timestamp'])

    for metric_name in metric_columns:
        try:
            plot_df = df[['Pattern', 'Workload Level', metric_name]].dropna()
            baseline_df = plot_df[plot_df['Pattern'] == 'Baseline']
            plot_df = plot_df[
                (plot_df['Pattern'] != 'Baseline') &
                (~plot_df['Pattern'].str.contains('Internal', na=False))
                ]

            workload_order = ['Low', 'Medium', 'High']
            workload_levels = [w for w in workload_order if w in plot_df['Workload Level'].unique()]

            fig, axes = plt.subplots(1, len(workload_levels), figsize=(5 * len(workload_levels), 6), sharey=True)
            if len(workload_levels) == 1:
                axes = [axes]

            for ax, workload in zip(axes, workload_levels):
                workload_df = plot_df[plot_df['Workload Level'] == workload]
                baseline_value = baseline_df[baseline_df['Workload Level'] == workload][metric_name].mean()

                sns.boxplot(
                    data=workload_df,
                    x='Pattern',
                    y=metric_name,
                    palette='colorblind',
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
            plot_path = os.path.join(RESULTS_FOLDER, f"{metric_name}_boxplot.png")
            plt.savefig(plot_path)
            plt.close()
            print(f"Saved {metric_name} boxplot to {plot_path}")

        except Exception as e:
            print(f"Error plotting {metric_name}: {e}")


def generate_energy_timeseries_plot(df):
    try:
        df['StepIndex'] = df['StepIndex'].astype(int)

        # Group by Pattern, Workload, StepIndex
        grouped = df.groupby(['Pattern', 'Workload', 'StepIndex'])['Energy (Joules)'].mean().reset_index()

        patterns = [p for p in grouped['Pattern'].unique() if p != 'Baseline']
        workload_order = ['Low', 'Medium', 'High']

        for pattern in patterns:
            plt.figure(figsize=(16, 5))
            for i, workload in enumerate(workload_order):
                ax = plt.subplot(1, len(workload_order), i + 1)

                # Subsets
                baseline_df = grouped[(grouped['Pattern'] == 'Baseline') & (grouped['Workload'] == workload)]
                pattern_df = grouped[(grouped['Pattern'] == pattern) & (grouped['Workload'] == workload)]

                if not baseline_df.empty:
                    ax.plot(baseline_df['StepIndex'], baseline_df['Energy (Joules)'],
                            label='Baseline', linestyle='--', color='red')

                if not pattern_df.empty:
                    ax.plot(pattern_df['StepIndex'], pattern_df['Energy (Joules)'],
                            label=pattern, linestyle='-', marker='o')

                ax.set_title(f"{workload} Workload")
                ax.set_xlabel("Time")
                ax.set_ylabel("Energy (Joules)")
                ax.grid(True, linestyle='--', alpha=0.5)
                ax.legend()

            plt.suptitle(f"Energy Time Series - Pattern: {pattern}", fontsize=16)
            plt.tight_layout(rect=[0, 0, 1, 0.93])

            filename = f"energy_timeseries_{pattern}.png"
            plot_path = os.path.join(RESULTS_FOLDER, filename)
            plt.savefig(plot_path)
            plt.close()

            print(f"Saved time series plot for pattern '{pattern}' at {plot_path}")

    except Exception as e:
        print("Error generating time series plot:", e)
        traceback.print_exc()


if __name__ == '__main__':
    app.run(port=5000, debug=True)
    # generate_metrics_standalone()