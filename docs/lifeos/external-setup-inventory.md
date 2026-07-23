# External setup inventory

This is the boundary between the open-source repository and the owner's private deployment.

## Private setup that exists

- Notion connection `liferpg`, authenticated through the Notion developer portal. The app uses the LifeOS data source by ID; no Notion webhook subscription, verification token, or signing secret was created.
- Google Sheets OAuth and the canonical workbook are private credentials/data. They are used by the personal hosted connector only.
- Cloudflare named tunnel `wonderfood-lifeos` exposes the personal server at `https://lifeos-api.thetechcruise.com` and forwards to the owner's Mac. Bearer auth and a user LaunchAgent keep that private runtime reachable while the Mac is logged in.
- OpenAI model configuration is private server configuration; no key is in this repository.

## What release users configure themselves

- An HTTPS LifeOS server URL and token, or a local server.
- Their own Notion integration/data source, Sheets workbook/OAuth, and model provider credentials if they enable those adapters.
- Pull/manual refresh. Webhooks are optional operational optimizations and are disabled by default.

`thetechcruise.com` is not an open-source dependency. It is only the owner's personal host and can be replaced with any HTTPS endpoint.
