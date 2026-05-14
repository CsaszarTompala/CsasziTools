package com.example.kitchenbot.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAiService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun getApiKey(): String =
        ApiKeyStore.getApiKey(context)
            ?: throw IllegalStateException("No OpenAI API key configured. Please set it in Settings.")

    // ── Recipe Generation (gpt-4o — powerful) ────────────────────────────────

    suspend fun generateRecipe(prompt: String, knownDimensions: Map<String, String> = emptyMap()): Recipe = withContext(Dispatchers.IO) {
        val response = callChat(
            model = "gpt-4o",
            systemPrompt = recipeSystemPrompt(knownDimensions),
            userPrompt = prompt
        )
        parseRecipeJson(response)
    }

    // ── Recipe Modification (gpt-4o) ─────────────────────────────────────────

    suspend fun modifyRecipe(recipe: Recipe, modificationPrompt: String, knownDimensions: Map<String, String> = emptyMap()): Recipe =
        withContext(Dispatchers.IO) {
            val recipeText = formatRecipeForAi(recipe)
            val response = callChat(
                model = "gpt-4o",
                systemPrompt = recipeModifyPrompt(knownDimensions),
                userPrompt = "Current recipe:\n$recipeText\n\nModification requested: $modificationPrompt"
            )
            parseRecipeJson(response)
        }

    // ── Cooking Suggestions (gpt-4o) ─────────────────────────────────────────

    suspend fun generateCookingSuggestions(
        homeItems: List<HomeItem>,
        extraPercent: Float,
        isVegetarian: Boolean,
        excludeAllergens: Set<String>,
        meatType: String?,
        dishType: String?,
        prioritizeOld: Boolean
    ): List<String> = withContext(Dispatchers.IO) {
        val itemsList = homeItems.joinToString(", ") { item ->
            val old = if (item.isOld) " [OLD - use first!]" else ""
            "${item.name} (${item.amount} ${item.dimension})$old"
        }

        val constraints = buildString {
            appendLine("Available ingredients at home: $itemsList")
            appendLine("Extra ingredients allowed: ${(extraPercent * 100).toInt()}%")
            if (isVegetarian) appendLine("MUST be vegetarian.")
            if (excludeAllergens.isNotEmpty()) appendLine("Exclude allergens: ${excludeAllergens.joinToString(", ")}")
            if (meatType != null) appendLine("Preferred meat: $meatType")
            if (dishType != null) appendLine("Dish type: $dishType")
            if (prioritizeOld) appendLine("Prioritize using items marked [OLD] to avoid food waste.")
        }

        val response = callChat(
            model = "gpt-4o",
            systemPrompt = cookingSuggestionsPrompt(),
            userPrompt = constraints
        )

        try {
            val arr = JSONArray(response.trim())
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            // Fallback: split by newlines
            response.lines()
                .map { it.trimStart('-', ' ', '*', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.') }
                .filter { it.isNotBlank() }
                .take(6)
        }
    }

    // ── Dimension Suggestion (gpt-4o-mini — cheap) ───────────────────────────

    suspend fun suggestDimension(itemName: String): String = withContext(Dispatchers.IO) {
        val response = callChat(
            model = "gpt-4o-mini",
            systemPrompt = """You suggest measurement units for grocery items.
                |${langInstruction()}
                |Respond with ONLY the unit word, nothing else.
                |Examples: kg, g, db, tekercs, L, ml, csomag, köteg, doboz, üveg, zacskó.
                |Choose the most common purchase unit for everyday shopping.""".trimMargin(),
            userPrompt = "What is the most common measurement unit for: $itemName"
        )
        response.trim().lowercase().take(20)
    }

    // ── Parse & Categorize Item (gpt-4o-mini — single cheap call) ────────────

    data class ParsedItem(
        val cleanName: String,
        val amount: String,
        val unit: String,
        val category: String
    )

    suspend fun parseAndCategorizeItem(rawText: String): ParsedItem = withContext(Dispatchers.IO) {
        val categories = ProductCategory.entries.joinToString(", ") { it.name }
        val response = callChat(
            model = "gpt-4o-mini",
            systemPrompt = """You parse grocery item text and extract structured data.
                |The input may contain an amount, unit, and product name mixed together (in any language, often Hungarian).
                |
                |Return a JSON object with these fields:
                |{"name": "clean product name only", "amount": "numeric amount or empty", "unit": "metric unit or empty", "category": "CATEGORY_NAME"}
                |
                |RULES:
                |1. "name" must be ONLY the product name, no amounts or units. E.g. "300g garnélarák" → name="garnélarák"
                |2. "amount" is the numeric value. E.g. "300g garnélarák" → amount="300"
                |3. "unit" is the metric unit. E.g. "300g garnélarák" → unit="g". Convert non-metric: 1 cup≈250ml, 1 tbsp≈15ml, etc.
                |4. "category" must be one of: $categories
                |5. Category rules:
                |   - Fish, seafood, shrimp (garnélarák, hal, lazac, tonhal) = MEAT
                |   - All cheese (sajt, trappista, parmezán, mozzarella) = DAIRY
                |   - só, bors, fűszer, oregánó, fahéj, curry = SPICES
                |   - Fresh paprika (vegetable) = VEGETABLE, ground paprika (spice) = SPICES
                |6. Return ONLY the JSON, no other text.""".trimMargin(),
            userPrompt = "Parse: $rawText"
        )
        try {
            val jsonStr = if (response.contains("```")) {
                response.substringAfter("```json", response.substringAfter("```"))
                    .substringBefore("```").trim()
            } else response.trim()
            val json = JSONObject(jsonStr)
            ParsedItem(
                cleanName = json.optString("name", rawText).trim(),
                amount = json.optString("amount", "").trim(),
                unit = json.optString("unit", "").trim(),
                category = json.optString("category", "OTHER").trim().uppercase()
            )
        } catch (_: Exception) {
            ParsedItem(cleanName = rawText, amount = "", unit = "", category = "OTHER")
        }
    }

    // ── Item Categorization (gpt-4o-mini — cheap) ────────────────────────────

    suspend fun categorizeItem(itemName: String): String = withContext(Dispatchers.IO) {
        val categories = ProductCategory.entries.joinToString(", ") { it.name }
        val response = callChat(
            model = "gpt-4o-mini",
            systemPrompt = """You categorize grocery items into exactly one of these categories: $categories
                |RULES:
                |1. Respond with ONLY the single category name in UPPERCASE. No punctuation, no explanation.
                |2. The item name may be in Hungarian or English.
                |3. Use the examples below to decide. If an item is a type of cheese, yogurt, milk, cream, or butter it is ALWAYS DAIRY.
                |
                |DAIRY: tej, vaj, sajt, túró, tejföl, joghurt, kefir, mascarpone, parmezán, mozzarella, trappista, milk, cheese, butter, cream, yogurt, cottage cheese, sour cream
                |FRUIT: alma, banán, narancs, szőlő, eper, málna, citrom, körte, barack, cseresznye, apple, banana, orange, lemon, strawberry
                |VEGETABLE: paradicsom, hagyma, burgonya, uborka, répa, brokkoli, karfiol, cukkíni, padlizsán, spenót, saláta, paprika (zöldség), tomato, onion, potato, carrot, broccoli
                |MEAT: csirke, sertés, marha, hal, szalonna, kolbász, sonka, pulyka, bacon, chicken, beef, pork, fish, turkey, ham, sausage
                |BAKERY: kenyér, zsemle, kifli, kalács, péksütemény, bread, roll, croissant, pastry
                |DRINKS: víz, sör, bor, juice, kóla, tea, kávé, üdítő, water, beer, wine, coffee, soda
                |FROZEN: fagyasztott zöldség, jégkrém, fagyasztott hal, frozen vegetables, ice cream
                |SNACKS: chips, csoki, keksz, mogyoró, mandula, dió, chocolate, cookies, nuts, crackers
                |SPICES: só, bors, paprika őrlemény, fűszerpaprika, oregánó, fahéj, curry, köménymag, bazsalikom, rozmaring, kakukkfű, majoránna, szerecsendió, fokhagyma por, chili, vanília, babérlevél, salt, pepper, cinnamon, cumin, oregano, basil, garlic powder, paprika powder
                |HOUSEHOLD: WC papír, szalvéta, mosószer, szivacs, alufólia, szemeteszsák, toilet paper, detergent, sponge
                |OTHER: anything that truly doesn't fit any category above
                |
                |IMPORTANT: "paprika" as a fresh vegetable = VEGETABLE. "paprika" as ground spice / fűszer / őrlemény = SPICES.
                |IMPORTANT: Any cheese (sajt, trappista, parmezán, mozzarella, etc.) = DAIRY. Never OTHER.
                |IMPORTANT: só (salt), bors (pepper), and all seasonings = SPICES. Never OTHER.""".trimMargin(),
            userPrompt = "Categorize: $itemName"
        )
        // Extract the category name robustly – strip anything that isn't a letter
        val cleaned = response.trim().uppercase().replace(Regex("[^A-Z]"), "")
        // Find the first matching category in the response
        ProductCategory.entries.firstOrNull { cleaned.contains(it.name) }?.name ?: cleaned
    }

    // ── Full Recipe from Suggestion Title (gpt-4o) ───────────────────────────

    suspend fun generateRecipeFromSuggestion(
        title: String,
        homeItems: List<HomeItem>,
        isVegetarian: Boolean,
        extraPercent: Float,
        knownDimensions: Map<String, String> = emptyMap()
    ): Recipe = withContext(Dispatchers.IO) {
        val itemsList = homeItems.joinToString(", ") { "${it.name} (${it.amount} ${it.dimension} available)" }
        val prompt = buildString {
            appendLine("Create a full recipe for: $title")
            appendLine("Available ingredients at home: $itemsList")
            appendLine("IMPORTANT: Use only REASONABLE amounts for a single meal (typically 2-4 servings). Do NOT use all available quantity of each ingredient. For example if I have 2 kg pasta, use only 400-500g; if I have 5 kg tomatoes, use only 400-500g, etc.")
            appendLine("Extra ingredients allowed: ${(extraPercent * 100).toInt()}%")
            if (isVegetarian) appendLine("Must be vegetarian.")
            appendLine("If using ingredients not at home, clearly mark them as [EXTRA].")
        }

        val response = callChat(
            model = "gpt-4o",
            systemPrompt = recipeSystemPrompt(knownDimensions),
            userPrompt = prompt
        )
        parseRecipeJson(response)
    }

    // ── Core API Call ────────────────────────────────────────────────────────

    private fun callChat(model: String, systemPrompt: String, userPrompt: String): String {
        val apiKey = getApiKey()

        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            })
            put("temperature", 0.7)
            put("max_tokens", 2000)
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw RuntimeException("Empty response from OpenAI")

        if (!response.isSuccessful) {
            val errorMsg = try {
                JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: responseBody
            } catch (_: Exception) { responseBody }
            throw RuntimeException("OpenAI API error (${response.code}): $errorMsg")
        }

        val json = JSONObject(responseBody)
        return json.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    // ── JSON Parsing ─────────────────────────────────────────────────────────

    private fun parseRecipeJson(raw: String): Recipe {
        // Extract JSON from markdown code blocks if present
        val jsonStr = if (raw.contains("```")) {
            raw.substringAfter("```json", raw.substringAfter("```"))
                .substringBefore("```")
                .trim()
        } else {
            raw.trim()
        }

        val json = JSONObject(jsonStr)

        val ingredients = mutableListOf<Ingredient>()
        val ingArr = json.getJSONArray("ingredients")
        for (i in 0 until ingArr.length()) {
            val ing = ingArr.getJSONObject(i)
            ingredients.add(
                Ingredient(
                    name = ing.getString("name"),
                    amount = ing.optString("amount", ""),
                    unit = ing.optString("unit", "")
                )
            )
        }

        val steps = mutableListOf<String>()
        val stepsArr = json.getJSONArray("steps")
        for (i in 0 until stepsArr.length()) {
            steps.add(stepsArr.getString(i))
        }

        val tags = mutableListOf<String>()
        val tagsArr = json.optJSONArray("tags")
        if (tagsArr != null) {
            for (i in 0 until tagsArr.length()) {
                tags.add(tagsArr.getString(i))
            }
        }

        val allergens = mutableListOf<String>()
        val allergensArr = json.optJSONArray("allergens")
        if (allergensArr != null) {
            for (i in 0 until allergensArr.length()) {
                allergens.add(allergensArr.getString(i))
            }
        }

        val priceStr = json.optString("priceRange", "MEDIUM")
        val priceRange = try {
            PriceRange.valueOf(priceStr.uppercase())
        } catch (_: Exception) {
            PriceRange.MEDIUM
        }

        return Recipe(
            name = json.getString("name"),
            ingredients = ingredients,
            steps = steps,
            tags = tags,
            allergens = allergens,
            priceRange = priceRange
        )
    }

    private fun formatRecipeForAi(recipe: Recipe): String = buildString {
        appendLine("Name: ${recipe.name}")
        appendLine("Ingredients:")
        recipe.ingredients.forEach {
            appendLine("- ${it.amount} ${it.unit} ${it.name}".trim())
        }
        appendLine("Steps:")
        recipe.steps.forEachIndexed { i, step ->
            appendLine("${i + 1}. $step")
        }
        if (recipe.tags.isNotEmpty()) appendLine("Tags: ${recipe.tags.joinToString(", ")}")
        if (recipe.allergens.isNotEmpty()) appendLine("Allergens: ${recipe.allergens.joinToString(", ")}")
        appendLine("Price range: ${recipe.priceRange.name}")
    }

    companion object {
        private fun currentLanguage(): String {
            val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            return if (tag.startsWith("en")) "English" else "Hungarian"
        }

        private fun langInstruction(): String =
            "IMPORTANT: ALL text content (recipe name, ingredient names, units, steps, tags, allergens) MUST be written in ${currentLanguage()}."

        private fun dimensionHint(knownDimensions: Map<String, String>): String {
            if (knownDimensions.isEmpty()) return ""
            val examples = knownDimensions.entries.take(30).joinToString(", ") { "${it.key}=${it.value}" }
            return "\n|- The user already tracks these ingredients in specific units. ALWAYS use the same unit for them: $examples"
        }

        fun recipeSystemPrompt(knownDimensions: Map<String, String> = emptyMap()) = """You are a professional chef and recipe creator. Generate a recipe based on the user's description.
            |${langInstruction()}
            |Return the result as JSON with this exact structure (no markdown, just raw JSON):
            |{
            |  "name": "Recipe Name",
            |  "ingredients": [{"amount": "500", "unit": "g", "name": "rizs"}],
            |  "steps": ["Step 1 description", "Step 2 description"],
            |  "tags": ["vegetarian", "quick"],
            |  "allergens": ["gluten", "dairy"],
            |  "priceRange": "CHEAP"
            |}
            |Rules:
            |- ALWAYS use SI/metric units: g, kg, ml, L, db (pieces). NEVER use cups (csésze), tablespoons (evőkanál), teaspoons (kiskanál), pinch (csipet), or any non-metric units.
            |- Convert everything to measurable metric amounts: "a pinch of salt" → "2 g só", "1 tablespoon oil" → "15 ml olaj", "1 cup flour" → "120 g liszt".${dimensionHint(knownDimensions)}
            |- The "name" field must contain ONLY the ingredient name (e.g. "rizs", "liszt", "tej"), NOT the amount or unit.
            |- Be thorough with ingredients (include amounts and units) and steps.
            |- Auto-detect vegetarian status. If no meat/fish, add "vegetarian" tag.
            |- Add meat type tags (chicken, beef, pork, fish, etc.) if used.
            |- Auto-detect allergens: gluten, dairy, nuts, eggs, soy, shellfish.
            |- Estimate priceRange: CHEAP (simple/few ingredients), MEDIUM (moderate), EXPENSIVE (premium/many ingredients).
            |- Return ONLY the JSON, no other text.""".trimMargin()

        fun recipeModifyPrompt(knownDimensions: Map<String, String> = emptyMap()) = """You are a professional chef. Modify the given recipe according to the user's request.
            |${langInstruction()}
            |Return the COMPLETE modified recipe as JSON with the same structure:
            |{
            |  "name": "Recipe Name",
            |  "ingredients": [{"amount": "500", "unit": "g", "name": "rizs"}],
            |  "steps": ["Step 1 description", "Step 2 description"],
            |  "tags": ["vegetarian", "quick"],
            |  "allergens": ["gluten", "dairy"],
            |  "priceRange": "CHEAP"
            |}
            |Rules:
            |- ALWAYS use SI/metric units: g, kg, ml, L, db (pieces). NEVER use cups (csésze), tablespoons (evőkanál), teaspoons (kiskanál), pinch (csipet), or any non-metric units.
            |- Convert everything to measurable metric amounts: "a pinch of salt" → "2 g só", "1 tablespoon oil" → "15 ml olaj".${dimensionHint(knownDimensions)}
            |- The "name" field must contain ONLY the ingredient name (e.g. "rizs", "liszt", "tej"), NOT the amount or unit.
            |- Apply the requested modifications.
            |- Recalculate tags, allergens, and priceRange for the modified version.
            |- Return ONLY the JSON, no other text.""".trimMargin()

        fun cookingSuggestionsPrompt() = """You are a creative home chef. Based on the available ingredients, suggest 5-6 recipe titles.
            |${langInstruction()}
            |Consider the constraints provided (vegetarian, allergens, dish type, extra ingredients allowed).
            |If extra ingredients are 0%, ONLY suggest recipes doable with the listed ingredients.
            |If items are marked [OLD], prioritize recipes that use them.
            |Return a JSON array of strings with just the recipe titles, e.g.:
            |["Creamy Tomato Pasta", "Quick Chicken Stir-fry", ...]
            |Return ONLY the JSON array, no other text.""".trimMargin()
    }
}
