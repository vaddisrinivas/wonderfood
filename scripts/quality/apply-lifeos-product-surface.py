#!/usr/bin/env python3
"""Apply product-facing LifeOS labels/sections to live Notion + Google Sheets.

No secret values are printed. The script writes a compact evidence JSON.
"""

from __future__ import annotations

import json
import os
import pathlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "app/build/evidence/lifeos-product-surface"
OUT_DIR.mkdir(parents=True, exist_ok=True)

NOTION_PAGE_ID = os.getenv("LIFEOS_NOTION_PAGE_ID", "3a45dd535a93816fb7d3d4a0a2bc2bf1").replace("-", "")
SPREADSHEET_ID = os.getenv("LIFEOS_SHEETS_ID", "1WpEwm07ApcnuiLDVhzl8vy4D5kU8KjmtbAVC4qLphcU")
TOKEN_FILE = pathlib.Path(os.getenv("GOOGLE_SHEETS_TOKEN_FILE", str(ROOT / "build/evidence/live-workspace/google-sheets-token.json")))
MARKER = "LifeOS product surface installed by WonderFood Android"


def request_json(method: str, url: str, headers: dict[str, str], body: object | None = None) -> dict:
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=45) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")
        raise RuntimeError(f"{method} {url} failed: HTTP {error.code}: {detail[:800]}") from error


def notion_headers() -> dict[str, str]:
    token = os.getenv("NOTION_TOKEN") or os.getenv("NOTION_API_KEY")
    if not token:
        raise RuntimeError("NOTION_TOKEN or NOTION_API_KEY missing")
    return {
        "Authorization": f"Bearer {token}",
        "Notion-Version": "2025-09-03",
        "Content-Type": "application/json",
    }


def append_lifeos_notion_section() -> dict:
    headers = notion_headers()
    children = request_json(
        "GET",
        f"https://api.notion.com/v1/blocks/{NOTION_PAGE_ID}/children?page_size=100",
        headers,
    ).get("results", [])
    existing_text = json.dumps(children)
    if MARKER in existing_text:
        return {"status": "already_present", "page_id": NOTION_PAGE_ID}

    blocks = [
        {
            "object": "block",
            "type": "heading_2",
            "heading_2": {"rich_text": [{"type": "text", "text": {"content": "📱 Android product surface"}}]},
        },
        {
            "object": "block",
            "type": "callout",
            "callout": {
                "icon": {"emoji": "🧬"},
                "rich_text": [
                    {
                        "type": "text",
                        "text": {
                            "content": f"{MARKER}. Food is the active Day 0 domain; Health is a companion through Health Connect; package runtime comes from the domain catalog."
                        },
                    }
                ],
            },
        },
        {
            "object": "block",
            "type": "bulleted_list_item",
            "bulleted_list_item": {
                "rich_text": [{"type": "text", "text": {"content": "App: full-screen multi-turn Chat, source cards, markdown tables, draft review, Data Home links, LifeOS control center."}}]
            },
        },
        {
            "object": "block",
            "type": "bulleted_list_item",
            "bulleted_list_item": {
                "rich_text": [{"type": "text", "text": {"content": "Data planes: Notion, Google Sheets, SQLite/Postgres, Health Connect, and MCP all map to schema surfaces instead of proof-only pages."}}]
            },
        },
        {
            "object": "block",
            "type": "bulleted_list_item",
            "bulleted_list_item": {
                "rich_text": [{"type": "text", "text": {"content": "Config source: app/src/main/assets/lifeos/domain-catalog.v1.json controls active package metadata."}}]
            },
        },
    ]
    request_json(
        "PATCH",
        f"https://api.notion.com/v1/blocks/{NOTION_PAGE_ID}/children",
        headers,
        {"children": blocks},
    )
    return {"status": "appended", "page_id": NOTION_PAGE_ID}


def google_access_token() -> str:
    direct = os.getenv("GOOGLE_SHEETS_ACCESS_TOKEN", "")
    if direct:
        return direct
    if not TOKEN_FILE.exists():
        raise RuntimeError(f"Google Sheets token file missing: {TOKEN_FILE}")
    cached = json.loads(TOKEN_FILE.read_text())
    refresh = cached.get("refresh_token", "")
    if not refresh:
        token = cached.get("access_token", "")
        if token:
            return token
        raise RuntimeError("Google Sheets token cache has no refresh_token/access_token")
    client_id = os.getenv("GOOGLE_CLIENT_ID", "")
    client_secret = os.getenv("GOOGLE_CLIENT_SECRET", "")
    if not client_id or not client_secret:
        raise RuntimeError("GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET missing")
    data = urllib.parse.urlencode(
        {
            "client_id": client_id,
            "client_secret": client_secret,
            "refresh_token": refresh,
            "grant_type": "refresh_token",
        }
    ).encode()
    req = urllib.request.Request(
        "https://oauth2.googleapis.com/token",
        data=data,
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=45) as response:
        refreshed = json.loads(response.read().decode("utf-8"))
    cached.update(refreshed)
    cached["refresh_token"] = refresh
    TOKEN_FILE.write_text(json.dumps(cached))
    return cached.get("access_token", "")


def sheets_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}


def update_lifeos_sheet() -> dict:
    token = google_access_token()
    if not token:
        raise RuntimeError("Google Sheets access token missing")
    headers = sheets_headers(token)
    metadata = request_json("GET", f"https://sheets.googleapis.com/v4/spreadsheets/{SPREADSHEET_ID}", headers)
    sheets = metadata.get("sheets", [])
    existing = {sheet["properties"]["title"]: sheet["properties"]["sheetId"] for sheet in sheets}
    requests: list[dict] = [
        {"updateSpreadsheetProperties": {"properties": {"title": "LifeOS 2026 — WonderFood Product Runtime"}, "fields": "title"}}
    ]
    runtime_sheet_id = existing.get("LifeOS Runtime")
    if runtime_sheet_id is None:
        requests.append(
            {
                "addSheet": {
                    "properties": {
                        "title": "LifeOS Runtime",
                        "gridProperties": {"rowCount": 80, "columnCount": 10},
                        "tabColor": {"red": 0.18, "green": 0.38, "blue": 0.27},
                    }
                }
            }
        )
    request_json(
        "POST",
        f"https://sheets.googleapis.com/v4/spreadsheets/{SPREADSHEET_ID}:batchUpdate",
        headers,
        {"requests": requests},
    )
    metadata = request_json("GET", f"https://sheets.googleapis.com/v4/spreadsheets/{SPREADSHEET_ID}", headers)
    existing = {sheet["properties"]["title"]: sheet["properties"]["sheetId"] for sheet in metadata.get("sheets", [])}
    runtime_sheet_id = existing["LifeOS Runtime"]

    values = [
        ["LifeOS 2026 — Product Runtime", "", "", ""],
        ["Marker", MARKER, "", ""],
        ["Active domain", "Food", "Day 0 native app surface", "Configured"],
        ["Companion domain", "Health", "Health Connect granted/read/write where available", "Configured"],
        ["Template domain", "Plants", "Zero-code package sanity check", "Template-ready"],
        ["Source model", "domain-catalog.v1.json", "Tabs, skills, schema surfaces, data planes", "Packaged in APK"],
        ["Data plane", "Notion", "LifeOS 2026 dashboard + linked databases", "Primary presentation"],
        ["Data plane", "Google Sheets", "Workbook tabs mirror schema surfaces", "Spreadsheet-primary"],
        ["Data plane", "SQLite/Postgres", "Canonical store + hosted snapshot route", "Runtime"],
        ["AI/MCP", "WonderFood Chat", "Multi-turn, sources, markdown tables, proposal review", "Runtime"],
        ["MCP", "wonderfood_mcp_server.py", "Skills, schemas, validation, packages, review-only app links", "Ready"],
    ]
    value_url = f"https://sheets.googleapis.com/v4/spreadsheets/{SPREADSHEET_ID}/values/{urllib.parse.quote('LifeOS Runtime!A1:D40')}"
    request_json("PUT", value_url + "?valueInputOption=RAW", headers, {"values": values})
    request_json(
        "POST",
        f"https://sheets.googleapis.com/v4/spreadsheets/{SPREADSHEET_ID}:batchUpdate",
        headers,
        {
            "requests": [
                {
                    "repeatCell": {
                        "range": {"sheetId": existing.get("LifeOS Runtime", runtime_sheet_id or 0), "startRowIndex": 0, "endRowIndex": 1},
                        "cell": {"userEnteredFormat": {"textFormat": {"bold": True, "fontSize": 14}}},
                        "fields": "userEnteredFormat.textFormat",
                    }
                },
                {
                    "autoResizeDimensions": {
                        "dimensions": {"sheetId": runtime_sheet_id, "dimension": "COLUMNS", "startIndex": 0, "endIndex": 4}
                    }
                },
            ]
        },
    )
    return {"status": "updated", "spreadsheet_id": SPREADSHEET_ID, "sheet": "LifeOS Runtime"}


def main() -> int:
    result: dict[str, object] = {"captured_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    errors: list[str] = []
    try:
        result["notion"] = append_lifeos_notion_section()
    except Exception as error:  # noqa: BLE001
        errors.append(f"notion: {error}")
    try:
        result["google_sheets"] = update_lifeos_sheet()
    except Exception as error:  # noqa: BLE001
        errors.append(f"google_sheets: {error}")
    result["errors"] = errors
    result["ok"] = not errors
    out = OUT_DIR / f"lifeos-product-surface-{int(time.time())}.json"
    out.write_text(json.dumps(result, indent=2))
    print(out)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
