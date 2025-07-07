import pandas as pd
import numpy as np
import os

def run_analysis(df, output_path):
    # Identify all numeric metric columns
    numeric_metrics = df.select_dtypes(include=[np.number]).columns.tolist()

    # Initialize result dictionary
    summary = {}

    # Compute baseline means
    baseline_means = df[df['Pattern'] == 'Baseline'][numeric_metrics].mean()
    summary['Baseline'] = baseline_means

    # Compute relative differences for each pattern
    for pattern in df['Pattern'].unique():
        if pattern == 'Baseline' or 'Internal' in pattern:
            continue
        pattern_means = df[df['Pattern'] == pattern][numeric_metrics].mean()
        rel_diff = ((pattern_means - baseline_means) / baseline_means * 100).round(2)
        summary[pattern] = rel_diff

    # Create summary DataFrame and save to Excel
    summary_df = pd.DataFrame(summary).T
    summary_df.index.name = 'Pattern'
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    summary_df.to_excel(output_path)
    print(f"Aggregated metrics saved to '{output_path}'")

if __name__ == "__main__":
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
    input_path = os.path.join(BASE_DIR, 'results', 'metrics_data.xlsx')
    output_path = os.path.join(BASE_DIR, 'results', 'differences', 'avg_metrics_diff.xlsx')

    if not os.path.exists(input_path):
        print(f"ERROR: Input file not found at '{input_path}'")
    else:
        df = pd.read_excel(input_path, sheet_name="Metrics")
        run_analysis(df, output_path)