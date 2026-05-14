package com.example.kitchenbot.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kitchenbot.data.*
import kotlinx.coroutines.launch

class ShoppingViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = KitchenRepository(application)
    private val aiService = OpenAiService(application)

    var items by mutableStateOf<List<ShoppingItem>>(emptyList())
        private set
    var storeLayouts by mutableStateOf<List<StoreLayout>>(emptyList())
        private set
    var selectedStore by mutableStateOf<String>("Alphabetical")
        private set
    var sortByCategory by mutableStateOf(true)
        private set

    init {
        items = repo.loadShoppingList()
        storeLayouts = repo.loadStoreLayouts()
    }

    fun addItem(name: String, category: ProductCategory = ProductCategory.OTHER) {
        if (name.isBlank()) return
        val item = ShoppingItem(name = name.trim(), category = category)
        items = items + item
        repo.saveShoppingList(items)
        repo.trackItemAdded(name.trim())

        // Use AI to parse and categorize in one call (handles embedded amounts and categorization)
        val apiKey = ApiKeyStore.getApiKey(getApplication())
        if (!apiKey.isNullOrBlank()) {
            viewModelScope.launch {
                try {
                    val parsed = aiService.parseAndCategorizeItem(name.trim())
                    val resolvedCat = ProductCategory.entries.find {
                        it.name.equals(parsed.category, ignoreCase = true)
                    } ?: ProductCategory.OTHER
                    val needsUpdate = parsed.cleanName != name.trim() || resolvedCat != ProductCategory.OTHER
                    if (needsUpdate) {
                        items = items.map {
                            if (it.id == item.id) it.copy(
                                name = parsed.cleanName.ifBlank { name.trim() },
                                category = if (resolvedCat != ProductCategory.OTHER) resolvedCat else it.category,
                                recipeAmount = parsed.amount.ifBlank { it.recipeAmount },
                                recipeUnit = parsed.unit.ifBlank { it.recipeUnit }
                            ) else it
                        }
                        repo.saveShoppingList(items)
                    }
                } catch (_: Exception) {
                    // Fallback: just categorize
                    if (category == ProductCategory.OTHER) {
                        try {
                            val catStr = aiService.categorizeItem(name.trim())
                            val resolved = ProductCategory.entries.find {
                                it.name.equals(catStr, ignoreCase = true)
                            } ?: ProductCategory.OTHER
                            if (resolved != ProductCategory.OTHER) {
                                items = items.map {
                                    if (it.id == item.id) it.copy(category = resolved) else it
                                }
                                repo.saveShoppingList(items)
                            }
                        } catch (_: Exception) { /* keep OTHER */ }
                    }
                }
            }
        }
    }

    fun removeItem(id: String) {
        items = items.filter { it.id != id }
        repo.saveShoppingList(items)
    }

    fun toggleCheck(id: String) {
        items = items.map {
            if (it.id == id) it.copy(checked = !it.checked) else it
        }
        repo.saveShoppingList(items)
    }

    fun purchaseChecked() {
        val checked = items.filter { it.checked }
        val pending = checked.map { item ->
            PendingPurchase(
                name = item.name,
                category = item.category,
                suggestedAmount = item.recipeAmount.toDoubleOrNull(),
                dimension = item.recipeUnit
            )
        }
        repo.savePendingPurchases(pending)
        items = items.filter { !it.checked }
        repo.saveShoppingList(items)
    }

    fun updateSelectedStore(store: String) {
        selectedStore = store
    }

    fun sortedItems(): List<ShoppingItem> {
        if (!sortByCategory) {
            return items.sortedBy { it.name.lowercase() }
        }
        val layout = storeLayouts.find { it.name == selectedStore }
        val order = layout?.categoryOrder ?: ProductCategory.entries
        return items.sortedWith(
            compareBy<ShoppingItem> { item ->
                val idx = order.indexOf(item.category)
                if (idx < 0) order.size else idx
            }.thenBy { it.name.lowercase() }
        )
    }

    fun toggleSortMode() {
        sortByCategory = !sortByCategory
    }

    fun getRemindMeSuggestions(): List<String> {
        val frequency = repo.loadFrequency()
        val currentNames = items.map { it.name.lowercase() }.toSet()
        val homeItems = repo.loadHomeInventory().map { it.name.lowercase() }.toSet()

        return frequency
            .filter { it.count > 0 }
            .filter { it.name.lowercase() !in currentNames }
            .filter { it.name.lowercase() !in homeItems }
            .sortedByDescending { it.count }
            .take(15)
            .map { it.name }
    }

    fun addItemsFromRecipe(ingredients: List<Ingredient>) {
        for (ing in ingredients) {
            if (ing.name.isBlank()) continue
            val item = ShoppingItem(
                name = ing.name.trim(),
                recipeAmount = ing.amount,
                recipeUnit = ing.unit
            )
            items = items + item
            repo.saveShoppingList(items)
            repo.trackItemAdded(ing.name.trim())

            // Auto-categorize in background
            val apiKey = ApiKeyStore.getApiKey(getApplication())
            if (!apiKey.isNullOrBlank()) {
                val itemId = item.id
                viewModelScope.launch {
                    try {
                        val catStr = aiService.categorizeItem(ing.name.trim())
                        val resolved = ProductCategory.entries.find {
                            it.name.equals(catStr, ignoreCase = true)
                        } ?: ProductCategory.OTHER
                        if (resolved != ProductCategory.OTHER) {
                            items = items.map {
                                if (it.id == itemId) it.copy(category = resolved) else it
                            }
                            repo.saveShoppingList(items)
                        }
                    } catch (_: Exception) { /* keep OTHER */ }
                }
            }
        }
    }
}
