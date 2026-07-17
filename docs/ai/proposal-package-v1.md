# AI Proposal Package v1

`wf.proposal-package.v1` is the external handoff wrapper for ChatGPT, custom GPTs,
GPT Skills, ChatGPT Apps, file import, Android share, and future App Links.

The package is untrusted transport. WonderFood validates it locally, unwraps the
nested `wf.ai.command-envelope.v1`, converts supported commands into review drafts,
and saves nothing until the user accepts.

## Required Shape

| Field | Contract |
| --- | --- |
| `schema_version` | Literal `wf.proposal-package.v1`. |
| `proposal_id` | External producer id. Not a database id. |
| `origin.kind` | Source family: `chatgpt`, `custom_gpt`, `gpt_skill`, `app_link`, `file`, `share`, or `other`. |
| `origin.producer` | Human producer label, such as `WonderFood GPT`. |
| `created_at` | ISO-8601 timestamp. |
| `expires_at` | ISO-8601 timestamp. Expired packages are rejected. |
| `command_envelope` | Complete `wf.ai.command-envelope.v1` object. |
| `signature` | Null for offline/share import. Future verified links may use a signature object. |

## Import State Machine

```text
received
  -> rejected_invalid_package
  -> rejected_expired
  -> rejected_invalid_envelope
  -> validated
       -> unsupported_no_draft
       -> draft_ready
            -> accepted
            -> rejected_by_user
```

Only `accepted` writes product data. All earlier states are review/audit state.

## Security Rules

- Never place food, health, OAuth, API-key, or database data in a raw URL.
- Prefer `application/vnd.wonderfood.proposal+json` for files.
- Verified links should use opaque one-time tokens, not embedded commands.
- External confidence is advisory only; WonderFood recalculates review/confirmation.
- Unknown package fields are ignored only after the required fields are valid.
- Unknown command types stay rejected/unsupported.
- Nutrition remains unknown unless supported by explicit user values, label/provider
  evidence, recipe data, or user-requested estimates.

## Minimal Example

```json
{
  "schema_version": "wf.proposal-package.v1",
  "proposal_id": "proposal-2026-001",
  "origin": {
    "kind": "custom_gpt",
    "producer": "WonderFood GPT"
  },
  "created_at": "2026-07-17T12:00:00Z",
  "expires_at": "2026-07-18T12:00:00Z",
  "command_envelope": {
    "schema_version": "wf.ai.command-envelope.v1",
    "catalog_version": "wf.ai.skill-catalog.v1",
    "skill_id": "shopping",
    "skill_version": "1.0.0",
    "envelope_id": "env_example_001",
    "idempotency_key": "external-example-001",
    "status": "commands",
    "evidence": [
      {
        "evidence_id": "ev_user_1",
        "type": "user_text",
        "source_ref": "external:chatgpt",
        "quote": "Need oats and bananas.",
        "observed_at": "2026-07-17T12:00:00Z",
        "confidence": 1.0
      }
    ],
    "commands": [
      {
        "command_id": "cmd_1",
        "type": "shopping.add_item",
        "summary": "Add oats to shopping.",
        "payload": {
          "name": "Oats",
          "quantity": null,
          "category": "grain"
        },
        "evidence_refs": ["ev_user_1"],
        "confidence": {
          "score": 0.9,
          "rationale": "Explicit item and shopping intent."
        },
        "confirmation": {
          "required": false,
          "level": "review",
          "reason": "External proposal requires review.",
          "prompt": "Add oats?"
        },
        "destructive": false,
        "mutation": true
      }
    ],
    "confidence": {
      "score": 0.9,
      "rationale": "Clear shopping request."
    },
    "confirmation": {
      "required": false,
      "level": "review",
      "reason": "External proposal requires review.",
      "prompt": "Review shopping proposal?"
    },
    "warnings": [],
    "unsupported": null
  },
  "signature": null
}
```
