import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import os

def generate_plots(df):
    RESULTS_FOLDER = "results/differences"
    os.makedirs(RESULTS_FOLDER, exist_ok=True)

    workload_order = ['Low', 'Medium', 'High']
    better_if_higher = ['IPC', 'RequestRate', 'TotalRequests', 'TotalSpans', 'PPW', 'RW']

    # Metric order
    preferred_order = [
        "ContainerJoulesTotal", "ContainerPowerWattsAvg", "ContainerCpuCyclesAvg",
        "ContainerCacheMissAvg", "ContainerCpuInstructionsAvg", "ContainerCpuInstructionsTotal",
        "MeanLatency", "95PercentileLatency", "RequestRate", "TotalRequests", "TotalSpans",
        "IPC", "PPW", "RW"
    ]

    available_metrics = [col for col in preferred_order if col in df.columns]
    metric_columns = available_metrics

    patterns = df['Pattern'].unique()
    patterns = [p for p in patterns if p != 'Baseline' and 'Internal' not in p]

    for pattern in patterns:
        fig, axes = plt.subplots(1, len(workload_order), figsize=(5 * len(workload_order), 6), sharey=True)
        if len(workload_order) == 1:
            axes = [axes]

        for ax, workload in zip(axes, workload_order):
            workload_df = df[df['Workload Level'] == workload]
            pattern_values = workload_df[workload_df['Pattern'] == pattern][metric_columns].mean()
            baseline_values = workload_df[workload_df['Pattern'] == 'Baseline'][metric_columns].mean()

            rel_diff = []
            for metric in metric_columns:
                try:
                    pattern_val = float(pattern_values[metric])
                    baseline_val = float(baseline_values[metric])
                    if pd.notna(pattern_val) and pd.notna(baseline_val) and baseline_val != 0:
                        diff_pct = ((pattern_val - baseline_val) / baseline_val) * 100
                    else:
                        diff_pct = np.nan
                except:
                    diff_pct = np.nan
                rel_diff.append(diff_pct)

            # Decide bar colors based on metric type
            colors = []
            for metric, diff in zip(metric_columns, rel_diff):
                if np.isnan(diff):
                    colors.append('grey')
                elif metric in better_if_higher:
                    colors.append('green' if diff >= 0 else 'red')
                else:
                    colors.append('green' if diff < 0 else 'red')

            x = np.arange(len(metric_columns))
            width = 0.6

            bars = ax.bar(x, rel_diff, width, color=colors)

            for bar in bars:
                height = bar.get_height()
                if not np.isnan(height):
                    ax.annotate(f'{height:+.1f}%',
                                xy=(bar.get_x() + bar.get_width() / 2, height),
                                xytext=(0, 5),
                                textcoords='offset points',
                                ha='center', va='bottom', fontsize=8)

            ax.set_title(f"{workload} Workload")
            ax.set_xticks(x)
            ax.set_xticklabels(metric_columns, rotation=45, ha='right', fontsize=8)
            ax.set_ylabel("Relative Difference (%) vs Baseline")
            ax.axhline(0, color='black', linewidth=1)
            ax.grid(axis='y', linestyle='--', alpha=0.7)

        plt.suptitle(f"{pattern} vs Baseline (Relative Differences)", fontsize=16)
        plt.tight_layout(rect=[0, 0, 1, 0.95])

        plot_path = os.path.join(RESULTS_FOLDER, f"{pattern}_relative_diff.png")
        plt.savefig(plot_path)
        plt.close()

        print(f"Relative difference plot saved for {pattern} at {plot_path}")

if __name__ == "__main__":
    file_path = 'results/metrics_data.xlsx'
    df = pd.read_excel(file_path)
    generate_plots(df)