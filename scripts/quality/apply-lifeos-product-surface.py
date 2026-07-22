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
LIFERPG_MARKER = "LiFE RPG benchmark folded into WonderFood LifeOS"
SYNC_LOOP_MARKER = "LifeOS source sync loop installed by WonderFood"
NOTION_VERSION = "2026-03-11"


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
        "Notion-Version": NOTION_VERSION,
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


def append_liferpg_benchmark_section() -> dict:
    headers = notion_headers()
    children = request_json(
        "GET",
        f"https://api.notion.com/v1/blocks/{NOTION_PAGE_ID}/children?page_size=100",
        headers,
    ).get("results", [])
    existing_text = json.dumps(children)
    if LIFERPG_MARKER in existing_text:
        return {"status": "already_present", "page_id": NOTION_PAGE_ID}

    blocks = [
        {
            "object": "block",
            "type": "heading_2",
            "heading_2": {"rich_text": [{"type": "text", "text": {"content": "🎮 LiFE RPG benchmark upgrades"}}]},
        },
        {
            "object": "block",
            "type": "callout",
            "callout": {
                "icon": {"emoji": "🧭"},
                "rich_text": [
                    {
                        "type": "text",
                        "text": {
                            "content": f"{LIFERPG_MARKER}. Borrow the useful structure, not the toy skin: quests, habits, boss fights, P.A.R.A., daily journal, RPGenie-style assistant posture, sample/empty parity, and template health checks."
                        },
                    }
                ],
            },
        },
        {
            "object": "block",
            "type": "bulleted_list_item",
            "bulleted_list_item": {
                "rich_text": [{"type": "text", "text": {"content": "Food quests: weekly cook/shop/clean/eat objectives linked to meals, groceries, recipes, spend, and Health Connect context."}}]
            },
        },
        {
            "object": "block",
            "type": "bulleted_list_item",
            "bulleted_list_item": {
                "rich_text": [{"type": "text", "text": {"content": "Habit loops: water/protein/home-cooked/prep as positive loops; waste/overspend/duplicate-buying as review loops."}}]
            },
        },
        {
            "object": "block",
            "type": "bulleted_list_item",
            "bulleted_list_item": {
                "rich_text": [{"type": "text", "text": {"content": "Template health: avoid frozen @now after duplication; keep sample and empty templates equivalent; surface relation/rollup/schema checks visibly."}}]
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


def append_sync_loop_section() -> dict:
    headers = notion_headers()
    children = request_json(
        "GET",
        f"https://api.notion.com/v1/blocks/{NOTION_PAGE_ID}/children?page_size=100",
        headers,
    ).get("results", [])
    existing_text = json.dumps(children)
    if SYNC_LOOP_MARKER in existing_text:
        return {"status": "already_present", "page_id": NOTION_PAGE_ID}

    blocks = [
        {
            "object": "block",
            "type": "heading_2",
            "heading_2": {"rich_text": [{"type": "text", "text": {"content": "🔁 Source sync loop"}}]},
        },
        {
            "object": "block",
            "type": "callout",
            "callout": {
                "icon": {"emoji": "🔁"},
                "rich_text": [
                    {
                        "type": "text",
                        "text": {
                            "content": f"{SYNC_LOOP_MARKER}. Notion, Google Sheets, Android, and MCP/GPT now expose the same domain catalog, source cards, template health checks, and review-only command loop."
                        },
                    }
                ],
            },
        },
        {
            "object": "block",
            "type": "bulleted_list_item",
            "bulleted_list_item": {
                "rich_text": [{"type": "text", "text": {"content": "Notion: human dashboard, relations, rollups, template health, and presentation layer."}}]
            },
        },
        {
            "object": "block",
            "type": "bulleted_list_item",
            "bulleted_list_item": {
                "rich_text": [{"type": "text", "text": {"content": "Google Sheets: auditable workbook mirror with LifeOS Runtime and LifeOS Sync Loop tabs."}}]
            },
        },
        {
            "object": "block",
            "type": "bulleted_list_item",
            "bulleted_list_item": {
                "rich_text": [{"type": "text", "text": {"content": "Android + MCP/GPT: native Food surface, source-quoting chat, schema/catalog resource, and review-only proposals."}}]
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
    sync_loop_sheet_id = existing.get("LifeOS Sync Loop")
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
    if sync_loop_sheet_id is None:
        requests.append(
            {
                "addSheet": {
                    "properties": {
                        "title": "LifeOS Sync Loop",
                        "gridProperties": {"rowCount": 80, "columnCount": 8},
                        "tabColor": {"red": 0.37, "green": 0.24, "blue": 0.70},
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
    sync_loop_sheet_id = existing["LifeOS Sync Loop"]

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
        ["Benchmark", "LiFE RPG 2.0", "Quests, habits, boss fights, P.A.R.A., RPGenie, sample/empty parity", "Borrowed"],
        ["Food loop", "Food quests", "Weekly cook/shop/clean/eat objectives", "Config"],
        ["Food loop", "Good habits", "Water, protein, home-cooked meals, prep blocks", "Config"],
        ["Food loop", "Bad-habit reviews", "Waste, missed meals, overspend, duplicate buying", "Config"],
        ["Template health", "@now duplication risk", "Prefer explicit dynamic date buttons/checks and visible health checklist", "Required"],
        ["Template health", "Source quoting", "App/local, Notion, Sheets, MCP, and web/file sources visible in chat", "Required"],
    ]
    value_url = f"https://sheets.googleapis.com/v4/spreadsheets/{SPREADSHEET_ID}/values/{urllib.parse.quote('LifeOS Runtime!A1:D40')}"
    request_json("PUT", value_url + "?valueInputOption=RAW", headers, {"values": values})
    sync_values = [
        ["LifeOS 2026 — Source Sync Loop", "", "", "", ""],
        ["Hop", "Surface", "Role", "Current evidence", "Next product bar"],
        ["1", "Notion", "Human data plane: dashboards, relations, rollups, template health", "LifeOS 2026 page updated", "Bidirectional schema diff + source snippets for chat"],
        ["2", "Google Sheets", "Spreadsheet-primary mirror: auditable rows, formulas, imports/exports", "LifeOS Runtime + Sync Loop tabs updated", "Formula-backed schema health + conflict inbox"],
        ["3", "Android", "Native Food app: review queue, local canonical store, Health Connect context", "S23U installed and screenshots captured", "Background sync jobs + source cards from active data home"],
        ["4", "MCP/GPT", "Skills, schemas, validation, review-only command envelopes", "domain-catalog resource exposed", "GPT/plugin parity with Android chat behavior"],
        ["Template health", "@now risk", "Avoid frozen duplication timestamps", "LiFE RPG benchmark added", "Visible checks in every domain template"],
        ["Source quoting", "Chat", "Answers cite app/local, Notion, Sheets, MCP, web/file sources", "S23U source-card screenshot captured", "Live Notion/Sheets snippet retrieval"],
    ]
    sync_value_url = f"https://sheets.googleapis.com/v4/spreadsheets/{SPREADSHEET_ID}/values/{urllib.parse.quote('LifeOS Sync Loop!A1:E40')}"
    request_json("PUT", sync_value_url + "?valueInputOption=RAW", headers, {"values": sync_values})
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
                {
                    "repeatCell": {
                        "range": {"sheetId": sync_loop_sheet_id, "startRowIndex": 0, "endRowIndex": 2},
                        "cell": {"userEnteredFormat": {"textFormat": {"bold": True}}},
                        "fields": "userEnteredFormat.textFormat",
                    }
                },
                {
                    "autoResizeDimensions": {
                        "dimensions": {"sheetId": sync_loop_sheet_id, "dimension": "COLUMNS", "startIndex": 0, "endIndex": 5}
                    }
                },
            ]
        },
    )
    return {"status": "updated", "spreadsheet_id": SPREADSHEET_ID, "sheets": ["LifeOS Runtime", "LifeOS Sync Loop"]}


def main() -> int:
    result: dict[str, object] = {"captured_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    errors: list[str] = []
    try:
        result["notion"] = append_lifeos_notion_section()
    except Exception as error:  # noqa: BLE001
        errors.append(f"notion: {error}")
    try:
        result["notion_liferpg"] = append_liferpg_benchmark_section()
    except Exception as error:  # noqa: BLE001
        errors.append(f"notion_liferpg: {error}")
    try:
        result["notion_sync_loop"] = append_sync_loop_section()
    except Exception as error:  # noqa: BLE001
        errors.append(f"notion_sync_loop: {error}")
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
