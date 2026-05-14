package com.example.kitchenbot.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kitchenbot.data.*
import kotlinx.coroutines.launch

class CookViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = KitchenRepository(application)
    private val aiService = OpenAiService(application)

    var extraSlider by mutableFloatStateOf(0f)      // 0.0 to 1.0
        private set
    var isVegetarian by mutableStateOf(false)
        private set
    var excludeAllergens by mutableStateOf<Set<String>>(emptySet())
        private set
    var selectedMeatType by mutableStateOf<String?>(null)
        private set
    var selectedDishType by mutableStateOf<String?>(null)
        private set
    var prioritizeOld by mutableStateOf(true)
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var aiError by mutableStateOf<String?>(null)
        private set

    fun updateExtraSlider(v: Float) { extraSlider = v.coerceIn(0f, 1f) }
    fun updateVegetarian(v: Boolean) { isVegetarian = v }
    fun toggleAllergen(a: String) {
        excludeAllergens = if (a in excludeAllergens) excludeAllergens - a else excludeAllergens + a
    }
    fun updateMeatType(t: String?) { selectedMeatType = t }
    fun updateDishType(t: String?) { selectedDishType = t }
    fun updatePrioritizeOld(v: Boolean) { prioritizeOld = v }

    fun getHomeItems(): List<HomeItem> = repo.loadHomeInventory()

    fun getAvailableMeats(): List<String> {
        val home = repo.loadHomeInventory()
        val meats = home.filter { it.category == ProductCategory.MEAT }.map { it.name }
        return if (extraSlider == 0f) meats
        else meats + listOf("Chicken", "Beef", "Pork", "Fish", "Turkey").filter { m ->
            meats.none { it.equals(m, ignoreCase = true) }
        }
    }

    fun getDishTypes(): List<String> {
        val home = repo.loadHomeInventory()
        val hasPasta = home.any { it.name.contains("pasta", ignoreCase = true) || it.name.contains("spaghetti", ignoreCase = true) }
        val base = mutableListOf("Soup", "Stew", "Salad", "Quick Meal", "Slow Cook", "Sandwich", "Stir-fry")
        if (hasPasta || extraSlider > 0f) base.add(0, "Pasta")
        if (home.any { it.name.contains("rice", ignoreCase = true) } || extraSlider > 0f) base.add("Rice Dish")
        return base
    }

    fun generateSuggestions() {
        val home = getHomeItems()
        if (!ApiKeyStore.hasApiKey(getApplication())) {
            // Fallback if no API key
            val meatStr = if (isVegetarian) "Vegetarian" else (selectedMeatType ?: "Mixed")
            val dishStr = selectedDishType ?: "Any"
            val itemNames = home.take(5).joinToString(", ") { it.name }
            suggestions = listOf(
                "$meatStr $dishStr with $itemNames",
                "Quick $meatStr Bowl",
                "Hearty $dishStr Surprise",
                "Simple ${home.firstOrNull()?.name ?: "Kitchen"} Delight",
                "Leftover Magic $dishStr"
            )
            return
        }

        isLoading = true
        aiError = null
        viewModelScope.launch {
            try {
                suggestions = aiService.generateCookingSuggestions(
                    homeItems = home,
                    extraPercent = extraSlider,
                    isVegetarian = isVegetarian,
                    excludeAllergens = excludeAllergens,
                    meatType = selectedMeatType,
                    dishType = selectedDishType,
                    prioritizeOld = prioritizeOld
                )
            } catch (e: Exception) {
                aiError = e.message ?: "Failed to generate suggestions"
            } finally {
                isLoading = false
            }
        }
    }

    fun generateRecipeFromSuggestion(
        title: String,
        onResult: (Recipe) -> Unit,
        onError: (String) -> Unit
    ) {
        val home = getHomeItems()
        isLoading = true
        viewModelScope.launch {
            try {
                val recipe = aiService.generateRecipeFromSuggestion(
                    title = title,
                    homeItems = home,
                    isVegetarian = isVegetarian,
                    extraPercent = extraSlider,
                    knownDimensions = repo.loadDimensions()
                )
                onResult(recipe)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to generate recipe")
            } finally {
                isLoading = false
            }
        }
    }

    fun clearError() { aiError = null }

    fun updateSuggestions(s: List<String>) { suggestions = s }
}
