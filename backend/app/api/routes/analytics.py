from fastapi import APIRouter, HTTPException, Query

from app.repositories.sqlite_store import log_analytics_snapshot
from app.schemas.analytics import AnalyticsResponse
from app.services.analytics_service import build_route_analytics


router = APIRouter(prefix="/analytics", tags=["analytics"])


@router.get("/{route_id}", response_model=AnalyticsResponse)
def analytics(route_id: str, weather: str = Query(default="clear")) -> AnalyticsResponse:
    try:
        response = build_route_analytics(route_id=route_id, weather=weather)
        log_analytics_snapshot(route_id=route_id, weather=weather, response=_to_dict(response))
        return response
    except ValueError as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


def _to_dict(model) -> dict:
    if hasattr(model, "model_dump"):
        return model.model_dump()
    return dict(model)
