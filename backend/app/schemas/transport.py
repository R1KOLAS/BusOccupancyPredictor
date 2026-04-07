from pydantic import BaseModel


class RouteSummaryResponse(BaseModel):
    route_id: str
    number: str
    from_name: str
    to_name: str
    direction_count: int
    platform_count: int


class RouteStopResponse(BaseModel):
    stop_id: str
    platform_id: str
    name: str
    lon: float
    lat: float
    stop_index: int


class RouteDirectionStopsResponse(BaseModel):
    direction_id: str
    direction_type: str
    stops: list[RouteStopResponse]


class RouteStopsResponse(BaseModel):
    route_id: str
    route_number: str
    directions: list[RouteDirectionStopsResponse]
