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
          layout: { $ref: '#/$defs/viewLayout' },
        },
      },
    },
    presentation: { $ref: '#/$defs/presentation' },
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
    rules: {
      type: 'array',
      items: { $ref: '#/$defs/rule' },
    },
    capabilities: {
      type: 'array',
      uniqueItems: true,
      items: { type: 'string', pattern: '^[A-Za-z0-9][A-Za-z0-9_.:-]*$' },
    },
    acceptanceTests: {
      type: 'array',
      uniqueItems: true,
      items: { type: 'string', pattern: '^[A-Za-z0-9][A-Za-z0-9_.:-]*$' },
    },
  },
  $defs: {
    jsonValue: {
      anyOf: [
        { type: 'null' },
        { type: 'string' },
        { type: 'number' },
        { type: 'boolean' },
        { type: 'array', items: { $ref: '#/$defs/jsonValue' } },
        { type: 'object', additionalProperties: { $ref: '#/$defs/jsonValue' } },
      ],
    },
    presentation: {
      type: 'object',
      additionalProperties: false,
      required: ['label', 'surfaces'],
      properties: {
        label: { type: 'string', minLength: 1 },
        homeSurface: { type: 'string', minLength: 1 },
        surfaces: {
          type: 'array',
          items: {
            type: 'object',
            additionalProperties: false,
            required: ['id', 'label', 'collections'],
            properties: {
              id: { type: 'string', minLength: 1 },
              label: { type: 'string', minLength: 1 },
              icon: { type: 'string' },
              imageUrl: { type: 'string' },
              views: { type: 'array', items: { type: 'string' } },
              collections: { type: 'array', items: { type: 'string' } },
            },
          },
        },
        visualIdentity: { type: 'object', additionalProperties: { $ref: '#/$defs/jsonValue' } },
        dashboardBlocks: { type: 'array', items: { type: 'object', additionalProperties: { $ref: '#/$defs/jsonValue' } } },
        render: { type: 'object', additionalProperties: { $ref: '#/$defs/jsonValue' } },
        richDetailSchema: { type: 'string', minLength: 1 },
        providerTemplateFields: { type: 'object', additionalProperties: { $ref: '#/$defs/jsonValue' } },
        sourceSchemaVersion: { type: 'string', minLength: 1 },
      },
    },
    viewLayout: {
      type: 'object',
      additionalProperties: false,
      properties: {
        size: { enum: ['compact', 'standard', 'wide', 'feature'] },
        tone: { enum: ['neutral', 'moss', 'amber', 'plum', 'blue'] },
        href: { type: 'string', minLength: 1 },
      },
    },
    rule: {
      type: 'object',
      additionalProperties: false,
      required: ['id', 'trigger', 'effect', 'mode', 'maxRunsPerEvent'],
      properties: {
        id: { type: 'string', pattern: '^[A-Za-z_][A-Za-z0-9_.:-]*$' },
        trigger: { $ref: '#/$defs/ruleTrigger' },
        when: {},
        effect: {
          type: 'object',
          additionalProperties: false,
          required: ['kind', 'operation'],
          properties: {
            kind: { const: 'propose_operation' },
            operation: {
              oneOf: [
                { type: 'string', pattern: '^[A-Za-z_][A-Za-z0-9_.:-]*$' },
                { $ref: '#/$defs/operationTemplate' },
              ],
            },
          },
        },
        mode: { enum: ['suggest', 'automatic'] },
        maxRunsPerEvent: { type: 'integer', minimum: 1, maximum: 64 },
      },
    },
    ruleTrigger: {
      oneOf: [
        {
          type: 'object',
          additionalProperties: false,
          required: ['kind'],
          properties: { kind: { enum: ['operation', 'schedule'] } },
        },
        {
          type: 'object',
          additionalProperties: false,
          required: ['kind', 'query'],
          properties: {
            kind: { const: 'query_transition' },
            query: { type: 'string', minLength: 1 },
            transition: { enum: ['enter', 'leave', 'change'] },
          },
        },
      ],
    },
    operationTemplate: {
      oneOf: [
        {
          type: 'object',
          additionalProperties: false,
          required: ['kind', 'tool'],
          properties: {
            kind: { const: 'custom' },
            tool: { type: 'string', pattern: '^[A-Za-z_][A-Za-z0-9_.:-]*$' },
          },
        },
        {
          type: 'object',
          additionalProperties: false,
          required: ['kind', 'collection'],
          properties: {
            kind: { const: 'create_record' },
            domain: { type: 'string', minLength: 1 },
            collection: { type: 'string', minLength: 1 },
            recordId: { type: 'string', minLength: 1 },
            properties: { type: 'object' },
          },
        },
        {
          type: 'object',
          additionalProperties: false,
          required: ['kind', 'recordId', 'changes'],
          properties: {
            kind: { const: 'update_record' },
            domain: { type: 'string', minLength: 1 },
            collection: { type: 'string', minLength: 1 },
            recordId: { type: 'string', minLength: 1 },
            expectedRevision: { type: 'integer', minimum: 0 },
            changes: { type: 'object' },
          },
        },
        {
          type: 'object',
          additionalProperties: false,
          required: ['kind', 'recordId'],
          properties: {
            kind: { enum: ['archive_record', 'restore_record'] },
            domain: { type: 'string', minLength: 1 },
            collection: { type: 'string', minLength: 1 },
            recordId: { type: 'string', minLength: 1 },
            expectedRevision: { type: 'integer', minimum: 0 },
          },
        },
      ],
    },
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
