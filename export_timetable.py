import asyncio
import getpass
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlsplit

from playwright.async_api import async_playwright


TABLE_EXTRACTOR = r"""
(table) => {
  if (table.tagName !== "TABLE") {
    throw new Error("The configured selector must match an HTML <table>.");
  }

  const allRows = Array.from(table.querySelectorAll("tr"));
  const headerRows = table.tHead ? Array.from(table.tHead.rows) : [];

  const headerRow = headerRows.length
    ? headerRows[headerRows.length - 1]
    : (
        allRows.find(row =>
          Array.from(row.cells).some(cell => cell.tagName === "TH")
        ) || allRows[0]
      );

  if (!headerRow) {
    return { headers: [], rows: [] };
  }

  const cellText = (cell) =>
    (cell.innerText || cell.textContent || "")
      .split(/\r?\n/)
      .map(part => part.trim())
      .filter(Boolean)
      .join(" | ");

  const seen = new Map();
  const headers = Array.from(headerRow.cells).map((cell, index) => {
    const base = cellText(cell) || `column_${index + 1}`;
    const count = (seen.get(base) || 0) + 1;
    seen.set(base, count);
    return count === 1 ? base : `${base}_${count}`;
  });

  let dataRows;
  if (headerRows.length) {
    dataRows = Array.from(table.tBodies).flatMap(body =>
      Array.from(body.rows)
    );

    // Some tables do not have a <tbody>.
    if (!dataRows.length) {
      dataRows = allRows.filter(row => !headerRows.includes(row));
    }
  } else {
    dataRows = allRows.filter(row => row !== headerRow);
  }

  const footerRows = table.tFoot ? Array.from(table.tFoot.rows) : [];
  dataRows = dataRows.filter(row => !footerRows.includes(row));

  const rows = dataRows
    .map(row => Array.from(row.cells).map(cellText))
    .filter(values => values.some(value => value.length > 0));

  return { headers, rows };
}
"""


DAY_ORDER_ALIASES = (
    "day order", "day order no", "day order number", "order", "order no"
)
DAY_ALIASES = ("day", "weekday", "week day", "day name", "class day")
DATE_ALIASES = ("date", "class date", "schedule date", "timetable date")

SUBJECT_ALIASES = (
    "subject", "subject name", "course", "course name",
    "course title", "paper", "course subject", "subject course"
)
COURSE_CODE_ALIASES = (
    "course code", "subject code", "course id", "subject id", "code"
)
START_TIME_ALIASES = ("start time", "from time", "begin time")
END_TIME_ALIASES = ("end time", "to time", "finish time")
TIME_ALIASES = ("time", "timing", "time slot", "period time", "class time", "hour")
ROOM_ALIASES = ("room", "room no", "room number", "classroom", "venue", "location")
FACULTY_ALIASES = ("faculty", "faculty name", "teacher", "instructor", "staff name")


def required(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise RuntimeError(f"Set the required environment variable {name}.")
    return value


def https_url(name: str) -> str:
    value = required(name)
    parsed = urlsplit(value)
    if parsed.scheme.lower() != "https" or not parsed.netloc:
        raise RuntimeError(f"{name} must be a valid HTTPS URL.")
    return value


def normalized_header(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", value.casefold())


def first_value(row: dict, aliases: tuple[str, ...]) -> str | None:
    values = {
        normalized_header(str(key)): value
        for key, value in row.items()
    }

    for alias in aliases:
        value = values.get(normalized_header(alias))
        if value is not None:
            text = str(value).strip()
            if text and text.casefold() not in {"-", "–", "—", "n/a"}:
                return text

    return None


TIME_TOKEN = r"\d{1,2}(?:[:.]\d{2})?\s*(?:a\.?m\.?|p\.?m\.?)?"
TIME_RANGE = re.compile(
    rf"(?P<start>{TIME_TOKEN})\s*(?:-|–|—|\bto\b)\s*(?P<end>{TIME_TOKEN})",
    re.IGNORECASE,
)


def parse_time_range(value: str | None) -> tuple[str | None, str | None]:
    if not value:
        return None, None

    match = TIME_RANGE.search(value)
    if not match:
        return None, None

    start = re.sub(r"\s+", " ", match.group("start")).strip()
    end = re.sub(r"\s+", " ", match.group("end")).strip()
    return start, end


def is_time_slot_header(header: str) -> bool:
    if parse_time_range(header)[0]:
        return True

    return bool(re.fullmatch(r"(?:period|hour)\s*\d+", header.strip(), re.I))


async def extract_table(page, selector: str, label: str, timeout_ms: int) -> dict:
    locator = page.locator(selector)

    if await locator.count() == 0:
        raise RuntimeError(
            f"No {label} table matched its configured selector."
        )

    table = locator.first
    await table.wait_for(state="visible", timeout=timeout_ms)
    extracted = await table.evaluate(TABLE_EXTRACTOR)

    headers = extracted.get("headers", [])
    rows = []

    for values in extracted.get("rows", []):
        record = {}
        for index, value in enumerate(values):
            key = headers[index] if index < len(headers) else f"column_{index + 1}"
            record[key] = value
        if any(str(value).strip() for value in record.values()):
            rows.append(record)

    if not headers or not rows:
        raise RuntimeError(
            f"The {label} table was found, but no header/data rows were extracted. "
            "Check the selector and portal table layout."
        )

    return {"headers": headers, "rows": rows}


def normalize_day_order_row(row: dict) -> dict:
    return {
        "day_order": first_value(row, DAY_ORDER_ALIASES),
        "day": first_value(row, DAY_ALIASES),
        "date": first_value(row, DATE_ALIASES),
        "raw": row,
    }


def normalize_class_row(row: dict) -> dict:
    start_time = first_value(row, START_TIME_ALIASES)
    end_time = first_value(row, END_TIME_ALIASES)
    time_value = first_value(row, TIME_ALIASES)

    if time_value and (not start_time or not end_time):
        parsed_start, parsed_end = parse_time_range(time_value)
        start_time = start_time or parsed_start
        end_time = end_time or parsed_end

    if not time_value and (start_time or end_time):
        time_value = " – ".join(
            part for part in (start_time, end_time) if part
        )

    return {
        "subject": first_value(row, SUBJECT_ALIASES),
        "course_code": first_value(row, COURSE_CODE_ALIASES),
        "day_order": first_value(row, DAY_ORDER_ALIASES),
        "day": first_value(row, DAY_ALIASES),
        "date": first_value(row, DATE_ALIASES),
        "start_time": start_time,
        "end_time": end_time,
        "time": time_value,
        "room": first_value(row, ROOM_ALIASES),
        "faculty": first_value(row, FACULTY_ALIASES),
        "details": first_value(
            row, ("details", "class details", "schedule details", "description")
        ),
        "raw": row,
    }


def normalize_classes(headers: list[str], rows: list[dict]) -> list[dict]:
    # Also support a common timetable layout where each row is a day order
    # and each time slot is a separate column.
    time_columns = [header for header in headers if is_time_slot_header(header)]
    has_day_information = any(
        first_value(row, DAY_ORDER_ALIASES + DAY_ALIASES + DATE_ALIASES)
        for row in rows
    )

    if time_columns and has_day_information:
        classes = []

        for row in rows:
            for time_column in time_columns:
                cell = str(row.get(time_column, "")).strip()
                if not cell or cell.casefold() in {"-", "–", "—", "n/a", "free"}:
                    continue

                start_time, end_time = parse_time_range(time_column)

                classes.append({
                    # In a grid-style table, the cell text may contain the
                    # subject, code, room, or other class details together.
                    "subject": first_value(row, SUBJECT_ALIASES) or cell,
                    "course_code": first_value(row, COURSE_CODE_ALIASES),
                    "day_order": first_value(row, DAY_ORDER_ALIASES),
                    "day": first_value(row, DAY_ALIASES),
                    "date": first_value(row, DATE_ALIASES),
                    "start_time": start_time,
                    "end_time": end_time,
                    "time": time_column,
                    "room": first_value(row, ROOM_ALIASES),
                    "faculty": first_value(row, FACULTY_ALIASES),
                    "details": cell,
                    "raw": {
                        "row": row,
                        "time_column": time_column,
                        "cell": cell,
                    },
                })

        if classes:
            return classes

    # Standard layout: one class per table row.
    return [normalize_class_row(row) for row in rows]


def write_json(data: dict) -> Path:
    default_output = (
        Path(__file__).resolve().parent
        / "app" / "src" / "main" / "assets"
        / "srm_timetable.json"
    )

    configured_path = os.getenv("OUTPUT_FILE", "").strip()
    output = Path(configured_path).expanduser() if configured_path else default_output

    if not output.is_absolute():
        output = (Path.cwd() / output).resolve()

    output.parent.mkdir(parents=True, exist_ok=True)

    temporary_file = output.with_name(output.name + ".tmp")
    temporary_file.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    temporary_file.replace(output)

    return output


async def main() -> None:
    login_url = https_url("SRM_LOGIN_URL")
    username_selector = required("SRM_USERNAME_SELECTOR")
    password_selector = required("SRM_PASSWORD_SELECTOR")
    submit_selector = required("SRM_SUBMIT_SELECTOR")
    success_selector = required("SRM_LOGIN_SUCCESS_SELECTOR")

    day_order_selector = required("SRM_DAY_ORDER_TABLE_SELECTOR")
    class_table_selector = required("SRM_CLASS_TABLE_SELECTOR")

    timetable_url = os.getenv("SRM_TIMETABLE_URL", "").strip()
    timetable_nav_selector = os.getenv("SRM_TIMETABLE_NAV_SELECTOR", "").strip()

    if not timetable_nav_selector and not timetable_url:
        raise RuntimeError(
            "Set SRM_TIMETABLE_URL or SRM_TIMETABLE_NAV_SELECTOR."
        )

    if timetable_url and not timetable_nav_selector:
        parsed = urlsplit(timetable_url)
        if parsed.scheme.lower() != "https" or not parsed.netloc:
            raise RuntimeError("SRM_TIMETABLE_URL must be a valid HTTPS URL.")

    username = os.getenv("SRM_USERNAME") or input("SRM username: ").strip()
    password = os.getenv("SRM_PASSWORD") or getpass.getpass("SRM password: ")

    if not username or not password:
        raise RuntimeError("A username and password are required.")

    headless = os.getenv("HEADLESS", "false").strip().casefold() in {
        "1", "true", "yes"
    }
    login_timeout_ms = int(os.getenv("LOGIN_TIMEOUT_MS", "180000"))
    table_timeout_ms = int(os.getenv("TABLE_TIMEOUT_MS", "30000"))

    async with async_playwright() as playwright:
        browser = await playwright.chromium.launch(headless=headless)
        try:
            context = await browser.new_context()
            try:
                page = await context.new_page()
                await page.goto(login_url, wait_until="domcontentloaded")

                await page.locator(username_selector).fill(username)
                await page.locator(password_selector).fill(password)
                await page.locator(submit_selector).click()

                print(
                    "If the portal requests MFA/OTP, complete it in the "
                    "visible browser window."
                )
                await page.locator(success_selector).wait_for(
                    state="visible",
                    timeout=login_timeout_ms,
                )

                if timetable_nav_selector:
                    await page.locator(timetable_nav_selector).click()
                else:
                    await page.goto(
                        timetable_url,
                        wait_until="domcontentloaded",
                    )

                day_order_table = await extract_table(
                    page,
                    day_order_selector,
                    "day-order",
                    table_timeout_ms,
                )
                class_table = await extract_table(
                    page,
                    class_table_selector,
                    "class schedule",
                    table_timeout_ms,
                )
            finally:
                await context.close()
        finally:
            await browser.close()

    data = {
        "schema_version": 1,
        "exported_at": datetime.now(timezone.utc).isoformat(),
        "portal_timezone": os.getenv("PORTAL_TIMEZONE", "Asia/Kolkata"),
        "day_order_mapping": [
            normalize_day_order_row(row)
            for row in day_order_table["rows"]
        ],
        "classes": normalize_classes(
            class_table["headers"],
            class_table["rows"],
        ),
        # Retain the original table rows in case SRM uses different column names.
        "raw_tables": {
            "day_order": day_order_table,
            "class_schedule": class_table,
        },
    }

    output = write_json(data)
    print(
        f"Saved {len(data['classes'])} class entries and "
        f"{len(data['day_order_mapping'])} day-order rows to {output}"
    )


if __name__ == "__main__":
    asyncio.run(main())

