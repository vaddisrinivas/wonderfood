import type { SheetsApiResponse, SheetsClientConfig } from './client';
import { createOfficialSheetsClient, readSheetsConfig } from './client';

export type SheetsPort = {
  getSpreadsheet(input: {
    spreadsheetId: string;
    signal?: AbortSignal;
  }): Promise<SheetsApiResponse<Record<string, unknown>>>;
  batchGetValues(input: {
    spreadsheetId: string;
    ranges: string[];
    majorDimension?: 'ROWS' | 'COLUMNS';
    signal?: AbortSignal;
  }): Promise<SheetsApiResponse<Record<string, unknown>>>;
  batchUpdateValues(input: {
    spreadsheetId: string;
    valueInputOption: string;
    data: Array<{
      range: string;
      majorDimension: string;
      values: string[][];
    }>;
    signal?: AbortSignal;
  }): Promise<SheetsApiResponse<Record<string, unknown>>>;
};

let sheetsPortOverride: SheetsPort | null = null;

function normalizeSheetsError(error: unknown): SheetsApiResponse<never> {
  const status = typeof (error as { code?: unknown })?.code === 'number'
    ? Number((error as { code: number }).code)
    : 0;
  const message = error instanceof Error ? error.message : 'google sheets sdk request failed';
  return {
    ok: false,
    status,
    error: message,
  };
}

export function createSdkSheetsPort(config?: SheetsClientConfig): SheetsPort | null {
  const resolved = config ?? readSheetsConfig();
  if (!resolved) {
    return null;
  }
  const client = createOfficialSheetsClient(resolved);
  if (!client) {
    return null;
  }

  const sdk = client as any;
  return {
    async getSpreadsheet(input) {
      try {
        const response = await sdk.spreadsheets.get({
          spreadsheetId: input.spreadsheetId,
          ...(input.signal ? { signal: input.signal } : {}),
        });
        return {
          ok: true,
          status: typeof response?.status === 'number' ? response.status : 200,
          data: (response?.data ?? {}) as Record<string, unknown>,
        };
      } catch (error: unknown) {
        return normalizeSheetsError(error);
      }
    },
    async batchGetValues(input) {
      try {
        const response = await sdk.spreadsheets.values.batchGet({
          spreadsheetId: input.spreadsheetId,
          majorDimension: input.majorDimension ?? 'ROWS',
          ranges: input.ranges,
          ...(input.signal ? { signal: input.signal } : {}),
        });
        return {
          ok: true,
          status: typeof response?.status === 'number' ? response.status : 200,
          data: (response?.data ?? {}) as Record<string, unknown>,
        };
      } catch (error: unknown) {
        return normalizeSheetsError(error);
      }
    },
    async batchUpdateValues(input) {
      try {
        const response = await sdk.spreadsheets.values.batchUpdate({
          spreadsheetId: input.spreadsheetId,
          requestBody: {
            valueInputOption: input.valueInputOption,
            data: input.data,
          },
          ...(input.signal ? { signal: input.signal } : {}),
        });
        return {
          ok: true,
          status: typeof response?.status === 'number' ? response.status : 200,
          data: (response?.data ?? {}) as Record<string, unknown>,
        };
      } catch (error: unknown) {
        return normalizeSheetsError(error);
      }
    },
  };
}

export function getSheetsPort(config?: SheetsClientConfig): SheetsPort | null {
  return sheetsPortOverride ?? createSdkSheetsPort(config);
}

export function setSheetsPortForTests(port: SheetsPort | null) {
  sheetsPortOverride = port;
}
