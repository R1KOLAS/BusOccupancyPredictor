from __future__ import annotations

import argparse
import json
from pathlib import Path

import pandas as pd
from catboost import CatBoostRegressor

BACKEND_ROOT = Path(__file__).resolve().parents[1]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train CatBoost baseline for occupancy prediction.")
    parser.add_argument(
        "--dataset",
        type=Path,
        default=BACKEND_ROOT / "data" / "synthetic_occupancy_dataset.csv",
        help="Input dataset CSV.",
    )
    parser.add_argument(
        "--model-output",
        type=Path,
        default=BACKEND_ROOT / "models" / "catboost_occupancy.cbm",
        help="Output CatBoost model path.",
    )
    parser.add_argument(
        "--metrics-output",
        type=Path,
        default=BACKEND_ROOT / "models" / "catboost_occupancy_metrics.json",
        help="Output metrics JSON path.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    if not args.dataset.exists():
        raise FileNotFoundError(f"Dataset not found: {args.dataset}")

    df = pd.read_csv(args.dataset)
    df["sample_date"] = pd.to_datetime(df["sample_date"])
    df = df.sort_values(["sample_date", "hour", "minute"]).reset_index(drop=True)

    target_column = "occupancy_percent"
    feature_columns = [
        "weekday",
        "is_weekend",
        "hour",
        "minute",
        "route_id",
        "route_number",
        "direction_id",
        "direction_type",
        "stop_id",
        "stop_name",
        "stop_index",
        "total_stops",
        "stop_progress_ratio",
        "is_terminal_stop",
        "weather",
        "interval_minutes",
        "active_buses_count",
        "direction_platform_count",
        "direction_path_points_count",
    ]
    categorical_features = [
        "weekday",
        "route_id",
        "route_number",
        "direction_id",
        "direction_type",
        "stop_id",
        "stop_name",
        "weather",
    ]

    split_index = int(len(df) * 0.8)
    train_df = df.iloc[:split_index].copy()
    valid_df = df.iloc[split_index:].copy()

    model = CatBoostRegressor(
        iterations=350,
        learning_rate=0.07,
        depth=8,
        loss_function="RMSE",
        eval_metric="RMSE",
        verbose=50,
        random_seed=42,
    )

    model.fit(
        train_df[feature_columns],
        train_df[target_column],
        cat_features=categorical_features,
        eval_set=(valid_df[feature_columns], valid_df[target_column]),
        use_best_model=True,
    )

    predictions = model.predict(valid_df[feature_columns])
    mae = float((valid_df[target_column] - predictions).abs().mean())
    rmse = float((((valid_df[target_column] - predictions) ** 2).mean()) ** 0.5)

    args.model_output.parent.mkdir(parents=True, exist_ok=True)
    model.save_model(args.model_output)

    metrics = {
        "rows_total": int(len(df)),
        "rows_train": int(len(train_df)),
        "rows_valid": int(len(valid_df)),
        "mae": round(mae, 4),
        "rmse": round(rmse, 4),
        "feature_columns": feature_columns,
        "categorical_features": categorical_features,
        "model_path": str(args.model_output),
    }
    args.metrics_output.write_text(json.dumps(metrics, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Model saved to: {args.model_output}")
    print(f"Metrics saved to: {args.metrics_output}")
    print(json.dumps(metrics, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
