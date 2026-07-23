import { Linking, Platform } from 'react-native';
import {
  SdkAvailabilityStatus,
  getGrantedPermissions,
  getSdkStatus,
  initialize,
  readRecords,
  requestPermission,
} from 'react-native-health-connect';

export const LIFEOS_HEALTH_PERMISSIONS = [
  { accessType: 'read', recordType: 'Nutrition' },
  { accessType: 'read', recordType: 'Hydration' },
  { accessType: 'read', recordType: 'Steps' },
  { accessType: 'read', recordType: 'ActiveCaloriesBurned' },
  { accessType: 'read', recordType: 'Weight' },
] as const;

export type HealthConnectAvailability =
  | 'available'
  | 'provider-update-required'
  | 'unavailable'
  | 'unsupported'
  | 'error';

export type HealthConnectStatus = {
  availability: HealthConnectAvailability;
  granted: string[];
  message: string;
};

export type HealthConnectSnapshot = HealthConnectStatus & {
  observedAt: string;
  range: { startTime: string; endTime: string };
  records: {
    nutrition: unknown[];
    hydration: unknown[];
    steps: unknown[];
    activeCalories: unknown[];
    weight: unknown[];
  };
};

function unsupportedStatus(): HealthConnectStatus {
  return {
    availability: 'unsupported',
    granted: [],
    message: 'Health Connect is available on Android only.',
  };
}

function statusFromSdk(value: number, granted: string[]): HealthConnectStatus {
  if (value === SdkAvailabilityStatus.SDK_AVAILABLE) {
    return { availability: 'available', granted, message: 'Health Connect is available.' };
  }
  if (value === SdkAvailabilityStatus.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
    return {
      availability: 'provider-update-required',
      granted,
      message: 'Update Health Connect, then try again.',
    };
  }
  return {
    availability: 'unavailable',
    granted,
    message: 'Health Connect is not available on this device.',
  };
}

export async function getLifeOSHealthStatus(): Promise<HealthConnectStatus> {
  if (Platform.OS !== 'android') {
    return unsupportedStatus();
  }

  try {
    const [sdkStatus, granted] = await Promise.all([
      getSdkStatus(),
      getGrantedPermissions().catch(() => []),
    ]);
    return statusFromSdk(
      sdkStatus,
      granted.map((permission) => `${permission.accessType}:${permission.recordType}`),
    );
  } catch (error) {
    return {
      availability: 'error',
      granted: [],
      message: error instanceof Error ? error.message : 'Health Connect status failed.',
    };
  }
}

export async function requestLifeOSHealthPermissions(): Promise<HealthConnectStatus> {
  if (Platform.OS !== 'android') {
    return unsupportedStatus();
  }

  try {
    const initialized = await initialize();
    if (!initialized) {
      return {
        availability: 'unavailable',
        granted: [],
        message: 'Health Connect could not be initialized.',
      };
    }
    await requestPermission([...LIFEOS_HEALTH_PERMISSIONS]);
    return getLifeOSHealthStatus();
  } catch (error) {
    return {
      availability: 'error',
      granted: [],
      message: error instanceof Error ? error.message : 'Health Connect permission request failed.',
    };
  }
}

/**
 * Opens the system-managed Health Connect permission surface through the app's
 * stable deep link. The Android activity owns the intent extras so callers do
 * not need to know platform-specific package names.
 */
export async function openLifeOSHealthSettings(): Promise<boolean> {
  if (Platform.OS !== 'android') return false;
  try {
    await Linking.openURL('wonderfood://health-connect');
    return true;
  } catch {
    return false;
  }
}

export async function readLifeOSHealthSnapshot(
  range: { startTime: string; endTime: string },
): Promise<HealthConnectSnapshot> {
  const status = await getLifeOSHealthStatus();
  const base = {
    ...status,
    observedAt: new Date().toISOString(),
    range,
    records: {
      nutrition: [],
      hydration: [],
      steps: [],
      activeCalories: [],
      weight: [],
    },
  } satisfies HealthConnectSnapshot;

  if (status.availability !== 'available') {
    return base;
  }

  const timeRangeFilter = { operator: 'between' as const, ...range };
  const read = async (recordType: 'Nutrition' | 'Hydration' | 'Steps' | 'ActiveCaloriesBurned' | 'Weight') => {
    try {
      const result = await readRecords(recordType, { timeRangeFilter });
      return result.records ?? [];
    } catch {
      // A denied individual scope must not hide other granted Health Connect data.
      return [];
    }
  };

  const [nutrition, hydration, steps, activeCalories, weight] = await Promise.all([
    read('Nutrition'),
    read('Hydration'),
    read('Steps'),
    read('ActiveCaloriesBurned'),
    read('Weight'),
  ]);

  return {
    ...base,
    records: { nutrition, hydration, steps, activeCalories, weight },
  };
}

export async function syncLifeOSHealthSnapshot(input: {
  baseUrl: string;
  token?: string;
  snapshot: HealthConnectSnapshot;
  signal?: AbortSignal;
}): Promise<{ status: 'stored' | 'duplicate' | 'error'; id?: string; message: string } | null> {
  const baseUrl = input.baseUrl.trim().replace(/\/$/, '');
  if (!baseUrl) return null;
  try {
    const response = await fetch(`${baseUrl}/health/connect/snapshot`, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        ...(input.token?.trim() ? { authorization: `Bearer ${input.token.trim()}` } : {}),
      },
      body: JSON.stringify(input.snapshot),
      signal: input.signal,
    });
    const payload = (await response.json().catch(() => null)) as { status?: 'stored' | 'duplicate'; message?: string; snapshot?: { id?: string } } | null;
    if (!response.ok || !payload) return { status: 'error', message: payload?.message || 'Health snapshot sync failed.' };
    return { status: payload.status || 'error', id: payload.snapshot?.id, message: payload.message || 'Health snapshot synced.' };
  } catch (error) {
    return { status: 'error', message: error instanceof Error ? error.message : 'Health snapshot sync failed.' };
  }
}
