#!/usr/bin/env python3
"""WonderFood local MCP bridge.

Stdio JSON-RPC server exposing the existing WonderFood skill/schema/app handoff
contracts to GPT/MCP clients. It is intentionally review-only: generated app
links and packages stage proposals in WonderFood; they do not auto-save data.
"""

from __future__ import annotations

import json
import sys
import urllib.parse
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
PROTOCOL_VERSION = "2024-11-05"
SERVER_NAME = "wonderfood-local-bridge"
SERVER_VERSION = "1.0.0"

RESOURCES = {
    "wonderfood://skill/bundled-food": ROOT / "app/src/main/assets/ai/wonderfood_food_skill.md",
    "wonderfood://skill/catalog-v1": ROOT / "docs/ai/skill-catalog-v1.md",
    "wonderfood://schema/command-envelope-v1": ROOT / "docs/ai/command-envelope.schema.v1.json",
    "wonderfood://schema/proposal-package-v1": ROOT / "docs/ai/proposal-package.schema.v1.json",
    "wonderfood://lifeos/domain-catalog-v1": ROOT / "app/src/main/assets/lifeos/domain-catalog.v1.json",
    "wonderfood://contract/app-command": ROOT / "docs/app-command-contract.md",
}

ACTION_TYPES = {
    "inventory.add",
    "inventory.edit",
    "inventory.delete",
    "grocery.add",
    "grocery.edit",
    "grocery.delete",
    "grocery.mark_bought",
    "recipe.add",
    "recipe.edit",
    "recipe.delete",
    "meal_log.log",
    "meal_log.edit",
    "meal_log.delete",
    "meal_plan.add",
    "meal_plan.edit",
    "meal_plan.delete",
    "plan_entry.add",
    "plan_entry.edit",
    "plan_entry.delete",
    "preferences.edit",
    "event.log",
}

SKILL_IDS = {
    "inventory",
    "shopping",
    "recipes",
    "meals",
    "planning",
    "preferences",
    "receipt_parsing",
    "nutrition_correction",
    "navigation",
}


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def json_response(request_id: Any, result: Any) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": request_id, "result": result}


def json_error(request_id: Any, code: int, message: str) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}}


def text_content(text: str) -> dict[str, Any]:
    return {"content": [{"type": "text", "text": text}]}


def json_content(payload: Any) -> dict[str, Any]:
    return text_content(json.dumps(payload, indent=2, sort_keys=True))


def tool_defs() -> list[dict[str, Any]]:
    return [
        {
            "name": "wonderfood.status",
            "description": "Show WonderFood bridge capabilities, resources, and safety posture.",
            "inputSchema": {"type": "object", "additionalProperties": False, "properties": {}},
        },
        {
            "name": "wonderfood.get_resource",
            "description": "Read a WonderFood skill, schema, or app command contract resource.",
            "inputSchema": {
                "type": "object",
                "additionalProperties": False,
                "required": ["uri"],
                "properties": {"uri": {"type": "string", "enum": sorted(RESOURCES)}},
            },
        },
        {
            "name": "wonderfood.validate_command_envelope",
            "description": "Validate the required outer shape of a wf.ai.command-envelope.v1 proposal.",
            "inputSchema": {
                "type": "object",
                "additionalProperties": False,
                "required": ["envelope"],
                "properties": {"envelope": {"type": "object"}},
            },
        },
        {
            "name": "wonderfood.propose_app_link",
            "description": "Create a review-only WonderFood action link for one or more supported app actions.",
            "inputSchema": {
                "type": "object",
                "additionalProperties": False,
                "required": ["requestId", "actions"],
                "properties": {
                    "requestId": {"type": "string", "minLength": 1},
                    "actions": {
                        "type": "array",
                        "minItems": 1,
                        "maxItems": 12,
                        "items": {
                            "type": "object",
                            "additionalProperties": True,
                            "required": ["type"],
                            "properties": {
                                "type": {"type": "string"},
                                "name": {"type": "string"},
                                "title": {"type": "string"},
                                "quantity": {"type": "string"},
                                "zone": {"type": "string"},
                                "category": {"type": "string"},
                                "target": {"type": "string"},
                                "id": {"type": "string"},
                            },
                        },
                    },
                    "scheme": {"type": "string", "enum": ["https", "wonderfood"], "default": "https"},
                },
            },
        },
        {
            "name": "wonderfood.wrap_proposal_package",
            "description": "Wrap a valid command envelope in wf.proposal-package.v1 for file/share handoff.",
            "inputSchema": {
                "type": "object",
                "additionalProperties": False,
                "required": ["proposal_id", "producer", "command_envelope"],
                "properties": {
                    "proposal_id": {"type": "string", "minLength": 1},
                    "producer": {"type": "string", "minLength": 1},
                    "command_envelope": {"type": "object"},
                },
            },
        },
    ]


def status_payload() -> dict[str, Any]:
    return {
        "server": {"name": SERVER_NAME, "version": SERVER_VERSION, "protocolVersion": PROTOCOL_VERSION},
        "safety": "review-only; no direct writes; no secrets; generated links/packages must be accepted inside WonderFood",
        "resources": sorted(RESOURCES),
        "tools": [tool["name"] for tool in tool_defs()],
        "dataHomes": ["local_sqlite", "notion", "google_sheets", "postgres"],
        "skills": sorted(SKILL_IDS),
        "schemas": ["wf.ai.command-envelope.v1", "wf.proposal-package.v1", "lifeos.domain-catalog.v1"],
        "appEntrypoints": ["https://wonderfood.app/action", "wonderfood://action", "Android SEND", "Android AppFunctions"],
    }


def validate_envelope(envelope: dict[str, Any]) -> dict[str, Any]:
    errors: list[str] = []
    required = [
        "schema_version",
        "catalog_version",
        "skill_id",
        "skill_version",
        "envelope_id",
        "idempotency_key",
        "status",
        "evidence",
        "commands",
        "confidence",
        "confirmation",
        "warnings",
        "unsupported",
    ]
    for key in required:
        if key not in envelope:
            errors.append(f"missing {key}")
    if envelope.get("schema_version") != "wf.ai.command-envelope.v1":
        errors.append("schema_version must be wf.ai.command-envelope.v1")
    if envelope.get("catalog_version") != "wf.ai.skill-catalog.v1":
        errors.append("catalog_version must be wf.ai.skill-catalog.v1")
    if envelope.get("skill_id") not in SKILL_IDS:
        errors.append("skill_id is not in WonderFood skill catalog")
    if envelope.get("skill_version") != "1.0.0":
        errors.append("skill_version must be 1.0.0")
    if envelope.get("status") not in {"commands", "needs_confirmation", "needs_clarification", "unsupported"}:
        errors.append("status is invalid")
    if not isinstance(envelope.get("evidence"), list) or not envelope.get("evidence"):
        errors.append("evidence must be a non-empty list")
    if not isinstance(envelope.get("commands"), list):
        errors.append("commands must be a list")
    if not isinstance(envelope.get("warnings"), list):
        errors.append("warnings must be a list")
    return {
        "valid": not errors,
        "errors": errors,
        "reviewOnly": True,
        "nextStep": "Share the package/link into WonderFood; user must review and accept in app.",
    }


def propose_app_link(arguments: dict[str, Any]) -> dict[str, Any]:
    request_id = str(arguments.get("requestId", "")).strip()
    actions = arguments.get("actions")
    scheme = arguments.get("scheme", "https")
    if not request_id:
        raise ValueError("requestId is required")
    if not isinstance(actions, list) or not actions:
        raise ValueError("actions must be a non-empty list")
    if len(actions) > 12:
        raise ValueError("WonderFood review links support at most 12 actions")
    clean_actions: list[dict[str, Any]] = []
    for index, action in enumerate(actions):
        if not isinstance(action, dict):
            raise ValueError(f"action {index} must be an object")
        action_type = str(action.get("type", "")).strip()
        if action_type not in ACTION_TYPES:
            raise ValueError(f"unsupported action type: {action_type}")
        clean = {k: v for k, v in action.items() if v not in (None, "")}
        clean["type"] = action_type
        clean_actions.append(clean)
    base = "https://wonderfood.app/action" if scheme == "https" else "wonderfood://action"
    query: dict[str, str] = {"requestId": request_id}
    if len(clean_actions) == 1:
        query.update({k: str(v) for k, v in clean_actions[0].items() if isinstance(v, (str, int, float, bool))})
    else:
        query["actions"] = json.dumps(clean_actions, separators=(",", ":"))
    url = base + "?" + urllib.parse.urlencode(query)
    return {
        "url": url,
        "actionCount": len(clean_actions),
        "reviewOnly": True,
        "safety": "Opening this link stages a WonderFood review. It must not auto-save, auto-delete, or auto-apply.",
    }


def wrap_package(arguments: dict[str, Any]) -> dict[str, Any]:
    envelope = arguments.get("command_envelope")
    if not isinstance(envelope, dict):
        raise ValueError("command_envelope must be an object")
    validation = validate_envelope(envelope)
    if not validation["valid"]:
        return {"valid": False, "errors": validation["errors"], "package": None}
    now = datetime.now(timezone.utc)
    package = {
        "schema_version": "wf.proposal-package.v1",
        "proposal_id": str(arguments["proposal_id"]),
        "origin": {"kind": "gpt_skill", "producer": str(arguments["producer"])},
        "created_at": now.isoformat().replace("+00:00", "Z"),
        "expires_at": (now + timedelta(hours=24)).isoformat().replace("+00:00", "Z"),
        "command_envelope": envelope,
        "signature": None,
    }
    return {"valid": True, "reviewOnly": True, "package": package}


def call_tool(name: str, arguments: dict[str, Any]) -> dict[str, Any]:
    if name == "wonderfood.status":
        return json_content(status_payload())
    if name == "wonderfood.get_resource":
        uri = arguments.get("uri", "")
        path = RESOURCES.get(uri)
        if path is None:
            raise ValueError(f"Unknown resource: {uri}")
        return text_content(read_text(path))
    if name == "wonderfood.validate_command_envelope":
        envelope = arguments.get("envelope")
        if not isinstance(envelope, dict):
            raise ValueError("envelope must be an object")
        return json_content(validate_envelope(envelope))
    if name == "wonderfood.propose_app_link":
        return json_content(propose_app_link(arguments))
    if name == "wonderfood.wrap_proposal_package":
        return json_content(wrap_package(arguments))
    raise ValueError(f"Unknown tool: {name}")


def resource_list() -> list[dict[str, Any]]:
    return [
        {
            "uri": uri,
            "name": uri.removeprefix("wonderfood://"),
            "mimeType": "application/json" if path.suffix == ".json" else "text/markdown",
            "description": "WonderFood MCP bridge resource",
        }
        for uri, path in sorted(RESOURCES.items())
    ]


def handle(request: dict[str, Any]) -> dict[str, Any] | None:
    request_id = request.get("id")
    method = request.get("method")
    params = request.get("params") or {}
    if method == "notifications/initialized":
        return None
    if method == "initialize":
        return json_response(
            request_id,
            {
                "protocolVersion": PROTOCOL_VERSION,
                "capabilities": {"tools": {}, "resources": {}},
                "serverInfo": {"name": SERVER_NAME, "version": SERVER_VERSION},
            },
        )
    if method == "tools/list":
        return json_response(request_id, {"tools": tool_defs()})
    if method == "tools/call":
        try:
            return json_response(request_id, call_tool(params.get("name", ""), params.get("arguments") or {}))
        except Exception as error:
            return json_error(request_id, -32602, str(error))
    if method == "resources/list":
        return json_response(request_id, {"resources": resource_list()})
    if method == "resources/read":
        uri = params.get("uri", "")
        path = RESOURCES.get(uri)
        if path is None:
            return json_error(request_id, -32602, f"Unknown resource: {uri}")
        return json_response(
            request_id,
            {"contents": [{"uri": uri, "mimeType": "application/json" if path.suffix == ".json" else "text/markdown", "text": read_text(path)}]},
        )
    return json_error(request_id, -32601, f"Method not found: {method}")


def main() -> int:
    for line in sys.stdin:
        if not line.strip():
            continue
        try:
            request = json.loads(line)
            response = handle(request)
        except Exception as error:
            response = json_error(None, -32700, str(error))
        if response is not None:
            sys.stdout.write(json.dumps(response, separators=(",", ":")) + "\n")
            sys.stdout.flush()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
