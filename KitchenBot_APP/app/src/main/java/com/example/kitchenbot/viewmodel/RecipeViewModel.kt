package com.example.kitchenbot.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kitchenbot.data.*
import kotlinx.coroutines.launch

class RecipeViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = KitchenRepository(application)
    private val aiService = OpenAiService(application)

    private fun knownDimensions(): Map<String, String> = repo.loadDimensions()

    var recipes by mutableStateOf<List<Recipe>>(emptyList())
        private set
    var currentRecipe by mutableStateOf<Recipe?>(null)
        private set
    var isEditing by mutableStateOf(false)
        private set
    var filterTag by mutableStateOf<String?>(null)
        private set
    var filterAllergen by mutableStateOf<String?>(null)
        private set
    var filterPrice by mutableStateOf<PriceRange?>(null)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var showArchived by mutableStateOf(false)
        private set

    // AI-related state
    var isAiPrompting by mutableStateOf(false)
        private set
    var isAiLoading by mutableStateOf(false)
        private set
    var aiError by mutableStateOf<String?>(null)
        private set
    var previousRecipe by mutableStateOf<Recipe?>(null)
        private set
    var showDiff by mutableStateOf(false)
        private set

    init {
        loadRecipes()
    }

    fun loadRecipes() {
        recipes = repo.listRecipes()
    }

    fun filteredRecipes(): List<Recipe> {
        return recipes.filter { r ->
            val matchArchive = if (showArchived) r.isArchived else !r.isArchived
            val matchTag = filterTag == null || r.tags.any { it.equals(filterTag, ignoreCase = true) }
            val matchAllergen = filterAllergen == null || r.allergens.none { it.equals(filterAllergen, ignoreCase = true) }
            val matchPrice = filterPrice == null || r.priceRange == filterPrice
            val matchSearch = searchQuery.isBlank() ||
                    r.name.contains(searchQuery, ignoreCase = true) ||
                    r.ingredients.any { it.name.contains(searchQuery, ignoreCase = true) }
            matchArchive && matchTag && matchAllergen && matchPrice && matchSearch
        }
    }

    fun openRecipe(recipe: Recipe) {
        currentRecipe = recipe
        isEditing = false
        isAiPrompting = false
        showDiff = false
        previousRecipe = null
        aiError = null
    }

    fun closeRecipe() {
        currentRecipe = null
        isEditing = false
        isAiPrompting = false
        isAiLoading = false
        showDiff = false
        previousRecipe = null
        aiError = null
    }

    fun startEditing() {
        isEditing = true
    }

    fun saveRecipe(recipe: Recipe) {
        repo.saveRecipe(recipe)
        currentRecipe = recipe
        isEditing = false
        isAiPrompting = false
        showDiff = false
        previousRecipe = null
        loadRecipes()
    }

    fun deleteRecipe(id: String) {
        repo.deleteRecipe(id)
        if (currentRecipe?.id == id) currentRecipe = null
        loadRecipes()
    }

    fun createNewRecipe(): Recipe {
        val recipe = Recipe()
        currentRecipe = recipe
        isAiPrompting = true
        isEditing = false
        aiError = null
        return recipe
    }

    // ── AI Recipe Generation ─────────────────────────────────────────────────

    fun generateRecipeFromPrompt(prompt: String) {
        isAiLoading = true
        aiError = null
        viewModelScope.launch {
            try {
                val generated = aiService.generateRecipe(prompt, knownDimensions())
                val recipe = currentRecipe?.copy(
                    name = generated.name,
                    ingredients = generated.ingredients,
                    steps = generated.steps,
                    tags = generated.tags,
                    allergens = generated.allergens,
                    priceRange = generated.priceRange
                ) ?: generated
                currentRecipe = recipe
                isAiPrompting = false
                isEditing = false
            } catch (e: Exception) {
                aiError = e.message ?: "Failed to generate recipe"
            } finally {
                isAiLoading = false
            }
        }
    }

    fun modifyRecipeWithAi(prompt: String) {
        val recipe = currentRecipe ?: return
        previousRecipe = recipe
        isAiLoading = true
        aiError = null
        viewModelScope.launch {
            try {
                val modified = aiService.modifyRecipe(recipe, prompt, knownDimensions())
                currentRecipe = recipe.copy(
                    name = modified.name,
                    ingredients = modified.ingredients,
                    steps = modified.steps,
                    tags = modified.tags,
                    allergens = modified.allergens,
                    priceRange = modified.priceRange
                )
                showDiff = true
                isEditing = false
            } catch (e: Exception) {
                aiError = e.message ?: "Failed to modify recipe"
                previousRecipe = null
            } finally {
                isAiLoading = false
            }
        }
    }

    fun acceptDiff() {
        showDiff = false
        previousRecipe = null
    }

    fun rejectDiff() {
        if (previousRecipe != null) {
            currentRecipe = previousRecipe
        }
        showDiff = false
        previousRecipe = null
    }

    fun clearAiError() {
        aiError = null
    }

    // ── Ingredient & Selection ───────────────────────────────────────────────

    fun toggleIngredientCheck(index: Int) {
        val r = currentRecipe ?: return
        val updated = r.ingredients.toMutableList()
        if (index in updated.indices) {
            updated[index] = updated[index].copy(checked = !updated[index].checked)
            currentRecipe = r.copy(ingredients = updated)
        }
    }

    fun selectAllIngredients(checked: Boolean) {
        val r = currentRecipe ?: return
        currentRecipe = r.copy(ingredients = r.ingredients.map { it.copy(checked = checked) })
    }

    fun updateFilterTag(tag: String?) { filterTag = tag }
    fun updateFilterAllergen(allergen: String?) { filterAllergen = allergen }
    fun updateFilterPrice(price: PriceRange?) { filterPrice = price }
    fun updateSearchQuery(q: String) { searchQuery = q }
    fun updateShowArchived(show: Boolean) { showArchived = show }

    fun getSelectedIngredients(): List<Ingredient> {
        return currentRecipe?.ingredients?.filter { it.checked } ?: emptyList()
    }

    fun allTags(): List<String> {
        return recipes.flatMap { it.tags }.distinct().sorted()
    }

    fun allAllergens(): List<String> {
        return recipes.flatMap { it.allergens }.distinct().sorted()
    }
}
