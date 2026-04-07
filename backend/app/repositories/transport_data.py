from __future__ import annotations

from dataclasses import dataclass
from functools import lru_cache

from app.repositories.sqlite_store import connect, ensure_sqlite_ready


@dataclass(frozen=True)
class PlatformRecord:
    platform_id: str
    station_id: str
    name: str
    lon: float
    lat: float
    stop_index: int


@dataclass(frozen=True)
class DirectionRecord:
    direction_id: str
    direction_type: str
    path_points_count: int
    platforms: list[PlatformRecord]


@dataclass(frozen=True)
class RouteRecord:
    route_id: str
    number: str
    from_name: str
    to_name: str
    description: str
    direction_count: int
    platform_count: int
    directions: list[DirectionRecord]


@dataclass(frozen=True)
class StopRecord:
    stop_id: str
    name: str
    lon: float
    lat: float
    route_numbers: list[str]


class TransportDataError(RuntimeError):
    pass


def initialize_transport_data(force_refresh: bool = False) -> None:
    ensure_sqlite_ready(force_refresh=force_refresh)
    load_routes.cache_clear()
    load_stops.cache_clear()


@lru_cache(maxsize=1)
def load_routes() -> list[RouteRecord]:
    ensure_sqlite_ready()
    with connect() as connection:
        route_rows = connection.execute(
            """
            SELECT route_id, number, from_name, to_name, description, direction_count, platform_count
            FROM routes
            ORDER BY number
            """
        ).fetchall()

        direction_rows = connection.execute(
            """
            SELECT direction_id, route_id, direction_type, path_points_count, direction_order
            FROM directions
            ORDER BY route_id, direction_order
            """
        ).fetchall()

        platform_rows = connection.execute(
            """
            SELECT direction_id, platform_id, station_id, name, lon, lat, stop_index
            FROM platforms
            ORDER BY direction_id, stop_index
            """
        ).fetchall()

    platforms_by_direction: dict[str, list[PlatformRecord]] = {}
    for row in platform_rows:
        platforms_by_direction.setdefault(row["direction_id"], []).append(
            PlatformRecord(
                platform_id=row["platform_id"],
                station_id=row["station_id"],
                name=row["name"],
                lon=float(row["lon"]),
                lat=float(row["lat"]),
                stop_index=int(row["stop_index"]),
            )
        )

    directions_by_route: dict[str, list[DirectionRecord]] = {}
    for row in direction_rows:
        platforms = platforms_by_direction.get(row["direction_id"], [])
        if not platforms:
            continue
        directions_by_route.setdefault(row["route_id"], []).append(
            DirectionRecord(
                direction_id=row["direction_id"],
                direction_type=row["direction_type"],
                path_points_count=int(row["path_points_count"]),
                platforms=platforms,
            )
        )

    routes: list[RouteRecord] = []
    for row in route_rows:
        directions = directions_by_route.get(row["route_id"], [])
        if not directions:
            continue
        routes.append(
            RouteRecord(
                route_id=row["route_id"],
                number=row["number"],
                from_name=row["from_name"],
                to_name=row["to_name"],
                description=row["description"],
                direction_count=int(row["direction_count"]),
                platform_count=int(row["platform_count"]),
                directions=directions,
            )
        )
    return routes


@lru_cache(maxsize=1)
def load_stops() -> list[StopRecord]:
    ensure_sqlite_ready()
    with connect() as connection:
        stop_rows = connection.execute(
            """
            SELECT s.stop_id, s.name, s.lon, s.lat, sr.route_number
            FROM stops s
            LEFT JOIN stop_routes sr ON sr.stop_id = s.stop_id
            ORDER BY s.name, sr.route_number
            """
        ).fetchall()

    grouped: dict[str, StopRecord] = {}
    route_numbers_by_stop: dict[str, list[str]] = {}

    for row in stop_rows:
        stop_id = row["stop_id"]
        grouped.setdefault(
            stop_id,
            StopRecord(
                stop_id=stop_id,
                name=row["name"],
                lon=float(row["lon"]),
                lat=float(row["lat"]),
                route_numbers=[],
            ),
        )
        route_number = row["route_number"]
        if route_number:
            route_numbers_by_stop.setdefault(stop_id, []).append(str(route_number))

    return [
        StopRecord(
            stop_id=stop.stop_id,
            name=stop.name,
            lon=stop.lon,
            lat=stop.lat,
            route_numbers=sorted(set(route_numbers_by_stop.get(stop.stop_id, []))),
        )
        for stop in grouped.values()
    ]


def list_routes() -> list[RouteRecord]:
    return load_routes()


def get_route(route_id: str) -> RouteRecord | None:
    return next((route for route in load_routes() if route.route_id == route_id), None)


def get_direction(route: RouteRecord, direction_id: str | None) -> DirectionRecord:
    if direction_id:
        matched = next((direction for direction in route.directions if direction.direction_id == direction_id), None)
        if matched is not None:
            return matched
    return route.directions[0]


def get_platform(direction: DirectionRecord, stop_id: str) -> PlatformRecord | None:
    return next(
        (
            platform
            for platform in direction.platforms
            if platform.station_id == stop_id or platform.platform_id == stop_id
        ),
        None,
    )
