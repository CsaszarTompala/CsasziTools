package com.example.moneysplitter.data

import java.util.UUID

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val amount: Double = 0.0,
    val currency: String = "HUF",
    val paidBy: String = "",
    val splitAmong: List<String> = emptyList(),
    val description: String = "",
    val name: String? = null,
    val notes: String? = null,
    val date: String? = null,
    val settled: Boolean = false
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: description
}

data class TripData(
    val name: String = "New Trip",
    val people: List<String> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val currencies: List<String> = listOf("HUF", "EUR", "USD"),
    val baseCurrency: String = "HUF",
    val conversionRates: Map<String, Double> = mapOf("EUR" to 410.0, "USD" to 380.0),
    val resultCurrency: String = "HUF",
    val createdAt: Long = System.currentTimeMillis(),
    val startDate: String? = null,
    val endDate: String? = null
)

data class Settlement(
    val from: String,
    val to: String,
    val amount: Double
)

data class TripSummary(
    val fileName: String,
    val name: String,
    val peopleCount: Int,
    val expenseCount: Int,
    val createdAt: Long
)
