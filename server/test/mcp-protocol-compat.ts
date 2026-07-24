import assert from 'node:assert/strict';
import {
  MCP_PROTOCOL_VERSIONS,
  isMcpProtocolVersion,
  isAllowedMcpOrigin,
  negotiateMcpProtocolVersion,
} from '../src/mcp/protocol-compat';

assert.equal(negotiateMcpProtocolVersion('2025-06-18'), '2025-06-18');
assert.equal(negotiateMcpProtocolVersion('unsupported-client-version'), MCP_PROTOCOL_VERSIONS[0]);
assert.equal(negotiateMcpProtocolVersion(undefined), MCP_PROTOCOL_VERSIONS[0]);
assert.equal(isMcpProtocolVersion('2024-11-05'), true);
assert.equal(isMcpProtocolVersion('not-mcp'), false);
assert.equal(isAllowedMcpOrigin(undefined), true);
assert.equal(isAllowedMcpOrigin('http://localhost'), true);
assert.equal(isAllowedMcpOrigin('https://attacker.example'), false);
assert.equal(isAllowedMcpOrigin('https://workspace.example', ['https://workspace.example']), true);
console.log('MCP protocol compatibility: PASS');
