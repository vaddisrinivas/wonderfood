# WonderFood MCP bridge

WonderFood exposes a local stdio MCP bridge for GPT/plugin-style clients:

```bash
python3 scripts/mcp/wonderfood_mcp_server.py
```

Client config shape:

```json
{
  "mcpServers": {
    "wonderfood": {
      "command": "python3",
      "args": ["scripts/mcp/wonderfood_mcp_server.py"]
    }
  }
}
```

The bridge is review-only. It never writes to Notion, Sheets, SQLite, Postgres, or the Android app directly. It exposes existing contracts and creates handoffs that WonderFood must review inside the app.

## Tools

- `wonderfood.status` — capabilities, data homes, skill ids, schema ids.
- `wonderfood.get_resource` — read bundled skill, catalog, schemas, app command contract.
- `wonderfood.validate_command_envelope` — validate outer `wf.ai.command-envelope.v1` shape.
- `wonderfood.propose_app_link` — create review-only `https://wonderfood.app/action?...` or `wonderfood://action?...` links.
- `wonderfood.wrap_proposal_package` — wrap a valid command envelope in `wf.proposal-package.v1`.

## Resources

- `wonderfood://skill/bundled-food`
- `wonderfood://skill/catalog-v1`
- `wonderfood://schema/command-envelope-v1`
- `wonderfood://schema/proposal-package-v1`
- `wonderfood://contract/app-command`

## Proof

Run:

```bash
scripts/quality/run-mcp-bridge-proof.sh
```

Expected artifact:

```text
app/build/evidence/mcp-bridge/mcp-bridge-proof.json
```

The proof covers initialize, tool listing, resource listing, skill reads, review-only app link creation, command-envelope validation, and proposal-package wrapping.
