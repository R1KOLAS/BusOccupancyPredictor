from fastapi import APIRouter, HTTPException

from app.repositories.sqlite_store import log_prediction
from app.schemas.predict import PredictMockRequest, PredictMockResponse, PredictRequest, PredictResponse
from app.services.mock_predictor import build_mock_prediction
from app.services.real_predictor import build_real_prediction


router = APIRouter(prefix="/predict", tags=["predict"])


@router.post("/mock", response_model=PredictMockResponse)
def predict_mock(payload: PredictMockRequest) -> PredictMockResponse:
    return build_mock_prediction(payload)


@router.post("", response_model=PredictResponse)
def predict(payload: PredictRequest) -> PredictResponse:
    try:
        response = build_real_prediction(payload)
        log_prediction(_to_dict(payload), _to_dict(response))
        return response
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


def _to_dict(model) -> dict:
    if hasattr(model, "model_dump"):
        return model.model_dump()
    return dict(model)
