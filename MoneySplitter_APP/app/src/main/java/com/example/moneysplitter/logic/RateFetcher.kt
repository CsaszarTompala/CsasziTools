package com.example.moneysplitter.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object RateFetcher {

    suspend fun fetchRates(baseCurrency: String, currencies: List<String>): Map<String, Double>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://open.er-api.com/v6/latest/$baseCurrency")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.requestMethod = "GET"

                if (connection.responseCode != 200) return@withContext null

                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                val json = JSONObject(response)
                if (json.getString("result") != "success") return@withContext null

                val apiRates = json.getJSONObject("rates")
                val result = mutableMapOf<String, Double>()

                for (currency in currencies) {
                    if (currency == baseCurrency) continue
                    if (apiRates.has(currency)) {
                        val apiRate = apiRates.getDouble(currency)
                        if (apiRate > 0) {
                            // API returns: 1 BASE = apiRate FOREIGN
                            // We store:   1 FOREIGN = (1/apiRate) BASE
                            result[currency] = 1.0 / apiRate
                        }
                    }
                }

                result
            } catch (e: Exception) {
                null
            }
        }
    }
}
