import pandas as pd
import matplotlib.pyplot as plt
import os

def generate_relative_difference_per_pattern(file_path, output_folder):
    try:
        # Load Excel file
        df = pd.read_excel(file_path)

        required_columns = {'Pattern', 'Deployment', 'Workload Level'}
        if not required_columns.issubset(df.columns):
            print("Error: Required columns 'Pattern', 'Deployment', 'Workload Level' not found.")
            return

        os.makedirs(output_folder, exist_ok=True)

        # Identify metric columns (exclude non-metrics)
        non_metric_columns = ['Pattern', 'Deployment', 'Workload Level', 'Timestamp']
        metric_columns = [col for col in df.columns if col not in non_metric_columns]

        # Convert metric columns to numeric (turn 'NULL' → NaN)
        df[metric_columns] = df[metric_columns].apply(pd.to_numeric, errors='coerce')

        if not metric_columns:
            print("No metric columns found for comparison.")
            return

        # Step 1: Pivot data → (Pattern, Workload Level) → External & Internal columns
        pivot_df = df.pivot_table(
            index=['Pattern', 'Workload Level'],
            columns='Deployment',
            values=metric_columns,
            aggfunc='mean'
        )

        # Flatten MultiIndex columns → External_metric, Internal_metric
        pivot_df.columns = [f'{dep}_{metric}' for metric, dep in pivot_df.columns]

        # Step 2: Calculate percentage change ((External/Internal * 100) - 100)
        change_df = pd.DataFrame(index=pivot_df.index)
        for metric in metric_columns:
            ext_col = f'External_{metric}'
            int_col = f'Internal_{metric}'

            if ext_col in pivot_df and int_col in pivot_df:
                external = pivot_df[ext_col]
                internal = pivot_df[int_col]

                # Skip metric if both are all NaN
                if external.isna().all() and internal.isna().all():
                    print(f"Skipping metric '{metric}' (all values are NaN)")
                    continue

                # Compute percentage change
                change_df[metric] = (external / internal) * 100 - 100
            else:
                print(f"Skipping metric '{metric}' (missing External or Internal column)")

        # Step 3: Group by Pattern and average across workloads
        avg_change_df = change_df.groupby('Pattern').mean()

        # Save computed average percentage changes as CSV and Excel
        excel_path = os.path.join(output_folder, 'average_percentage_changes.xlsx')
        avg_change_df.to_excel(excel_path)
        print(f"Saved average percentage change values to:\n- {excel_path}")

        # Step 4: Plot combined per pattern
        for pattern, row in avg_change_df.iterrows():
            # Drop NaN metrics
            row = row.dropna()
            if row.empty:
                print(f"Skipping pattern '{pattern}' (all metric differences are NaN)")
                continue

            metrics = row.index
            values = row.values

            plt.figure(figsize=(14, 6))
            colors = ['red' if v >= 0 else 'green' for v in values]
            bars = plt.bar(metrics, values, edgecolor='black', color=colors)

            # Add labels on top of bars
            for bar, val in zip(bars, values):
                plt.text(bar.get_x() + bar.get_width() / 2,
                         bar.get_height(),
                         f"{val:+.1f}%",
                         ha='center',
                         va='bottom',
                         fontsize=9)

            plt.title(f"{pattern} (Average over All Workloads)\nPercentage Change External vs Internal", fontsize=14)
            plt.xlabel("Metric", fontsize=12)
            plt.ylabel("Average Change (%)", fontsize=12)
            plt.axhline(0, color='black', linestyle='--', linewidth=1)
            plt.xticks(rotation=45, ha='right')
            plt.grid(axis='y', linestyle='--', alpha=0.7)
            plt.tight_layout()

            # Save plot
            safe_pattern = pattern.replace(" ", "_").replace("/", "_")
            plot_path = os.path.join(output_folder, f"{safe_pattern}_combined_change.png")
            plt.savefig(plot_path)
            plt.close()

            print(f"Saved combined plot for Pattern '{pattern}' at {plot_path}")

    except Exception as e:
        print(f"Error generating combined percentage change plots: {e}")


if __name__ == '__main__':
    input_excel_file = "results/validation/metrics_diff.xlsx"
    output_folder = "results/differences"
    generate_relative_difference_per_pattern(input_excel_file, output_folder)