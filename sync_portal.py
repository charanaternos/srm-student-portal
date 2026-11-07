import asyncio
import os
import uuid
from datetime import datetime, timezone
from urllib.parse import urlparse
from zoneinfo import ZoneInfo

import requests
from bs4 import BeautifulSoup
from playwright.async_api import async_playwright


def required(name: str) -> str:
    value = os.environ.get(name)
    if not value:
        raise RuntimeError(f"Missing required setting: {name}")
    return value


def https_url(name: str) -> str:
    value = required(name)
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc:
        raise RuntimeError(f"{name} must be a valid HTTPS URL")
    return value


def table_to_records(html: str) -> list[dict[str, str]]:
    soup = BeautifulSoup(html, "html.parser")
    table = soup.find("table")
    if table is None:
        raise RuntimeError("The configured selector did not point to an HTML table")

    rows = table.find_all("tr")
    if not rows:
        return []

    header_row = table.select_one("thead tr")
    if header_row is None:
        header_row = rows[0]
        data_rows = rows[1:]
    else:
        data_rows = table.select("tbody tr") or rows[1:]

    header_cells = header_row.find_all(["th", "td"], recursive=False)
    headers = [
        cell.get_text(" ", strip=True) or f"column_{i + 1}"
        for i, cell in enumerate(header_cells)
    ]

    records = []
    for row in data_rows:
        cells = row.find_all(["th", "td"], recursive=False)
        values = [cell.get_text(" ", strip=True) for cell in cells]
        if not any(values):
            continue

        record = {}
        for i, value in enumerate(values):
            key = headers[i] if i < len(headers) and headers[i] else f"column_{i + 1}"
            record[key] = value
        records.append(record)

    return records


async def scrape_portal() -> dict:
    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=True)
        try:
            context = await browser.new_context()
            try:
                page = await context.new_page()

                await page.goto(
                    https_url("SRM_LOGIN_URL"),
                    wait_until="domcontentloaded",
                    timeout=60_000,
                )
                await page.locator(required("SRM_USERNAME_SELECTOR")).fill(
                    required("SRM_USERNAME")
                )
                await page.locator(required("SRM_PASSWORD_SELECTOR")).fill(
                    required("SRM_PASSWORD")
                )
                await page.locator(required("SRM_SUBMIT_SELECTOR")).click()

                # Set this to an element that appears only after successful login.
                await page.locator(required("SRM_LOGIN_SUCCESS_SELECTOR")).wait_for(
                    state="visible",
                    timeout=45_000,
                )

                result = {}
                for section in ("ATTENDANCE", "CLASSES", "MARKS"):
                    await page.goto(
                        https_url(f"SRM_{section}_URL"),
                        wait_until="domcontentloaded",
                        timeout=60_000,
                    )

                    table = page.locator(
                        required(f"SRM_{section}_TABLE_SELECTOR")
                    ).first
                    await table.wait_for(state="visible", timeout=30_000)
                    html = await table.evaluate("(el) => el.outerHTML")
                    records = table_to_records(html)

                    if not records:
                        raise RuntimeError(
                            f"No {section.lower()} rows found; refusing to upload."
                        )

                    result[section.lower()] = records

                return result
            finally:
                await context.close()
        finally:
            await browser.close()


def upload_to_supabase(data: dict) -> None:
    base_url = https_url("SUPABASE_URL").rstrip("/")
    key = required("SUPABASE_SERVICE_ROLE_KEY")
    owner_id = str(uuid.UUID(required("SUPABASE_USER_ID")))

    local_now = datetime.now(
        ZoneInfo(os.environ.get("PORTAL_TIMEZONE", "Asia/Kolkata"))
    )

    row = {
        "owner_id": owner_id,
        "snapshot_date": local_now.date().isoformat(),
        "fetched_at": datetime.now(timezone.utc).isoformat(),
        "data": data,
    }

    response = requests.post(
        f"{base_url}/rest/v1/student_portal_snapshots",
        params={"on_conflict": "owner_id,snapshot_date"},
        headers={
            "apikey": key,
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
            "Prefer": "resolution=merge-duplicates,return=minimal",
        },
        json=row,
        timeout=30,
    )

    if not response.ok:
        raise RuntimeError(
            f"Supabase upload failed with HTTP {response.status_code}; "
            "response body omitted."
        )


async def main() -> None:
    data = await scrape_portal()
    upload_to_supabase(data)
    print("Student data sync completed.")


if __name__ == "__main__":
    asyncio.run(main())

