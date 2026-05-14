package com.example.kitchenbot.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kitchenbot.data.*
import kotlinx.coroutines.launch

class HomeInventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = KitchenRepository(application)
    private val aiService = OpenAiService(application)

    var items by mutableStateOf<List<HomeItem>>(emptyList())
        private set
    var pendingPurchases by mutableStateOf<List<PendingPurchase>>(emptyList())
        private set
    var hasPending by mutableStateOf(false)
        private set
    var dimensions by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var sortByCategory by mutableStateOf(true)
        private set

    init {
        reload()
    }

    fun reload() {
        items = repo.loadHomeInventory()
        pendingPurchases = repo.loadPendingPurchases()
        hasPending = pendingPurchases.isNotEmpty()
        dimensions = repo.loadDimensions()
        // Enrich pending purchases with AI-suggested dimensions where missing
        enrichPendingPurchases()
    }

    private fun enrichPendingPurchases() {
        val apiKey = ApiKeyStore.getApiKey(getApplication())
        if (apiKey.isNullOrBlank()) return
        for (p in pendingPurchases) {
            if (p.dimension.isBlank() && getDimensionForItem(p.name) == null) {
                viewModelScope.launch {
                    try {
                        val dim = aiService.suggestDimension(p.name)
                        pendingPurchases = pendingPurchases.map {
                            if (it.id == p.id) it.copy(dimension = dim) else it
                        }
                        repo.savePendingPurchases(pendingPurchases)
                    } catch (_: Exception) { /* keep blank */ }
                }
            }
        }
    }

    fun addItem(name: String, amount: Double, dimension: String, category: ProductCategory) {
        if (name.isBlank()) return
        val existing = items.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (existing >= 0) {
            val old = items[existing]
            val newAmount = convertAndAdd(old.amount, old.dimension, amount, dimension)
            val updated = old.copy(amount = newAmount, dimension = dimension)
            items = items.toMutableList().apply { set(existing, updated) }
        } else {
            items = items + HomeItem(
                name = name.trim(),
                amount = amount,
                dimension = dimension,
                category = category
            )
        }
        // Remember dimension for this item
        dimensions = dimensions + (name.lowercase() to dimension)
        repo.saveDimensions(dimensions)
        repo.saveHomeInventory(items)
    }

    fun removeItem(id: String) {
        items = items.filter { it.id != id }
        repo.saveHomeInventory(items)
    }

    fun toggleOld(id: String) {
        items = items.map {
            if (it.id == id) it.copy(isOld = !it.isOld) else it
        }
        repo.saveHomeInventory(items)
    }

    fun updateAmount(id: String, amount: Double, dimension: String) {
        items = items.map {
            if (it.id == id) {
                dimensions = dimensions + (it.name.lowercase() to dimension)
                it.copy(amount = amount, dimension = dimension)
            } else it
        }
        repo.saveDimensions(dimensions)
        repo.saveHomeInventory(items)
    }

    fun processPendingPurchases(resolvedItems: List<HomeItem>) {
        for (item in resolvedItems) {
            addItem(item.name, item.amount, item.dimension, item.category)
        }
        repo.clearPendingPurchases()
        pendingPurchases = emptyList()
        hasPending = false
    }

    fun getDimensionForItem(name: String): String? {
        return dimensions[name.lowercase()]
    }

    fun guessItemDetails(name: String, onResult: (dimension: String, category: ProductCategory) -> Unit) {
        if (name.isBlank()) return
        val apiKey = ApiKeyStore.getApiKey(getApplication())
        if (apiKey.isNullOrBlank()) return
        viewModelScope.launch {
            try {
                val dimension = aiService.suggestDimension(name)
                val categoryStr = aiService.categorizeItem(name)
                val category = ProductCategory.entries.find {
                    it.name.equals(categoryStr, ignoreCase = true)
                } ?: ProductCategory.OTHER
                onResult(dimension, category)
            } catch (_: Exception) {
                // Silently fail – user can still pick manually
            }
        }
    }

    fun getAmountOffers(name: String, dimension: String): List<Double> {
        val item = items.find { it.name.equals(name, ignoreCase = true) }
        val customOffers = item?.customAmountOffers ?: emptyList()

        val defaults = when {
            dimension in listOf("kg", "g") -> listOf(200.0, 500.0, 1000.0)
            dimension == "L" -> listOf(0.5, 1.0, 2.0)
            dimension == "ml" -> listOf(250.0, 500.0, 1000.0)
            dimension == "rolls" -> listOf(2.0, 6.0, 12.0)
            dimension == "pieces" -> listOf(1.0, 3.0, 6.0)
            else -> listOf(1.0, 2.0, 5.0)
        }

        return (customOffers + defaults).distinct().sorted().take(4)
    }

    fun addCustomAmountOffer(name: String, amount: Double) {
        // Only add round values
        if (!isRoundValue(amount)) return
        items = items.map {
            if (it.name.equals(name, ignoreCase = true)) {
                val offers = (it.customAmountOffers + amount).distinct().sorted()
                it.copy(customAmountOffers = offers)
            } else it
        }
        repo.saveHomeInventory(items)
    }

    fun sortedItems(): List<HomeItem> {
        return if (sortByCategory) {
            items.sortedWith(compareBy<HomeItem> { it.category.ordinal }.thenBy { it.name.lowercase() })
        } else {
            items.sortedBy { it.name.lowercase() }
        }
    }

    fun toggleSortMode() {
        sortByCategory = !sortByCategory
    }

    fun getOldItems(): List<HomeItem> = items.filter { it.isOld }

    private fun isRoundValue(v: Double): Boolean {
        if (v <= 0) return false
        // Accept multiples of 50 for g/ml, multiples of 0.5 for kg/L, whole numbers for pieces/rolls
        return v % 50.0 == 0.0 || v % 100.0 == 0.0 || v % 0.5 == 0.0 || v == v.toLong().toDouble()
    }

    private fun convertAndAdd(oldAmount: Double, oldDim: String, newAmount: Double, newDim: String): Double {
        // Handle g/kg conversions
        if (oldDim == "g" && newDim == "kg") return oldAmount + newAmount * 1000
        if (oldDim == "kg" && newDim == "g") return oldAmount + newAmount / 1000
        if (oldDim == "ml" && newDim == "L") return oldAmount + newAmount * 1000
        if (oldDim == "L" && newDim == "ml") return oldAmount + newAmount / 1000
        return oldAmount + newAmount
    }
}
