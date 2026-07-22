export async function callOpenAI(input: { prompt: string }) {
  if (!process.env.OPENAI_API_KEY) {
    return {
      status: 'disabled',
      text: 'OPENAI_API_KEY is missing in server environment. Use offline fallback.',
    };
  }

  return {
    status: 'stubbed',
    text: `Live OpenAI call skipped in scaffold for: ${input.prompt}`,
  };
}
