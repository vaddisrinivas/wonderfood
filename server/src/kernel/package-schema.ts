/** JSON Schema for the executable-data package boundary. No code fields are permitted. */
export const appPackageSchema = {
  $schema: 'http://json-schema.org/draft-07/schema#',
  type: 'object',
  additionalProperties: false,
  required: ['schemaVersion', 'id', 'version', 'collections', 'queries', 'views', 'rules', 'capabilities', 'acceptanceTests'],
  properties: {
    schemaVersion: { const: 'wonder.app-package.v2' },
    id: { type: 'string', minLength: 1 },
    version: { type: 'string', minLength: 1 },
    collections: {
      type: 'object',
      additionalProperties: {
        type: 'object',
        additionalProperties: false,
        required: ['id', 'fields'],
        properties: {
          id: { type: 'string', minLength: 1 },
          fields: {
            type: 'object',
            additionalProperties: {
              type: 'object',
              additionalProperties: false,
              required: ['type'],
              properties: {
                type: { enum: ['text', 'number', 'boolean', 'timestamp', 'json'] },
                required: { type: 'boolean' },
                indexed: { type: 'boolean' },
              },
            },
          },
        },
      },
    },
    queries: {
      type: 'object',
      additionalProperties: {
        type: 'object',
        additionalProperties: false,
        required: ['from'],
        properties: {
          from: { type: 'string', minLength: 1 },
          where: { $ref: '#/$defs/predicate' },
          orderBy: { type: 'array', items: { type: 'object', required: ['field'], additionalProperties: false, properties: { field: { type: 'string', minLength: 1 }, direction: { enum: ['asc', 'desc'] } } } },
          limit: { type: 'integer', minimum: 0 },
        },
      },
    },
    views: {
      type: 'object',
      additionalProperties: {
        type: 'object',
        additionalProperties: false,
        required: ['id', 'query', 'mode', 'fields'],
        properties: {
          id: { type: 'string', minLength: 1 },
          query: { type: 'string', minLength: 1 },
          mode: { enum: ['list', 'board', 'table', 'calendar', 'timeline', 'chart'] },
          fields: { type: 'array', items: { type: 'string', minLength: 1 } },
          groupBy: { type: 'string', minLength: 1 },
          layout: { type: 'object' },
        },
      },
    },
    computedFields: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['id', 'collection', 'dependsOn', 'expression'],
        properties: {
          id: { type: 'string', pattern: '^[A-Za-z_][A-Za-z0-9_]*$' },
          collection: { type: 'string', minLength: 1 },
          dependsOn: {
            type: 'array',
            uniqueItems: true,
            items: { type: 'string', pattern: '^[A-Za-z_][A-Za-z0-9_]*$' },
          },
          expression: {},
        },
      },
    },
    rules: { type: 'array', items: { type: 'object' } },
    capabilities: { type: 'array', items: { type: 'string' } },
    acceptanceTests: { type: 'array', items: { type: 'string' } },
  },
  $defs: {
    predicate: {
      oneOf: [
        { type: 'object', required: ['op', 'args'], additionalProperties: false, properties: { op: { enum: ['and', 'or'] }, args: { type: 'array', minItems: 1, items: { $ref: '#/$defs/predicate' } } } },
        { type: 'object', required: ['op', 'arg'], additionalProperties: false, properties: { op: { const: 'not' }, arg: { $ref: '#/$defs/predicate' } } },
        { type: 'object', required: ['op', 'field', 'value'], additionalProperties: false, properties: { op: { enum: ['eq', 'neq', 'gt', 'gte', 'lt', 'lte', 'contains', 'starts_with'] }, field: { type: 'string', minLength: 1 }, value: {} } },
        { type: 'object', required: ['op', 'field'], additionalProperties: false, properties: { op: { const: 'exists' }, field: { type: 'string', minLength: 1 }, value: { type: 'boolean' } } },
      ],
    },
  },
} as const;
