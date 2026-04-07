#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import time
from dataclasses import dataclass, asdict
from html import unescape
from pathlib import Path
from typing import Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin
from urllib.request import Request, urlopen


BASE_URL = "https://2gis.kz"
DEFAULT_SEARCH_HTML = "data/raw/2gis/data.html"
DEFAULT_OUT_JSON = "data/exports/stops_2gis.json"
DEFAULT_OUT_CSV = "data/exports/stops_2gis.csv"


STATION_PATH_RE = re.compile(r"/almaty/station/(\d+)")
SEARCH_PAGE_PATH_RE = re.compile(r'(/almaty/search/[^"]+/page/\d+)')
CANONICAL_RE = re.compile(r'<link href="https://2gis\.kz/almaty/station/(\d+)" rel="canonical"', re.IGNORECASE)
TITLE_RE = re.compile(r"<title>Остановка\s+(.+?):.*?</title>", re.IGNORECASE | re.DOTALL)
H1_RE = re.compile(r"<h1[^>]*>\s*<span>(.*?)</span>\s*</h1>", re.IGNORECASE | re.DOTALL)
COORDS_RE = re.compile(
    r"/almaty/directions/points/(?:%7C|\|)\s*([0-9.]+)(?:%2C|,)\s*([0-9.]+)(?:%3B|;)\s*(\d+)",
    re.IGNORECASE,
)
OG_CENTER_RE = re.compile(r"center=([0-9.]+)(?:%2C|,)([0-9.]+)", re.IGNORECASE)
ROUTE_RE = re.compile(
    r'<a href="/almaty/route/(?P<route_id>\d+)"[^>]*>\s*<span[^>]*>(?P<number>.*?)</span>\s*</a>'
    r".{0,300}?"
    r'<div class="_xh0r19">(?P<title>.*?)</div>',
    re.IGNORECASE | re.DOTALL,
)


@dataclass
class RouteInfo:
    route_id: str
    number: str
    title: str


@dataclass
class StopInfo:
    stop_id: str
    name: str
    lon: float | None
    lat: float | None
    url: str
    route_numbers: list[str]
    routes: list[RouteInfo]


def clean_html_text(value: str) -> str:
    no_tags = re.sub(r"<[^>]+>", " ", value)
    normalized = re.sub(r"\s+", " ", unescape(no_tags)).strip()
    return normalized.replace("\u200b", "")


def extract_station_urls(search_html: str, base_url: str) -> list[str]:
    stop_ids = []
    seen = set()
    for match in STATION_PATH_RE.finditer(search_html):
        stop_id = match.group(1)
        if stop_id not in seen:
            seen.add(stop_id)
            stop_ids.append(stop_id)
    return [urljoin(base_url, f"/almaty/station/{stop_id}") for stop_id in stop_ids]


def extract_search_page_urls(search_html: str, base_url: str) -> list[str]:
    page_paths = []
    seen = set()
    for match in SEARCH_PAGE_PATH_RE.finditer(search_html):
        page_path = match.group(1)
        if page_path not in seen:
            seen.add(page_path)
            page_paths.append(page_path)
    return [urljoin(base_url, page_path) for page_path in page_paths]


def extract_stop_name(detail_html: str) -> str:
    match = H1_RE.search(detail_html)
    if match:
        return clean_html_text(match.group(1))
    match = TITLE_RE.search(detail_html)
    if match:
        return clean_html_text(match.group(1))
    return ""


def extract_stop_id(detail_html: str, fallback_url: str) -> str:
    match = CANONICAL_RE.search(detail_html)
    if match:
        return match.group(1)
    match = STATION_PATH_RE.search(fallback_url)
    if match:
        return match.group(1)
    raise ValueError("Не удалось определить stop_id")


def extract_coordinates(detail_html: str) -> tuple[float | None, float | None]:
    match = COORDS_RE.search(detail_html)
    if match:
        return float(match.group(1)), float(match.group(2))
    match = OG_CENTER_RE.search(detail_html)
    if match:
        return float(match.group(1)), float(match.group(2))
    return None, None


def extract_routes(detail_html: str) -> list[RouteInfo]:
    routes: list[RouteInfo] = []
    seen = set()
    for match in ROUTE_RE.finditer(detail_html):
        route_id = match.group("route_id").strip()
        number = clean_html_text(match.group("number"))
        title = clean_html_text(match.group("title"))
        key = (route_id, number, title)
        if key in seen:
            continue
        seen.add(key)
        routes.append(RouteInfo(route_id=route_id, number=number, title=title))
    return routes


def parse_stop_page(detail_html: str, url: str) -> StopInfo:
    stop_id = extract_stop_id(detail_html, url)
    lon, lat = extract_coordinates(detail_html)
    routes = extract_routes(detail_html)
    return StopInfo(
        stop_id=stop_id,
        name=extract_stop_name(detail_html),
        lon=lon,
        lat=lat,
        url=url,
        route_numbers=[route.number for route in routes],
        routes=routes,
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
    stop_url: str,
    detail_dir: Path | None,
    timeout: float,
    use_network: bool,
) -> str:
    stop_id_match = STATION_PATH_RE.search(stop_url)
    stop_id = stop_id_match.group(1) if stop_id_match else None

    if detail_dir and stop_id:
        for candidate in (detail_dir / f"{stop_id}.html", detail_dir / f"station_{stop_id}.html"):
            if candidate.exists():
                return candidate.read_text(encoding="utf-8", errors="replace")

    if not use_network:
        raise FileNotFoundError(
            f"Для {stop_url} не найден локальный HTML, а сеть отключена. "
            f"Сохраните страницу в {detail_dir} или включите --use-network."
        )

    return fetch_html(stop_url, timeout=timeout)


def write_json(stops: Iterable[StopInfo], output_path: Path) -> None:
    payload = []
    for stop in stops:
        item = asdict(stop)
        item["routes"] = [asdict(route) for route in stop.routes]
        payload.append(item)
    output_path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def write_csv(stops: Iterable[StopInfo], output_path: Path) -> None:
    with output_path.open("w", encoding="utf-8", newline="") as csvfile:
        writer = csv.DictWriter(
            csvfile,
            fieldnames=[
                "stop_id",
                "name",
                "lon",
                "lat",
                "url",
                "route_numbers",
                "routes_json",
            ],
        )
        writer.writeheader()
        for stop in stops:
            writer.writerow(
                {
                    "stop_id": stop.stop_id,
                    "name": stop.name,
                    "lon": stop.lon,
                    "lat": stop.lat,
                    "url": stop.url,
                    "route_numbers": ", ".join(stop.route_numbers),
                    "routes_json": json.dumps(
                        [asdict(route) for route in stop.routes],
                        ensure_ascii=False,
                    ),
                }
            )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Извлекает остановки из сохранённого HTML поиска 2ГИС и собирает "
            "для каждой остановки название, координаты и маршруты."
        )
    )
    parser.add_argument(
        "--search-html",
        default=DEFAULT_SEARCH_HTML,
        help=f"Путь к сохранённой странице поиска 2ГИС. По умолчанию: {DEFAULT_SEARCH_HTML}",
    )
    parser.add_argument(
        "--detail-dir",
        help=(
            "Папка с локально сохранёнными страницами остановок. "
            "Скрипт ищет файлы вида <stop_id>.html или station_<stop_id>.html."
        ),
    )
    parser.add_argument(
        "--use-network",
        action="store_true",
        help="Если локальной страницы остановки нет, скачать её с 2GIS.",
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Ограничить количество остановок для обработки. 0 = без лимита.",
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=0.5,
        help="Пауза между сетевыми запросами в секундах. По умолчанию: 0.5",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=20.0,
        help="Таймаут сетевого запроса в секундах. По умолчанию: 20",
    )
    parser.add_argument(
        "--json-out",
        default=DEFAULT_OUT_JSON,
        help=f"Куда сохранить JSON. По умолчанию: {DEFAULT_OUT_JSON}",
    )
    parser.add_argument(
        "--csv-out",
        default=DEFAULT_OUT_CSV,
        help=f"Куда сохранить CSV. По умолчанию: {DEFAULT_OUT_CSV}",
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    search_html_path = Path(args.search_html)
    if not search_html_path.exists():
        parser.error(f"Не найден файл поиска: {search_html_path}")

    detail_dir = Path(args.detail_dir) if args.detail_dir else None
    if detail_dir and not detail_dir.exists():
        parser.error(f"Не найдена папка detail-dir: {detail_dir}")

    search_html = load_search_html(search_html_path)
    station_urls = extract_station_urls(search_html, BASE_URL)

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
                for stop_url in extract_station_urls(page_html, BASE_URL):
                    if stop_url not in station_urls:
                        station_urls.append(stop_url)

                for next_page_url in extract_search_page_urls(page_html, BASE_URL):
                    if next_page_url not in seen_search_pages and next_page_url not in queued_search_pages:
                        queued_search_pages.append(next_page_url)

                time.sleep(max(args.delay, 0))
            except (HTTPError, URLError) as exc:
                print(f"  ! ошибка страницы поиска {page_url}: {exc}", file=sys.stderr)

    if args.limit and args.limit > 0:
        station_urls = station_urls[: args.limit]

    if not station_urls:
        print("В search HTML не найдено ни одной ссылки на остановку.", file=sys.stderr)
        return 1

    stops: list[StopInfo] = []
    errors = 0

    for index, stop_url in enumerate(station_urls, start=1):
        print(f"[{index}/{len(station_urls)}] {stop_url}")
        try:
            detail_html = load_detail_html(
                stop_url=stop_url,
                detail_dir=detail_dir,
                timeout=args.timeout,
                use_network=args.use_network,
            )
            stops.append(parse_stop_page(detail_html, stop_url))
            if args.use_network:
                time.sleep(max(args.delay, 0))
        except (FileNotFoundError, ValueError, HTTPError, URLError) as exc:
            errors += 1
            print(f"  ! ошибка: {exc}", file=sys.stderr)

    if not stops:
        print("Не удалось извлечь ни одной остановки.", file=sys.stderr)
        return 2

    json_out = Path(args.json_out)
    csv_out = Path(args.csv_out)
    write_json(stops, json_out)
    write_csv(stops, csv_out)

    print(f"Готово. Остановок: {len(stops)}. Ошибок: {errors}.")
    print(f"JSON: {json_out}")
    print(f"CSV:  {csv_out}")

    if errors:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
