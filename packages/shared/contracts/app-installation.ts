import { z } from 'zod';

export const DEFAULT_WORKSPACE_ID = 'default-workspace' as const;
export const DEFAULT_APP_INSTALLATION_ID = 'default' as const;

export type WorkspaceId = string;
export type AppInstallationId = string;
export type AppInstallationStatus = 'active' | 'archived' | 'disabled';

export type AppInstallation = Readonly<{
  id: AppInstallationId;
  workspaceId: WorkspaceId;
  label: string;
  status: AppInstallationStatus;
  createdAt: string;
  updatedAt: string;
}>;

export type InstallationPackageState = Readonly<{
  installationId: AppInstallationId;
  activePackageKey: string | null;
  previousPackageKey: string | null;
  updatedAt: string;
}>;

const timestampSchema = z.string().refine((value) => !Number.isNaN(Date.parse(value)), {
  message: 'must be an ISO timestamp',
});

export const workspaceIdSchema = z.string().trim().min(1).max(128);
export const appInstallationIdSchema = z.string().trim().min(1).max(128);
export const appInstallationStatusSchema = z.enum(['active', 'archived', 'disabled']);

export const appInstallationSchema = z.object({
  id: appInstallationIdSchema,
  workspaceId: workspaceIdSchema,
  label: z.string().trim().min(1).max(160),
  status: appInstallationStatusSchema,
  createdAt: timestampSchema,
  updatedAt: timestampSchema,
});

export const installationPackageStateSchema = z.object({
  installationId: appInstallationIdSchema,
  activePackageKey: z.string().trim().min(1).max(256).nullable(),
  previousPackageKey: z.string().trim().min(1).max(256).nullable(),
  updatedAt: timestampSchema,
});

export function parseAppInstallation(input: unknown): AppInstallation {
  return appInstallationSchema.parse(input);
}

export function parseInstallationPackageState(input: unknown): InstallationPackageState {
  return installationPackageStateSchema.parse(input);
}
