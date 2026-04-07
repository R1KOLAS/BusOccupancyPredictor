from __future__ import annotations

from collections import defaultdict

from app.repositories.transport_data import PlatformRecord, get_route
from app.schemas.analytics import (
    AnalyticsDayItem,
    AnalyticsDirectionItem,
    AnalyticsHourItem,
    AnalyticsResponse,
    AnalyticsStopItem,
    AnalyticsSummaryItem,
)
from app.services.real_predictor import predict_percent_value


def build_route_analytics(route_id: str, weather: str = "clear") -> AnalyticsResponse:
    route = get_route(route_id)
    if route is None:
        raise ValueError(f"Route not found: {route_id}")

    hours = _build_hour_items(route, weather)
    summary = _build_summary(hours)
    busy_stops = _build_busy_stops(route, weather)
    weekday_stats = _build_weekday_stats(route, weather)
    direction_stats = _build_direction_stats(route, weather)

    return AnalyticsResponse(
        route_id=route.route_id,
        route_number=route.number,
        summary=summary,
        hours=hours,
        busy_stops=busy_stops,
        weekday_stats=weekday_stats,
        direction_stats=direction_stats,
    )


def _build_hour_items(route, weather: str) -> list[AnalyticsHourItem]:
    items = []
    for hour in (8, 10, 12, 14, 16, 18, 20, 22):
        percent = _route_percent(route, day_of_week="Mon", time_value=f"{hour:02d}:00", weather=weather)
        items.append(AnalyticsHourItem(hour=hour, status=_status_for_percent(percent)))
    return items


def _build_summary(hours: list[AnalyticsHourItem]) -> list[AnalyticsSummaryItem]:
    groups = [
        ("Утро", "07-11", [item for item in hours if item.hour in {8, 10}], False),
        ("День", "12-16", [item for item in hours if item.hour in {12, 14, 16}], True),
        ("Вечер", "17-21", [item for item in hours if item.hour in {18, 20}], False),
    ]
    return [
        AnalyticsSummaryItem(
            title=title,
            time_range=time_range,
            status=_aggregate_status([item.status for item in items]),
            highlighted=highlighted,
        )
        for title, time_range, items, highlighted in groups
    ]


def _build_busy_stops(route, weather: str) -> list[AnalyticsStopItem]:
    stop_scores: dict[str, list[int]] = defaultdict(list)

    for direction in route.directions:
        for platform in _sample_platforms(direction.platforms):
            percent = _average(
                [
                    predict_percent_value(route, direction, platform, "Mon", "08:00", weather),
                    predict_percent_value(route, direction, platform, "Mon", "12:00", weather),
                    predict_percent_value(route, direction, platform, "Mon", "18:00", weather),
                ]
            )
            stop_scores[platform.name].append(percent)

    aggregated = [
        AnalyticsStopItem(
            name=name,
            percent=round(_average(values)),
            status=_status_for_percent(round(_average(values))),
        )
        for name, values in stop_scores.items()
    ]
    return sorted(aggregated, key=lambda item: item.percent, reverse=True)[:10]


def _build_weekday_stats(route, weather: str) -> list[AnalyticsDayItem]:
    day_map = [
        ("Пн", "Mon"),
        ("Вт", "Tue"),
        ("Ср", "Wed"),
        ("Чт", "Thu"),
        ("Пт", "Fri"),
        ("Сб", "Sat"),
        ("Вс", "Sun"),
    ]
    result = []
    for label, weekday in day_map:
        percent = _average(
            [
                _route_percent(route, weekday, "08:00", weather),
                _route_percent(route, weekday, "13:00", weather),
                _route_percent(route, weekday, "18:00", weather),
            ]
        )
        rounded = round(percent)
        result.append(
            AnalyticsDayItem(
                label=label,
                percent=rounded,
                status=_status_for_percent(rounded),
            )
        )
    return result


def _build_direction_stats(route, weather: str) -> list[AnalyticsDirectionItem]:
    items = []
    for direction in route.directions:
        percent = _average(
            [
                _direction_percent(route, direction, "Mon", "08:00", weather),
                _direction_percent(route, direction, "Mon", "13:00", weather),
                _direction_percent(route, direction, "Mon", "18:00", weather),
            ]
        )
        rounded = round(percent)
        items.append(
            AnalyticsDirectionItem(
                title=_direction_label(route.from_name, route.to_name, direction.direction_type),
                stop_count=len(direction.platforms),
                path_point_count=direction.path_points_count,
                percent=rounded,
                status=_status_for_percent(rounded),
            )
        )
    return items


def _route_percent(route, day_of_week: str, time_value: str, weather: str) -> float:
    values = [
        _direction_percent(route, direction, day_of_week, time_value, weather)
        for direction in route.directions
    ]
    return _average(values)


def _direction_percent(route, direction, day_of_week: str, time_value: str, weather: str) -> float:
    values = [
        predict_percent_value(route, direction, platform, day_of_week, time_value, weather)
        for platform in _sample_platforms(direction.platforms)
    ]
    return _average(values)


def _sample_platforms(platforms: list[PlatformRecord]) -> list[PlatformRecord]:
    if len(platforms) <= 6:
        return platforms

    sampled_indices = {0, len(platforms) - 1}
    step = max(1, len(platforms) // 5)
    sampled_indices.update(range(0, len(platforms), step))
    return [platforms[index] for index in sorted(index for index in sampled_indices if index < len(platforms))]


def _average(values: list[int | float]) -> float:
    if not values:
        return 0.0
    return sum(values) / len(values)


def _aggregate_status(statuses: list[str]) -> str:
    if not statuses:
        return "available"
    score = sum({"available": 1, "few": 2, "none": 3}.get(status, 2) for status in statuses) / len(statuses)
    return "none" if score >= 2.5 else "few" if score >= 1.6 else "available"


def _status_for_percent(percent: int) -> str:
    if percent >= 70:
        return "none"
    if percent >= 45:
        return "few"
    return "available"


def _direction_label(from_name: str, to_name: str, direction_type: str) -> str:
    normalized = direction_type.lower()
    if normalized == "forward" and to_name:
        return f"В сторону {to_name}"
    if normalized == "backward" and from_name:
        return f"В сторону {from_name}"
    return "Маршрут"
