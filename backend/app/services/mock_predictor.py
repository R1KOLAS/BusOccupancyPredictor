from app.schemas.predict import ForecastPoint, PredictMockRequest, PredictMockResponse


def build_mock_prediction(payload: PredictMockRequest) -> PredictMockResponse:
    base = _base_percent(payload)
    points = []
    for minutes, delta in ((10, 4), (20, 9), (30, 13), (60, -8)):
        percent = _clamp(base + delta)
        points.append(
            ForecastPoint(
                minutes=minutes,
                occupancy_percent=percent,
                seat_status=_seat_status(percent),
            )
        )

    return PredictMockResponse(
        route_id=payload.route_id,
        route_number=payload.route_number,
        stop_id=payload.stop_id,
        occupancy_percent=base,
        seat_status=_seat_status(base),
        forecast=points,
    )


def _base_percent(payload: PredictMockRequest) -> int:
    hour = _parse_hour(payload.time_value)
    route_factor = sum(ord(ch) for ch in payload.route_number) % 18
    stop_factor = sum(ord(ch) for ch in payload.stop_id[-4:]) % 12
    weather_factor = {"clear": 0, "rain": 8, "snow": 12}.get(payload.weather.lower(), 3)
    peak_factor = 18 if hour in {7, 8, 9, 17, 18, 19} else 4
    weekend_factor = -8 if payload.day_of_week.lower() in {"sat", "sun"} else 0
    return _clamp(22 + route_factor + stop_factor + weather_factor + peak_factor + weekend_factor)


def _parse_hour(time_value: str) -> int:
    try:
        return int(time_value.split(":", 1)[0])
    except (ValueError, IndexError):
        return 12


def _seat_status(percent: int) -> str:
    if percent >= 70:
        return "none"
    if percent >= 45:
        return "few"
    return "available"


def _clamp(value: int) -> int:
    return max(5, min(95, value))
