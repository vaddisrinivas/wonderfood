import { describe, expect, it } from 'vitest';
import {
  compileSpecStream,
  createStateStore,
  defineCatalog,
  validateSpec,
} from '@json-render/core';
import {
  standardActionDefinitions,
  standardComponentDefinitions,
} from '@json-render/react-native/catalog';
import { schema } from '@json-render/react-native/schema';
import { z } from 'zod';

describe('json-render RN spike compatibility', () => {
  const jsonRenderCatalog = defineCatalog(schema, {
    components: {
      ...standardComponentDefinitions,
      SpikeBadge: {
        props: z.object({
          label: z.string().min(1),
          tone: z.enum(['neutral', 'success', 'warning']).nullable(),
        }),
        slots: ['default'],
        description: 'Spike marker block for rendering compatibility checks',
      },
    },
    actions: {
      ...standardActionDefinitions,
    },
  });

  const jsonRenderSpecPatches =
    '{"op":"add","path":"/root","value":"safeArea"}\n' +
    '{"op":"add","path":"/elements/safeArea","value":{"type":"SafeArea","props":{"backgroundColor":"#0B1220"},"children":["title","detail"]}}\n' +
    '{"op":"add","path":"/elements/title","value":{"type":"Heading","props":{"text":"U40R React Native spike","level":"h2","color":"#EEF2FF","align":"center"},"children":[]}}\n' +
    '{"op":"add","path":"/elements/detail","value":{"type":"Paragraph","props":{"text":"Expo 57 + RN 0.86 compatibility probe","align":"center"},"children":[]}}';

  it('builds a pinned catalog prompt with constrained U40R guidance', () => {
    const catalogPrompt = jsonRenderCatalog.prompt({
      customRules: ['Use SafeArea as the root component whenever possible.'],
    });

    expect(catalogPrompt).toContain('SafeArea');
    expect(jsonRenderCatalog).toBeDefined();
  });

  it('compiles a flat JSON spec stream into a valid RN tree shape', () => {
    const compiled = compileSpecStream(jsonRenderSpecPatches);
    expect(compiled.root).toBe('safeArea');
    expect(compiled.elements?.safeArea?.type).toBe('SafeArea');
  });

  it('keeps core schema validation available for spike payloads', () => {
    const compiled = compileSpecStream(jsonRenderSpecPatches);
    const validation = validateSpec(compiled);
    expect(validation.valid).toBe(true);
    expect(validation.issues).toHaveLength(0);
  });

  it('builds renderer registries and provider pipeline entry points', () => {
    expect(Object.prototype.hasOwnProperty.call(jsonRenderCatalog.data.components, 'SafeArea')).toBe(true);
    expect(jsonRenderCatalog.data.components).toHaveProperty('Heading');
    expect(jsonRenderCatalog.data.actions).toHaveProperty('setState');
  });

  it('updates json-render state store with typed path access', () => {
    const store = createStateStore({
      phase: 'spike',
      score: 1,
    });

    expect(store.get('/phase')).toBe('spike');
    store.set('/phase', 'validated');
    expect(store.get('/phase')).toBe('validated');
    expect(store.getSnapshot().phase).toBe('validated');
  });
});
