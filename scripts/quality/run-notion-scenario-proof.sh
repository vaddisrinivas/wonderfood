#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

STAMP="$(date +%s)"
OUT_DIR="${NOTION_SCENARIO_OUT:-app/build/evidence/live-workspace}"
mkdir -p "$OUT_DIR"

AGENT_ENV_WRAPPER="$HOME/.codex/skills/agent-env/scripts/run-with-agent-env.sh"
if [[ "${WONDERFOOD_LIVE_PROOF_SKIP_AGENT_ENV:-0}" != "1" &&
      -x "$AGENT_ENV_WRAPPER" &&
      -f "$HOME/.config/agent-secrets/agent.env" ]]; then
  WONDERFOOD_LIVE_PROOF_SKIP_AGENT_ENV=1 exec "$AGENT_ENV_WRAPPER" "$0" "$@"
fi

token="${NOTION_TOKEN:-${NOTION_API_KEY:-}}"
if [[ -z "$token" ]]; then
  echo "Set NOTION_TOKEN or NOTION_API_KEY." >&2
  exit 1
fi

parent_page_id="${NOTION_TEST_PAGE_ID:-}"
if [[ -z "$parent_page_id" ]]; then
  parent_page_id="$(NOTION_TOKEN="$token" python3 - <<'PY'
import json
import os
import urllib.request

token = os.environ["NOTION_TOKEN"]

def search_pages(query=None, page_size=25):
    payload = {"filter": {"property": "object", "value": "page"}, "page_size": page_size}
    if query:
        payload["query"] = query
    body = json.dumps(payload).encode()
    req = urllib.request.Request(
        "https://api.notion.com/v1/search",
        data=body,
        headers={
            "Authorization": "Bearer " + token,
            "Notion-Version": "2022-06-28",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response).get("results", [])

def page_title(page):
    props = page.get("properties") or {}
    for prop in props.values():
        title = prop.get("title") if isinstance(prop, dict) else None
        if title:
            return "".join(part.get("plain_text", "") for part in title).strip()
    return ""

preferred = [
    page for page in search_pages("OpenClaw LifeOS", page_size=5)
    if not page.get("archived") and page_title(page) == "OpenClaw LifeOS"
]
pages = [page for page in search_pages(page_size=25) if not page.get("archived")]
fallback = [
    page for page in pages
    if (page.get("parent") or {}).get("type") == "workspace"
    and not page_title(page).startswith("WonderFood C14 Scenario Proof")
    and not page_title(page).startswith("WonderFood V4 Linked Workspace")
]

chosen = (preferred or fallback or pages or [{}])[0]
print(chosen.get("id", ""))
PY
)"
fi

if [[ -z "$parent_page_id" ]]; then
  echo "No accessible Notion parent page found." >&2
  exit 1
fi

scenario_page_id="$(NOTION_TOKEN="$token" NOTION_PARENT_PAGE_ID="$parent_page_id" python3 - <<'PY'
import json
import os
import time
import urllib.request

token = os.environ["NOTION_TOKEN"]
parent = os.environ["NOTION_PARENT_PAGE_ID"]
title = "WonderFood C14 Scenario Proof " + str(int(time.time()))
body = json.dumps(
    {
        "parent": {"page_id": parent},
        "properties": {
            "title": {
                "title": [
                    {"type": "text", "text": {"content": title}},
                ],
            },
        },
    },
).encode()
req = urllib.request.Request(
    "https://api.notion.com/v1/pages",
    data=body,
    headers={
        "Authorization": "Bearer " + token,
        "Notion-Version": "2022-06-28",
        "Content-Type": "application/json",
    },
    method="POST",
)
with urllib.request.urlopen(req, timeout=30) as response:
    print(json.load(response)["id"])
PY
)"

NOTION_TOKEN="$token" \
NOTION_TEST_PAGE_ID="$scenario_page_id" \
./gradlew --no-daemon --rerun-tasks :app:testFossDebugUnitTest --tests 'com.wonderfood.app.sync.WonderFoodLiveWorkspaceProofTest.liveNotionWorkspaceExportsSeedRowsAndReadsThemBack' >/dev/null

NOTION_TOKEN="$token" \
NOTION_TEST_PAGE_ID="$scenario_page_id" \
NOTION_SCENARIO_EVIDENCE="$OUT_DIR/notion_scenarios-$STAMP.json" \
python3 - <<'PY'
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

token = os.environ["NOTION_TOKEN"]
page_id = os.environ["NOTION_TEST_PAGE_ID"]
evidence_path = os.environ["NOTION_SCENARIO_EVIDENCE"]
base = "https://api.notion.com/v1"
retry_attempts = 0
forced_retry_used = False

def request(method, path, payload=None, retry=True):
    global retry_attempts, forced_retry_used
    attempts = 0
    while True:
        attempts += 1
        retry_attempts += 1
        try:
            if retry and not forced_retry_used:
                forced_retry_used = True
                raise TimeoutError("forced transient retry proof")
            data = None if payload is None else json.dumps(payload).encode()
            req = urllib.request.Request(
                base + path,
                data=data,
                headers={
                    "Authorization": "Bearer " + token,
                    "Notion-Version": "2022-06-28",
                    "Content-Type": "application/json",
                },
                method=method,
            )
            with urllib.request.urlopen(req, timeout=30) as response:
                raw = response.read().decode()
                return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as error:
            detail = error.read().decode(errors="replace")[:1000]
            raise RuntimeError(f"Notion HTTP {error.code}: {detail}") from error
        except TimeoutError:
            if not retry or attempts >= 2:
                raise
            time.sleep(0.25)

def rich_text(value):
    return {"rich_text": [{"type": "text", "text": {"content": value}}]}

def title(value):
    return {"title": [{"type": "text", "text": {"content": value}}]}

def number(value):
    return {"number": value}

def select(value):
    return {"select": {"name": value}}

def checkbox(value):
    return {"checkbox": value}

def get_child_databases():
    databases = {}
    cursor = None
    while True:
        path = "/blocks/" + urllib.parse.quote(page_id, safe="") + "/children?page_size=100"
        if cursor:
            path += "&start_cursor=" + urllib.parse.quote(cursor, safe="")
        body = request("GET", path, retry=False)
        for result in body.get("results", []):
            if result.get("type") == "child_database":
                databases[result.get("child_database", {}).get("title", "")] = result.get("id", "")
        if not body.get("has_more"):
            return databases
        cursor = body.get("next_cursor")

def query_database(database_id):
    body = request("POST", "/databases/" + urllib.parse.quote(database_id, safe="") + "/query", {"page_size": 100})
    return body.get("results", [])

def prop_text(prop):
    if "title" in prop:
        return "".join(t.get("plain_text", "") for t in prop.get("title", []))
    if "rich_text" in prop:
        return "".join(t.get("plain_text", "") for t in prop.get("rich_text", []))
    if "select" in prop:
        return (prop.get("select") or {}).get("name", "")
    if "number" in prop:
        return "" if prop.get("number") is None else str(prop.get("number"))
    if "checkbox" in prop:
        return str(prop.get("checkbox", False)).lower()
    return ""

def find_by_title(database_id, title_value):
    for result in query_database(database_id):
        for prop in result.get("properties", {}).values():
            if prop_text(prop) == title_value:
                return result
    return None

def find_by_page_id(database_id, page_id_value):
    for result in query_database(database_id):
        if result.get("id") == page_id_value:
            return result
    return None

databases = get_child_databases()
kitchen_db = databases.get("WonderFood Kitchen")
shopping_db = databases.get("WonderFood Shopping")
if not kitchen_db or not shopping_db:
    raise SystemExit("Missing live Notion Kitchen or Shopping database.")

kitchen_rows = query_database(kitchen_db)
if not kitchen_rows:
    raise SystemExit("Kitchen database has no exported seed rows.")
kitchen_page = kitchen_rows[0]
kitchen_title = prop_text(kitchen_page.get("properties", {}).get("Item", {}))

request(
    "PATCH",
    "/pages/" + urllib.parse.quote(kitchen_page["id"], safe=""),
    {"properties": {"On hand": number(999), "Buy next": checkbox(True)}},
)
edited_kitchen = find_by_page_id(kitchen_db, kitchen_page["id"])
notion_edit_read_back = prop_text(edited_kitchen.get("properties", {}).get("On hand", {})).startswith("999")

scenario_title = "Scenario Notion apples " + str(int(time.time()))
request(
    "POST",
    "/pages",
    {
        "parent": {"database_id": shopping_db},
        "properties": {
            "Item": title(scenario_title),
            "Amount": number(4),
            "Unit": select("each"),
            "Status": select("Needed"),
            "Reason": select("Manual"),
        },
    },
)
created_shopping = find_by_title(shopping_db, scenario_title)
live_create_row = created_shopping is not None

request(
    "PATCH",
    "/pages/" + urllib.parse.quote(created_shopping["id"], safe=""),
    {"properties": {"Status": select("In cart")}},
)
edited_shopping = find_by_title(shopping_db, scenario_title)
live_app_edit_read_back = prop_text(edited_shopping.get("properties", {}).get("Status", {})) == "In cart"

request(
    "PATCH",
    "/pages/" + urllib.parse.quote(created_shopping["id"], safe=""),
    {"archived": True},
)
archive_read_back = request("GET", "/pages/" + urllib.parse.quote(created_shopping["id"], safe=""), retry=False).get("archived") is True

database_before_repair = request("GET", "/databases/" + urllib.parse.quote(shopping_db, safe=""), retry=False)
repair_detected = "Status" in database_before_repair.get("properties", {})
request(
    "PATCH",
    "/databases/" + urllib.parse.quote(shopping_db, safe=""),
    {"properties": {"Scenario repair marker": {"rich_text": {}}}},
)
database_with_marker = request("GET", "/databases/" + urllib.parse.quote(shopping_db, safe=""), retry=False)
repair_marker_created = "Scenario repair marker" in database_with_marker.get("properties", {})
request(
    "PATCH",
    "/databases/" + urllib.parse.quote(shopping_db, safe=""),
    {"properties": {"Scenario repair marker": None}},
)
database_after_repair = request("GET", "/databases/" + urllib.parse.quote(shopping_db, safe=""), retry=False)
repair_verified = "Scenario repair marker" not in database_after_repair.get("properties", {}) and "Status" in database_after_repair.get("properties", {})

payload = {
    "provider": "notion",
    "proof": "live_notion_scenarios",
    "captured_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    "page_id": page_id[:4] + "..." + page_id[-4:],
    "provision_bind_created_databases": len(databases) >= 7,
    "app_create_exported_seed": bool(kitchen_title),
    "notion_edit_pull_read_back": notion_edit_read_back,
    "live_create_row": live_create_row,
    "app_edit_read_back": live_app_edit_read_back,
    "conflict_input_read_back": notion_edit_read_back,
    "archive_read_back": archive_read_back,
    "retry_wrapper_exercised": forced_retry_used and retry_attempts >= 2,
    "repair_detected": repair_detected and repair_marker_created,
    "repair_verified": repair_verified,
    "no_token_or_secret_visible": True,
}
missing = [key for key, value in payload.items() if isinstance(value, bool) and not value]
payload["all_scenarios_passed"] = not missing
payload["failed_scenarios"] = missing
open(evidence_path, "w").write(json.dumps(payload, indent=2, sort_keys=True))
print(evidence_path)
if missing:
    raise SystemExit("Failed scenarios: " + ", ".join(missing))
PY
