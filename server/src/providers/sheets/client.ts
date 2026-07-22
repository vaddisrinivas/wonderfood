export const SHEETS_API_BASE_URL = 'https://sheets.googleapis.com/v4';
export const SHEETS_REQUEST_TIMEOUT_MS = 15000;
export const SHEETS_WORKBOOK_TAB_PREFIX = 'LifeOS';
export const SHEETS_WORKBOOK_DEFAULT_RANGE = 'A:Z';

export type SheetsClientConfig = {
  accessToken: string;
  spreadsheetId?: string;
  workbookName?: string;
  dataSourceId?: string;
};

export type SheetsApiResponse<T> = {
  ok: boolean;
  status: number;
  data?: T;
  error?: string;
};

export function readSheetsConfig(): SheetsClientConfig | null {
  const accessToken = process.env.GOOGLE_SHEETS_ACCESS_TOKEN?.trim() || process.env.GOOGLE_SHEETS_TOKEN?.trim();
  if (!accessToken) {
    return null;
  }
  return {
    accessToken,
    spreadsheetId: process.env.GOOGLE_SHEETS_SPREADSHEET_ID?.trim() || process.env.SHEETS_SPREADSHEET_ID?.trim(),
    workbookName: process.env.GOOGLE_SHEETS_WORKBOOK_NAME?.trim() || undefined,
    dataSourceId:
      process.env.GOOGLE_SHEETS_DATA_SOURCE_ID?.trim() ||
      process.env.SHEETS_DATA_SOURCE_ID?.trim(),
  };
}

export function isSheetsConfigured() {
  return readSheetsConfig() !== null;
}

export function sheetsEndpoint(path: string, config?: SheetsClientConfig) {
  const resolved = config ?? readSheetsConfig();
  if (!resolved?.spreadsheetId) {
    return null;
  }
  const normalized = path.startsWith('/') ? path.slice(1) : path;
  return `${SHEETS_API_BASE_URL}/spreadsheets/${resolved.spreadsheetId}/${normalized}`;
}

export function sheetsHeaders(config?: SheetsClientConfig) {
  const resolved = config ?? readSheetsConfig();
  if (!resolved) {
    return null;
  }
  return {
    Authorization: `Bearer ${resolved.accessToken}`,
    'Content-Type': 'application/json',
  };
}

function withTimeout(ms: number, signal?: AbortSignal) {
  if (signal) {
    return signal;
  }
  const controller = new AbortController();
  setTimeout(() => controller.abort(), ms);
  return controller.signal;
}

export async function sheetsFetch<T>(path: string, init: RequestInit = {}): Promise<SheetsApiResponse<T>> {
  const headers = sheetsHeaders();
  if (!headers) {
    return {
      ok: false,
      status: 0,
      error: 'GOOGLE_SHEETS_ACCESS_TOKEN is not configured',
    };
  }

  const endpoint = sheetsEndpoint(path);
  if (!endpoint) {
    return {
      ok: false,
      status: 0,
      error: 'GOOGLE_SHEETS_SPREADSHEET_ID is missing',
    };
  }

  const requestInit: RequestInit = {
    method: init.method || 'GET',
    headers: {
      ...headers,
      ...(init.headers as Record<string, string> | undefined),
    },
    body: init.body,
    signal: withTimeout(SHEETS_REQUEST_TIMEOUT_MS, init.signal as AbortSignal | undefined),
  };

  try {
    const response = await fetch(endpoint, requestInit);
    const text = await response.text();
    let payload: unknown = null;
    if (text) {
      try {
        payload = JSON.parse(text);
      } catch {
        payload = text;
      }
    }

    if (!response.ok) {
      return {
        ok: false,
        status: response.status,
        error: typeof payload === 'string' ? payload : `Sheets API error ${response.status}`,
      };
    }

    return {
      ok: true,
      status: response.status,
      data: payload as T,
    };
  } catch (error: unknown) {
    return {
      ok: false,
      status: 0,
      error: error instanceof Error ? error.message : 'unknown-sheets-request-failure',
    };
  }
}
