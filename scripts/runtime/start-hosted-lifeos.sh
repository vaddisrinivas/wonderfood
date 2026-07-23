#!/usr/bin/env bash
set -euo pipefail

ROOT="/Users/srinivasvaddi/Projects/wonderfood"
BUNDLED_NODE="/Users/srinivasvaddi/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/bin"
export PATH="$BUNDLED_NODE:/opt/homebrew/bin:/usr/bin:/bin"
export NPM_CONFIG_CACHE="/tmp/wonderfood-server-npm-cache2"

export PORT="8790"
export LIFEOS_SERVER_HOST="0.0.0.0"
export OPENAI_MODEL="gpt-4o-mini"
export NOTION_DATA_SOURCE_ID="3a7dace3-e35e-4ce9-b817-0b80af6e413c"
export GOOGLE_SHEETS_TOKEN_FILE="$ROOT/build/evidence/live-workspace/google-sheets-token.json"
export GOOGLE_SHEETS_SPREADSHEET_ID="1WpEwm07ApcnuiLDVhzl8vy4D5kU8KjmtbAVC4qLphcU"
export LIFEOS_CHAT_CONVERSATIONS_PATH="/tmp/wonderfood-lan-chat-final.json"
export LIFEOS_MCP_STATE_PATH="/tmp/wonderfood-lan-mcp-final.json"
export LIFEOS_SERVER_TOKEN="$(security find-generic-password -a "$USER" -s wonderfood-lifeos-server-token -w)"
export LIFEOS_MCP_TOKEN="$LIFEOS_SERVER_TOKEN"

mkdir -p "$NPM_CONFIG_CACHE"

# Keep the Mac-backed hosted connector from losing Sheets after the one-hour
# access token expires. The refresh token and OAuth client values remain in the
# private agent environment; only the refreshed cache is written locally.
refresh_sheets_token() {
  if [[ ! -s "$GOOGLE_SHEETS_TOKEN_FILE" || -z "${GOOGLE_CLIENT_ID:-}" || -z "${GOOGLE_CLIENT_SECRET:-}" ]]; then
    return 0
  fi
  local refresh_token response
  refresh_token="$(python3 - "$GOOGLE_SHEETS_TOKEN_FILE" <<'PY'
import json, sys
try:
    value = json.load(open(sys.argv[1])).get('refresh_token', '')
except Exception:
    value = ''
print(value)
PY
)"
  [[ -n "$refresh_token" ]] || return 0
  if ! response="$(curl -fsS --data-urlencode "client_id=$GOOGLE_CLIENT_ID" --data-urlencode "client_secret=$GOOGLE_CLIENT_SECRET" --data-urlencode "refresh_token=$refresh_token" --data-urlencode "grant_type=refresh_token" https://oauth2.googleapis.com/token)"; then
    return 0
  fi
  RESPONSE="$response" TOKEN_FILE="$GOOGLE_SHEETS_TOKEN_FILE" python3 - <<'PY'
import json, os
path = os.environ['TOKEN_FILE']
try:
    with open(path) as f:
        cached = json.load(f)
    refreshed = json.loads(os.environ['RESPONSE'])
    cached.update(refreshed)
    cached['refresh_token'] = cached.get('refresh_token') or json.loads(os.environ['RESPONSE']).get('refresh_token', '')
    with open(path, 'w') as f:
        json.dump(cached, f)
except Exception:
    pass
PY
}
refresh_sheets_token

server_log="/tmp/wonderfood-lifeos-server.log"
(cd "$ROOT" && npm --prefix server start >>"$server_log" 2>&1) &
server_pid=$!
cleanup() {
  kill "$server_pid" 2>/dev/null || true
  wait "$server_pid" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

for _ in $(seq 1 30); do
  if curl -fsS --max-time 2 http://127.0.0.1:8790/health >/dev/null; then
    break
  fi
  sleep 1
done
curl -fsS --max-time 2 http://127.0.0.1:8790/health >/dev/null

tunnel_token="$(curl -fsS "https://api.cloudflare.com/client/v4/accounts/$CLOUDFLARE_ACCOUNT_ID/cfd_tunnel/cb4a47c0-fb40-4407-8bb7-37c4cd7d370b/token" \
  --header "X-Auth-Key: $CLOUDFLARE_API_KEY" \
  --header "X-Auth-Email: $CLOUDFLARE_EMAIL" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["result"])')"

exec /opt/homebrew/opt/cloudflared/bin/cloudflared tunnel --no-autoupdate run --token "$tunnel_token"
