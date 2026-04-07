from fastapi import APIRouter, HTTPException

from app.repositories.transport_data import get_route, list_routes
from app.schemas.transport import (
    RouteDirectionStopsResponse,
    RouteStopsResponse,
    RouteStopResponse,
    RouteSummaryResponse,
)


router = APIRouter(tags=["transport"])


@router.get("/routes", response_model=list[RouteSummaryResponse])
def routes() -> list[RouteSummaryResponse]:
    return [
        RouteSummaryResponse(
            route_id=route.route_id,
            number=route.number,
            from_name=route.from_name,
            to_name=route.to_name,
            direction_count=route.direction_count,
            platform_count=route.platform_count,
        )
        for route in list_routes()
    ]


@router.get("/routes/{route_id}/stops", response_model=RouteStopsResponse)
def route_stops(route_id: str) -> RouteStopsResponse:
    route = get_route(route_id)
    if route is None:
        raise HTTPException(status_code=404, detail=f"Route not found: {route_id}")

    return RouteStopsResponse(
        route_id=route.route_id,
        route_number=route.number,
        directions=[
            RouteDirectionStopsResponse(
                direction_id=direction.direction_id,
                direction_type=direction.direction_type,
                stops=[
                    RouteStopResponse(
                        stop_id=platform.station_id or platform.platform_id,
                        platform_id=platform.platform_id,
                        name=platform.name,
                        lon=platform.lon,
                        lat=platform.lat,
                        stop_index=platform.stop_index,
                    )
                    for platform in direction.platforms
                ],
            )
            for direction in route.directions
        ],
    )
