import pandas as pd
import numpy as np
import os

def run_analysis(df, output_path):
    # Identify all numeric metric columns
    numeric_metrics = df.select_dtypes(include=[np.number]).columns.tolist()
    workloads = ['Low', 'Medium', 'High']
    records = []

    for metric in numeric_metrics:
        for workload in workloads:
            row = {
                'Metric': metric,
                'Workload': workload
            }

            baseline_val = df[(df['Pattern'] == 'Baseline') & (df['Workload Level'] == workload)][metric].mean()
            row['Baseline'] = baseline_val

            for pattern in df['Pattern'].unique():
                if pattern == 'Baseline' or 'Internal' in pattern:
                    continue
                pattern_val = df[(df['Pattern'] == pattern) & (df['Workload Level'] == workload)][metric].mean()
                if pd.notna(baseline_val) and baseline_val != 0:
                    diff_pct = ((pattern_val - baseline_val) / baseline_val) * 100
                else:
                    diff_pct = np.nan
                row[pattern] = round(diff_pct, 2)

            records.append(row)

    result_df = pd.DataFrame(records)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    result_df.to_excel(output_path, index=False)
    print(f"Separated metric/workload format saved to '{output_path}'")

if __name__ == "__main__":
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
    input_path = os.path.join(BASE_DIR, 'results', 'metrics_data.xlsx')
    output_path = os.path.join(BASE_DIR, 'results', 'differences', 'avg_metrics_diff_per_workload.xlsx')

    if not os.path.exists(input_path):
        print(f"ERROR: Input file not found at '{input_path}'")
    else:
        df = pd.read_excel(input_path, sheet_name="Metrics")
        run_analysis(df, output_path)