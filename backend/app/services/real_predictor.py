from __future__ import annotations

from functools import lru_cache
from pathlib import Path

import pandas as pd
from catboost import CatBoostRegressor

from app.repositories.transport_data import (
    DirectionRecord,
    PlatformRecord,
    RouteRecord,
    get_direction,
    get_platform,
    get_route,
)
from app.schemas.predict import ForecastPoint, PredictRequest, PredictResponse

BACKEND_ROOT = Path(__file__).resolve().parents[2]
MODEL_PATH = BACKEND_ROOT / "models" / "catboost_occupancy.cbm"

WEEKDAY_ORDER = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
FEATURE_COLUMNS = [
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


def build_real_prediction(payload: PredictRequest | dict) -> PredictResponse:
    if isinstance(payload, dict):
        payload = PredictRequest(**payload)

    route_id = _payload_value(payload, "route_id")
    direction_id = _payload_value(payload, "direction_id")
    stop_id = _payload_value(payload, "stop_id")

    route = get_route(route_id)
    if route is None:
        raise ValueError(f"Route not found: {route_id}")

    direction = get_direction(route, direction_id)
    platform = get_platform(direction, stop_id)
    if platform is None:
        raise ValueError(f"Stop {stop_id} not found in route {route.route_id}")

    points = []
    for minutes in (10, 20, 30, 60):
        percent = _predict_percent(route, direction, platform, payload, minute_offset=minutes)
        points.append(
            ForecastPoint(
                minutes=minutes,
                occupancy_percent=percent,
                seat_status=_seat_status(percent),
            )
        )

    base = _predict_percent(route, direction, platform, payload, minute_offset=0)

    return PredictResponse(
        route_id=route.route_id,
        route_number=route.number,
        direction_id=direction.direction_id,
        direction_label=_direction_label(route, direction),
        stop_id=platform.station_id or platform.platform_id,
        stop_name=platform.name,
        stop_index=platform.stop_index,
        total_stops=len(direction.platforms),
        occupancy_percent=base,
        seat_status=_seat_status(base),
        forecast=points,
    )


def _predict_percent(
    route: RouteRecord,
    direction: DirectionRecord,
    platform: PlatformRecord,
    payload: PredictRequest,
    minute_offset: int,
) -> int:
    try:
        model = _load_model()
        if model is None:
            raise RuntimeError("CatBoost model not found")

        weekday, hour, minute = _shifted_time(payload.day_of_week, payload.time_value, minute_offset)
        features = pd.DataFrame(
            [
                {
                    "weekday": weekday,
                    "is_weekend": 1 if weekday in {"Sat", "Sun"} else 0,
                    "hour": hour,
                    "minute": minute,
                    "route_id": route.route_id,
                    "route_number": route.number,
                    "direction_id": direction.direction_id,
                    "direction_type": direction.direction_type,
                    "stop_id": platform.station_id or platform.platform_id,
                    "stop_name": platform.name,
                    "stop_index": platform.stop_index,
                    "total_stops": len(direction.platforms),
                    "stop_progress_ratio": _stop_progress_ratio(platform, direction),
                    "is_terminal_stop": 1 if platform.stop_index in {0, len(direction.platforms) - 1} else 0,
                    "weather": payload.weather.lower(),
                    "interval_minutes": _estimate_interval_minutes(direction),
                    "active_buses_count": _estimate_active_buses(direction),
                    "direction_platform_count": len(direction.platforms),
                    "direction_path_points_count": direction.path_points_count,
                }
            ],
            columns=FEATURE_COLUMNS,
        )
        prediction = float(model.predict(features)[0])
        return _clamp(round(prediction))
    except Exception:
        base = _base_percent(route, direction, platform, payload)
        return _clamp(base + _future_delta(payload, platform, minute_offset))


def predict_percent_value(
    route: RouteRecord,
    direction: DirectionRecord,
    platform: PlatformRecord,
    day_of_week: str,
    time_value: str,
    weather: str = "clear",
    minute_offset: int = 0,
) -> int:
    payload = PredictRequest(
        route_id=route.route_id,
        direction_id=direction.direction_id,
        stop_id=platform.station_id or platform.platform_id,
        day_of_week=day_of_week,
        time_value=time_value,
        weather=weather,
    )
    return _predict_percent(route, direction, platform, payload, minute_offset=minute_offset)


@lru_cache(maxsize=1)
def _load_model() -> CatBoostRegressor | None:
    if not MODEL_PATH.exists():
        return None
    model = CatBoostRegressor()
    model.load_model(MODEL_PATH)
    return model


def _estimate_active_buses(direction: DirectionRecord) -> int:
    total_stops = len(direction.platforms)
    if total_stops >= 50:
        return 6
    if total_stops >= 35:
        return 5
    if total_stops >= 20:
        return 4
    return 3


def _estimate_interval_minutes(direction: DirectionRecord) -> int:
    total_stops = len(direction.platforms)
    active_buses = _estimate_active_buses(direction)
    base = max(5, 30 - active_buses * 3 - total_stops // 10)
    return max(4, base)


def _stop_progress_ratio(platform: PlatformRecord, direction: DirectionRecord) -> float:
    total_stops = len(direction.platforms)
    return platform.stop_index / max(total_stops - 1, 1)


def _shifted_time(day_of_week: str, time_value: str, minute_offset: int) -> tuple[str, int, int]:
    hour = _parse_hour(time_value)
    minute = _parse_minute(time_value)
    total_minutes = hour * 60 + minute + minute_offset
    day_shift, normalized_minutes = divmod(total_minutes, 24 * 60)
    weekday = _shifted_weekday(day_of_week, day_shift)
    shifted_hour, shifted_minute = divmod(normalized_minutes, 60)
    return weekday, shifted_hour, shifted_minute


def _shifted_weekday(day_of_week: str, day_shift: int) -> str:
    try:
        index = WEEKDAY_ORDER.index(day_of_week)
    except ValueError:
        index = 0
    return WEEKDAY_ORDER[(index + day_shift) % len(WEEKDAY_ORDER)]


def _base_percent(
    route: RouteRecord,
    direction: DirectionRecord,
    platform: PlatformRecord,
    payload: PredictRequest,
) -> int:
    hour = _parse_hour(payload.time_value)
    route_factor = min(20, route.platform_count // 6 + route.direction_count * 2)
    path_factor = min(10, direction.path_points_count // 120)
    stop_factor = _stop_load_factor(platform.stop_index, len(direction.platforms), hour)
    weather_factor = {"clear": 0, "rain": 8, "snow": 12}.get(payload.weather.lower(), 2)
    peak_factor = 16 if hour in {7, 8, 9, 17, 18, 19} else (8 if hour in {12, 13, 14} else 2)
    weekend_factor = -8 if payload.day_of_week.lower() in {"sat", "sun"} else 0
    direction_factor = 3 if direction.direction_type.lower() == "forward" else 1
    return _clamp(18 + route_factor + path_factor + stop_factor + weather_factor + peak_factor + weekend_factor + direction_factor)


def _future_delta(payload: PredictRequest, platform: PlatformRecord, minutes: int) -> int:
    hour = _parse_hour(payload.time_value)
    near_terminal = platform.stop_index < 3
    if minutes == 10:
        return 4 if hour in {7, 8, 17, 18} else 2
    if minutes == 20:
        return 8 if not near_terminal else 5
    if minutes == 30:
        return 10 if payload.weather.lower() != "clear" else 6
    return -6 if hour not in {7, 8, 17, 18, 19} else 3


def _stop_load_factor(stop_index: int, total_stops: int, hour: int) -> int:
    if total_stops <= 1:
        return 8
    progress = stop_index / max(total_stops - 1, 1)
    if hour in {7, 8, 9}:
        if progress < 0.2:
            return 18
        if progress < 0.55:
            return 12
        return 5
    if hour in {17, 18, 19}:
        if progress > 0.75:
            return 16
        if progress > 0.45:
            return 11
        return 6
    if 11 <= hour <= 15:
        if 0.25 <= progress <= 0.75:
            return 12
        return 6
    return 7


def _direction_label(route: RouteRecord, direction: DirectionRecord) -> str:
    kind = direction.direction_type.lower()
    if kind == "forward" and route.to_name:
        return f"To {route.to_name}"
    if kind == "backward" and route.from_name:
        return f"To {route.from_name}"
    return f"{route.from_name} - {route.to_name}".strip(" -")


def _seat_status(percent: int) -> str:
    if percent >= 70:
        return "none"
    if percent >= 45:
        return "few"
    return "available"


def _parse_hour(time_value: str) -> int:
    try:
        return int(time_value.split(":", 1)[0])
    except (ValueError, IndexError):
        return 12


def _parse_minute(time_value: str) -> int:
    try:
        return int(time_value.split(":", 1)[1])
    except (ValueError, IndexError):
        return 0


def _clamp(value: int) -> int:
    return max(5, min(95, value))


def _payload_value(payload: PredictRequest | dict, name: str):
    return getattr(payload, name)
