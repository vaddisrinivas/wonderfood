export async function buildPlan(input: { command: string; domain: string }) {
  return {
    command: input.command,
    domain: input.domain,
    steps: [
      { id: 'validate', action: 'validate_schema', required: true },
      { id: 'execute', action: 'execute_command', required: true },
      { id: 'emit_receipt', action: 'write_receipt', required: true },
    ],
  };
}
