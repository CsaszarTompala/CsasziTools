package com.example.traveltool.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Manages the editable list of currencies and exchange rates.
 * Rates are stored EUR-based internally, but displayed relative to the trip's base currency.
 */
object CurrencyManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // Default currencies shown first
    val DEFAULT_CURRENCIES = listOf("HUF", "EUR", "USD")

    private const val PREFS_NAME = "traveltool_currencies"
    private const val KEY_CUSTOM_CURRENCIES = "custom_currencies"
    private const val RATES_FILE = "exchange_rates.json"

    /**
     * Get the full ordered currency list: defaults first, then custom ones.
     */
    fun getCurrencyList(context: Context): List<String> {
        val custom = getCustomCurrencies(context)
        return (DEFAULT_CURRENCIES + custom).distinct()
    }

    /**
     * Get user-added custom currencies.
     */
    private fun getCustomCurrencies(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CUSTOM_CURRENCIES, "[]") ?: "[]"
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Add a custom currency code to the list.
     */
    fun addCustomCurrency(context: Context, code: String) {
        val current = getCustomCurrencies(context).toMutableList()
        val upper = code.uppercase().trim()
        if (upper !in DEFAULT_CURRENCIES && upper !in current) {
            current.add(upper)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_CUSTOM_CURRENCIES, gson.toJson(current)).apply()
        }
    }

    /**
     * Remove a custom currency from the list.
     */
    fun removeCustomCurrency(context: Context, code: String) {
        val current = getCustomCurrencies(context).toMutableList()
        current.remove(code.uppercase().trim())
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CUSTOM_CURRENCIES, gson.toJson(current)).apply()
    }

    /**
     * Validate a currency code by checking if exchange rates exist for it.
     * Returns true if the currency is valid.
     */
    suspend fun validateCurrency(code: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val upper = code.uppercase().trim()
            val url = "https://api.frankfurter.app/latest?from=EUR&to=$upper"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext false
            if (!response.isSuccessful) return@withContext false
            val json = JsonParser.parseString(body).asJsonObject
            val rates = json.getAsJsonObject("rates")
            rates != null && rates.has(upper)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fetch exchange rates from web and cache them. Rates are EUR-based.
     */
    suspend fun fetchAndCacheRates(context: Context): Map<String, Double> = withContext(Dispatchers.IO) {
        try {
            val currencies = getCurrencyList(context).filter { it != "EUR" }.joinToString(",")
            val url = "https://api.frankfurter.app/latest?from=EUR&to=$currencies"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext loadCachedRates(context)
            if (!response.isSuccessful) return@withContext loadCachedRates(context)

            val json = JsonParser.parseString(body).asJsonObject
            val ratesObj = json.getAsJsonObject("rates")
            val rateMap = mutableMapOf("EUR" to 1.0)
            ratesObj?.entrySet()?.forEach { (key, value) -> rateMap[key] = value.asDouble }

            // Cache to file
            val file = File(context.filesDir, RATES_FILE)
            file.writeText(gson.toJson(rateMap))

            rateMap
        } catch (_: Exception) {
            loadCachedRates(context)
        }
    }

    /**
     * Load cached exchange rates from file.
     */
    fun loadCachedRates(context: Context): Map<String, Double> {
        return try {
            val file = File(context.filesDir, RATES_FILE)
            if (!file.exists()) return mapOf("EUR" to 1.0, "HUF" to 400.0, "USD" to 1.08)
            val json = file.readText()
            val type = object : TypeToken<Map<String, Double>>() {}.type
            gson.fromJson(json, type) ?: mapOf("EUR" to 1.0)
        } catch (_: Exception) {
            mapOf("EUR" to 1.0, "HUF" to 400.0, "USD" to 1.08)
        }
    }

    /**
     * Save a manually edited rate. Rates are stored EUR-based internally.
     * @param baseCurrency the trip's display currency (e.g. "HUF")
     * @param targetCurrency the currency being edited (e.g. "EUR")
     * @param rateInBase how many units of baseCurrency equals 1 unit of targetCurrency
     *                   e.g. if base=HUF and target=EUR, rateInBase=400 means 1 EUR = 400 HUF
     */
    fun saveManualRate(context: Context, baseCurrency: String, targetCurrency: String, rateInBase: Double) {
        val rates = loadCachedRates(context).toMutableMap()

        // We need to convert to EUR-based rates
        // If base == EUR: rateInBase is how many EUR per 1 target → target's EUR rate = 1/rateInBase
        //   Wait, let's think again. EUR-based means: rates[X] = how many X per 1 EUR
        //   e.g. rates["HUF"] = 400 means 1 EUR = 400 HUF
        //
        // If baseCurrency == "HUF" and targetCurrency == "EUR":
        //   rateInBase = 400 means "1 EUR = 400 HUF"
        //   That directly means rates["HUF"] = 400, rates["EUR"] = 1.0
        //   But we already know rates["EUR"] = 1.0
        //   Actually rateInBase means: 1 targetCurrency = rateInBase baseCurrency
        //   So: 1 EUR = 400 HUF → rates["HUF"]/rates["EUR"] should = 400
        //   Since rates["EUR"]=1, rates["HUF"]=400. Correct.
        //
        // General: 1 target = rateInBase base (in base currency units)
        //   In EUR terms: (rates[target] / rates[base]) should equal (1 / rateInBase) inverted...
        //   Actually: rateInBase = rates[target] / rates[base] ... no.
        //   Let me think differently:
        //   rates[X] = how many X per 1 EUR
        //   1 target = rateInBase base
        //   So: (1 / rates[target]) EUR = (rateInBase / rates[base]) EUR
        //   → 1/rates[target] = rateInBase/rates[base]
        //   → rates[target] = rates[base] / rateInBase

        val baseEurRate = rates[baseCurrency] ?: 1.0
        val newTargetEurRate = baseEurRate / rateInBase

        rates[targetCurrency] = newTargetEurRate

        val file = File(context.filesDir, RATES_FILE)
        file.writeText(gson.toJson(rates))
    }

    /**
     * Get rates expressed relative to baseCurrency.
     * Returns map of currency → "how many baseCurrency per 1 of that currency"
     * e.g. if base=HUF: {"EUR" → 400, "USD" → 370, "HUF" → 1}
     */
    fun getRatesRelativeTo(baseCurrency: String, rates: Map<String, Double>): Map<String, Double> {
        val baseRate = rates[baseCurrency] ?: 1.0
        return rates.mapValues { (_, eurRate) ->
            // eurRate = how many of that currency per 1 EUR
            // baseRate = how many baseCurrency per 1 EUR
            // We want: how many baseCurrency per 1 of that currency
            // = baseRate / eurRate
            baseRate / eurRate
        }
    }

    /**
     * Convert an amount from one currency to another using cached rates.
     * Rates are EUR-based.
     */
    fun convert(amount: Double, from: String, to: String, rates: Map<String, Double>): Double {
        if (from == to) return amount
        val fromRate = rates[from] ?: return amount
        val toRate = rates[to] ?: return amount
        // Convert: amount in FROM → EUR → TO
        val inEur = amount / fromRate
        return inEur * toRate
    }
}
