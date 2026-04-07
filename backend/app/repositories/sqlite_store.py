from __future__ import annotations

import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path


class SQLiteStoreError(RuntimeError):
    pass


def project_root() -> Path:
    return Path(__file__).resolve().parents[3]


def backend_root() -> Path:
    return Path(__file__).resolve().parents[2]


DB_PATH = backend_root() / "data" / "transport.sqlite3"


def _resolve_data_file(file_name: str) -> Path:
    candidates = [
        backend_root() / "data" / file_name,
        project_root() / "data" / "exports" / file_name,
        project_root() / "app" / "src" / "main" / "assets" / file_name,
        project_root() / file_name,
    ]
    for candidate in candidates:
        if candidate.exists() and candidate.is_file():
            return candidate
    raise SQLiteStoreError(f"Data file not found: {file_name}")


def connect() -> sqlite3.Connection:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(DB_PATH)
    connection.row_factory = sqlite3.Row
    return connection


def ensure_sqlite_ready(force_refresh: bool = False) -> None:
    with connect() as connection:
        _create_schema(connection)
        routes_count = connection.execute("SELECT COUNT(*) FROM routes").fetchone()[0]
        stops_count = connection.execute("SELECT COUNT(*) FROM stops").fetchone()[0]
        if force_refresh or routes_count == 0 or stops_count == 0:
            _populate_from_json(connection)


def _create_schema(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        PRAGMA foreign_keys = ON;

        CREATE TABLE IF NOT EXISTS routes (
            route_id TEXT PRIMARY KEY,
            number TEXT NOT NULL,
            from_name TEXT NOT NULL,
            to_name TEXT NOT NULL,
            description TEXT NOT NULL,
            direction_count INTEGER NOT NULL,
            platform_count INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS directions (
            direction_id TEXT PRIMARY KEY,
            route_id TEXT NOT NULL,
            direction_type TEXT NOT NULL,
            path_points_count INTEGER NOT NULL,
            direction_order INTEGER NOT NULL,
            FOREIGN KEY(route_id) REFERENCES routes(route_id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS platforms (
            direction_id TEXT NOT NULL,
            platform_id TEXT NOT NULL,
            route_id TEXT NOT NULL,
            station_id TEXT NOT NULL,
            name TEXT NOT NULL,
            lon REAL NOT NULL,
            lat REAL NOT NULL,
            stop_index INTEGER NOT NULL,
            PRIMARY KEY(direction_id, platform_id, stop_index),
            FOREIGN KEY(direction_id) REFERENCES directions(direction_id) ON DELETE CASCADE,
            FOREIGN KEY(route_id) REFERENCES routes(route_id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS stops (
            stop_id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            lon REAL NOT NULL,
            lat REAL NOT NULL
        );

        CREATE TABLE IF NOT EXISTS stop_routes (
            stop_id TEXT NOT NULL,
            route_number TEXT NOT NULL,
            PRIMARY KEY(stop_id, route_number),
            FOREIGN KEY(stop_id) REFERENCES stops(stop_id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS prediction_requests (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            created_at TEXT NOT NULL,
            route_id TEXT NOT NULL,
            direction_id TEXT,
            stop_id TEXT NOT NULL,
            day_of_week TEXT NOT NULL,
            time_value TEXT NOT NULL,
            weather TEXT NOT NULL,
            request_json TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS prediction_results (
            request_id INTEGER PRIMARY KEY,
            created_at TEXT NOT NULL,
            occupancy_percent INTEGER NOT NULL,
            seat_status TEXT NOT NULL,
            forecast_json TEXT NOT NULL,
            response_json TEXT NOT NULL,
            FOREIGN KEY(request_id) REFERENCES prediction_requests(id) ON DELETE CASCADE
        );

        CREATE TABLE IF NOT EXISTS analytics_snapshots (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            created_at TEXT NOT NULL,
            route_id TEXT NOT NULL,
            weather TEXT NOT NULL,
            response_json TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS model_registry (
            model_key TEXT PRIMARY KEY,
            version TEXT NOT NULL,
            file_path TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            metadata_json TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS dataset_registry (
            dataset_key TEXT PRIMARY KEY,
            version TEXT NOT NULL,
            file_path TEXT NOT NULL,
            row_count INTEGER NOT NULL,
            updated_at TEXT NOT NULL,
            metadata_json TEXT NOT NULL
        );
        """
    )


def _populate_from_json(connection: sqlite3.Connection) -> None:
    routes_path = _resolve_data_file("routes_2gis.json")
    stops_path = _resolve_data_file("stops_2gis.json")

    with routes_path.open("r", encoding="utf-8") as handle:
        routes_raw = json.load(handle)

    with stops_path.open("r", encoding="utf-8") as handle:
        stops_raw = json.load(handle)

    connection.execute("DELETE FROM stop_routes")
    connection.execute("DELETE FROM stops")
    connection.execute("DELETE FROM platforms")
    connection.execute("DELETE FROM directions")
    connection.execute("DELETE FROM routes")

    route_rows: list[tuple] = []
    direction_rows: list[tuple] = []
    platform_rows: list[tuple] = []

    for route_item in routes_raw:
        directions_raw = route_item.get("directions", [])
        valid_directions = 0
        total_platforms = 0

        for direction_index, direction_item in enumerate(directions_raw):
            platforms_raw = direction_item.get("platforms", [])
            valid_platforms = []
            for stop_index, platform in enumerate(platforms_raw):
                if platform.get("name") is None:
                    continue
                valid_platforms.append(
                    (
                        str(direction_item.get("direction_id", "")),
                        str(platform.get("platform_id", "")),
                        str(route_item.get("route_id", "")),
                        str(platform.get("station_id", "")),
                        str(platform.get("name", "")),
                        float(platform.get("lon")),
                        float(platform.get("lat")),
                        stop_index,
                    )
                )

            if not valid_platforms:
                continue

            valid_directions += 1
            total_platforms += len(valid_platforms)
            direction_rows.append(
                (
                    str(direction_item.get("direction_id", "")),
                    str(route_item.get("route_id", "")),
                    str(direction_item.get("direction_type", "")),
                    len(direction_item.get("path_points", [])),
                    direction_index,
                )
            )
            platform_rows.extend(valid_platforms)

        if valid_directions == 0:
            continue

        route_rows.append(
            (
                str(route_item.get("route_id", "")),
                str(route_item.get("number", "")),
                str(route_item.get("from_name", "")),
                str(route_item.get("to_name", "")),
                str(route_item.get("description", "")),
                int(route_item.get("direction_count", valid_directions)),
                int(route_item.get("platform_count", total_platforms)),
            )
        )

    stop_rows_by_id: dict[str, tuple] = {}
    stop_route_rows: set[tuple[str, str]] = set()
    for stop_item in stops_raw:
        if stop_item.get("stop_id") is None or stop_item.get("name") is None:
            continue
        stop_id = str(stop_item.get("stop_id", ""))
        stop_rows_by_id[stop_id] = (
            stop_id,
            str(stop_item.get("name", "")),
            float(stop_item.get("lon")),
            float(stop_item.get("lat")),
        )
        for route_number in stop_item.get("route_numbers", []):
            stop_route_rows.add((stop_id, str(route_number)))

    connection.executemany(
        """
        INSERT INTO routes (
            route_id, number, from_name, to_name, description, direction_count, platform_count
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        route_rows,
    )
    connection.executemany(
        """
        INSERT INTO directions (
            direction_id, route_id, direction_type, path_points_count, direction_order
        ) VALUES (?, ?, ?, ?, ?)
        """,
        direction_rows,
    )
    connection.executemany(
        """
        INSERT INTO platforms (
            direction_id, platform_id, route_id, station_id, name, lon, lat, stop_index
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        platform_rows,
    )
    connection.executemany(
        "INSERT INTO stops (stop_id, name, lon, lat) VALUES (?, ?, ?, ?)",
        list(stop_rows_by_id.values()),
    )
    connection.executemany(
        "INSERT INTO stop_routes (stop_id, route_number) VALUES (?, ?)",
        sorted(stop_route_rows),
    )
    connection.commit()


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def upsert_model_registry(model_key: str, version: str, file_path: Path | str, metadata: dict) -> None:
    ensure_sqlite_ready()
    with connect() as connection:
        connection.execute(
            """
            INSERT INTO model_registry (model_key, version, file_path, updated_at, metadata_json)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(model_key) DO UPDATE SET
                version=excluded.version,
                file_path=excluded.file_path,
                updated_at=excluded.updated_at,
                metadata_json=excluded.metadata_json
            """,
            (model_key, version, str(file_path), _now_iso(), json.dumps(metadata, ensure_ascii=False)),
        )
        connection.commit()


def upsert_dataset_registry(
    dataset_key: str,
    version: str,
    file_path: Path | str,
    row_count: int,
    metadata: dict,
) -> None:
    ensure_sqlite_ready()
    with connect() as connection:
        connection.execute(
            """
            INSERT INTO dataset_registry (dataset_key, version, file_path, row_count, updated_at, metadata_json)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(dataset_key) DO UPDATE SET
                version=excluded.version,
                file_path=excluded.file_path,
                row_count=excluded.row_count,
                updated_at=excluded.updated_at,
                metadata_json=excluded.metadata_json
            """,
            (
                dataset_key,
                version,
                str(file_path),
                row_count,
                _now_iso(),
                json.dumps(metadata, ensure_ascii=False),
            ),
        )
        connection.commit()


def log_prediction(payload: dict, response: dict) -> int:
    ensure_sqlite_ready()
    with connect() as connection:
        cursor = connection.execute(
            """
            INSERT INTO prediction_requests (
                created_at, route_id, direction_id, stop_id, day_of_week, time_value, weather, request_json
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                _now_iso(),
                payload.get("route_id", ""),
                payload.get("direction_id"),
                payload.get("stop_id", ""),
                payload.get("day_of_week", ""),
                payload.get("time_value", ""),
                payload.get("weather", "clear"),
                json.dumps(payload, ensure_ascii=False),
            ),
        )
        request_id = int(cursor.lastrowid)
        connection.execute(
            """
            INSERT INTO prediction_results (
                request_id, created_at, occupancy_percent, seat_status, forecast_json, response_json
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                request_id,
                _now_iso(),
                int(response.get("occupancy_percent", 0)),
                str(response.get("seat_status", "")),
                json.dumps(response.get("forecast", []), ensure_ascii=False),
                json.dumps(response, ensure_ascii=False),
            ),
        )
        connection.commit()
        return request_id


def log_analytics_snapshot(route_id: str, weather: str, response: dict) -> int:
    ensure_sqlite_ready()
    with connect() as connection:
        cursor = connection.execute(
            """
            INSERT INTO analytics_snapshots (created_at, route_id, weather, response_json)
            VALUES (?, ?, ?, ?)
            """,
            (
                _now_iso(),
                route_id,
                weather,
                json.dumps(response, ensure_ascii=False),
            ),
        )
        connection.commit()
        return int(cursor.lastrowid)
