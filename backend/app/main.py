import json
from pathlib import Path

from fastapi import FastAPI

from app.api.routes.analytics import router as analytics_router
from app.api.routes.health import router as health_router
from app.api.routes.predict import router as predict_router
from app.api.routes.transport import router as transport_router
from app.repositories.transport_data import initialize_transport_data
from app.repositories.sqlite_store import upsert_dataset_registry, upsert_model_registry


app = FastAPI(
    title="Bus Occupancy Predictor Backend",
    version="0.1.0",
    description="Backend API for forecast and analytics."
)


@app.on_event("startup")
def on_startup() -> None:
    initialize_transport_data()
    _register_ml_artifacts()

app.include_router(health_router)
app.include_router(analytics_router)
app.include_router(predict_router)
app.include_router(transport_router)


def _register_ml_artifacts() -> None:
    backend_root = Path(__file__).resolve().parents[1]
    model_path = backend_root / "models" / "catboost_occupancy.cbm"
    metrics_path = backend_root / "models" / "catboost_occupancy_metrics.json"
    dataset_path = backend_root / "data" / "synthetic_occupancy_dataset.csv"

    metrics = {}
    if metrics_path.exists():
        metrics = json.loads(metrics_path.read_text(encoding="utf-8"))

    if model_path.exists():
        upsert_model_registry(
            model_key="catboost_occupancy",
            version=str(metrics.get("best_iteration", "latest")),
            file_path=model_path,
            metadata=metrics or {"file_size": model_path.stat().st_size},
        )

    if dataset_path.exists():
        row_count = _csv_row_count(dataset_path)
        upsert_dataset_registry(
            dataset_key="synthetic_occupancy_dataset",
            version=str(row_count),
            file_path=dataset_path,
            row_count=row_count,
            metadata={"file_size": dataset_path.stat().st_size},
        )


def _csv_row_count(path: Path) -> int:
    with path.open("r", encoding="utf-8") as handle:
        return max(sum(1 for _ in handle) - 1, 0)
