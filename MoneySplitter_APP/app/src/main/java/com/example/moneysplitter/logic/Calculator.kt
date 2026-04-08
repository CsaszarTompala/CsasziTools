package com.example.moneysplitter.logic

import com.example.moneysplitter.data.Settlement
import com.example.moneysplitter.data.TripData
import kotlin.math.abs

object Calculator {

    fun convertAmount(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        baseCurrency: String,
        rates: Map<String, Double>
    ): Double {
        if (fromCurrency == toCurrency) return amount

        // Step 1: convert to base currency (rates store: 1 FOREIGN = X BASE)
        val inBase = if (fromCurrency == baseCurrency) {
            amount
        } else {
            amount * (rates[fromCurrency] ?: 1.0)
        }

        // Step 2: convert from base to target
        return if (toCurrency == baseCurrency) {
            inBase
        } else {
            inBase / (rates[toCurrency] ?: 1.0)
        }
    }

    fun calculateBalances(trip: TripData): Map<String, Double> {
        val balances = mutableMapOf<String, Double>()
        trip.people.forEach { balances[it] = 0.0 }

        for (expense in trip.expenses) {
            if (expense.amount <= 0 || expense.paidBy.isBlank()) continue

            val convertedAmount = convertAmount(
                expense.amount,
                expense.currency,
                trip.resultCurrency,
                trip.baseCurrency,
                trip.conversionRates
            )

            val beneficiaries = expense.splitAmong.ifEmpty { trip.people }
            if (beneficiaries.isEmpty()) continue
            val share = convertedAmount / beneficiaries.size

            // Credit the payer
            balances[expense.paidBy] = (balances[expense.paidBy] ?: 0.0) + convertedAmount

            // Debit each beneficiary
            for (person in beneficiaries) {
                balances[person] = (balances[person] ?: 0.0) - share
            }
        }

        return balances
    }

    fun calculateSettlements(balances: Map<String, Double>): List<Settlement> {
        data class Entry(val name: String, var amount: Double)

        val debtors = mutableListOf<Entry>()
        val creditors = mutableListOf<Entry>()

        for ((person, balance) in balances) {
            when {
                balance < -0.01 -> debtors.add(Entry(person, abs(balance)))
                balance > 0.01 -> creditors.add(Entry(person, balance))
            }
        }

        debtors.sortByDescending { it.amount }
        creditors.sortByDescending { it.amount }

        val settlements = mutableListOf<Settlement>()
        var i = 0
        var j = 0

        while (i < debtors.size && j < creditors.size) {
            val amount = minOf(debtors[i].amount, creditors[j].amount)
            if (amount > 0.01) {
                settlements.add(Settlement(debtors[i].name, creditors[j].name, amount))
            }
            debtors[i].amount -= amount
            creditors[j].amount -= amount
            if (debtors[i].amount < 0.01) i++
            if (creditors[j].amount < 0.01) j++
        }

        return settlements
    }
}
