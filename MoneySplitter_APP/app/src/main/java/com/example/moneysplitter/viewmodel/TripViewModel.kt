package com.example.moneysplitter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneysplitter.data.Expense
import com.example.moneysplitter.data.Settlement
import com.example.moneysplitter.data.TripData
import com.example.moneysplitter.data.TripRepository
import com.example.moneysplitter.logic.Calculator
import com.example.moneysplitter.logic.RateFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TripViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TripRepository(application)

    private val _trip = MutableStateFlow(TripData())
    val trip: StateFlow<TripData> = _trip.asStateFlow()

    private val _balances = MutableStateFlow<Map<String, Double>>(emptyMap())
    val balances: StateFlow<Map<String, Double>> = _balances.asStateFlow()

    private val _settlements = MutableStateFlow<List<Settlement>>(emptyList())
    val settlements: StateFlow<List<Settlement>> = _settlements.asStateFlow()

    private val _isFetchingRates = MutableStateFlow(false)
    val isFetchingRates: StateFlow<Boolean> = _isFetchingRates.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var fileName: String = ""

    private val undoStack = mutableListOf<TripData>()
    private val redoStack = mutableListOf<TripData>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun loadTrip(fileName: String) {
        this.fileName = fileName
        val loaded = repository.loadTrip(fileName)
        if (loaded != null) {
            _trip.value = loaded
            recalculate()
        }
    }

    private fun save() {
        if (fileName.isNotEmpty()) {
            repository.saveTrip(_trip.value, fileName)
        }
    }

    private fun pushUndo() {
        undoStack.add(_trip.value)
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(_trip.value)
            _trip.value = undoStack.removeLast()
            recalculate()
            save()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(_trip.value)
            _trip.value = redoStack.removeLast()
            recalculate()
            save()
        }
    }

    private fun update(transform: (TripData) -> TripData) {
        pushUndo()
        _trip.value = transform(_trip.value)
        recalculate()
        save()
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    // --- People management ---

    fun addPerson(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || _trip.value.people.contains(trimmed)) return
        update { it.copy(people = it.people + trimmed) }
    }

    fun removePerson(name: String) {
        update { trip ->
            trip.copy(
                people = trip.people - name,
                expenses = trip.expenses.map { expense ->
                    expense.copy(
                        splitAmong = expense.splitAmong - name,
                        paidBy = if (expense.paidBy == name) "" else expense.paidBy
                    )
                }
            )
        }
    }

    // --- Expense management ---

    fun addExpense(expense: Expense) {
        update { it.copy(expenses = it.expenses + expense) }
    }

    fun updateExpense(expense: Expense) {
        update { trip ->
            trip.copy(expenses = trip.expenses.map {
                if (it.id == expense.id) expense else it
            })
        }
    }

    fun removeExpense(expenseId: String) {
        update { trip ->
            trip.copy(expenses = trip.expenses.filter { it.id != expenseId })
        }
    }

    // --- Currency management ---

    fun addCurrency(code: String, rate: Double) {
        val trimmed = code.trim().uppercase()
        if (trimmed.isBlank() || _trip.value.currencies.contains(trimmed)) return
        update { trip ->
            trip.copy(
                currencies = trip.currencies + trimmed,
                conversionRates = if (trimmed != trip.baseCurrency) {
                    trip.conversionRates + (trimmed to rate)
                } else trip.conversionRates
            )
        }
    }

    fun removeCurrency(code: String) {
        if (code == _trip.value.baseCurrency) return
        update { trip ->
            trip.copy(
                currencies = trip.currencies - code,
                conversionRates = trip.conversionRates - code,
                resultCurrency = if (trip.resultCurrency == code) trip.baseCurrency else trip.resultCurrency,
                expenses = trip.expenses.map { expense ->
                    if (expense.currency == code) expense.copy(currency = trip.baseCurrency) else expense
                }
            )
        }
    }

    fun updateConversionRate(currency: String, rate: Double) {
        update { trip ->
            trip.copy(conversionRates = trip.conversionRates + (currency to rate))
        }
    }

    fun changeBaseCurrency(newBase: String) {
        update { trip ->
            if (newBase == trip.baseCurrency) return@update trip

            val oldRates = trip.conversionRates
            val newBaseOldRate = oldRates[newBase] ?: 1.0

            val newRates = mutableMapOf<String, Double>()
            for ((currency, oldRate) in oldRates) {
                if (currency == newBase) continue
                newRates[currency] = oldRate / newBaseOldRate
            }
            // Add old base as a new foreign currency
            newRates[trip.baseCurrency] = 1.0 / newBaseOldRate

            trip.copy(
                baseCurrency = newBase,
                conversionRates = newRates
            )
        }
    }

    fun setResultCurrency(currency: String) {
        update { it.copy(resultCurrency = currency) }
    }

    fun fetchLiveRates() {
        viewModelScope.launch {
            _isFetchingRates.value = true
            val rates = RateFetcher.fetchRates(
                _trip.value.baseCurrency,
                _trip.value.currencies
            )
            if (rates != null) {
                update { trip ->
                    trip.copy(conversionRates = trip.conversionRates + rates)
                }
                _snackbarMessage.value = "Exchange rates updated"
            } else {
                _snackbarMessage.value = "Failed to fetch rates. Check your connection."
            }
            _isFetchingRates.value = false
        }
    }

    fun renameTripName(newName: String) {
        if (newName.isBlank()) return
        update { it.copy(name = newName.trim()) }
    }

    private fun recalculate() {
        _balances.value = Calculator.calculateBalances(_trip.value)
        _settlements.value = Calculator.calculateSettlements(_balances.value)
    }
}
