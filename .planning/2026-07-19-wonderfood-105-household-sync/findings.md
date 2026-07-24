# Findings: WonderFood 1.0.5 household inventory, spending, and real sync

## User direction

- Shopping list must support non-food items.
- Pantry/inventory must support non-food household items.
- Shopping can be anything from anywhere, not only grocery food.
- Expense tracking should be receipt-backed and category-aware.
- Remove the 1.0.4 limitation that Notion, Google Sheets, and Postgres/Supabase are only foundation-level/partial sync.
- Notion and Sheets must feel like real household workspaces, not dumps.
- Existing external projects are references, not drop-in dependencies.

## Open issue clusters

- Daily loop and household UX: #18, #2, #11, #13, #28, #30, #38.
- Receipt and spending: #35, #7.
- Data homes and sync: #38 plus new focused sync issues needed.
- Recipe import and provider skills: #14, #19, #20, #21, #22, #23, #24, #25, #26.
- AI skills: #26, #27, #29, #31, #33, #34.
- Nutrition, household constraints, meal prep: #12, #16, #17, #36.
- Quality and trust: #1, #5, #6, #15, #37.

## Skill/reference mapping

- RecipeImportSkill: use Mealie, recipe-scrapers, html-recipe-parser patterns as references.
- NutritionEstimateSkill: use nutrition-mcp-server-python and USDA/Open Food Facts references.
- CookingCoachSkill: use PantryPalApp as a UX reference.
- MealPlanSkill: use CrewAI Pantry-to-Plate as a conceptual prompt/agent reference only.
- CartBuilderSkill: use Mealie, Tandoor, and KitchenOwl product behavior as references.

## Product call

The next release should generalize from a food-only app to a household consumption cockpit while keeping food as the strongest first-class domain. The data model should support item kind/category, purchase provenance, storage, quantity, and expense categories for both food and non-food items.
