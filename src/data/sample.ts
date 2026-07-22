export type FoodRecord = {
  id: string;
  title: string;
  meta: string;
  status: string;
  tone: 'moss' | 'amber' | 'plum' | 'blue';
  body: string;
  source: string;
};

export const foodRecords: FoodRecord[] = [
  {
    id: 'pantry-yogurt',
    title: 'Greek yogurt',
    meta: 'Pantry · Dairy · 2 tubs',
    status: 'Use in 2 days',
    tone: 'amber',
    body: 'Bought at Whole Foods. Use for breakfast bowls or the tandoori marinade.',
    source: 'Notion · Pantry',
  },
  {
    id: 'recipe-tandoori',
    title: 'Sheet-pan tandoori chicken',
    meta: 'Recipe · 35 min · High protein',
    status: 'Tonight',
    tone: 'moss',
    body: 'Chicken thighs, yogurt, lemon, garam masala, broccoli. Serves four.',
    source: 'Notion · Recipes',
  },
  {
    id: 'shopping-spinach',
    title: 'Baby spinach',
    meta: 'Shopping · Produce · 1 bag',
    status: 'To buy',
    tone: 'blue',
    body: 'Needed for green dal and breakfast omelettes.',
    source: 'Google Sheets · Shopping',
  },
  {
    id: 'meal-green-dal',
    title: 'Green dal + rice',
    meta: 'Meal plan · Thursday dinner',
    status: 'Planned',
    tone: 'plum',
    body: 'Use spinach, moong dal and frozen ginger. Batch enough for Friday lunch.',
    source: 'SQLite · Meal plan',
  },
];

export const sourceRows = [
  ['Notion', 'Configured', 'Demo snapshot', 'LifeOS 2026'],
  ['Google Sheets', 'Configured', 'Demo snapshot', 'LifeOS Master'],
  ['SQLite', 'UI ready', 'Demo snapshot', 'Local store next'],
  ['Postgres', 'Available', '—', 'Add connection'],
] as const;

export const domains = [
  ['Food', 'Active', '12 schemas · 6 skills'],
  ['Health', 'Ready', 'Health Connect adapter'],
  ['Plants', 'Ready', '4 schemas · 3 skills'],
  ['Money', 'Preview', 'Template available'],
] as const;
