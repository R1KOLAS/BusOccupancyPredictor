#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import time
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any, Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin
from urllib.request import Request, urlopen


BASE_URL = "https://2gis.kz"
DEFAULT_SEARCH_HTML = "bus.html"
DEFAULT_OUT_JSON = "routes_2gis.json"
DEFAULT_OUT_CSV = "routes_2gis.csv"


ROUTE_PATH_RE = re.compile(r"/almaty/route/(\d+)")
SEARCH_PAGE_PATH_RE = re.compile(r'(/almaty/search/[^"]+/page/\d+)')
CANONICAL_RE = re.compile(
    r'<link href="https://2gis\.kz/almaty/route/(\d+)" rel="canonical"',
    re.IGNORECASE,
)
INITIAL_STATE_RE = re.compile(
    r"var initialState = JSON\.parse\('(.+?)'\);\s+var __REACT_QUERY_STATE__",
    re.DOTALL,
)


@dataclass
class RoutePlatformInfo:
    platform_id: str
    station_id: str
    name: str
    lon: float | None
    lat: float | None


@dataclass
class RouteDirectionInfo:
    direction_id: str
    direction_type: str
    centroid_lon: float | None
    centroid_lat: float | None
    path_wkt: str
    path_points: list[list[float]]
    platforms: list[RoutePlatformInfo]


@dataclass
class BusRouteInfo:
    route_id: str
    number: str
    subtype: str
    from_name: str
    to_name: str
    description: str
    url: str
    direction_count: int
    platform_count: int
    directions: list[RouteDirectionInfo]


def clean_text(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()


def cyrillic_score(value: str) -> int:
    return sum(1 for char in value if "\u0400" <= char <= "\u04FF")


def maybe_fix_mojibake(value: str) -> str:
    try:
        fixed = value.encode("latin1").decode("utf-8")
    except (UnicodeEncodeError, UnicodeDecodeError):
        return value
    return fixed if cyrillic_score(fixed) > cyrillic_score(value) else value


def normalize_strings(value: Any) -> Any:
    if isinstance(value, str):
        return maybe_fix_mojibake(value)
    if isinstance(value, list):
        return [normalize_strings(item) for item in value]
    if isinstance(value, dict):
        return {key: normalize_strings(item) for key, item in value.items()}
    return value


def parse_point_wkt(value: str) -> tuple[float | None, float | None]:
    match = re.fullmatch(r"POINT\(\s*([0-9.\-]+)\s+([0-9.\-]+)\s*\)", value.strip())
    if not match:
        return None, None
    return float(match.group(1)), float(match.group(2))


def parse_linestring_wkt(value: str) -> list[list[float]]:
    match = re.fullmatch(r"LINESTRING\((.+)\)", value.strip())
    if not match:
        return []

    points: list[list[float]] = []
    for pair in match.group(1).split(","):
        chunks = pair.strip().split()
        if len(chunks) != 2:
            continue
        lon = float(chunks[0])
        lat = float(chunks[1])
        point = [lon, lat]
        if not points or points[-1] != point:
            points.append(point)
    return points


def extract_route_urls(search_html: str, base_url: str) -> list[str]:
    route_ids: list[str] = []
    seen = set()
    for match in ROUTE_PATH_RE.finditer(search_html):
        route_id = match.group(1)
        if route_id not in seen:
            seen.add(route_id)
            route_ids.append(route_id)
    return [urljoin(base_url, f"/almaty/route/{route_id}") for route_id in route_ids]


def extract_search_page_urls(search_html: str, base_url: str) -> list[str]:
    page_paths: list[str] = []
    seen = set()
    for match in SEARCH_PAGE_PATH_RE.finditer(search_html):
        page_path = match.group(1)
        if page_path not in seen:
            seen.add(page_path)
            page_paths.append(page_path)
    return [urljoin(base_url, page_path) for page_path in page_paths]


def extract_route_id(detail_html: str, fallback_url: str) -> str:
    match = CANONICAL_RE.search(detail_html)
    if match:
        return match.group(1)
    match = ROUTE_PATH_RE.search(fallback_url)
    if match:
        return match.group(1)
    raise ValueError("Route ID was not found in the HTML or URL.")


def extract_initial_state(detail_html: str) -> dict[str, Any]:
    match = INITIAL_STATE_RE.search(detail_html)
    if not match:
        raise ValueError("initialState was not found in the route HTML.")

    decoded = match.group(1).encode("utf-8").decode("unicode_escape")
    try:
        state = json.loads(decoded)
    except json.JSONDecodeError as exc:
        raise ValueError(f"Failed to parse initialState JSON: {exc}") from exc

    return normalize_strings(state)


def find_route_payload(initial_state: dict[str, Any], route_id: str) -> dict[str, Any]:
    profiles = initial_state.get("data", {}).get("entity", {}).get("profile", {})
    if not isinstance(profiles, dict):
        raise ValueError("Route profiles were not found in initialState.")

    if route_id in profiles:
        payload = profiles[route_id].get("data", {})
        if payload.get("type") == "route":
            return payload

    for profile in profiles.values():
        payload = profile.get("data", {})
        if payload.get("type") == "route":
            return payload

    raise ValueError("A route payload was not found in initialState.")


def parse_platform(platform_payload: dict[str, Any]) -> RoutePlatformInfo:
    lon, lat = parse_point_wkt(platform_payload.get("geometry", {}).get("centroid", ""))
    return RoutePlatformInfo(
        platform_id=str(platform_payload.get("id", "")),
        station_id=str(platform_payload.get("station_id", "")),
        name=clean_text(str(platform_payload.get("name", ""))),
        lon=lon,
        lat=lat,
    )


def parse_direction(direction_payload: dict[str, Any]) -> RouteDirectionInfo:
    geometry = direction_payload.get("geometry", {})
    centroid_lon, centroid_lat = parse_point_wkt(str(geometry.get("centroid", "")))
    path_wkt = str(geometry.get("selection", ""))
    path_points = parse_linestring_wkt(path_wkt)
    platforms = [parse_platform(item) for item in direction_payload.get("platforms", [])]

    return RouteDirectionInfo(
        direction_id=str(direction_payload.get("id", "")),
        direction_type=str(direction_payload.get("type", "")),
        centroid_lon=centroid_lon,
        centroid_lat=centroid_lat,
        path_wkt=path_wkt,
        path_points=path_points,
        platforms=platforms,
    )


def parse_route_page(detail_html: str, url: str) -> BusRouteInfo:
    route_id = extract_route_id(detail_html, url)
    initial_state = extract_initial_state(detail_html)
    route_payload = find_route_payload(initial_state, route_id)
    directions = [parse_direction(item) for item in route_payload.get("directions", [])]

    return BusRouteInfo(
        route_id=str(route_payload.get("id", route_id)),
        number=clean_text(str(route_payload.get("name", ""))),
        subtype=clean_text(str(route_payload.get("subtype", ""))),
        from_name=clean_text(str(route_payload.get("from_name", ""))),
        to_name=clean_text(str(route_payload.get("to_name", ""))),
        description=clean_text(str(route_payload.get("description", ""))),
        url=url,
        direction_count=len(directions),
        platform_count=sum(len(direction.platforms) for direction in directions),
        directions=directions,
    )


def fetch_html(url: str, timeout: float) -> str:
    request = Request(
        url,
        headers={
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/124.0 Safari/537.36"
            )
        },
    )
    with urlopen(request, timeout=timeout) as response:
        return response.read().decode("utf-8", errors="replace")


def load_search_html(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def load_detail_html(
    route_url: str,
    detail_dir: Path | None,
    timeout: float,
    use_network: bool,
) -> str:
    route_id_match = ROUTE_PATH_RE.search(route_url)
    route_id = route_id_match.group(1) if route_id_match else None

    if detail_dir and route_id:
        direct_candidates = [
            detail_dir / f"{route_id}.html",
            detail_dir / f"route_{route_id}.html",
        ]
        for candidate in direct_candidates:
            if candidate.exists():
                return candidate.read_text(encoding="utf-8", errors="replace")

        for candidate in detail_dir.glob("*.html"):
            text = candidate.read_text(encoding="utf-8", errors="replace")
            if CANONICAL_RE.search(text) and extract_route_id(text, route_url) == route_id:
                return text

    if not use_network:
        raise FileNotFoundError(
            f"No local HTML was found for {route_url}. "
            "Save the route page in detail-dir or use --use-network."
        )

    return fetch_html(route_url, timeout=timeout)


def write_json(routes: Iterable[BusRouteInfo], output_path: Path) -> None:
    payload = []
    for route in routes:
        item = asdict(route)
        item["directions"] = [
            {
                **asdict(direction),
                "platforms": [asdict(platform) for platform in direction.platforms],
            }
            for direction in route.directions
        ]
        payload.append(item)

    output_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def write_csv(routes: Iterable[BusRouteInfo], output_path: Path) -> None:
    with output_path.open("w", encoding="utf-8", newline="") as csvfile:
        writer = csv.DictWriter(
            csvfile,
            fieldnames=[
                "route_id",
                "number",
                "subtype",
                "from_name",
                "to_name",
                "description",
                "url",
                "direction_count",
                "platform_count",
                "direction_types",
                "platform_names",
                "directions_json",
            ],
        )
        writer.writeheader()

        for route in routes:
            direction_summaries = [
                {
                    "direction_id": direction.direction_id,
                    "direction_type": direction.direction_type,
                    "platform_count": len(direction.platforms),
                    "path_points_count": len(direction.path_points),
                }
                for direction in route.directions
            ]
            platform_names = []
            seen_names = set()
            for direction in route.directions:
                for platform in direction.platforms:
                    if platform.name and platform.name not in seen_names:
                        seen_names.add(platform.name)
                        platform_names.append(platform.name)

            writer.writerow(
                {
                    "route_id": route.route_id,
                    "number": route.number,
                    "subtype": route.subtype,
                    "from_name": route.from_name,
                    "to_name": route.to_name,
                    "description": route.description,
                    "url": route.url,
                    "direction_count": route.direction_count,
                    "platform_count": route.platform_count,
                    "direction_types": ", ".join(
                        sorted({direction.direction_type for direction in route.directions if direction.direction_type})
                    ),
                    "platform_names": " | ".join(platform_names),
                    "directions_json": json.dumps(direction_summaries, ensure_ascii=False),
                }
            )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Extract 2GIS bus routes from a saved search HTML and route detail pages. "
            "Outputs route metadata, directions, path geometry, and route platforms."
        )
    )
    parser.add_argument(
        "--search-html",
        default=DEFAULT_SEARCH_HTML,
        help=f"Saved 2GIS search HTML. Default: {DEFAULT_SEARCH_HTML}",
    )
    parser.add_argument(
        "--detail-dir",
        help=(
            "Directory with saved route detail pages. "
            "The script looks for <route_id>.html, route_<route_id>.html, "
            "or any HTML whose canonical URL matches the route."
        ),
    )
    parser.add_argument(
        "--use-network",
        action="store_true",
        help="Fetch missing route pages from 2GIS.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Limit how many routes to process. 0 means no limit.",
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=0.5,
        help="Delay between network requests in seconds.",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=20.0,
        help="Network timeout in seconds.",
    )
    parser.add_argument(
        "--json-out",
        default=DEFAULT_OUT_JSON,
        help=f"Where to write JSON output. Default: {DEFAULT_OUT_JSON}",
    )
    parser.add_argument(
        "--csv-out",
        default=DEFAULT_OUT_CSV,
        help=f"Where to write CSV output. Default: {DEFAULT_OUT_CSV}",
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    search_html_path = Path(args.search_html)
    if not search_html_path.exists():
        parser.error(f"Search HTML was not found: {search_html_path}")

    detail_dir = Path(args.detail_dir) if args.detail_dir else None
    if detail_dir and not detail_dir.exists():
        parser.error(f"detail-dir was not found: {detail_dir}")

    search_html = load_search_html(search_html_path)
    route_urls = extract_route_urls(search_html, BASE_URL)

    if args.use_network:
        seen_search_pages = set()
        queued_search_pages = extract_search_page_urls(search_html, BASE_URL)

        while queued_search_pages:
            page_url = queued_search_pages.pop(0)
            if page_url in seen_search_pages:
                continue
            seen_search_pages.add(page_url)

            try:
                page_html = fetch_html(page_url, timeout=args.timeout)
                for route_url in extract_route_urls(page_html, BASE_URL):
                    if route_url not in route_urls:
                        route_urls.append(route_url)

                for next_page_url in extract_search_page_urls(page_html, BASE_URL):
                    if next_page_url not in seen_search_pages and next_page_url not in queued_search_pages:
                        queued_search_pages.append(next_page_url)

                time.sleep(max(args.delay, 0))
            except (HTTPError, URLError) as exc:
                print(f"  ! search page failed {page_url}: {exc}", file=sys.stderr)

    if args.limit and args.limit > 0:
        route_urls = route_urls[: args.limit]

    if not route_urls:
        print("No route links were found in the search HTML.", file=sys.stderr)
        return 1

    routes: list[BusRouteInfo] = []
    errors = 0

    for index, route_url in enumerate(route_urls, start=1):
        print(f"[{index}/{len(route_urls)}] {route_url}")
        try:
            detail_html = load_detail_html(
                route_url=route_url,
                detail_dir=detail_dir,
                timeout=args.timeout,
                use_network=args.use_network,
            )
            routes.append(parse_route_page(detail_html, route_url))
            if args.use_network:
                time.sleep(max(args.delay, 0))
        except (FileNotFoundError, ValueError, HTTPError, URLError) as exc:
            errors += 1
            print(f"  ! failed: {exc}", file=sys.stderr)

    if not routes:
        print("No routes were parsed successfully.", file=sys.stderr)
        return 2

    json_out = Path(args.json_out)
    csv_out = Path(args.csv_out)
    write_json(routes, json_out)
    write_csv(routes, csv_out)

    print(f"Done. Routes: {len(routes)}. Errors: {errors}.")
    print(f"JSON: {json_out}")
    print(f"CSV:  {csv_out}")
    return 0 if errors == 0 else 3


if __name__ == "__main__":
    raise SystemExit(main())
