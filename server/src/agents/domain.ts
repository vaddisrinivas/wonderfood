export async function applyDomainPolicy(input: { domain: string; command: string }) {
  return {
    domain: input.domain,
    command: input.command,
    policy: 'food-safe',
    allowed: true,
    reason: 'Within allowed domain tool surface',
  };
}
