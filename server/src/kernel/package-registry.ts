import { validateComputedFieldGraph } from './computed-fields';
import { AppPackageV2, type PackageValidation, validateAppPackage } from './package';

export class PackageRegistry {
  private active: AppPackageV2 | null = null;
  private previous: AppPackageV2 | null = null;

  preview(input: unknown): PackageValidation {
    const result = validateAppPackage(input);
    if (!result.valid) return result;
    try {
      validateComputedFieldGraph({
        specs: result.package.computedFields ?? [],
        collections: Object.keys(result.package.collections),
      });
      return result;
    } catch (error) {
      return {
        valid: false,
        errors: [error instanceof Error ? error.message : 'computed_field_graph_invalid'],
      };
    }
  }

  activate(input: unknown): AppPackageV2 {
    const result = this.preview(input);
    if (!result.valid) throw new Error(`package_invalid:${result.errors.join('|')}`);
    this.previous = this.active;
    this.active = result.package;
    return this.active;
  }

  rollback(): AppPackageV2 | null {
    const current = this.active;
    this.active = this.previous;
    this.previous = current;
    return this.active;
  }

  getActive(): AppPackageV2 | null {
    return this.active;
  }
}
