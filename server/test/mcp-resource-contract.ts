import { readMcpResource } from '../src/resources/catalog';

function ensure(condition: boolean, message: string) {
  if (!condition) throw new Error(message);
}

const catalog = readMcpResource('wonderfood://domain-catalog-v1');
ensure(catalog.includes('lifeos.domain-catalog.v1'), 'Expected domain catalog resource to be readable from repo root');

const legacyCatalog = readMcpResource('wonderfood://lifeos/domain-catalog-v1');
ensure(legacyCatalog.includes('lifeos.domain-catalog.v1'), 'Expected legacy domain catalog resource alias to remain readable');

const foodSkill = readMcpResource('wonderfood://skill/bundled-food');
ensure(foodSkill.includes('Food'), 'Expected bundled Food skill resource to be readable');

const commandSchema = readMcpResource('wonderfood://schema/command.v1');
ensure(commandSchema.includes('lifeos.command.v1'), 'Expected command schema resource to be readable');

console.log('PASS server/test/mcp-resource-contract.ts');
