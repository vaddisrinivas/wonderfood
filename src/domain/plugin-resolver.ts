import type {
  PluginCompatibilityRequest,
  PluginCompatibilityResult,
  PluginManifest,
  PluginLock,
} from '@/packages/shared/contracts/plugin';
import {
  lockPluginManifest,
  resolvePluginCompatibility,
  validatePluginManifest,
} from '@/packages/shared/contracts/plugin';

export type { PluginCompatibilityRequest, PluginCompatibilityResult, PluginManifest, PluginLock };

export function buildLockedPlugin(manifest: PluginManifest): PluginLock {
  return lockPluginManifest(manifest);
}

export function checkPluginCompatibility(
  manifest: PluginManifest,
  request: PluginCompatibilityRequest,
): PluginCompatibilityResult {
  return resolvePluginCompatibility(manifest, request);
}

export function validatePlugin(manifest: unknown) {
  return validatePluginManifest(manifest);
}

