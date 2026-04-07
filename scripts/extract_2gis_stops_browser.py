#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path
from urllib.parse import urljoin

from extract_2gis_stops import (
    BASE_URL,
    DEFAULT_OUT_CSV,
    DEFAULT_OUT_JSON,
    parse_stop_page,
    write_csv,
    write_json,
)

try:
    from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
    from playwright.sync_api import sync_playwright
except ImportError:  # pragma: no cover
    sync_playwright = None
    PlaywrightTimeoutError = Exception


DEFAULT_SEARCH_HTML = "data/raw/2gis/data.html"
DEFAULT_USER_DATA_DIR = ".playwright_2gis_profile"
SEARCH_LINK_SELECTOR = 'a[href*="/almaty/station/"]'
PAGE_LINK_SELECTOR = 'a[href*="/page/"]'
PAGE_NUMBER_LINK_SELECTOR = "._1x4k6z7 a._12164l30"
CURRENT_PAGE_NUMBER_SELECTOR = "._1x4k6z7 ._l934xo5 ._19xy60y"
NEXT_PAGES_SELECTOR = "._n5hmn94"
CONTENT_READY_SELECTOR = 'a[href*="/almaty/station/"], ._1x4k6z7'
DEFAULT_CHROME_CANDIDATES = [
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
]


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Scrape 2GIS stop pages with a real browser, including pagination "
            "that requires clicking the next-pages button."
        )
    )
    parser.add_argument(
        "--start-url",
        help="Search page URL to open in the browser.",
    )
    parser.add_argument(
        "--search-html",
        default=DEFAULT_SEARCH_HTML,
        help=(
            "Saved search HTML used to infer the canonical start URL when "
            "--start-url is not provided."
        ),
    )
    parser.add_argument(
        "--browser-path",
        help="Path to Chrome or Edge executable. If omitted, common locations are checked.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        help="Run the browser in headless mode.",
    )
    parser.add_argument(
        "--user-data-dir",
        default=DEFAULT_USER_DATA_DIR,
        help=(
            "Persistent browser profile directory. "
            f"Default: {DEFAULT_USER_DATA_DIR}"
        ),
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=0,
        help="Limit how many stops to parse. 0 means no limit.",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=20.0,
        help="Timeout for page actions in seconds.",
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=0.5,
        help="Delay between browser actions in seconds.",
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


def infer_start_url(search_html_path: Path) -> str:
    if not search_html_path.exists():
        raise FileNotFoundError(f"Search HTML not found: {search_html_path}")

    text = search_html_path.read_text(encoding="utf-8", errors="replace")
    marker = 'rel="canonical"'
    idx = text.find(marker)
    if idx == -1:
        raise ValueError("Canonical search URL was not found in the saved HTML.")

    snippet = text[max(0, idx - 400): idx + 200]
    href_marker = 'href="'
    href_idx = snippet.find(href_marker)
    if href_idx == -1:
        raise ValueError("Canonical href was not found near rel=canonical.")

    href_start = href_idx + len(href_marker)
    href_end = snippet.find('"', href_start)
    if href_end == -1:
        raise ValueError("Canonical href is malformed.")

    return snippet[href_start:href_end]


def resolve_browser_path(explicit_path: str | None) -> str:
    if explicit_path:
        candidate = Path(explicit_path)
        if candidate.exists():
            return str(candidate)
        raise FileNotFoundError(f"Browser executable not found: {candidate}")

    for candidate in DEFAULT_CHROME_CANDIDATES:
        if Path(candidate).exists():
            return candidate

    raise FileNotFoundError(
        "Chrome or Edge executable was not found. "
        "Pass --browser-path explicitly."
    )


def collect_href_values(page, selector: str) -> list[str]:
    hrefs = page.eval_on_selector_all(
        selector,
        """
        elements => elements
            .map(el => el.getAttribute('href') || '')
            .filter(Boolean)
        """,
    )
    normalized: list[str] = []
    seen = set()
    for href in hrefs:
        absolute = urljoin(BASE_URL, href)
        if absolute not in seen:
            seen.add(absolute)
            normalized.append(absolute)
    return normalized


def collect_text_values(page, selector: str) -> list[str]:
    values = page.eval_on_selector_all(
        selector,
        """
        elements => elements
            .map(el => (el.textContent || '').trim())
            .filter(Boolean)
        """,
    )
    return values


def is_captcha_page(page) -> bool:
    return "captcha.2gis.ru" in page.url.lower() or "captcha" in page.title().lower()


def wait_for_real_content(page, selector: str, timeout_ms: int, headless: bool) -> None:
    start = time.time()
    captcha_prompt_shown = False

    while True:
        if not is_captcha_page(page):
            try:
                page.wait_for_selector(selector, timeout=1500)
                return
            except PlaywrightTimeoutError:
                pass

        if is_captcha_page(page):
            if headless:
                raise RuntimeError(
                    "2GIS returned a captcha page in headless mode. "
                    "Run without --headless and solve the captcha in the opened browser window."
                )
            if not captcha_prompt_shown:
                print(
                    "2GIS captcha detected. Solve it in the opened browser window, "
                    "then the script will continue automatically."
                )
                captcha_prompt_shown = True

        if (time.time() - start) * 1000 > timeout_ms:
            raise RuntimeError(
                f"Timed out waiting for page content. Last URL: {page.url}"
            )

        page.wait_for_timeout(1000)


def get_current_page_number(page) -> int | None:
    locator = page.locator(CURRENT_PAGE_NUMBER_SELECTOR)
    if locator.count() == 0:
        return None
    text = locator.first.text_content() or ""
    text = text.strip()
    return int(text) if text.isdigit() else None


def get_visible_page_numbers(page) -> list[int]:
    numbers: list[int] = []
    for text in collect_text_values(page, PAGE_NUMBER_LINK_SELECTOR):
        if text.isdigit():
            numbers.append(int(text))
    return numbers


def click_page_number(page, target_page: int, timeout_ms: int, headless: bool) -> None:
    print(f"  -> click page {target_page}")
    locator = page.locator(PAGE_NUMBER_LINK_SELECTOR).filter(has_text=str(target_page))
    if locator.count() == 0:
        raise RuntimeError(f"Page button {target_page} was not found in the visible paginator.")

    locator.first.click(timeout=timeout_ms)
    try:
        page.wait_for_function(
            """
            target => {
                const el = document.querySelector('._1x4k6z7 ._l934xo5 ._19xy60y');
                return el && (el.textContent || '').trim() === String(target);
            }
            """,
            arg=target_page,
            timeout=timeout_ms,
        )
    except PlaywrightTimeoutError:
        wait_for_real_content(page, SEARCH_LINK_SELECTOR, timeout_ms=timeout_ms, headless=headless)
        current_page = get_current_page_number(page)
        if current_page != target_page:
            raise RuntimeError(f"Failed to switch to page {target_page}. Current page is {current_page}.")


def click_next_pages_group(page, timeout_ms: int, headless: bool) -> tuple[bool, int | None, list[int]]:
    next_button = page.locator(NEXT_PAGES_SELECTOR)
    if next_button.count() == 0:
        return False, get_current_page_number(page), get_visible_page_numbers(page)

    before_current = get_current_page_number(page)
    before_numbers = get_visible_page_numbers(page)
    print(f"  -> click next pages group, visible before: {before_numbers}")
    next_button.first.click(timeout=timeout_ms)

    try:
        page.wait_for_function(
            """
            before => {
                const current = Array.from(document.querySelectorAll('._1x4k6z7 a._12164l30'))
                    .map(el => (el.textContent || '').trim())
                    .filter(Boolean);
                return JSON.stringify(current) !== JSON.stringify(before);
            }
            """,
            arg=[str(number) for number in before_numbers],
            timeout=timeout_ms,
        )
    except PlaywrightTimeoutError:
        wait_for_real_content(page, CONTENT_READY_SELECTOR, timeout_ms=timeout_ms, headless=headless)

    after_current = get_current_page_number(page)
    after_numbers = get_visible_page_numbers(page)

    if before_current is not None and after_current is not None and after_current < before_current:
        print(
            f"  -> paginator moved backward ({before_current} -> {after_current}), stopping at the max page reached"
        )
        return False, after_current, after_numbers

    if after_numbers == before_numbers:
        return False, after_current, after_numbers

    return True, after_current, after_numbers


def collect_stop_urls_via_pagination(page, start_url: str, timeout_ms: int, delay: float, headless: bool) -> list[str]:
    page.goto(start_url, wait_until="domcontentloaded", timeout=timeout_ms)
    wait_for_real_content(page, CONTENT_READY_SELECTOR, timeout_ms=timeout_ms, headless=headless)

    stop_urls: list[str] = []
    seen_stop_urls = set()
    visited_pages = set()
    visited_page_windows = set()

    while True:
        current_page = get_current_page_number(page) or 1

        if current_page not in visited_pages:
            print(f"[search page {current_page}] {page.url}")
            wait_for_real_content(page, SEARCH_LINK_SELECTOR, timeout_ms=timeout_ms, headless=headless)
            for stop_url in collect_href_values(page, SEARCH_LINK_SELECTOR):
                if stop_url not in seen_stop_urls:
                    seen_stop_urls.add(stop_url)
                    stop_urls.append(stop_url)
            visited_pages.add(current_page)

        visible_numbers = get_visible_page_numbers(page)
        print(f"  visible page buttons: {visible_numbers}")
        next_number = next(
            (number for number in visible_numbers if number > current_page and number not in visited_pages),
            None,
        )
        if next_number is not None:
            click_page_number(page, next_number, timeout_ms=timeout_ms, headless=headless)
            time.sleep(max(delay, 0))
            continue

        window_signature = (current_page, *visible_numbers)
        if window_signature in visited_page_windows:
            break
        visited_page_windows.add(window_signature)

        moved_forward, after_current, after_numbers = click_next_pages_group(
            page,
            timeout_ms=timeout_ms,
            headless=headless,
        )
        print(f"  visible page buttons: {after_numbers}")

        if not moved_forward:
            break

        time.sleep(max(delay, 0))

    return stop_urls


def parse_stops(page, stop_urls: list[str], timeout_ms: int, delay: float, headless: bool):
    stops = []
    errors = 0

    for index, stop_url in enumerate(stop_urls, start=1):
        print(f"[stop {index}/{len(stop_urls)}] {stop_url}")
        try:
            page.goto(stop_url, wait_until="domcontentloaded", timeout=timeout_ms)
            wait_for_real_content(page, "h1", timeout_ms=timeout_ms, headless=headless)
            stops.append(parse_stop_page(page.content(), stop_url))
        except Exception as exc:  # pragma: no cover
            errors += 1
            print(f"  ! failed: {exc}", file=sys.stderr)

        time.sleep(max(delay, 0))

    return stops, errors


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    if sync_playwright is None:
        print(
            "Playwright is not installed. Run: python -m pip install --user playwright",
            file=sys.stderr,
        )
        return 10

    try:
        start_url = args.start_url or infer_start_url(Path(args.search_html))
        browser_path = resolve_browser_path(args.browser_path)
    except (FileNotFoundError, ValueError) as exc:
        print(str(exc), file=sys.stderr)
        return 11

    timeout_ms = int(args.timeout * 1000)

    with sync_playwright() as playwright:
        context = playwright.chromium.launch_persistent_context(
            user_data_dir=str(Path(args.user_data_dir).resolve()),
            executable_path=browser_path,
            headless=args.headless,
        )
        page = context.new_page()

        try:
            try:
                stop_urls = collect_stop_urls_via_pagination(
                    page=page,
                    start_url=start_url,
                    timeout_ms=timeout_ms,
                    delay=args.delay,
                    headless=args.headless,
                )

                if args.limit and args.limit > 0:
                    stop_urls = stop_urls[: args.limit]

                if not stop_urls:
                    print("No stop URLs were found.", file=sys.stderr)
                    return 12

                stops, errors = parse_stops(
                    page=page,
                    stop_urls=stop_urls,
                    timeout_ms=timeout_ms,
                    delay=args.delay,
                    headless=args.headless,
                )
            except RuntimeError as exc:
                print(str(exc), file=sys.stderr)
                return 14
        finally:
            context.close()

    if not stops:
        print("No stops were parsed successfully.", file=sys.stderr)
        return 13

    write_json(stops, Path(args.json_out))
    write_csv(stops, Path(args.csv_out))

    print(f"Done. Stops: {len(stops)}. Errors: {errors}.")
    print(f"JSON: {args.json_out}")
    print(f"CSV:  {args.csv_out}")
    return 0 if errors == 0 else 3


if __name__ == "__main__":
    raise SystemExit(main())
