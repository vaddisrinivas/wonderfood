# Open-source UI expansion plan

Status: research and implementation plan only
Observed: 2026-07-26
Target: WonderFood/Utopian renderer on Expo 57, React Native 0.86, React 19.2, new architecture, Hermes, Android/iOS/web

## Executive recommendation

Do not embed one “universal builder.” None of the credible visual builders is a native React Native renderer. Use three layers:

1. **Web Studio:** spike [Puck](https://github.com/puckeditor/puck) as the drag/drop authoring shell. It edits a bounded `wonder.ui.v1` JSON document; it never emits React code, HTML, CSS, queries, or writes.
2. **Shared declarative renderer:** build a small native/web layout tree and compiled widget registry in this repository. This is the product runtime and remains config-driven.
3. **Capability adapters:** add maps, boards, charts, documents, and media behind typed widget contracts. Every data binding resolves an existing package `ViewModel`; every mutation proposes an existing canonical `Operation`.

Hard boundaries:

- Preserve the canonical operation/query/rules/workflow/provider kernel.
- UI config is not a record store, query engine, automation engine, or second writer.
- No package may contain executable code. No downloaded native plugin code.
- Web-only editors author config; they do not become the Android/iOS renderer.
- Runtime widgets receive projected rows and named actions only. They receive no DB, provider, token, filesystem, network, or arbitrary route handle.
- Package activation, rollback, receipts, policy, authority, and Undo stay on existing boundaries.

Recommended decisions:

| Capability | Primary | Fallback | Decision now |
|---|---|---|---|
| Drag/drop page builder | Puck in a web-only Studio | Craft.js concepts; React Grid Layout for bounded dashboard resizing | **SPIKE** Puck; **REJECT** native parity claim |
| Maps | MapLibre React Native + MapLibre GL JS | react-native-maps for simple marker/tile views | **SPIKE** MapLibre; adopt only after native/offline proof |
| Kanban board | react-native-reanimated-dnd on native; dnd-kit on web | Atlassian Pragmatic DnD on web | **SPIKE** native DnD; adopt accessible operation-backed board after proof |
| Full charts | Apache ECharts + React Native ECharts renderer | Victory Native; Gifted Charts for a narrow mobile set | **SPIKE**; block adoption on license clarification and bundle proof |
| Rich text | TenTap/Tiptap document model | react-native-enriched-html; Lexical for a web-only editor | **SPIKE** round-trip/security; no adoption before IME/a11y proof |
| Video/audio | SDK-matched `expo-video` and `expo-audio` | react-native-video for advanced video | **ADOPT** Expo modules; **REJECT** Track Player v5 license |
| Plugin widgets | Compiled widget registry + declarative manifest | isolated iframe/WebView for explicitly untrusted display-only widgets | **ADOPT** declarative registry; **REJECT** downloaded native code |
| Arbitrary nested layout | Owned `wonder.ui.v1` layout tree | Puck as authoring UI, React Grid Layout for web dashboard edit mode | **ADOPT** bounded nesting; **REJECT** arbitrary styles/code |

Brutal two-week boundary: the contract, nested renderer, media, and basic boards can be production candidates. Maps/charts can reach beta. The web Studio and rich editor can reach guarded spikes. A production-grade “Claude-design-like” builder, full rich-document parity, and safe third-party code marketplace cannot all be completed in two weeks.

## Current repository reality

Current facts:

- `packages/shared/contracts/package.ts` declares `list | board | table | calendar | timeline | chart`.
- `server/src/kernel/view.ts` only projects fields and groups rows. This is good: it is pure and renderer-neutral.
- `src/domain/renderer.tsx` only maps canonical records to one card/list view model.
- `src/components/ui.tsx` provides page, card, pill, row, metric, icon/image, and button primitives.
- Dashboard blocks are limited to `spotlight | metric | list | action`.
- The package schema permits only `size`, `tone`, and `href` in `ViewSpec.layout`.
- Package validation rejects objects containing keys named `code`, `javascript`, or `script`, but this heuristic is not a sufficient plugin sandbox.
- The app already has Reanimated 4 and Worklets. It does not directly depend on Gesture Handler, SVG, Skia, WebView, maps, audio, or video packages.

Consequences:

- Board/chart declarations are data-model capability, not polished UI capability.
- Maps, documents, and media should be widgets bound to existing views; they do not require a second query runtime.
- Layout and widget contracts should be one optional, independently versioned presentation document inside `AppPackageV2`.
- Existing package validators, registry activation, receipts, rollback, and control-plane sync are the right distribution path.

## Capability matrix

| Capability | Android/iOS | Web | Offline | Accessibility floor | Main cost |
|---|---|---|---|---|---|
| Web Studio | Viewer only | Full authoring | Bundled app shell; publishing queues locally | Keyboard reorder, focus restoration, live announcements | Large web-only editor bundle |
| Nested layout | Native | Native via RN Web | Yes | Semantic order must match visual order; depth does not alter focus order | Owned renderer/test surface |
| Map | MapLibre Native | MapLibre GL JS | Offline packs/tiles after explicit download | Always provide synchronized list/table alternative | Large native SDK, tiles/storage |
| Board | Reanimated/Gesture Handler | dnd-kit | Yes | Move menu and keyboard controls; drag never exclusive | Gesture complexity, virtualization |
| Chart | ECharts native renderer | ECharts | Yes for local rows | Text summary and data table required | ECharts + SVG/Skia bundle |
| Rich document | TenTap WebView or Enriched native | Tiptap/TenTap web | Yes for local docs | Screen-reader, keyboard, IME, selection tests | WebView/native editor complexity |
| Video/audio | Expo native modules | Expo web implementations | Local URI/cache | Captions, transcript, native controls, no autoplay | Native modules and media cache |
| Third-party widget | Compiled renderer only | Compiled renderer; optional sandboxed iframe | Preinstalled/cached only | Host-supplied label, fallback, focus boundary | Security review and quotas |

## Candidate ledger

Stars and activity are live GitHub observations from 2026-07-26, not durable quality scores.

Theming rule across all candidates:

- Puck, Craft.js, dnd-kit, Pragmatic DnD, Restyle, and the owned registry are headless/host-controlled enough to use existing LifeOS tokens. Puck Studio chrome stays web-only.
- GrapesJS and Utopia own much more CSS/editor surface; theme divergence is another reason not to embed them.
- MapLibre uses an allowlisted map style plus host colors/icons; map style URLs are data-source policy, not arbitrary theme URLs.
- Chart configs use semantic series/axis/status tokens compiled to ECharts/Victory props. Package config never supplies raw renderer themes.
- TenTap/Tiptap uses a bundled editor stylesheet; Enriched uses its native HTML style API. Both must map the same typography/color tokens.
- Media uses native controls where possible and host tokens for wrappers/posters; plugins receive a read-only resolved token subset.

Exact disposition:

- **Adopt now:** owned `wonder.ui.v1` layout/registry contracts; SDK-matched Expo video/audio; dnd-kit where needed inside web authoring.
- **Spike:** Puck, MapLibre native/web, Reanimated DnD, ECharts/React Native ECharts versus Victory Native, TenTap versus Enriched, optional WebView sandbox.
- **Borrow:** Craft.js node/slot serialization, Utopia inspector UX, Pragmatic DnD accessibility patterns, Endo capability vocabulary.
- **Reject/defer:** GrapesJS/Utopia as runtime, Mapbox without requirement, old Victory Native package, BlockNote v1 adoption, Track Player v5, QuickJS/SES on native, Tamagui migration, Remotion.
- **Fork:** none now. Fork only after a successful spike exposes a small, bounded upstream gap and maintenance/security ownership is accepted.

### 1. Drag/drop page builder and layouts

| Candidate | Observed state | Compatibility and cost | A11y/offline/security | Decision |
|---|---|---|---|---|
| [Puck](https://github.com/puckeditor/puck) | 13,019 stars; pushed 2026-07-24; MIT | React/DOM, React 18/19; web-only; medium/high bundle including dnd-kit, Radix, Tiptap, virtualization | Can be bundled/offline; must prove keyboard/focus flows; Puck config renders compiled React components | **SPIKE primary web Studio.** Compile its saved data to `wonder.ui.v1`; never use Puck output directly on native |
| [Craft.js](https://github.com/prevwong/craft.js) | 8,700; last push 2025-02-14; MIT | React/DOM only; serializes editor JSON; medium | No current accessibility claim found; stale release/activity relative to alternatives | **BORROW** node/slot ideas; **AVOID** new core dependency |
| [GrapesJS](https://github.com/GrapesJS/grapesjs) | 26,067; pushed 2026-07-24; BSD-3-Clause text | Web HTML/CSS template builder; high bundle and integration cost | Mature plugin system, but HTML/CSS/plugin output creates XSS and capability-bypass surface | **REJECT** as app renderer. Consider only for a separate HTML-template product |
| [Utopia](https://github.com/concrete-utopia/utopia) | 3,779; last push 2025-06-30; MIT | Full browser code editor/application, not an embeddable renderer library; very high | Executes/edits code and has a large server/toolchain footprint | **REJECT** embedding. Borrow inspector/canvas UX references only |
| [React Grid Layout](https://github.com/react-grid-layout/react-grid-layout) | 22,367; pushed 2026-04-15; MIT | Web-only responsive draggable/resizable grid | Useful for bounded dashboard coordinates; not a nested page model | **SPIKE fallback** inside Studio only |
| [dnd-kit](https://github.com/clauderic/dnd-kit) | 17,438; pushed 2026-07-13; MIT | Web/DOM; pointer, touch, keyboard; Puck already depends on its current packages | Built-in ARIA/live-region/keyboard concepts; offline once bundled | **ADOPT web** where Puck does not already encapsulate it |
| [Pragmatic DnD](https://github.com/atlassian/pragmatic-drag-and-drop) | 12,711; pushed 2026-07-25; Apache-2.0 text | Web native drag/drop; ~4.7 kB core; mirror of Atlassian monorepo | Strong assistive-control guidance and optional packages | **FALLBACK web board**, not native |
| [React Flow](https://github.com/xyflow/xyflow) | 37,803; pushed 2026-07-23; MIT | Web node/edge canvas; not a page layout system | Good future workflow/relationship canvas; large separate interaction model | **DEFER** to workflow/graph editor |

No reusable GitHub result for the exact “Claude design visual builder” query was found. Treat that phrase as a UX target. Puck is the closest embeddable open-source authoring shell; it is still web-only.

### 2. Maps

| Candidate | Observed state | Compatibility and cost | A11y/offline/security | Decision |
|---|---|---|---|---|
| [MapLibre React Native](https://github.com/maplibre/maplibre-react-native) | 635; pushed 2026-07-23; MIT | Expo >=54, RN >=0.80, React >=19.1; Android/iOS native; high native/build size | Has OfflineManager/offline-pack APIs; style/tile URLs and storage need allowlists/quotas; map needs list alternative | **SPIKE primary native** |
| [MapLibre GL JS](https://github.com/maplibre/maplibre-gl-js) | 11,150; pushed 2026-07-26; BSD-3-Clause text | Browser vector maps; separate renderer from native | Browser cache/offline needs app-managed service-worker policy; canvas needs list alternative | **SPIKE primary web** with shared map config |
| [react-native-maps](https://github.com/react-native-maps/react-native-maps) | 15,981; pushed 2026-07-25; MIT | RN >=0.76; Android/iOS; medium native cost; platform/provider differences | Supports local tile overlays; Android must disable base map to avoid background downloads | **FALLBACK** for simple pins, regions, and local tiles |
| [rnmapbox/maps](https://github.com/rnmapbox/maps) | 2,884; pushed 2026-07-22; MIT wrapper | Native Mapbox SDK, token, provider terms, native build | Strong map features, but Mapbox SDK/data licensing and credential handling add risk | **AVOID** unless a Mapbox-only requirement is approved |

One shared `map.v1` widget config must map to separate MapLibre native/web renderers. Do not use a WebView map on mobile as the default.

### 3. Kanban-quality board

| Candidate | Observed state | Compatibility and cost | A11y/offline/security | Decision |
|---|---|---|---|---|
| [react-native-reanimated-dnd](https://github.com/entropyconquers/react-native-reanimated-dnd) | 1,052; pushed 2026-03-16; MIT | RN >=0.80, Reanimated >=4.2, Gesture Handler >=2.28, Worklets >=0.7; good baseline fit | No library can make drag sufficient: add move-left/right/to-column controls and announcements | **SPIKE primary native** with 500-card/10-column stress gate |
| [dnd-kit](https://github.com/clauderic/dnd-kit) | 17,438; active; MIT | Web/DOM, nested containers, virtualization, keyboard sensors | Strongest shared web choice; operations must commit only on drop | **ADOPT web** |
| [Pragmatic DnD](https://github.com/atlassian/pragmatic-drag-and-drop) | 12,711; active; Apache-2.0 text | Web-only, low-level and incremental | Best fallback if dnd-kit/Puck interactions conflict; explicit assistive-control packages | **FALLBACK web** |
| [FlashList](https://github.com/Shopify/flash-list) | 7,150; pushed 2026-07-25; MIT | Native list virtualization; not DnD | Helps long columns, but nested horizontal/vertical measurement needs proof | **SPIKE support dependency** only if normal lists miss budget |

A board move is an `update_record` proposal using record revision and configured status/order fields. Optimistic UI may animate, but success state comes from the canonical operation receipt. Failure restores the source column. Reorder writes require stable fractional order keys or a bounded batch operation; never rewrite every card during drag.

### 4. Full charts

| Candidate | Observed state | Compatibility and cost | A11y/offline/security | Decision |
|---|---|---|---|---|
| [Apache ECharts](https://github.com/apache/echarts) | 66,906; pushed 2026-07-26; Apache-2.0 | Full browser chart engine; high JS bundle; tree-shakable by registered charts/components | Local/offline; forbid JS formatter callbacks and unsafe HTML tooltips; table fallback mandatory | **SPIKE primary engine** |
| [React Native ECharts](https://github.com/wuba/react-native-echarts) | 962; pushed 2026-07-06; root Apache-2.0, package says MIT | SVG or Skia native renderer, code reusable with web; requires ECharts/zrender and optional SVG/Skia/Gesture Handler | Avoid WebView; high bundle/native cost; license metadata conflict must be resolved | **SPIKE renderer; BLOCK ADOPTION** until license and Expo 57 matrix pass |
| [Victory Native XL](https://github.com/FormidableLabs/victory-native-xl) | 1,203; pushed 2026-07-06; package says MIT | D3 + Skia + Reanimated + Gesture Handler; performant native focus; smaller chart grammar than ECharts | Local/offline; still needs semantic summary/table | **FALLBACK native** if ECharts cost or license fails |
| [Victory](https://github.com/FormidableLabs/victory) | 11,242; last push 2025-12-19; MIT license text | Composable React/web and older native packages; medium/high dependency graph | Easier declarative subset, less “full library” coverage | **AVOID** old native package; use current Victory Native repo only |
| [visx](https://github.com/airbnb/visx) | 20,982; pushed 2026-06-22; MIT | Web/SVG primitives, not React Native parity | Maximum custom work and a11y responsibility | **BORROW/DEFER** for bespoke web-only visualizations |
| [Gifted Charts](https://github.com/Abhinandan-Kushwaha/react-native-gifted-charts) | 1,361; pushed 2026-05-20; MIT | Native, SVG/gradient peers, broad common chart list | Faster narrow adoption; no evidence it replaces ECharts-level grammar | **FALLBACK** for a small mobile chart subset |

`chart.v1` must be an allowlisted grammar: chart type, field encodings, aggregation, scale, legend, axis, theme token, interaction flags, and maximum points. No raw ECharts option object.

### 5. Rich-text document editor

| Candidate | Observed state | Compatibility and cost | A11y/offline/security | Decision |
|---|---|---|---|---|
| [TenTap](https://github.com/10play/10tap-editor) | 1,177; pushed/released 2025-11-27; MIT | React Native WebView over Tiptap/ProseMirror; Expo dev build for advanced use; supports Expo web; high JS/WebView cost | Best schema parity; highest WebView/XSS/bridge/focus risk; bundle editor locally, no remote page | **SPIKE primary cross-platform model**, not production adoption yet |
| [Tiptap](https://github.com/ueberdosis/tiptap) | 37,783; pushed 2026-07-24; MIT core | Web editor; ProseMirror JSON; strong extension ecosystem; no native renderer | Offline when bundled; sanitize links/media and disallow executable extensions | **ADOPT web model only** if TenTap round-trip wins |
| [react-native-enriched-html](https://github.com/software-mansion/react-native-enriched-html) | 1,336; pushed 2026-07-24; MIT | Fully native Android/iOS, new architecture, explicitly supports RN 0.86; experimental web; native/prebuild cost | Avoids WebView on native, uses sanitized HTML/Dompurify dependency; HTML canonical format is less structured | **SPIKE security-first native fallback** |
| [Lexical](https://github.com/facebook/lexical) | 23,708; pushed 2026-07-25; MIT | Excellent browser editor, JSON/Markdown/HTML, no maintained React Native renderer in this scan | Strong explicit WCAG/accessibility claim; plugin nodes still require allowlisting | **FALLBACK web**, not shared native solution |
| [BlockNote](https://github.com/TypeCellOS/BlockNote) | 10,014; pushed 2026-07-25; MPL-2.0 core; XL GPL-3/commercial | Polished Notion-style browser editor; no native renderer; heavier UI | Attractive block UX; mixed licensing and extension surface complicate product packaging | **REJECT for v1**; borrow block/slash UX |

Canonical storage must be `rich_document.v1`, not arbitrary HTML:

- `doc`: bounded ProseMirror-compatible JSON nodes.
- `plainText`: deterministic projection for search/accessibility.
- `schemaVersion`, `editorVersion`, attachment references, and content hash.
- Allowed nodes: paragraph, heading, text, strong, emphasis, link, list, quote, code block, image reference.
- No iframe, embed HTML, script, style, event handler, `data:` navigation, or raw remote attachment credentials.

Required spike: TenTap native -> persist JSON -> web Tiptap -> edit -> native TenTap, including Android backspace/IME/composition, paste, links, lists, images, undo, restart, and screen reader.

### 6. Video and audio

| Candidate | Observed state | Compatibility and cost | A11y/offline/security | Decision |
|---|---|---|---|---|
| [Expo](https://github.com/expo/expo) `expo-video` / `expo-audio` | 51,036; pushed 2026-07-26; MIT | First-party Android/iOS/web modules; SDK-matched install required; medium native cost | Local URIs and app-managed downloads work offline; add captions/transcripts and native controls | **ADOPT NOW** after Expo 57 package compatibility check |
| [react-native-video](https://github.com/TheWidlarzGroup/react-native-video) | 7,705; pushed 2026-07-07; MIT core | Advanced video, DRM/streaming, Expo plugin, new architecture; Nitro dependency; higher cost | Core local playback is open; advertised turnkey offline download is an optional commercial SDK | **FALLBACK** for approved advanced video requirements |
| [React Native Track Player](https://github.com/doublesymmetry/react-native-track-player) | 3,713; pushed 2026-07-25; v5 commercial/non-commercial license | Strong background playback, caching, Android Auto, new architecture | License is incompatible with an uncomplicated open-source/commercial distribution path; v4 is older Apache-2.0 | **REJECT v5** unless commercial license is explicitly approved |
| [react-player](https://github.com/cookpete/react-player) | 10,276; active in media scan | Browser player only | Useful only for a web-specific provider matrix | **AVOID** until Expo web playback is proven insufficient |

`media.v1` stores attachment/source references, MIME, duration, poster, caption track, transcript reference, autoplay=false, controls=true, and cache policy. Provider tokens and signed headers are never UI config.

### 7. Plugin widgets and arbitrary nesting

| Candidate | Observed state | Compatibility and cost | A11y/offline/security | Decision |
|---|---|---|---|---|
| Owned declarative registry | Repository code | Native/web, smallest runtime, exact kernel fit | Strongest allowlist, testability, offline, and fallback control | **ADOPT NOW** |
| [React Native WebView](https://github.com/react-native-webview/react-native-webview) | 7,185; pushed 2026-07-12; MIT | RN >=0.76 new architecture; Expo compatible; high security/test cost | Can isolate a local HTML widget imperfectly; bridge/navigation/file/network must be disabled/allowlisted | **SPIKE display-only sandbox**, never default |
| [quickjs-emscripten](https://github.com/justjake/quickjs-emscripten) | 1,684; pushed 2026-07-23; MIT text | Node/browser WASM, no React Native entry; Hermes/Metro integration unproven | Better JS isolation semantics than `eval`, but host capabilities and resource limits remain hard | **REJECT native adoption now**; possible server/web worker research |
| [Endo/SES](https://github.com/endojs/endo) | 1,035; pushed 2026-07-25; Apache-2.0 | Browser/Node secure JS compartments; no proven RN/Hermes product fit here | Strong conceptual capability model; large integration/security-review burden | **BORROW model**, no runtime dependency now |
| [Restyle](https://github.com/Shopify/restyle) | 3,397; pushed 2026-02-09; MIT | Small typed RN design-system helper | Does not create layouts or sandbox widgets | **DEFER**; current theme primitives are adequate |
| [Tamagui](https://github.com/tamagui/tamagui) | 14,101; pushed 2026-07-25; MIT | Cross-platform styling/UI/compiler; substantial migration/build change | Good parity, unrelated to plugin isolation | **REJECT migration for this goal** |

## `wonder.ui.v1` contract

Add one optional, independently versioned UI manifest under `AppPackageV2.presentation`. Do not widen `ViewSpec.layout` to arbitrary JSON.

```ts
type UiManifestV1 = {
  schemaVersion: 'wonder.ui.v1';
  roots: Record<string, string>; // surface id -> layout node id
  nodes: Record<string, LayoutNode>;
  widgets: Record<string, WidgetInstance>;
  actions: Record<string, UiActionBinding>;
  limits?: { maxDepth?: number; maxNodes?: number; maxWidgets?: number };
};

type LayoutNode =
  | { id: string; kind: 'stack'; axis: 'horizontal' | 'vertical'; gap: SpaceToken; children: string[] }
  | { id: string; kind: 'grid'; columns: 1 | 2 | 3 | 4; gap: SpaceToken; children: string[] }
  | { id: string; kind: 'scroll'; axis: 'horizontal' | 'vertical'; child: string }
  | { id: string; kind: 'tabs'; tabs: Array<{ id: string; label: string; child: string }> }
  | { id: string; kind: 'section'; title?: string; child: string }
  | { id: string; kind: 'widget'; widget: string };

type WidgetInstance = {
  id: string;
  kind:
    | 'records.list.v1'
    | 'records.metric.v1'
    | 'board.v1'
    | 'map.v1'
    | 'chart.v1'
    | 'rich-document.v1'
    | 'media.v1';
  view: string; // existing package view id
  props: Record<string, JsonValue>; // validated per widget kind
  actions?: string[]; // names from UiManifestV1.actions
  targets: Array<'native' | 'web'>;
  fallback: 'list' | 'table' | 'summary';
};

type UiActionBinding = {
  operation: string; // existing approved operation/tool id
  capability: string;
  mode: 'propose' | 'execute-if-policy-allows';
};
```

Validation:

- Node graph must be acyclic, reachable from roots, maximum depth 8, maximum 200 nodes, maximum 80 widgets.
- IDs and references must exist and be unique.
- Widget kind must be registered at build time.
- Widget `view` must reference `AppPackageV2.views`.
- Props use a per-kind JSON Schema with `additionalProperties: false`.
- Field mappings must reference `ViewSpec.fields`; no raw SQL, property path evaluation, regex, URL templates, or expression code.
- Layout accepts tokenized spacing, size, tone, alignment, and responsive visibility only. No raw CSS/style object.
- Actions reference named canonical operations and declared package capabilities.
- Unknown widget kinds fail package activation unless a known safe fallback is supplied and policy permits degraded activation.
- Existing executable-key scan stays as defense in depth; schema allowlisting becomes the actual boundary.

Example mapping:

```json
{
  "id": "projects.board",
  "kind": "board.v1",
  "view": "open-projects",
  "props": {
    "columnField": "status",
    "orderField": "rank",
    "titleField": "title",
    "columns": ["backlog", "active", "blocked", "done"]
  },
  "actions": ["move-project"],
  "targets": ["native", "web"],
  "fallback": "list"
}
```

## Plugin and sandbox security model

### Tier 0: built-in widgets

- Renderer code is compiled into the signed app.
- Package config only selects registered widget IDs and validated props.
- Widget receives immutable `ViewModel` rows, theme tokens, locale, and an action dispatcher.
- Action dispatcher accepts a named action plus validated values, then enters existing policy/operation flow.
- No direct imports from DB, providers, operation apply functions, secure store, filesystem, or networking.

### Tier 1: reviewed build-time plugins

- Normal npm/native dependency review and signed app release.
- Plugin exports manifest, prop schema, renderer, fallback, and capability list.
- Host registry wraps errors, time, row count, memory-heavy inputs, accessibility labels, and telemetry without record contents.
- Still no runtime code download.

### Tier 2: untrusted display-only web widget, experimental

- Web: sandboxed cross-origin iframe with CSP `default-src 'none'`; explicitly allow only required image/font sources. `sandbox` omits same-origin, forms, downloads, popups, top navigation, and storage.
- Native: local bundled HTML in React Native WebView only. Block all navigation and remote requests; disable file/universal access, storage, mixed content, geolocation, camera, microphone, and arbitrary bridge handlers.
- Bridge accepts versioned JSON messages validated with size, frequency, origin/session nonce, widget ID, and action allowlist.
- No provider credentials, cookies, DB handles, source URLs containing secrets, clipboard, or raw record objects.
- CPU/message quotas, 1 MB input cap, 64 KB message cap, timeout, crash fallback, and user-visible disable switch.
- Display-only by default. Mutations are named proposals through host policy.

### Forbidden

- `eval`, `Function`, remote JS bundle, Metro dynamic import from URL, arbitrary HTML in native views, arbitrary React component name from config, Node module access, direct MCP/provider call, custom SQL/query callback, raw ECharts formatter, raw WebView bridge command, or unsigned plugin update.
- QuickJS/SES does not waive capability review. A JS sandbox with a powerful host bridge is still a powerful plugin.

## Minimal glue code

Planned files:

| File | Change |
|---|---|
| `packages/shared/contracts/ui.ts` | `UiManifestV1`, layout/widget/action unions, JSON value types |
| `packages/shared/contracts/package.ts` | Optional `presentation.ui?: UiManifestV1` only |
| `server/src/kernel/ui.ts` | Graph/reference/limits/capability validation; no rendering or writes |
| `server/src/kernel/package-schema.ts` | Closed JSON Schema for `wonder.ui.v1` |
| `server/src/kernel/package.ts` | Invoke UI validator; keep executable-code rejection |
| `server/src/kernel/view.ts` | No semantic change; continue pure field projection/grouping |
| `src/domain/app-package-bridge.ts` | Pass validated UI manifest; legacy packages use existing dashboard blocks |
| `src/renderers/registry.tsx` | Compiled widget registry and target/fallback selection |
| `src/renderers/layout-renderer.tsx` | Bounded recursive layout renderer, error boundary, a11y order |
| `src/renderers/widgets/*.tsx` | One adapter per built-in widget kind |
| `src/studio/*.web.tsx` | Puck adapter/compiler/preview/publish flow, web only |
| `tests/contracts/ui-manifest.test.ts` | Schema, limits, cycles, missing refs, code rejection |
| `tests/renderers/*.test.tsx` | Per-widget config/view/action and fallback tests |
| `scripts/quality/check-ui-capability-matrix.mjs` | Android/web export and feature dependency gates |

Adapter rule: third-party components live behind one local adapter. Package config never contains a third-party library option object.

## Incompatibilities and explicit rejections

- Puck, Craft.js, GrapesJS, Utopia, React Grid Layout, dnd-kit, Pragmatic DnD, React Flow, Lexical, Tiptap, BlockNote, visx, and MapLibre GL JS are browser/DOM tools. They do not render native Android/iOS UI.
- A WebView around a web builder is not native parity. It adds keyboard, focus, memory, clipboard, accessibility, navigation, and security problems.
- MapLibre React Native and MapLibre GL JS are separate renderers sharing config, styles, and data—not one component.
- Native DnD and web DnD require separate interaction adapters.
- Full ECharts options are executable/configurable enough to violate the package boundary. Only a safe subset is allowed.
- Rich-text HTML is unsafe as an unvalidated canonical format. Raw HTML is an import/export projection only.
- BlockNote XL is GPL-3/commercial; do not pull it into the app accidentally.
- React Native ECharts has Apache-2.0 at repository root but `MIT` in package metadata. Adoption is blocked until maintainers clarify the distributed package license.
- React Native Track Player v5 is commercial/non-commercial licensed. Do not add it under an open-source assumption.
- react-native-video core is MIT, but advertised turnkey offline download is commercial. Do not list that SDK as open-source capability.
- Tamagui/Restyle are styling systems, not page builders or plugin sandboxes. Replacing the current design system does not solve this task.
- Remotion is a video composition/rendering framework, not an in-app media component; reject it here.

## Two-week aggressive execution plan

Assumption: 10 working days, four bounded lanes plus one integrator. Current repository instructions prohibit Expo UI edits in this pass; implementation starts only in an approved UI-expansion pass.

### Days 1-2: hard contracts and native-build risk first

- Lane A: implement `wonder.ui.v1` types, closed schema, graph validation, capability checks, fixtures, legacy-package compatibility.
- Lane B: create renderer registry/layout skeleton with list/summary fallback and error boundary.
- Lane C: create isolated native spikes for MapLibre, Reanimated DnD, ECharts renderer/Victory Native, TenTap/Enriched, Expo media. Record APK/AAB size deltas and build failures; do not merge packages yet.
- Lane D: Puck web Studio spike compiling one nested dashboard to `wonder.ui.v1`.
- Stop gate: no package adoption if clean Expo 57 Android/web export or license inventory fails.

### Days 3-4: vertical contract proof

- Render nested stack/grid/scroll/tabs/section/widget trees on Android and web.
- Publish Studio changes as a package-change proposal; activate only through registry approval/receipt; prove rollback.
- Prove map config renders two native markers and web equivalent with synchronized accessible list.
- Prove board move creates one canonical operation and Undo; drag cancel creates zero writes.
- Decide chart primary from bundle, frame time, license, and feature coverage.
- Decide rich editor primary from round-trip, IME, paste, restart, accessibility, and security evidence.

### Days 5-6: adapters in parallel

- Lane B1: board adapter, virtualization, non-drag move menu, optimistic rollback.
- Lane B2: chart adapter safe grammar, point caps, text/table alternative.
- Lane B3: media adapter, local/remote source policy, captions/transcript, lifecycle.
- Lane B4: map adapter, tile/style allowlist, offline quota/status, list fallback.
- Lane C: rich document adapter and import/export sanitizer.
- Integrator alone owns `package.json`, lockfile, Expo config, native project files, and renderer registry merge points.

### Days 7-8: hostile-state and cross-surface proof

- Empty/loading/error/stale/large datasets; dark/light, compact/comfortable; phone/tablet/web breakpoints.
- Offline restart for layouts, boards, charts, rich docs, local media, and downloaded map region.
- Screen reader and keyboard pass. Every drag/chart/map/media feature has a non-gesture/text alternative.
- Kill/restart during board move and Studio publish; prove no duplicate or partial writes.
- Untrusted config corpus: cycles, excessive depth, unknown widget, bad field, raw code keys, unsafe URL, HTML/script, oversized props, capability escalation.

### Days 9-10: integration and cut

- Current-tree config, typecheck, unit, server, web export, Android export, native emulator, accessibility, visual, performance, and security gates.
- Produce dependency/license/SBOM delta and APK/web-bundle size receipts.
- Ship only capabilities with current-tree evidence.
- Expected cut: nested renderer + Expo media production candidate; board/map/chart beta if gates pass; Puck Studio and rich editor guarded preview; Tier-2 untrusted widget remains disabled unless its separate security review passes.

## Parallel lanes and ownership

| Lane | Owns | Forbidden |
|---|---|---|
| A — contract/security | `packages/shared/contracts/ui.ts`, `server/src/kernel/ui.ts`, package validators/schemas, contract tests | UI components, dependencies, native files |
| B — renderer core | `src/renderers/registry.tsx`, `src/renderers/layout-renderer.tsx`, renderer-core tests | Kernel, provider, operations, lockfile |
| C1 — map | `src/renderers/widgets/map/**`, map tests/spike report | Other widgets, package schema |
| C2 — board | `src/renderers/widgets/board/**`, board tests/spike report | Operation implementation; it may only dispatch |
| C3 — chart | `src/renderers/widgets/chart/**`, chart tests/spike report | Raw engine options in public config |
| C4 — document/media | Separate `rich-document/**` and `media/**` ownership; no shared edits | Provider/source writers |
| D — Studio web | `src/studio/**`, web-only tests | Native renderer, direct registry activation, record writes |
| Q — quality | `tests/renderers/**`, quality script, evidence reports | Product code |
| Integrator | `package.json`, lockfile, Expo config, native files, registry index, shared route wiring | Feature implementation |

No lane edits `src/ops/**`, `src/actions/**`, `src/workflows/**`, `src/providers/**`, or provider/server writer paths.

## Tests and gates

### Contract

- Old package without `presentation.ui` remains byte-compatible in behavior.
- UI graph cycle, orphan, missing root, excessive depth/node/widget count rejected.
- Unknown widget, target, prop, field, view, action, operation, or capability rejected.
- Keys/values attempting script, function, remote module, HTML event, unsafe scheme, SQL, or provider credential rejected.
- Canonical JSON hashing is stable; activation/rollback receipts include package/UI hash.

### Renderer

- Every widget consumes only a frozen `ViewModel`.
- Native/web snapshot/interaction tests for each supported widget and fallback.
- Missing adapter or failed dynamic native surface shows deterministic fallback, not blank screen.
- 500 board cards, 5,000 chart points before downsampling, 200 layout nodes, and 50 map markers meet measured budgets.
- No render causes a write. Only explicit action dispatch reaches policy.

### Security

- Dependency audit, lockfile diff, license inventory, SBOM, and native permission diff.
- Rich-text malicious paste/import corpus.
- Map/style/media URL scheme, redirect, cleartext, credential, and file-access corpus.
- WebView/iframe navigation, bridge spoofing, oversized message, flood, timeout, crash, and reload tests.
- Plugin cannot read another widget’s rows or invoke undeclared actions.

### Accessibility

- Android TalkBack and web keyboard/screen-reader smoke.
- Board has move controls and announcements without drag.
- Chart has summary plus data table.
- Map has synchronized searchable list and selected-location announcement.
- Media has controls, captions/transcript path, no autoplay.
- Rich editor has label, toolbar state, selection/IME, headings/lists/links, undo/redo.
- Studio supports keyboard add/move/delete, focus restoration, and publish validation errors.

### Required repository gates

At integration:

```sh
npm run config:validate
npm run typecheck
npm run doctor
npm run export:web
npm run export:android
npm run test
npm run test:server:direct
npm run check:kernel-boundaries
npm run check:operation-boundary
npm run check:accessibility-smoke
npm run phase9:check:responsive-visual-matrix
npm run phase9:check:performance-budget
npm run phase3:check:chat-send
npm run phase3:check:chat-rollback-idempotency
```

Add:

```sh
npm run check:ui-contract
npm run check:ui-renderers
npm run check:ui-security
npm run check:ui-native-matrix
```

Native adoption also requires a physical/emulator proof at current HEAD, not only Expo export.

## Dependency and licensing risks

- Pin exact versions only after an Expo 57/RN 0.86 compatibility spike. GitHub main versions are observations, not an install plan.
- Prefer SVG over Skia for the first ECharts spike to avoid adding both native renderers. Compare against Victory Native before deciding.
- MapLibre increases Android/iOS binary size and adds tile/style/provider obligations. Product must choose a tile source and caching policy.
- Puck adds a substantial web-only dependency graph. Lazy-load Studio; exclude it from native bundles and normal app startup.
- TenTap adds React DOM, Tiptap, ProseMirror, and WebView. Enriched adds native C/new-architecture code. Neither is “small.”
- Third-party editor/chart plugin ecosystems are not automatically allowed. Only reviewed extensions enter the compiled registry.
- Root/package license mismatches are blockers, not paperwork to defer.
- Re-run GitHub activity, release, security, npm provenance, and license checks at dependency-selection time.

## Acceptance definition

The expansion is not “done” because components render.

Done for one capability means:

- Typed config and closed schema.
- Native and web support or an explicit platform/fallback declaration.
- Existing view/query input only.
- Existing operation/policy/receipt/Undo output only.
- Offline behavior documented and tested.
- Accessibility alternative tested.
- Security corpus passed.
- Dependency/license/native-permission delta approved.
- Current-tree build, performance, and device evidence stored.

## Research artifacts

Keyword scans used `--search-limit 100 --deep-scan-top 5 --deep-scan-page-limit 2 --deep-scan-max-raw-files 12`:

- `/Users/srinivasvaddi/projects/repos-exploration/utopian-page-builder`
- `/Users/srinivasvaddi/projects/repos-exploration/utopian-maps`
- `/Users/srinivasvaddi/projects/repos-exploration/utopian-kanban`
- `/Users/srinivasvaddi/projects/repos-exploration/utopian-charts`
- `/Users/srinivasvaddi/projects/repos-exploration/utopian-richtext`
- `/Users/srinivasvaddi/projects/repos-exploration/utopian-media`
- `/Users/srinivasvaddi/projects/repos-exploration/utopian-plugin-layout`

Targeted direct scans are stored as `/Users/srinivasvaddi/projects/repos-exploration/utopian-direct-*`. Thirty-three candidate folders were captured, including Puck, Craft.js, GrapesJS, Utopia, MapLibre native/web, react-native-maps, three DnD options, ECharts renderers, Victory Native, five editor options, Expo media, React Native Video, WebView, QuickJS, Endo, and layout helpers.

The initial page-builder/maps/Kanban keyword batch hit GitHub’s short search-rate limit on one topic sweep each. Their other sweeps and top-five deep scans completed; targeted direct scans supplied the candidate evidence. Some direct scans captured all JSON/raw artifacts but failed while generating `SUMMARY.md` because the skill assumed `repositoryTopics` was never null. Those partial folders remain usable and are not counted as clean scan completions.
