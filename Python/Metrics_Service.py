from flask import Flask, jsonify
import pandas as pd
import numpy as np
import matplotlib
matplotlib.use('Agg')
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
    latency_metrics = {'MeanLatency', '95PercentileLatency'}

    # Determine consistent pattern order with Baseline first
    all_patterns = df['Pattern'].dropna().unique()
    all_patterns = [p for p in all_patterns if p != 'Test' and 'Internal' not in p]
    if 'Baseline' in all_patterns:
        all_patterns = ['Baseline'] + [p for p in all_patterns if p != 'Baseline']
    elif 'Baseline' in df['Pattern'].values:
        all_patterns = ['Baseline'] + all_patterns

    workload_order = ['Low', 'Medium', 'High']

    for metric_name in metric_columns:
        try:
            plot_df = df[['Pattern', 'Workload Level', metric_name]].dropna()
            plot_df = plot_df[plot_df['Pattern'].isin(all_patterns)]

            workload_levels = [w for w in workload_order if w in plot_df['Workload Level'].unique()]
            share_y = False if metric_name in latency_metrics else True

            fig, axes = plt.subplots(1, len(workload_levels), figsize=(5 * len(workload_levels), 6), sharey=share_y)
            if len(workload_levels) == 1:
                axes = [axes]

            for ax, workload in zip(axes, workload_levels):
                workload_df = plot_df[plot_df['Workload Level'] == workload]

                sns.boxplot(
                    data=workload_df,
                    x='Pattern',
                    y=metric_name,
                    order=all_patterns,
                    palette='colorblind',
                    ax=ax
                )

                # Baseline mean line (optional for reference)
                baseline_df = workload_df[workload_df['Pattern'] == 'Baseline']
                if not baseline_df.empty:
                    baseline_value = baseline_df[metric_name].mean()
                    if not np.isnan(baseline_value):
                        ax.axhline(y=baseline_value, color='red', linestyle='--', label='Baseline')

                ax.set_title(f"{workload} Workload", fontsize=12)
                ax.set_xlabel("Pattern", fontsize=10)
                ax.set_ylabel(metric_name, fontsize=10)
                ax.tick_params(axis='x', rotation=45)
                ax.grid(axis='y', linestyle='--', alpha=0.7)

                if not baseline_df.empty and not np.isnan(baseline_value):
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

        patterns = grouped['Pattern'].unique()
        workload_order = ['Low', 'Medium', 'High']

        # Set up the plotting canvas with subplots per workload
        fig, axes = plt.subplots(1, len(workload_order), figsize=(18, 5), sharey=True)

        for i, workload in enumerate(workload_order):
            ax = axes[i]
            for pattern in patterns:
                data = grouped[(grouped['Pattern'] == pattern) & (grouped['Workload'] == workload)]
                if not data.empty:
                    linestyle = '--' if pattern == 'Baseline' else '-'
                    ax.plot(data['StepIndex'], data['Energy (Joules)'],
                            label=pattern, linestyle=linestyle, marker='o' if pattern != 'Baseline' else '')
            ax.set_title(f"{workload} Workload")
            ax.set_xlabel("Time Step")
            if i == 0:
                ax.set_ylabel("Energy (Joules)")
            ax.grid(True, linestyle='--', alpha=0.5)

        # Create shared legend above
        handles, labels = axes[0].get_legend_handles_labels()
        fig.legend(handles, labels, loc='upper center', ncol=len(patterns), bbox_to_anchor=(0.5, 1.15))

        plt.suptitle("Energy Time Series - All Patterns", fontsize=16, y=1.25)
        plt.tight_layout(rect=[0, 0, 1, 0.95])

        filename = "energy_timeseries.png"
        plot_path = os.path.join(RESULTS_FOLDER, filename)
        plt.savefig(plot_path, bbox_inches='tight')
        plt.close()

        print(f"Saved energy time series plot at {plot_path}")

    except Exception as e:
        print("Error generating energy time series plot:", e)
        traceback.print_exc()


if __name__ == '__main__':
    app.run(port=5000, debug=True)
    # generate_metrics_standalone()