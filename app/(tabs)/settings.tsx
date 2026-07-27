import { JsonRenderSurface } from '@/src/presentation/json-render-surface';
import { SETTINGS_SHELL_UI } from '@/src/presentation/shell-ui';

export default function SettingsScreen() {
  return (
    <JsonRenderSurface
      eyebrow="WONDER"
      title="Settings"
      subtitle="Simple by default. Advanced controls live behind focused screens."
      ui={SETTINGS_SHELL_UI}
    />
  );
}
