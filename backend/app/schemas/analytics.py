from pydantic import BaseModel


class AnalyticsSummaryItem(BaseModel):
    title: str
    time_range: str
    status: str
    highlighted: bool = False


class AnalyticsHourItem(BaseModel):
    hour: int
    status: str


class AnalyticsStopItem(BaseModel):
    name: str
    status: str
    percent: int


class AnalyticsDayItem(BaseModel):
    label: str
    status: str
    percent: int


class AnalyticsDirectionItem(BaseModel):
    title: str
    stop_count: int
    path_point_count: int
    status: str
    percent: int


class AnalyticsResponse(BaseModel):
    route_id: str
    route_number: str
    summary: list[AnalyticsSummaryItem]
    hours: list[AnalyticsHourItem]
    busy_stops: list[AnalyticsStopItem]
    weekday_stats: list[AnalyticsDayItem]
    direction_stats: list[AnalyticsDirectionItem]
