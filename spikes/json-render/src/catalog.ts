import { defineCatalog } from '@json-render/core';
import { schema } from '@json-render/react-native/schema';
import {
  standardActionDefinitions,
  standardComponentDefinitions,
} from '@json-render/react-native/catalog';
import { z } from 'zod';

export const jsonRenderCatalog = defineCatalog(schema, {
  components: {
    ...standardComponentDefinitions,
    SpikeBadge: {
      props: z.object({
        label: z.string().min(1),
        tone: z.enum(['neutral', 'success', 'warning']).nullable(),
      }),
      slots: ['default'],
      description: 'Spike marker block for rendering compatibility checks',
      example: {
        label: 'json-render',
        tone: 'success',
      },
    },
  },
  actions: {
    ...standardActionDefinitions,
  },
});

export const jsonRenderSystemPrompt = jsonRenderCatalog.prompt({
  customRules: [
    'Use SafeArea as the root component whenever possible.',
    'Keep root tree short and readable; show one primary heading and one supporting paragraph.',
  ],
});
