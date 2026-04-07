from pydantic import BaseModel, Field


class PredictMockRequest(BaseModel):
    route_id: str = Field(..., examples=["9430017310195742"])
    route_number: str = Field(..., examples=["118"])
    direction_id: str = Field(..., examples=["forward"])
    stop_id: str = Field(..., examples=["9430197698822523"])
    day_of_week: str = Field(..., examples=["Mon"])
    time_value: str = Field(..., examples=["08:30"])
    weather: str = Field(default="clear", examples=["clear"])


class ForecastPoint(BaseModel):
    minutes: int
    occupancy_percent: int
    seat_status: str


class PredictMockResponse(BaseModel):
    route_id: str
    route_number: str
    stop_id: str
    occupancy_percent: int
    seat_status: str
    forecast: list[ForecastPoint]


class PredictRequest(BaseModel):
    route_id: str = Field(..., examples=["9430017310195742"])
    stop_id: str = Field(..., examples=["9430197698822523"])
    direction_id: str | None = Field(default=None, examples=["14098291669506045"])
    day_of_week: str = Field(..., examples=["Mon"])
    time_value: str = Field(..., examples=["08:30"])
    weather: str = Field(default="clear", examples=["clear"])


class PredictResponse(BaseModel):
    route_id: str
    route_number: str
    direction_id: str
    direction_label: str
    stop_id: str
    stop_name: str
    stop_index: int
    total_stops: int
    occupancy_percent: int
    seat_status: str
    forecast: list[ForecastPoint]
