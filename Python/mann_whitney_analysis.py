
#!/usr/bin/env python3

import pandas as pd
import numpy as np
from scipy.stats import mannwhitneyu
from statsmodels.stats.power import TTestIndPower
import os

def cohens_d(x, y):
    nx = len(x)
    ny = len(y)
    dof = nx + ny - 2
    return (np.mean(x) - np.mean(y)) / np.sqrt(((nx - 1)*np.std(x, ddof=1)**2 + (ny - 1)*np.std(y, ddof=1)**2) / dof)

def run_analysis(df, output_path):
    metrics = [
        'ContainerPowerWattsAvg',
        'MeanLatency',
        '95PercentileLatency',
        'RequestRate',
        'TotalRequests',
        'IPC',
        'PPW',
        'RW'
    ]
    workloads = ['Low', 'Medium', 'High']
    baseline = 'Baseline'
    analysis = TTestIndPower()

    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    with pd.ExcelWriter(output_path) as writer:
        for pattern in df['Pattern'].unique():
            if pattern == baseline or "Internal" in pattern:
                continue

            results = []

            for workload in workloads:
                for metric in metrics:
                    group1 = df[(df['Pattern'] == baseline) & (df['Workload Level'] == workload)][metric]
                    group2 = df[(df['Pattern'] == pattern) & (df['Workload Level'] == workload)][metric]

                    if len(group1) > 1 and len(group2) > 1:
                        stat, p_val = mannwhitneyu(group1, group2, alternative='two-sided')
                        d = cohens_d(group1, group2)
                        n_required = (
                            analysis.solve_power(effect_size=abs(d), alpha=0.05, power=0.8, alternative='two-sided')
                            if np.isfinite(d) and abs(d) > 0 else None
                        )

                        # Compute percentage difference
                        try:
                            baseline_median = float(group1.median())
                            pattern_median = float(group2.median())
                            if pd.notna(baseline_median) and pd.notna(pattern_median) and baseline_median != 0:
                                diff_pct = ((pattern_median - baseline_median) / baseline_median) * 100
                            else:
                                diff_pct = np.nan
                        except:
                            diff_pct = np.nan

                        results.append({
                            'Compared Pattern': pattern,
                            'Workload Level': workload,
                            'Metric': metric,
                            f'{baseline} Median': baseline_median,
                            f'{pattern} Median': pattern_median,
                            'Relative Difference (%)': diff_pct,
                            'U-statistic': stat,
                            'p-value': p_val,
                            "Cohen's d": d,
                            "Estimated Sample Size per Group": int(np.ceil(n_required)) if n_required else "N/A"
                        })

            df_results = pd.DataFrame(results)
            sheet_name = pattern[:31]
            df_results.to_excel(writer, sheet_name=sheet_name, index=False)

    print(f"Analysis complete. Results saved to {output_path}")

if __name__ == "__main__":
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
    input_path = os.path.join(BASE_DIR, 'results', 'metrics_data.xlsx')
    output_path = os.path.join(BASE_DIR, 'results', 'significance', 'stat_results.xlsx')

    if not os.path.exists(input_path):
        print(f"ERROR: Input file not found at '{input_path}'")
    else:
        df = pd.read_excel(input_path, sheet_name="Metrics")
        run_analysis(df, output_path)
