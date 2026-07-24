#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

OUT_DIR="${MCP_BRIDGE_PROOF_OUT:-app/build/evidence/mcp-bridge}"
mkdir -p "$OUT_DIR"

python3 - <<'PY'
import json
import subprocess
import sys
from pathlib import Path

root = Path.cwd()
out_dir = root / "app/build/evidence/mcp-bridge"
out_dir.mkdir(parents=True, exist_ok=True)
server = root / "scripts/mcp/wonderfood_mcp_server.py"

requests = [
    {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": "2024-11-05", "capabilities": {}, "clientInfo": {"name": "proof", "version": "1"}}},
    {"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}},
    {"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}},
    {"jsonrpc": "2.0", "id": 3, "method": "resources/list", "params": {}},
    {"jsonrpc": "2.0", "id": 4, "method": "tools/call", "params": {"name": "wonderfood.status", "arguments": {}}},
    {"jsonrpc": "2.0", "id": 5, "method": "tools/call", "params": {"name": "wonderfood.get_resource", "arguments": {"uri": "wonderfood://skill/bundled-food"}}},
    {"jsonrpc": "2.0", "id": 6, "method": "tools/call", "params": {"name": "wonderfood.propose_app_link", "arguments": {"requestId": "mcp-proof-001", "actions": [{"type": "inventory.add", "name": "Eggs", "quantity": "12", "zone": "fridge"}]}}},
    {"jsonrpc": "2.0", "id": 7, "method": "tools/call", "params": {"name": "wonderfood.validate_command_envelope", "arguments": {"envelope": {
        "schema_version": "wf.ai.command-envelope.v1",
        "catalog_version": "wf.ai.skill-catalog.v1",
        "skill_id": "inventory",
        "skill_version": "1.0.0",
        "envelope_id": "env_mcp_proof_001",
        "idempotency_key": "mcp-proof-001",
        "status": "needs_confirmation",
        "evidence": [{"evidence_id": "ev_user_text", "type": "user_text", "source_ref": "mcp-proof", "quote": "add eggs", "observed_at": None, "confidence": 1.0}],
        "commands": [{"type": "inventory.add_lot", "payload": {"name": "Eggs", "quantity": "12"}}],
        "confidence": {"score": 0.9, "rationale": "explicit item and count"},
        "confirmation": {"required": True, "level": "review", "reason": "external MCP proposal", "prompt": "Review before saving"},
        "warnings": [],
        "unsupported": None
    }}}},
    {"jsonrpc": "2.0", "id": 8, "method": "tools/call", "params": {"name": "wonderfood.wrap_proposal_package", "arguments": {"proposal_id": "pkg_mcp_proof_001", "producer": "WonderFood MCP proof", "command_envelope": {
        "schema_version": "wf.ai.command-envelope.v1",
        "catalog_version": "wf.ai.skill-catalog.v1",
        "skill_id": "inventory",
        "skill_version": "1.0.0",
        "envelope_id": "env_mcp_proof_001",
        "idempotency_key": "mcp-proof-001",
        "status": "needs_confirmation",
        "evidence": [{"evidence_id": "ev_user_text", "type": "user_text", "source_ref": "mcp-proof", "quote": "add eggs", "observed_at": None, "confidence": 1.0}],
        "commands": [{"type": "inventory.add_lot", "payload": {"name": "Eggs", "quantity": "12"}}],
        "confidence": {"score": 0.9, "rationale": "explicit item and count"},
        "confirmation": {"required": True, "level": "review", "reason": "external MCP proposal", "prompt": "Review before saving"},
        "warnings": [],
        "unsupported": None
    }}}},
]

wire_input = "\n".join(json.dumps(request) for request in requests) + "\n"
proc = subprocess.run(
    [sys.executable, str(server)],
    input=wire_input,
    text=True,
    capture_output=True,
    check=True,
    timeout=20,
)
responses = [json.loads(line) for line in proc.stdout.splitlines() if line.strip()]
by_id = {response["id"]: response for response in responses if "id" in response}

def tool_text(response_id):
    return by_id[response_id]["result"]["content"][0]["text"]

tool_names = [tool["name"] for tool in by_id[2]["result"]["tools"]]
resource_uris = [resource["uri"] for resource in by_id[3]["result"]["resources"]]
status = json.loads(tool_text(4))
link = json.loads(tool_text(6))
validation = json.loads(tool_text(7))
package = json.loads(tool_text(8))
skill_text = tool_text(5)

checks = {
    "initialize_ok": by_id[1]["result"]["serverInfo"]["name"] == "wonderfood-local-bridge",
    "tools_visible": all(name in tool_names for name in [
        "wonderfood.status",
        "wonderfood.get_resource",
        "wonderfood.validate_command_envelope",
        "wonderfood.propose_app_link",
        "wonderfood.wrap_proposal_package",
    ]),
    "resources_visible": all(uri in resource_uris for uri in [
        "wonderfood://skill/bundled-food",
        "wonderfood://schema/command-envelope-v1",
        "wonderfood://schema/proposal-package-v1",
        "wonderfood://contract/app-command",
    ]),
    "status_review_only": "review-only" in status["safety"],
    "skill_contract_visible": "LifeOS Food Skill" in skill_text and "Response contract" in skill_text,
    "link_review_only": link["reviewOnly"] and "wonderfood.app/action" in link["url"] and "inventory.add" in link["url"],
    "envelope_valid": validation["valid"] and validation["reviewOnly"],
    "package_valid": package["valid"] and package["package"]["schema_version"] == "wf.proposal-package.v1",
    "no_stderr": proc.stderr.strip() == "",
}
proof = {
    "proof": "wonderfood_mcp_bridge_stdio",
    "checks": checks,
    "all_checks_passed": all(checks.values()),
    "tool_names": tool_names,
    "resource_uris": resource_uris,
    "sample_link": link["url"],
    "sample_package_schema": package["package"]["schema_version"],
}
(out_dir / "mcp-bridge-proof.json").write_text(json.dumps(proof, indent=2, sort_keys=True), encoding="utf-8")
(out_dir / "mcp-bridge-wire.jsonl").write_text(proc.stdout, encoding="utf-8")
print(out_dir / "mcp-bridge-proof.json")
if not proof["all_checks_passed"]:
    raise SystemExit("MCP bridge proof failed: " + ", ".join(k for k, v in checks.items() if not v))
PY
