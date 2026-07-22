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
