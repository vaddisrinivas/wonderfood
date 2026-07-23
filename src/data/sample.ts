export type FoodRecord = {
  id: string;
  title: string;
  meta: string;
  status: string;
  tone: 'moss' | 'amber' | 'plum' | 'blue';
  body: string;
  source: string;
  collection: 'inventory' | 'recipe' | 'shopping_item' | 'meal_plan';
  relations?: Array<{ name: string; target_id: string }>;
  food_detail: {
    nutrition: Array<[string, string]>;
    ingredients: Array<{ name: string; amount: string; state: 'available' | 'needed' | 'shopping' | 'previous' }>;
    instructions: string[];
    logs: Array<[string, string]>;
    variations: string[];
  };
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
    collection: 'inventory',
    relations: [{ name: 'supports', target_id: 'recipe-tandoori' }],
    food_detail: {
      nutrition: [
        ['Calories', '~140 kcal'],
        ['Protein', '~18 g'],
        ['Carbs', '~7 g'],
        ['Fat', '~4 g'],
        ['Serving', '170 g'],
      ],
      ingredients: [
        { name: 'Greek yogurt', amount: '2 tubs', state: 'available' },
        { name: 'Blueberries', amount: 'breakfast bowls', state: 'previous' },
        { name: 'Tandoori marinade', amount: '1 cup needed', state: 'available' },
      ],
      instructions: [
        'Use first in breakfast bowls or tandoori marinade.',
        'Check date before planning weekend meals.',
      ],
      logs: [
        ['Bought', 'Whole Foods'],
        ['Use by', '2 days'],
        ['Linked recipe', 'Sheet-pan tandoori chicken'],
      ],
      variations: ['Raita', 'Smoothie bowl', 'Protein pancake topping'],
    },
  },
  {
    id: 'recipe-tandoori',
    title: 'Sheet-pan tandoori chicken',
    meta: 'Recipe · 35 min · High protein',
    status: 'Tonight',
    tone: 'moss',
    body: 'Chicken thighs, yogurt, lemon, garam masala, broccoli. Serves four.',
    source: 'Notion · Recipes',
    collection: 'recipe',
    relations: [
      { name: 'uses', target_id: 'pantry-yogurt' },
      { name: 'plans', target_id: 'meal-green-dal' },
    ],
    food_detail: {
      nutrition: [
        ['Calories', '~610 kcal'],
        ['Protein', '~46 g'],
        ['Carbs', '~28 g'],
        ['Fat', '~34 g'],
        ['Fiber', '~7 g'],
        ['Serving', '1 of 4'],
      ],
      ingredients: [
        { name: 'Chicken thighs', amount: '1.5 lb', state: 'needed' },
        { name: 'Greek yogurt', amount: '1 cup marinade', state: 'available' },
        { name: 'Lemon', amount: '1', state: 'needed' },
        { name: 'Garam masala', amount: '2 tsp', state: 'previous' },
        { name: 'Broccoli', amount: '1 head', state: 'needed' },
      ],
      instructions: [
        'Mix yogurt, lemon, garam masala, salt and oil.',
        'Coat chicken and rest 20 minutes if time allows.',
        'Spread chicken and broccoli on a sheet pan.',
        'Roast at 425 F until chicken is cooked through.',
        'Rest 5 minutes before serving.',
      ],
      logs: [
        ['Last cooked', 'Use yogurt before expiry'],
        ['Planned', 'Tonight'],
        ['Serves', 'Four'],
      ],
      variations: ['Swap broccoli for cauliflower.', 'Serve with rice bowl base.', 'Use tofu or paneer with the same marinade.'],
    },
  },
  {
    id: 'shopping-spinach',
    title: 'Baby spinach',
    meta: 'Shopping · Produce · 1 bag',
    status: 'To buy',
    tone: 'blue',
    body: 'Needed for green dal and breakfast omelettes.',
    source: 'Google Sheets · Shopping',
    collection: 'shopping_item',
    relations: [{ name: 'restocks', target_id: 'meal-green-dal' }],
    food_detail: {
      nutrition: [
        ['Calories', '~23 kcal'],
        ['Protein', '~3 g'],
        ['Fiber', '~2 g'],
        ['Iron', 'High'],
      ],
      ingredients: [
        { name: 'Baby spinach', amount: '1 bag', state: 'shopping' },
        { name: 'Green dal', amount: 'linked meal', state: 'needed' },
        { name: 'Breakfast omelettes', amount: 'planned use', state: 'needed' },
      ],
      instructions: ['Buy one fresh bag.', 'Put away in produce drawer.', 'Use first for green dal, then omelettes.'],
      logs: [
        ['Reason', 'Needed for green dal and breakfast omelettes'],
        ['State', 'To buy'],
      ],
      variations: ['Frozen spinach backup', 'Baby kale if spinach unavailable'],
    },
  },
  {
    id: 'meal-green-dal',
    title: 'Green dal + rice',
    meta: 'Meal plan · Thursday dinner',
    status: 'Planned',
    tone: 'plum',
    body: 'Use spinach, moong dal and frozen ginger. Batch enough for Friday lunch.',
    source: 'SQLite · Meal plan',
    collection: 'meal_plan',
    relations: [
      { name: 'plans', target_id: 'shopping-spinach' },
      { name: 'plans', target_id: 'recipe-tandoori' },
    ],
    food_detail: {
      nutrition: [
        ['Calories', '~520 kcal'],
        ['Protein', '~24 g'],
        ['Fiber', '~13 g'],
        ['Carbs', '~82 g'],
        ['Fat', '~10 g'],
        ['Sodium', 'Depends on salt/stock'],
      ],
      ingredients: [
        { name: 'Moong dal', amount: '1 cup dry', state: 'available' },
        { name: 'Rice', amount: '1.5 cups cooked', state: 'available' },
        { name: 'Frozen ginger', amount: '1 tbsp', state: 'available' },
        { name: 'Baby spinach', amount: '1 bag', state: 'shopping' },
        { name: 'Tomato or lemon', amount: 'optional acid', state: 'previous' },
      ],
      instructions: [
        'Rinse moong dal until water runs mostly clear.',
        'Simmer dal with turmeric, ginger and salt until soft.',
        'Cook rice separately or use leftover rice.',
        'Fold spinach into dal at the end so it stays green.',
        'Finish with lemon, ghee or tempering if available.',
        'Batch extra dal for Friday lunch.',
      ],
      logs: [
        ['Planned', 'Thursday dinner'],
        ['Previous note', 'Batch enough for Friday lunch'],
        ['Shopping link', 'Baby spinach needed'],
      ],
      variations: [
        'Add yogurt raita if dinner needs more protein.',
        'Use frozen spinach if fresh spinach is not bought.',
        'Turn leftovers into khichdi with extra water and rice.',
      ],
    },
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
