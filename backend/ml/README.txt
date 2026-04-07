ML utilities for BusOccupancyPredictor backend

Files
- generate_synthetic_dataset.py
- train_catboost.py

Suggested flow
1. Generate dataset:
   .\.venv\Scripts\python.exe .\ml\generate_synthetic_dataset.py

2. Train baseline CatBoost model:
   .\.venv\Scripts\python.exe .\ml\train_catboost.py

Outputs
- backend/data/synthetic_occupancy_dataset.csv
- backend/models/catboost_occupancy.cbm
- backend/models/catboost_occupancy_metrics.json

Notes
- The dataset is synthetic, not real passenger telemetry.
- It is useful for MVP, experimentation, and backend integration before real data is available.
