import {
  buildPackageInstallPreview,
  parsePackageInstallTarget,
  validateRegistryManifest,
  type PackageInstallPreview,
  type PackageInstallTarget,
  type UtopiaRegistryManifest,
  type UtopiaRegistryPackage,
} from '@/packages/shared/contracts/package-install';

export type PackageInstallFetchResponse = Readonly<{
  ok: boolean;
  status: number;
  headers?: {
    get(name: string): string | null;
  };
  json?: () => Promise<unknown>;
  text?: () => Promise<string>;
}>;

export type PackageInstallFetcher = (url: string) => Promise<PackageInstallFetchResponse>;

export type PackageInstallCandidate = Readonly<{
  target: PackageInstallTarget;
  packageJson: unknown;
  preview: PackageInstallPreview;
}>;

export async function fetchPackageInstallCandidate(
  input: string,
  fetcher: PackageInstallFetcher,
  options: {
    registryPackage?: UtopiaRegistryPackage;
    expectedChecksum?: string;
  } = {},
): Promise<PackageInstallCandidate> {
  const target = parsePackageInstallTarget(input);
  const packageJson = await fetchJson(target.packageUrl, fetcher);
  return {
    target,
    packageJson,
    preview: buildPackageInstallPreview(packageJson, {
      sourceUrl: target.packageUrl,
      registryPackage: options.registryPackage,
      expectedChecksum: options.expectedChecksum,
    }),
  };
}

export async function fetchRegistryManifest(url: string, fetcher: PackageInstallFetcher): Promise<UtopiaRegistryManifest> {
  parsePackageInstallTarget(url);
  return validateRegistryManifest(await fetchJson(url, fetcher));
}

async function fetchJson(url: string, fetcher: PackageInstallFetcher): Promise<unknown> {
  let response: PackageInstallFetchResponse;
  try {
    response = await fetcher(url);
  } catch (error) {
    throw new Error(`package_fetch_failed:${error instanceof Error ? error.message : 'network_error'}`);
  }

  if (!response.ok) throw new Error(`package_fetch_failed:http_${response.status}`);
  const contentType = response.headers?.get('content-type') ?? '';
  if (contentType && !contentType.toLowerCase().includes('json')) {
    throw new Error('package_fetch_not_json');
  }

  if (response.json) return response.json();
  if (!response.text) throw new Error('package_fetch_no_body_reader');

  const body = await response.text();
  try {
    return JSON.parse(body);
  } catch {
    throw new Error('package_fetch_invalid_json');
  }
}
