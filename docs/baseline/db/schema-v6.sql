CREATE TABLE android_metadata (locale TEXT);
CREATE TABLE chat_messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    role TEXT NOT NULL,
    body TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE TABLE sqlite_sequence(name,seq);
CREATE TABLE inventory_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    quantity TEXT NOT NULL,
    zone TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT '',
    serving_text TEXT NOT NULL DEFAULT '',
    calories INTEGER,
    protein_g REAL,
    carbs_g REAL,
    fat_g REAL,
    nutrition_source TEXT NOT NULL DEFAULT '',
    notes TEXT NOT NULL DEFAULT '',
    image_uri TEXT,
    expires_at INTEGER,
    source TEXT NOT NULL,
    source_message_id INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE TABLE grocery_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    quantity TEXT NOT NULL,
    status TEXT NOT NULL,
    category TEXT NOT NULL DEFAULT '',
    serving_text TEXT NOT NULL DEFAULT '',
    calories INTEGER,
    protein_g REAL,
    carbs_g REAL,
    fat_g REAL,
    nutrition_source TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL DEFAULT 'chat',
    image_uri TEXT,
    source_message_id INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE TABLE recipes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    ingredients TEXT NOT NULL,
    steps TEXT NOT NULL,
    servings INTEGER,
    prep_minutes INTEGER,
    tags TEXT NOT NULL DEFAULT '',
    rating INTEGER,
    image_uri TEXT,
    source_message_id INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE TABLE meal_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    calories INTEGER NOT NULL,
    protein_g REAL NOT NULL,
    carbs_g REAL NOT NULL,
    fat_g REAL NOT NULL,
    meal_slot TEXT NOT NULL DEFAULT 'FLEX',
    used_items_text TEXT NOT NULL DEFAULT '',
    logged_date_epoch_day INTEGER NOT NULL DEFAULT 0,
    source TEXT NOT NULL,
    source_message_id INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE TABLE meal_plans (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    days_text TEXT NOT NULL,
    grocery_hint TEXT NOT NULL,
    status TEXT NOT NULL,
    start_date_epoch_day INTEGER,
    source_message_id INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE TABLE meal_plan_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id INTEGER NOT NULL,
    date_epoch_day INTEGER NOT NULL,
    slot TEXT NOT NULL,
    title TEXT NOT NULL,
    calorie_target INTEGER,
    status TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE TABLE receipt_captures (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    image_uri TEXT NOT NULL,
    raw_text TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE TABLE user_preferences (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE TABLE chat_actions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    summary TEXT NOT NULL,
    rows_text TEXT NOT NULL,
    operation TEXT NOT NULL,
    status TEXT NOT NULL,
    source TEXT NOT NULL,
    confidence REAL NOT NULL,
    source_message_id INTEGER,
    created_at INTEGER NOT NULL,
    resolved_at INTEGER
);
CREATE TABLE inventory_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    inventory_item_id INTEGER,
    item_name TEXT NOT NULL,
    quantity_text TEXT NOT NULL DEFAULT '',
    zone TEXT NOT NULL,
    action TEXT NOT NULL,
    reason TEXT NOT NULL DEFAULT '',
    related_recipe_id INTEGER,
    related_meal_log_id INTEGER,
    occurred_date_epoch_day INTEGER NOT NULL,
    source TEXT NOT NULL DEFAULT 'chat',
    created_at INTEGER NOT NULL
);
CREATE TABLE food_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,
    started_at INTEGER NOT NULL,
    ended_at INTEGER,
    duration_minutes INTEGER,
    amount REAL,
    unit TEXT NOT NULL DEFAULT '',
    source TEXT NOT NULL DEFAULT 'manual',
    confidence TEXT NOT NULL DEFAULT 'ESTIMATED',
    related_recipe_id INTEGER,
    meal_log_id INTEGER,
    shopping_trip_id INTEGER,
    inventory_item_id INTEGER,
    note TEXT NOT NULL DEFAULT '',
    created_at INTEGER NOT NULL
);
