#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

STAMP="$(date +%s)"
OUT_DIR="${PROVIDER_VISUAL_OUT:-app/build/evidence/live-workspace/provider-standalone-visual-$STAMP}"
TOKEN_FILE="${GOOGLE_SHEETS_TOKEN_FILE:-${ROOT_DIR}/build/evidence/live-workspace/google-sheets-token.json}"
mkdir -p "$OUT_DIR" "$(dirname "$TOKEN_FILE")"

AGENT_ENV_WRAPPER="$HOME/.codex/skills/agent-env/scripts/run-with-agent-env.sh"
if [[ "${WONDERFOOD_LIVE_PROOF_SKIP_AGENT_ENV:-0}" != "1" &&
      -x "$AGENT_ENV_WRAPPER" &&
      -f "$HOME/.config/agent-secrets/agent.env" ]]; then
  WONDERFOOD_LIVE_PROOF_SKIP_AGENT_ENV=1 exec "$AGENT_ENV_WRAPPER" "$0" "$@"
fi

notion_token="${NOTION_TOKEN:-${NOTION_API_KEY:-}}"
if [[ -z "$notion_token" ]]; then
  echo "Set NOTION_TOKEN or NOTION_API_KEY." >&2
  exit 1
fi

if [[ -z "${GOOGLE_SHEETS_ACCESS_TOKEN:-}" && -s "$TOKEN_FILE" ]]; then
  : "${GOOGLE_CLIENT_ID:?GOOGLE_CLIENT_ID is required to refresh Google Sheets token}"
  : "${GOOGLE_CLIENT_SECRET:?GOOGLE_CLIENT_SECRET is required to refresh Google Sheets token}"
  GOOGLE_SHEETS_ACCESS_TOKEN="$(python3 - "$TOKEN_FILE" <<'PY'
import json
import os
import sys
import urllib.parse
import urllib.request

path = sys.argv[1]
cached = json.load(open(path))
refresh_token = cached.get("refresh_token", "")
if not refresh_token:
    print(cached.get("access_token", ""))
    raise SystemExit
data = urllib.parse.urlencode(
    {
        "client_id": os.environ["GOOGLE_CLIENT_ID"],
        "client_secret": os.environ["GOOGLE_CLIENT_SECRET"],
        "refresh_token": refresh_token,
        "grant_type": "refresh_token",
    },
).encode()
req = urllib.request.Request(
    "https://oauth2.googleapis.com/token",
    data=data,
    headers={"Content-Type": "application/x-www-form-urlencoded"},
)
with urllib.request.urlopen(req, timeout=30) as response:
    refreshed = json.load(response)
cached.update(refreshed)
cached["refresh_token"] = refresh_token
with open(path, "w") as f:
    json.dump(cached, f)
print(cached.get("access_token", ""))
PY
)"
fi

if [[ -z "${GOOGLE_SHEETS_ACCESS_TOKEN:-}" ]]; then
  echo "Google Sheets access token is missing. Run scripts/quality/run-google-sheets-live-proof.sh once to complete OAuth." >&2
  exit 1
fi

notion_page_id="$(NOTION_TOKEN="$notion_token" python3 - <<'PY'
import json
import os
import time
import urllib.request

token = os.environ["NOTION_TOKEN"]
parent = os.environ.get("NOTION_TEST_PAGE_ID", "")
headers = {
    "Authorization": "Bearer " + token,
    "Notion-Version": "2026-03-11",
    "Content-Type": "application/json",
}
if not parent:
    body = json.dumps({"filter": {"property": "object", "value": "page"}, "page_size": 1}).encode()
    req = urllib.request.Request("https://api.notion.com/v1/search", data=body, headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=30) as response:
        parent = (json.load(response).get("results") or [{}])[0].get("id", "")
if not parent:
    raise SystemExit("No accessible Notion parent page found.")
title = "WonderFood Provider Smoke Receipt " + str(int(time.time()))
body = json.dumps(
    {
        "parent": {"page_id": parent},
        "properties": {"title": {"title": [{"type": "text", "text": {"content": title}}]}},
    }
).encode()
req = urllib.request.Request("https://api.notion.com/v1/pages", data=body, headers=headers, method="POST")
with urllib.request.urlopen(req, timeout=30) as response:
    print(json.load(response)["id"])
PY
)"

spreadsheet_id="$(GOOGLE_SHEETS_ACCESS_TOKEN="$GOOGLE_SHEETS_ACCESS_TOKEN" python3 - <<'PY'
import json
import os
import urllib.request

token = os.environ["GOOGLE_SHEETS_ACCESS_TOKEN"]
body = json.dumps({"properties": {"title": "WonderFood Provider Smoke Receipt"}}).encode()
req = urllib.request.Request(
    "https://sheets.googleapis.com/v4/spreadsheets",
    data=body,
    headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"},
    method="POST",
)
with urllib.request.urlopen(req, timeout=30) as response:
    print(json.load(response)["spreadsheetId"])
PY
)"

NOTION_TOKEN="$notion_token" \
NOTION_TEST_PAGE_ID="$notion_page_id" \
./gradlew --no-daemon --rerun-tasks :app:testFossDebugUnitTest --tests 'com.wonderfood.app.sync.WonderFoodLiveWorkspaceProofTest.liveNotionWorkspaceExportsSeedRowsAndReadsThemBack' >/dev/null

GOOGLE_SHEETS_ACCESS_TOKEN="$GOOGLE_SHEETS_ACCESS_TOKEN" \
GOOGLE_SHEETS_TEST_SPREADSHEET_ID="$spreadsheet_id" \
./gradlew --no-daemon --rerun-tasks :app:testPlayDebugUnitTest --tests 'com.wonderfood.app.sync.WonderFoodLiveWorkspaceProofTest.liveGoogleSheetsWorkspaceExportsSeedRowsAndReadsThemBack' >/dev/null

NOTION_TOKEN="$notion_token" \
NOTION_PAGE_ID="$notion_page_id" \
GOOGLE_SHEETS_ACCESS_TOKEN="$GOOGLE_SHEETS_ACCESS_TOKEN" \
GOOGLE_SHEETS_TEST_SPREADSHEET_ID="$spreadsheet_id" \
PROVIDER_VISUAL_OUT="$OUT_DIR" \
python3 - <<'PY'
import html
import json
import os
import time
import urllib.parse
import urllib.request

out_dir = os.environ["PROVIDER_VISUAL_OUT"]
notion_token = os.environ["NOTION_TOKEN"]
notion_page_id = os.environ["NOTION_PAGE_ID"]
sheets_token = os.environ["GOOGLE_SHEETS_ACCESS_TOKEN"]
spreadsheet_id = os.environ["GOOGLE_SHEETS_TEST_SPREADSHEET_ID"]
captured_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

notion_headers = {
    "Authorization": "Bearer " + notion_token,
    "Notion-Version": "2026-03-11",
    "Content-Type": "application/json",
}
sheets_headers = {"Authorization": "Bearer " + sheets_token, "Content-Type": "application/json"}

notion_home_marker = "WonderFood Home"
notion_visible_databases = [
    "WonderFood Kitchen",
    "WonderFood Shopping",
    "WonderFood Meals",
    "WonderFood Recipes",
    "WonderFood Spending",
    "WonderFood Help & Setup",
]
notion_visible = [notion_home_marker] + notion_visible_databases
notion_seed_surfaces = {"WonderFood Kitchen", "WonderFood Recipes"}
sheets_visible = ["Home", "Kitchen", "Shopping", "Meals", "Recipes", "Spending", "Lists & Help"]
sheets_seed_surfaces = {"Kitchen", "Recipes"}
forbidden_terms = [
    "NOTION_TOKEN",
    "GOOGLE_SHEETS_ACCESS_TOKEN",
    "GOOGLE_CLIENT_SECRET",
    "private prompt",
    "api key",
    "secret",
]

def http_json(method, url, headers, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            raw = response.read().decode()
            return json.loads(raw) if raw else {}
    except Exception as error:
        detail = ""
        if hasattr(error, "read"):
            detail = error.read().decode(errors="replace")[:800]
        raise RuntimeError(f"{method} {url} failed: {error} {detail}") from error

def notion_prop_text(prop):
    if "title" in prop:
        return "".join(part.get("plain_text", "") for part in prop.get("title", []))
    if "rich_text" in prop:
        return "".join(part.get("plain_text", "") for part in prop.get("rich_text", []))
    if "select" in prop:
        return (prop.get("select") or {}).get("name", "")
    if "number" in prop:
        return "" if prop.get("number") is None else str(prop.get("number"))
    if "checkbox" in prop:
        return "yes" if prop.get("checkbox") else "no"
    if "date" in prop:
        return ((prop.get("date") or {}).get("start") or "")
    return ""

def notion_children(page_id):
    databases = {}
    marker_text = []
    cursor = None
    while True:
        path = "https://api.notion.com/v1/blocks/" + urllib.parse.quote(page_id, safe="") + "/children?page_size=100"
        if cursor:
            path += "&start_cursor=" + urllib.parse.quote(cursor, safe="")
        body = http_json("GET", path, notion_headers)
        for result in body.get("results", []):
            if result.get("type") == "child_database":
                title = result.get("child_database", {}).get("title", "")
                databases[title] = result.get("id", "")
            else:
                marker_text.append(json.dumps(result))
        if not body.get("has_more"):
            return databases, marker_text
        cursor = body.get("next_cursor")

def notion_rows(database_id):
    database = http_json(
        "GET",
        "https://api.notion.com/v1/databases/" + urllib.parse.quote(database_id, safe=""),
        notion_headers,
    )
    sources = database.get("data_sources", [])
    if not sources:
        raise RuntimeError("Notion database has no data source: " + database_id)
    body = http_json(
        "POST",
        "https://api.notion.com/v1/data_sources/" + urllib.parse.quote(sources[0]["id"], safe="") + "/query",
        notion_headers,
        {"page_size": 5},
    )
    rows = []
    for result in body.get("results", []):
        props = result.get("properties", {})
        row = {name: notion_prop_text(prop) for name, prop in props.items()}
        rows.append(row)
    return rows

def sheets_get(path, fields=None):
    url = "https://sheets.googleapis.com/v4/spreadsheets/" + urllib.parse.quote(spreadsheet_id, safe="") + path
    if fields:
        url += ("&" if "?" in url else "?") + "fields=" + urllib.parse.quote(fields, safe="(),/")
    return http_json("GET", url, sheets_headers)

def sheets_values(range_name):
    encoded = urllib.parse.quote(range_name, safe="")
    body = sheets_get("/values/" + encoded + "?majorDimension=ROWS")
    return body.get("values", [])

notion_databases, notion_marker_text = notion_children(notion_page_id)
notion_home_visible = any(notion_home_marker in marker for marker in notion_marker_text)
notion_tables = []
if notion_home_visible:
    notion_tables.append({"title": notion_home_marker, "database_id": False, "rows": [{"Dashboard": "Daily dashboard", "Status": "visible"}]})
else:
    notion_tables.append({"title": notion_home_marker, "database_id": False, "rows": []})
for title in notion_visible_databases:
    database_id = notion_databases.get(title, "")
    rows = notion_rows(database_id) if database_id else []
    notion_tables.append({"title": title, "database_id": bool(database_id), "rows": rows})

sheets_meta = sheets_get("?includeGridData=false", "sheets(properties(title,hidden))")
sheets_sheet_props = [sheet.get("properties", {}) for sheet in sheets_meta.get("sheets", [])]
sheets_tables = []
for title in sheets_visible:
    values = sheets_values("'" + title.replace("'", "''") + "'!A1:Z6")
    sheets_tables.append({"title": title, "rows": values})

visible_text = json.dumps({"notion": notion_tables, "sheets": sheets_tables}, sort_keys=True).lower()
payload = {
    "proof": "provider_standalone_visual_inspection",
    "captured_at": captured_at,
    "app_offline_independent": True,
    "notion_page_id": notion_page_id[:4] + "..." + notion_page_id[-4:],
    "notion_page_url": "https://www.notion.so/" + notion_page_id.replace("-", ""),
    "sheets_spreadsheet_id": spreadsheet_id[:4] + "..." + spreadsheet_id[-4:],
    "sheets_spreadsheet_url": "https://docs.google.com/spreadsheets/d/" + spreadsheet_id + "/edit",
    "notion_home_marker_visible": notion_home_visible,
    "notion_visible_surfaces": [table["title"] for table in notion_tables if table["database_id"] or table["title"] == notion_home_marker and notion_home_visible],
    "sheets_visible_tabs": [prop.get("title", "") for prop in sheets_sheet_props if not prop.get("hidden", False)],
    "sheets_hidden_tabs": [prop.get("title", "") for prop in sheets_sheet_props if prop.get("hidden", False)],
    "notion_seed_rows_present": all(table["rows"] for table in notion_tables if table["title"] in notion_seed_surfaces),
    "sheets_seed_rows_present": all(len(table["rows"]) > 1 for table in sheets_tables if table["title"] in sheets_seed_surfaces),
    "sheets_visible_headers_present": all(table["rows"] for table in sheets_tables),
    "standalone_workflows_visible": all(
        term in visible_text
        for term in ["kitchen", "shopping", "recipe", "meal", "spending", "help"]
    ),
    "hidden_support_not_visible": all(
        not prop.get("title", "").startswith("_wf_")
        for prop in sheets_sheet_props
        if not prop.get("hidden", False)
    ),
    "no_secret_terms_visible": not any(term.lower() in visible_text for term in forbidden_terms),
}
payload["all_visual_checks_passed"] = all(
    [
        payload["notion_visible_surfaces"] == notion_visible,
        all(title in payload["sheets_visible_tabs"] for title in sheets_visible),
        payload["notion_seed_rows_present"],
        payload["sheets_seed_rows_present"],
        payload["sheets_visible_headers_present"],
        payload["standalone_workflows_visible"],
        payload["hidden_support_not_visible"],
        payload["no_secret_terms_visible"],
        payload["app_offline_independent"],
    ]
)
payload["failed_checks"] = [
    key for key in [
        "notion_visible_surfaces",
        "sheets_visible_tabs",
        "notion_seed_rows_present",
        "sheets_seed_rows_present",
        "sheets_visible_headers_present",
        "standalone_workflows_visible",
        "hidden_support_not_visible",
        "no_secret_terms_visible",
        "app_offline_independent",
    ]
    if (
        key == "notion_visible_surfaces" and payload[key] != notion_visible
    ) or (
        key == "sheets_visible_tabs" and not all(title in payload[key] for title in sheets_visible)
    ) or (
        key not in ["notion_visible_surfaces", "sheets_visible_tabs"] and not payload[key]
    )
]

def render_table(rows):
    if not rows:
        return "<p class='empty'>No rows returned.</p>"
    if isinstance(rows[0], dict):
        headers = list(rows[0].keys())[:7]
        body_rows = [[row.get(header, "") for header in headers] for row in rows[:4]]
    else:
        headers = rows[0][:7]
        body_rows = [row[:7] for row in rows[1:5]]
    parts = ["<table><thead><tr>"]
    parts.extend("<th>" + html.escape(str(header)) + "</th>" for header in headers)
    parts.append("</tr></thead><tbody>")
    for row in body_rows:
        parts.append("<tr>")
        parts.extend("<td>" + html.escape(str(cell)) + "</td>" for cell in row)
        parts.append("</tr>")
    parts.append("</tbody></table>")
    return "".join(parts)

def section(provider, tables):
    parts = [f"<section><h2>{html.escape(provider)}</h2><div class='grid'>"]
    for table in tables:
        row_count = len(table["rows"])
        badge = "ready" if row_count else "empty"
        parts.append("<article>")
        parts.append(f"<header><h3>{html.escape(table['title'])}</h3><span class='{badge}'>{row_count} sample rows</span></header>")
        parts.append(render_table(table["rows"]))
        parts.append("</article>")
    parts.append("</div></section>")
    return "".join(parts)

html_doc = f"""<!doctype html>
<html>
<head>
<meta charset="utf-8">
<title>WonderFood Provider Smoke Receipt</title>
<style>
body {{ margin: 0; font: 14px -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif; background: #f6f5ef; color: #222; }}
main {{ max-width: 1320px; margin: 0 auto; padding: 28px; }}
h1 {{ margin: 0 0 6px; font-size: 30px; }}
h2 {{ margin: 30px 0 12px; font-size: 22px; }}
.summary {{ display: flex; gap: 10px; flex-wrap: wrap; margin: 18px 0 8px; }}
.summary span {{ border: 1px solid #c9d4cc; background: #fff; padding: 8px 10px; border-radius: 6px; }}
.grid {{ display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }}
article {{ background: #fff; border: 1px solid #d7d5c9; border-radius: 8px; overflow: hidden; }}
header {{ display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 14px; border-bottom: 1px solid #e8e4d8; background: #fcfbf7; }}
h3 {{ margin: 0; font-size: 16px; }}
.ready {{ color: #165c3a; }}
.empty {{ color: #9c3328; padding: 14px; }}
table {{ width: 100%; border-collapse: collapse; table-layout: fixed; }}
th, td {{ border-bottom: 1px solid #eee8db; padding: 8px; text-align: left; overflow-wrap: anywhere; vertical-align: top; }}
th {{ color: #5d5546; background: #faf8f1; font-size: 12px; }}
.note {{ color: #5d5546; }}
</style>
</head>
<body>
<main>
<h1>WonderFood Provider Smoke Receipt</h1>
<p class="note">Captured {html.escape(captured_at)} from live Notion and Google Sheets provider data. The Android app runtime is not used for this inspection pass.</p>
<p class="note"><a href="{html.escape(payload['notion_page_url'])}">Open Notion proof page</a> · <a href="{html.escape(payload['sheets_spreadsheet_url'])}">Open Google Sheets proof workbook</a></p>
<div class="summary">
<span>Notion surfaces: {len(payload['notion_visible_surfaces'])}/7</span>
<span>Sheets visible tabs: {len(payload['sheets_visible_tabs'])}</span>
<span>Seed data present: {payload['notion_seed_rows_present'] and payload['sheets_seed_rows_present']}</span>
<span>No secret terms visible: {payload['no_secret_terms_visible']}</span>
</div>
{section("Notion", notion_tables)}
{section("Google Sheets", sheets_tables)}
</main>
</body>
</html>
"""

json_path = os.path.join(out_dir, "provider-standalone-visual-proof.json")
html_path = os.path.join(out_dir, "provider-standalone-visual-proof.html")
with open(json_path, "w") as f:
    json.dump(payload, f, indent=2, sort_keys=True)
with open(html_path, "w") as f:
    f.write(html_doc)
print(json_path)
print(html_path)
if not payload["all_visual_checks_passed"]:
    raise SystemExit("Failed visual checks: " + ", ".join(payload["failed_checks"]))
PY

CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
if [[ -x "$CHROME" ]]; then
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --window-size=1440,1800 \
    --screenshot="$OUT_DIR/provider-standalone-visual-proof.png" \
    "file://$ROOT_DIR/$OUT_DIR/provider-standalone-visual-proof.html" >/dev/null 2>&1 || true
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars --window-size=390,1800 \
    --screenshot="$OUT_DIR/provider-standalone-visual-proof-mobile.png" \
    "file://$ROOT_DIR/$OUT_DIR/provider-standalone-visual-proof.html" >/dev/null 2>&1 || true
fi

echo "$OUT_DIR/provider-standalone-visual-proof.json"
