export const APP_PACKAGE_WIDGET_KINDS = [
  'assistantChat',
  'healthConnect',
  'schemaEditor',
  'widgetCatalog',
  'postCard',
  'pollCard',
  'linkPreview',
  'feedList',
  'kanbanBoard',
  'chartBlock',
  'mediaBlock',
  'mapBlock',
  'formCard',
  'checklistCard',
  'calendarBlock',
  'timelineBlock',
  'galleryGrid',
  'dataTable',
  'permissionCard',
  'providerStatus',
  'themePreview',
  'foodHero',
  'useFirstCarousel',
  'mealTimeline',
  'recipeCard',
  'receiptReviewCard',
  'pantryShelf',
  'askFoodBar',
] as const;

export type AppPackageWidgetKind = typeof APP_PACKAGE_WIDGET_KINDS[number];

export const APP_PACKAGE_WIDGET_KIND_SET = new Set<string>([...APP_PACKAGE_WIDGET_KINDS]);

export function isAppPackageWidgetKind(value: unknown): value is AppPackageWidgetKind {
  return typeof value === 'string' && APP_PACKAGE_WIDGET_KIND_SET.has(value);
}
