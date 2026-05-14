package com.example.kitchenbot.data

import java.util.UUID

// ── Recipe Models ────────────────────────────────────────────────────────────

data class Recipe(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val tags: List<String> = emptyList(),          // vegetarian, pork, beef, etc.
    val allergens: List<String> = emptyList(),      // gluten, dairy, nuts, etc.
    val priceRange: PriceRange = PriceRange.MEDIUM,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isTemporary: Boolean = false
)

data class Ingredient(
    val name: String,
    val amount: String = "",
    val unit: String = "",
    val checked: Boolean = false
)

enum class PriceRange(val label: String, val symbol: String) {
    CHEAP("Cheap", "$"),
    MEDIUM("Medium", "$$"),
    EXPENSIVE("Expensive", "$$$")
}

// ── Shopping List Models ─────────────────────────────────────────────────────

data class ShoppingItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: ProductCategory = ProductCategory.OTHER,
    val checked: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val recipeAmount: String = "",
    val recipeUnit: String = ""
)

enum class ProductCategory(val label: String, val emoji: String) {
    DAIRY("Dairy", "🧀"),
    FRUIT("Fruit", "🍎"),
    VEGETABLE("Vegetable", "🥦"),
    MEAT("Meat", "🥩"),
    BAKERY("Bakery", "🍞"),
    DRINKS("Drinks", "🥤"),
    FROZEN("Frozen", "🧊"),
    SNACKS("Snacks", "🍿"),
    SPICES("Spices", "🧂"),
    HOUSEHOLD("Household", "🧻"),
    OTHER("Other", "📦")
}

data class StoreLayout(
    val name: String,
    val categoryOrder: List<ProductCategory>
)

// ── Home Inventory Models ────────────────────────────────────────────────────

data class HomeItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: Double = 0.0,
    val dimension: String = "",                    // kg, rolls, pieces, L, etc.
    val category: ProductCategory = ProductCategory.OTHER,
    val isOld: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val customAmountOffers: List<Double> = emptyList()  // user-selected round amounts
)

data class PendingPurchase(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val category: ProductCategory = ProductCategory.OTHER,
    val suggestedAmount: Double? = null,
    val dimension: String = "",
    val fromReceipt: Boolean = false,
    val accepted: Boolean = true
)

// ── Frequently-bought tracking ───────────────────────────────────────────────

data class FrequencyEntry(
    val name: String,
    val count: Int = 0,
    val lastAdded: Long = 0L
)

// ── Recipe diff (for AI modifications) ───────────────────────────────────────

data class RecipeDiff(
    val removedLines: List<String> = emptyList(),
    val addedLines: List<String> = emptyList(),
    val originalRecipe: Recipe? = null,
    val modifiedRecipe: Recipe? = null
)
