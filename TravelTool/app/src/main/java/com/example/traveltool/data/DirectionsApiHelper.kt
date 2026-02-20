package com.example.traveltool.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Uses OpenAI GPT to identify tolls, vignettes, and road fees for a trip route.
 */
object DirectionsApiHelper {

    private const val TAG = "TollFinder"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    data class TollResult(
        val name: String = "",
        val price: Double = 0.0,
        val currency: String = "EUR"
    )

    /**
     * Find all tolls for a complete trip using OpenAI GPT.
     */
    suspend fun findTollsForTrip(
        startingPoint: String,
        accommodations: List<Accommodation>,
        openAiApiKey: String,
        travelMode: TravelMode = TravelMode.CAR,
        tripStartMillis: Long = 0L,
        tripEndMillis: Long = 0L,
        model: String = "gpt-4o-mini"
    ): List<TollRoad> = withContext(Dispatchers.IO) {
        if (startingPoint.isBlank() || accommodations.isEmpty() || openAiApiKey.isBlank()) {
            Log.w(TAG, "Missing data: start=${startingPoint.isNotBlank()}, accoms=${accommodations.size}, key=${openAiApiKey.isNotBlank()}")
            return@withContext emptyList()
        }

        val sortedAccoms = accommodations.sortedBy { it.startMillis }
            .filter { it.location.isNotBlank() }

        if (sortedAccoms.isEmpty()) {
            Log.w(TAG, "No accommodations with locations")
            return@withContext emptyList()
        }

        // Build route
        val waypoints = mutableListOf(startingPoint)
        for (accom in sortedAccoms) {
            if (waypoints.last() != accom.location) {
                waypoints.add(accom.location)
            }
        }
        if (waypoints.last() != startingPoint) {
            waypoints.add(startingPoint)
        }

        val routeDescription = waypoints.zipWithNext().joinToString("\n") { (a, b) -> "$a → $b" }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dateRange = if (tripStartMillis > 0 && tripEndMillis > 0) {
            "Travel dates: ${dateFormat.format(Date(tripStartMillis))} to ${dateFormat.format(Date(tripEndMillis))}"
        } else {
            ""
        }

        val vehicleType = when (travelMode) {
            TravelMode.CAR -> "a regular passenger car (category 1 / under 3.5t)"
            TravelMode.MICROBUS -> "a minibus / microbus (category 2, 9+ seats, under 3.5t or up to 7.5t)"
            TravelMode.PLANE -> "a car" // fallback
        }

        val prompt = """You are an expert on European road tolls, vignettes, and motorway fees. I am driving $vehicleType on the following route and I need to know ALL road fees.
$dateRange

My complete route:
$routeDescription

List ALL of the following that apply:
1. Country vignettes / e-vignettes / motorway stickers (e.g. Austrian "Vignette", Hungarian "e-Matrica / e-vignette", Czech "e-Dálniční známka", Slovenian "e-Vinjeta", Swiss "Vignette", etc.)
2. Specific toll sections (tunnels, bridges, specific motorway sections that charge per-use)
3. Any other mandatory road fees for using motorways/highways

RULES:
- Include ALL countries the route passes through (including transit countries!)
- For vignettes, use the shortest duration option (e.g. 10-day)
- Each vignette only needs to be listed ONCE even if multiple route segments cross that country
- Give realistic current prices in EUR
- For ${if (travelMode == TravelMode.MICROBUS) "a minibus/microbus - use the appropriate vehicle category which may be more expensive than a car" else "a regular car - use category 1 / passenger car prices"}
- Do NOT include fuel costs, only road/toll fees
- If a country has no motorway tolls or vignettes, do NOT list it

You MUST respond with ONLY a JSON array. No text before or after. No markdown formatting. Just the raw JSON array.
Each object must have exactly these fields:
- "name": descriptive name including country (string)
- "price": price in EUR (number)
- "currency": "EUR" (string)

Example: [{"name":"Hungary e-Matrica 10-day vignette","price":22.0,"currency":"EUR"},{"name":"Austria 10-day vignette","price":9.90,"currency":"EUR"}]

If there are no tolls at all, respond with: []"""

        try {
            val messagesJson = gson.toJson(listOf(
                mapOf("role" to "user", "content" to prompt)
            ))
            val bodyJson = """{"model":"$model","messages":$messagesJson,"temperature":0.2,"max_tokens":1500}"""

            Log.d(TAG, "Sending request to OpenAI...")
            Log.d(TAG, "Route: $routeDescription")

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $openAiApiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            Log.d(TAG, "Response code: ${response.code}")

            if (!response.isSuccessful) {
                Log.e(TAG, "API error ${response.code}: $responseBody")
                return@withContext emptyList()
            }

            val json = JsonParser.parseString(responseBody).asJsonObject
            val choices = json.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                Log.e(TAG, "No choices in response")
                return@withContext emptyList()
            }

            val content = choices[0].asJsonObject
                .getAsJsonObject("message")
                .get("content").asString.trim()

            Log.d(TAG, "GPT response: $content")

            // Extract JSON array — handle markdown wrapping, extra text, etc.
            val jsonContent = extractJsonArray(content)
            if (jsonContent == null) {
                Log.e(TAG, "Could not extract JSON array from response")
                return@withContext emptyList()
            }

            val type = object : TypeToken<List<TollResult>>() {}.type
            val tollResults: List<TollResult> = gson.fromJson(jsonContent, type) ?: emptyList()

            Log.d(TAG, "Parsed ${tollResults.size} tolls")

            tollResults.map { result ->
                TollRoad(
                    id = UUID.randomUUID().toString(),
                    name = result.name,
                    price = result.price,
                    currency = result.currency.ifBlank { "EUR" },
                    isAutoGenerated = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Extract a JSON array from a GPT response that may contain markdown or extra text.
     */
    private fun extractJsonArray(text: String): String? {
        // Try direct parse first
        try {
            val parsed = JsonParser.parseString(text)
            if (parsed.isJsonArray) return text.trim()
        } catch (_: Exception) {}

        // Remove markdown code fences
        val cleaned = text
            .replace("```json", "")
            .replace("```", "")
            .trim()

        try {
            val parsed = JsonParser.parseString(cleaned)
            if (parsed.isJsonArray) return cleaned
        } catch (_: Exception) {}

        // Find first [ and last ]
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start >= 0 && end > start) {
            val extracted = text.substring(start, end + 1)
            try {
                val parsed = JsonParser.parseString(extracted)
                if (parsed.isJsonArray) return extracted
            } catch (_: Exception) {}
        }

        return null
    }
}
