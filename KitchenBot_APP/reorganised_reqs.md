# KitchenBot — Organised Requirements

## 1. General

- **Home screen**: 4 menu tiles styled like Android app icons (custom images, not emoji)
- **AI backend**: OpenAI API (key asked at first launch, stored encrypted)
- **Model tiering**: gpt-4o for recipes/cooking, gpt-4o-mini for cheap tasks (dimension/category guessing)
- **Navigation**: bottom bar must respect Android system navigation bar (no overlap)

---

## 2. Recipe Book

### 2.1 Creating a recipe (AI-powered)
- User writes a free-text prompt describing the dish they want.
- AI generates a full recipe: title, ingredients (with amounts), steps, tags (vegetarian, meat types, allergens), price range ($, $$, $$$).
- Recipe appears with ingredients on top, steps below.

### 2.2 Viewing a recipe
- Checkboxes next to every ingredient + a "Select All" master checkbox.
- **"To Shopping List"** button sends checked ingredients to the Shopping List.
- Price range badge ($, $$, $$$) shown on the recipe card.
- Auto-generated tags: vegetarian / meat type / allergen content.

### 2.3 Modifying a recipe (AI chat)
- Stationary chat box at the bottom (appears after pressing a "Modify" button).
- Modifications rendered as a diff: removed text in red, added text in green.
- User can accept or reject the diff.
- Works on both new and previously saved recipes.

### 2.4 Saving & organising
- User names the recipe and saves to the book.
- Sort and filter by: tags, allergens, meat type, price range, vegetarian.
- **Archived recipes**: temporary recipes auto-archive after 10 days; still searchable under "Archived".

---

## 3. Shopping List

### 3.1 Adding items
- Add manually by typing.
- Add via voice command (speech → AI → item entry).
- Add from Recipe Book ("To Shopping List" button).

### 3.2 Sorting & categories
- Default sort: by product type (dairy, fruit, vegetable, meat, etc.).
- Alphabetical sort toggle.
- **Store presets**: Lidl, Aldi, Auchan, Tesco, Spar, Coop — each defines a category display order.
- User can reorder categories, edit presets, or create new ones.

### 3.3 "Remind Me" feature
- App learns frequently added items over time (weighted by frequency).
- "Remind Me" suggests items: frequently bought, not already on the list, not in the home inventory.
- Pre-seeded with common essentials (toilet paper, etc.).
- User can add/remove items from the suggestion pool.

### 3.4 Purchasing flow
- Checkboxes next to each item; checking = crossed out + greyed.
- **"I Have Purchased"** button: removes checked items from the list and pushes them to the home inventory as pending purchases.

---

## 4. What I Have at Home (Home Inventory)

### 4.1 Incoming purchases
- After "I Have Purchased" in Shopping List, items enter a transient stage.
- On next open of this screen, user is asked for each item: **how much was bought?**
- 3 quick-amount buttons (e.g. 200 g, 500 g, 1 kg) + a custom text input.
- Dimension (unit) remembered per item; AI guesses dimension for new items (e.g. toilet paper → rolls, meat → kg, lemon → pieces).
- If user changes dimension, the new one is remembered for next time.

### 4.2 Smart quick-amount buttons
- Offer standard round values (200, 500, 1000 for grams; 0.5, 1, 2 for litres; etc.).
- If user enters a round custom value (e.g. 800 g), add it to future offers.
- Non-round values (e.g. 1352 g) are accepted but NOT added to offers.
- Support mixed units (g ↔ kg, ml ↔ L) with background conversion.

### 4.3 Receipt scanning (future)
- Take a photo of the receipt → AI (vision model) extracts items and amounts.
- Matched items pre-filled; unmatched items shown in grey (accept/reject).
- Receipt-missing items highlighted in faint red.

### 4.4 Display & management
- Categorised like the shopping list (same product types).
- Manual add with auto-filled dimension (or AI guess if new).
- **Mark as old**: flags the item so "Cook from What I Have" can prioritise using it.

---

## 5. Cook from What I Have

### 5.1 Configuration
- **Extra ingredient slider**: 0 % (only what's at home) → 100 % (anything goes).
- Filters: vegetarian, allergen exclusions, meat type.
- Meal type: pasta, soup, stew, quick meal, slow cook — only available if ingredients allow (at 0 %).
- **Prioritise emptying fridge**: uses items marked "old" first.

### 5.2 Suggestion flow
1. AI proposes recipe **titles** based on current inventory + filters.
2. User clicks a title → a more powerful AI model generates the full recipe.
3. User is taken to the Recipe Book create-screen pre-filled with the generated recipe.
4. Baseline is "what we have at home"; deviations trigger a warning.
5. User can modify via the chat box, then save or keep as temporary.

---

## 6. Settings

- Enter / update / delete OpenAI API key (stored in EncryptedSharedPreferences).

---

## 7. UI / UX

- Custom icon images for menu tiles (provided in `images/` folder).
- Dark theme or system-default theme support.
- Emojis for categories and visual cues throughout the app.
- Bottom bars must account for Android navigation bar padding.
