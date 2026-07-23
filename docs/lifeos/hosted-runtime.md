# Hosted LifeOS runtime

The personal runtime is exposed at `https://lifeos-api.thetechcruise.com` through the named Cloudflare Tunnel `wonderfood-lifeos`.

## Local service

The Mac runs a user-level LaunchAgent:

`~/Library/LaunchAgents/com.wonderfood.lifeos-hosted.plist`

It starts `scripts/runtime/start-hosted-lifeos.sh`, which:

1. loads the private agent environment;
2. starts the Node server on `127.0.0.1:8790`/LAN `0.0.0.0:8790`;
3. reads the bearer token from macOS Keychain (never Git); and
4. starts the named Cloudflare connector with the remote ingress configuration.

Useful checks:

```bash
launchctl print gui/$(id -u)/com.wonderfood.lifeos-hosted
curl https://lifeos-api.thetechcruise.com/health
```

Chat, Health Connect, provider sync, write actions, and MCP calls use the matching `Authorization: Bearer ...` token. The phone release build was produced with the hosted URL and token; tokens are intentionally absent from source and documentation.

Cloudflare's current API flow requires a named tunnel, ingress configuration, a DNS CNAME to `<tunnel-id>.cfargotunnel.com`, and a running connector. See the [Cloudflare remote tunnel API guide](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/get-started/create-remote-tunnel-api/).
