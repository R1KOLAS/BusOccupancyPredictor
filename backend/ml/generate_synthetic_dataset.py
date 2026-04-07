from __future__ import annotations

import argparse
import csv
import random
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path
from typing import Iterable

PROJECT_ROOT = Path(__file__).resolve().parents[2]
BACKEND_ROOT = Path(__file__).resolve().parents[1]

if str(BACKEND_ROOT) not in __import__("sys").path:
    __import__("sys").path.insert(0, str(BACKEND_ROOT))

from app.repositories.transport_data import list_routes  # noqa: E402


WEEKDAY_LABELS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
WEATHER_OPTIONS = ["clear", "rain", "snow"]


@dataclass(frozen=True)
class SyntheticRow:
    sample_date: str
    weekday: str
    is_weekend: int
    hour: int
    minute: int
    route_id: str
    route_number: str
    direction_id: str
    direction_type: str
    stop_id: str
    stop_name: str
    stop_index: int
    total_stops: int
    stop_progress_ratio: float
    is_terminal_stop: int
    weather: str
    interval_minutes: int
    active_buses_count: int
    direction_platform_count: int
    direction_path_points_count: int
    occupancy_percent: int


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate synthetic bus occupancy dataset.")
    parser.add_argument(
        "--output",
        type=Path,
        default=BACKEND_ROOT / "data" / "synthetic_occupancy_dataset.csv",
        help="Output CSV path.",
    )
    parser.add_argument(
        "--rows-per-direction",
        type=int,
        default=360,
        help="How many synthetic rows to generate for each route direction.",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    rng = random.Random(args.seed)
    routes = list_routes()

    args.output.parent.mkdir(parents=True, exist_ok=True)

    fieldnames = list(SyntheticRow.__annotations__.keys())
    row_count = 0

    with args.output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()

        for route in routes:
            for direction in route.directions:
                platforms = direction.platforms
                if not platforms:
                    continue

                for _ in range(args.rows_per_direction):
                    sample_date = random_date(rng)
                    weekday = WEEKDAY_LABELS[sample_date.weekday()]
                    hour, minute = sample_service_time(rng, weekday)
                    platform = rng.choice(platforms)
                    total_stops = len(platforms)
                    stop_progress = platform.stop_index / max(total_stops - 1, 1)
                    weather = weighted_choice(rng, WEATHER_OPTIONS, (0.72, 0.18, 0.10))
                    active_buses_count = estimate_active_buses(total_stops)
                    interval_minutes = estimate_interval_minutes(active_buses_count, total_stops, rng)
                    occupancy_percent = synthesize_occupancy(
                        rng=rng,
                        route_number=route.number,
                        direction_type=direction.direction_type,
                        weekday=weekday,
                        hour=hour,
                        stop_progress_ratio=stop_progress,
                        weather=weather,
                        total_stops=total_stops,
                        active_buses_count=active_buses_count,
                        interval_minutes=interval_minutes,
                    )

                    row = SyntheticRow(
                        sample_date=sample_date.isoformat(),
                        weekday=weekday,
                        is_weekend=1 if weekday in {"Sat", "Sun"} else 0,
                        hour=hour,
                        minute=minute,
                        route_id=route.route_id,
                        route_number=route.number,
                        direction_id=direction.direction_id,
                        direction_type=direction.direction_type,
                        stop_id=platform.station_id or platform.platform_id,
                        stop_name=platform.name,
                        stop_index=platform.stop_index,
                        total_stops=total_stops,
                        stop_progress_ratio=round(stop_progress, 4),
                        is_terminal_stop=1 if platform.stop_index in {0, total_stops - 1} else 0,
                        weather=weather,
                        interval_minutes=interval_minutes,
                        active_buses_count=active_buses_count,
                        direction_platform_count=total_stops,
                        direction_path_points_count=direction.path_points_count,
                        occupancy_percent=occupancy_percent,
                    )
                    writer.writerow(row.__dict__)
                    row_count += 1

    print(f"Generated dataset: {args.output}")
    print(f"Rows: {row_count}")


def sample_service_time(rng: random.Random, weekday: str) -> tuple[int, int]:
    hours = list(range(6, 24))
    if weekday in {"Sat", "Sun"}:
        weights = [
            0.8, 1.0, 1.2, 1.2, 1.0, 0.9,
            1.0, 1.1, 1.1, 1.0, 1.1, 1.2,
            1.4, 1.3, 1.2, 1.0, 0.8, 0.5,
        ]
    else:
        weights = [
            1.2, 2.2, 2.6, 2.3, 1.2, 0.9,
            0.9, 1.0, 1.1, 1.0, 1.2, 1.4,
            1.8, 2.2, 1.8, 1.2, 0.8, 0.5,
        ]
    hour = weighted_choice(rng, hours, weights)
    minute = weighted_choice(rng, (0, 15, 30, 45), (0.35, 0.2, 0.3, 0.15))
    return int(hour), int(minute)


def random_date(rng: random.Random) -> date:
    start = date(2025, 1, 1)
    offset = rng.randint(0, 179)
    return start + timedelta(days=offset)


def weighted_choice(rng: random.Random, values: Iterable[str], weights: Iterable[float]) -> str:
    values_list = list(values)
    weights_list = list(weights)
    return rng.choices(values_list, weights=weights_list, k=1)[0]


def estimate_active_buses(total_stops: int) -> int:
    if total_stops >= 50:
        return 6
    if total_stops >= 35:
        return 5
    if total_stops >= 20:
        return 4
    return 3


def estimate_interval_minutes(active_buses_count: int, total_stops: int, rng: random.Random) -> int:
    base = max(5, 30 - active_buses_count * 3 - total_stops // 10)
    jitter = rng.randint(-2, 3)
    return max(4, base + jitter)


def synthesize_occupancy(
    rng: random.Random,
    route_number: str,
    direction_type: str,
    weekday: str,
    hour: int,
    stop_progress_ratio: float,
    weather: str,
    total_stops: int,
    active_buses_count: int,
    interval_minutes: int,
) -> int:
    route_profile = build_route_profile(route_number, total_stops)
    route_factor = route_profile["base_load"]
    peak_factor = peak_load(
        weekday=weekday,
        hour=hour,
        stop_progress_ratio=stop_progress_ratio,
        morning_peak_bias=route_profile["morning_peak_bias"],
        evening_peak_bias=route_profile["evening_peak_bias"],
        midday_bias=route_profile["midday_bias"],
    )
    weather_factor = weather_load(
        weather=weather,
        weather_sensitivity=route_profile["weather_sensitivity"],
    )
    weekend_factor = -18 if weekday in {"Sat", "Sun"} else 0
    direction_factor = 3 if direction_type.lower() == "forward" else 1
    fleet_factor = max(-14, min(10, 14 - active_buses_count * 2 + interval_minutes // 2))
    random_noise = rng.randint(-8, 8)

    occupancy = 16 + route_factor + peak_factor + weather_factor + weekend_factor + direction_factor + fleet_factor + random_noise
    return max(5, min(95, occupancy))


def build_route_profile(route_number: str, total_stops: int) -> dict[str, int]:
    digit_sum = sum(int(ch) for ch in route_number if ch.isdigit())
    ascii_sum = sum(ord(ch) for ch in route_number)
    return {
        "base_load": 8 + (ascii_sum % 12) + min(12, total_stops // 7),
        "weather_sensitivity": 8 + (digit_sum % 9),
        "morning_peak_bias": 16 + (ascii_sum % 12),
        "evening_peak_bias": 14 + ((ascii_sum // 3) % 12),
        "midday_bias": 1 + (digit_sum % 3),
    }


def weather_load(weather: str, weather_sensitivity: int) -> int:
    if weather == "rain":
        return weather_sensitivity
    if weather == "snow":
        return weather_sensitivity + 5
    return 0


def peak_load(
    weekday: str,
    hour: int,
    stop_progress_ratio: float,
    morning_peak_bias: int,
    evening_peak_bias: int,
    midday_bias: int,
) -> int:
    is_weekend = weekday in {"Sat", "Sun"}
    weekend_scale = 0.22 if is_weekend else 1.0

    if hour == 6:
        return 10 if not is_weekend else 4
    if hour in {7, 8, 9}:
        if stop_progress_ratio < 0.25:
            return round((20 + morning_peak_bias) * weekend_scale)
        if stop_progress_ratio < 0.65:
            return round((12 + morning_peak_bias) * weekend_scale)
        return round((7 + morning_peak_bias // 2) * weekend_scale)
    if hour == 10:
        return 1 if not is_weekend else 0
    if hour == 11:
        return 0
    if hour in {17, 18}:
        if stop_progress_ratio > 0.7:
            return round((18 + evening_peak_bias) * weekend_scale)
        if stop_progress_ratio > 0.35:
            return round((10 + evening_peak_bias) * weekend_scale)
        return round((6 + evening_peak_bias // 2) * weekend_scale)
    if hour == 19:
        return round((4 + evening_peak_bias // 2) * weekend_scale)
    if hour in {12, 13, 14}:
        return 1 + midday_bias if 0.2 <= stop_progress_ratio <= 0.8 else 0
    if hour in {15, 16}:
        return 3 if not is_weekend else 1
    if hour in {20, 21}:
        return 1
    if hour in {22, 23}:
        return 0
    return 1


if __name__ == "__main__":
    main()
