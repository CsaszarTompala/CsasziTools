package com.example.kitchenbot.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File

class KitchenRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    // ── Directories ──────────────────────────────────────────────────────────

    private val recipesDir: File
        get() = File(context.filesDir, "recipes").also { it.mkdirs() }

    private val shoppingFile: File
        get() = File(context.filesDir, "shopping_list.json")

    private val homeInventoryFile: File
        get() = File(context.filesDir, "home_inventory.json")

    private val pendingFile: File
        get() = File(context.filesDir, "pending_purchases.json")

    private val frequencyFile: File
        get() = File(context.filesDir, "frequency.json")

    private val dimensionsFile: File
        get() = File(context.filesDir, "dimensions.json")

    private val storeLayoutsFile: File
        get() = File(context.filesDir, "store_layouts.json")

    // ── Recipes ──────────────────────────────────────────────────────────────

    fun saveRecipe(recipe: Recipe) {
        val file = File(recipesDir, "${recipe.id}.json")
        file.writeText(gson.toJson(recipe))
    }

    fun loadRecipe(id: String): Recipe? {
        val file = File(recipesDir, "$id.json")
        if (!file.exists()) return null
        return gson.fromJson(file.readText(), Recipe::class.java)
    }

    fun deleteRecipe(id: String) {
        File(recipesDir, "$id.json").delete()
    }

    fun listRecipes(): List<Recipe> {
        if (!recipesDir.exists()) return emptyList()
        return recipesDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                try { gson.fromJson(f.readText(), Recipe::class.java) }
                catch (_: Exception) { null }
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    // ── Shopping List ────────────────────────────────────────────────────────

    fun saveShoppingList(items: List<ShoppingItem>) {
        shoppingFile.writeText(gson.toJson(items))
    }

    fun loadShoppingList(): List<ShoppingItem> {
        if (!shoppingFile.exists()) return emptyList()
        val type = object : TypeToken<List<ShoppingItem>>() {}.type
        return gson.fromJson(shoppingFile.readText(), type) ?: emptyList()
    }

    // ── Home Inventory ───────────────────────────────────────────────────────

    fun saveHomeInventory(items: List<HomeItem>) {
        homeInventoryFile.writeText(gson.toJson(items))
    }

    fun loadHomeInventory(): List<HomeItem> {
        if (!homeInventoryFile.exists()) return emptyList()
        val type = object : TypeToken<List<HomeItem>>() {}.type
        return gson.fromJson(homeInventoryFile.readText(), type) ?: emptyList()
    }

    // ── Pending Purchases ────────────────────────────────────────────────────

    fun savePendingPurchases(items: List<PendingPurchase>) {
        pendingFile.writeText(gson.toJson(items))
    }

    fun loadPendingPurchases(): List<PendingPurchase> {
        if (!pendingFile.exists()) return emptyList()
        val type = object : TypeToken<List<PendingPurchase>>() {}.type
        return gson.fromJson(pendingFile.readText(), type) ?: emptyList()
    }

    fun clearPendingPurchases() {
        pendingFile.delete()
    }

    // ── Frequency Tracking ───────────────────────────────────────────────────

    fun saveFrequency(entries: List<FrequencyEntry>) {
        frequencyFile.writeText(gson.toJson(entries))
    }

    fun loadFrequency(): List<FrequencyEntry> {
        if (!frequencyFile.exists()) return getDefaultFrequencyEntries()
        val type = object : TypeToken<List<FrequencyEntry>>() {}.type
        return gson.fromJson(frequencyFile.readText(), type) ?: getDefaultFrequencyEntries()
    }

    fun trackItemAdded(name: String) {
        val entries = loadFrequency().toMutableList()
        val idx = entries.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (idx >= 0) {
            entries[idx] = entries[idx].copy(
                count = entries[idx].count + 1,
                lastAdded = System.currentTimeMillis()
            )
        } else {
            entries.add(FrequencyEntry(name, 1, System.currentTimeMillis()))
        }
        saveFrequency(entries)
    }

    private fun getDefaultFrequencyEntries(): List<FrequencyEntry> = listOf(
        FrequencyEntry("Milk", 5, 0L),
        FrequencyEntry("Bread", 5, 0L),
        FrequencyEntry("Eggs", 4, 0L),
        FrequencyEntry("Butter", 3, 0L),
        FrequencyEntry("Toilet Paper", 3, 0L),
        FrequencyEntry("Bananas", 3, 0L),
        FrequencyEntry("Chicken Breast", 2, 0L),
        FrequencyEntry("Rice", 2, 0L),
        FrequencyEntry("Pasta", 2, 0L),
        FrequencyEntry("Onion", 2, 0L),
        FrequencyEntry("Tomatoes", 2, 0L),
        FrequencyEntry("Cheese", 2, 0L),
        FrequencyEntry("Yogurt", 2, 0L),
        FrequencyEntry("Olive Oil", 1, 0L),
        FrequencyEntry("Salt", 1, 0L),
        FrequencyEntry("Pepper", 1, 0L),
        FrequencyEntry("Garlic", 1, 0L),
        FrequencyEntry("Dish Soap", 1, 0L),
        FrequencyEntry("Paper Towels", 1, 0L),
        FrequencyEntry("Trash Bags", 1, 0L)
    )

    // ── Dimension Memory ─────────────────────────────────────────────────────

    fun saveDimensions(map: Map<String, String>) {
        dimensionsFile.writeText(gson.toJson(map))
    }

    fun loadDimensions(): Map<String, String> {
        if (!dimensionsFile.exists()) return emptyMap()
        val type = object : TypeToken<Map<String, String>>() {}.type
        return gson.fromJson(dimensionsFile.readText(), type) ?: emptyMap()
    }

    // ── Store Layouts ────────────────────────────────────────────────────────

    fun saveStoreLayouts(layouts: List<StoreLayout>) {
        storeLayoutsFile.writeText(gson.toJson(layouts))
    }

    fun loadStoreLayouts(): List<StoreLayout> {
        if (!storeLayoutsFile.exists()) return getDefaultStoreLayouts()
        val type = object : TypeToken<List<StoreLayout>>() {}.type
        return gson.fromJson(storeLayoutsFile.readText(), type) ?: getDefaultStoreLayouts()
    }

    private fun getDefaultStoreLayouts(): List<StoreLayout> = listOf(
        StoreLayout("Lidl", listOf(
            ProductCategory.BAKERY, ProductCategory.FRUIT, ProductCategory.VEGETABLE,
            ProductCategory.DAIRY, ProductCategory.MEAT, ProductCategory.FROZEN,
            ProductCategory.DRINKS, ProductCategory.SNACKS, ProductCategory.SPICES,
            ProductCategory.HOUSEHOLD, ProductCategory.OTHER
        )),
        StoreLayout("Aldi", listOf(
            ProductCategory.FRUIT, ProductCategory.VEGETABLE, ProductCategory.BAKERY,
            ProductCategory.DAIRY, ProductCategory.MEAT, ProductCategory.FROZEN,
            ProductCategory.SNACKS, ProductCategory.DRINKS, ProductCategory.SPICES,
            ProductCategory.HOUSEHOLD, ProductCategory.OTHER
        )),
        StoreLayout("Auchan", listOf(
            ProductCategory.FRUIT, ProductCategory.VEGETABLE, ProductCategory.MEAT,
            ProductCategory.DAIRY, ProductCategory.BAKERY, ProductCategory.FROZEN,
            ProductCategory.DRINKS, ProductCategory.SNACKS, ProductCategory.SPICES,
            ProductCategory.HOUSEHOLD, ProductCategory.OTHER
        )),
        StoreLayout("Tesco", listOf(
            ProductCategory.FRUIT, ProductCategory.VEGETABLE, ProductCategory.BAKERY,
            ProductCategory.MEAT, ProductCategory.DAIRY, ProductCategory.FROZEN,
            ProductCategory.DRINKS, ProductCategory.SNACKS, ProductCategory.SPICES,
            ProductCategory.HOUSEHOLD, ProductCategory.OTHER
        )),
        StoreLayout("Spar", listOf(
            ProductCategory.BAKERY, ProductCategory.DAIRY, ProductCategory.MEAT,
            ProductCategory.FRUIT, ProductCategory.VEGETABLE, ProductCategory.FROZEN,
            ProductCategory.DRINKS, ProductCategory.SNACKS, ProductCategory.SPICES,
            ProductCategory.HOUSEHOLD, ProductCategory.OTHER
        )),
        StoreLayout("Coop", listOf(
            ProductCategory.BAKERY, ProductCategory.DAIRY, ProductCategory.FRUIT,
            ProductCategory.VEGETABLE, ProductCategory.MEAT, ProductCategory.FROZEN,
            ProductCategory.DRINKS, ProductCategory.SNACKS, ProductCategory.SPICES,
            ProductCategory.HOUSEHOLD, ProductCategory.OTHER
        )),
        StoreLayout("Alphabetical", ProductCategory.entries.sortedBy { it.label })
    )
}
