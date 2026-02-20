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
 * Result of a driving time + distance estimation.
 */
data class DrivingEstimate(
    val timeMinutes: Int,
    val distanceKm: Double,
)

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
        endingPoint: String = "",
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
        // Always append the return leg to the ending point (or starting point if not set)
        val returnTo = endingPoint.ifBlank { startingPoint }
        if (waypoints.size < 2 || waypoints.last() != returnTo) {
            waypoints.add(returnTo)
        }

        val routeDescription = waypoints.zipWithNext().mapIndexed { i, (a, b) ->
            val label = if (i == waypoints.size - 2) " (return journey home)" else ""
            "${i + 1}. $a → $b$label"
        }.joinToString("\n")

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

        val prompt = """You are an expert on European road tolls, vignettes, motorway fees, and ferry crossings. I am driving $vehicleType on the following route and I need to know ALL road fees and required ferry crossings.
$dateRange

My complete route (including the return journey home):
$routeDescription

List ALL of the following that apply:
1. Country vignettes / e-vignettes / motorway stickers (e.g. Austrian "Vignette", Hungarian "e-Matrica / e-vignette", Czech "e-Dálniční známka", Slovenian "e-Vinjeta", Swiss "Vignette", etc.)
2. Specific toll sections (tunnels, bridges, specific motorway sections that charge per-use) — if a per-use toll must be paid in BOTH directions, list it TWICE (once for each direction)
3. Ferry crossings that are necessary or common for driving this route (include the price per vehicle, one-way)
4. Any other mandatory road fees for using motorways/highways

IMPORTANT: The route includes a RETURN JOURNEY home. Make sure you include all tolls, ferries, and vignettes needed for the ENTIRE route including the way back!

RULES:
- Include ALL countries the route passes through (including transit countries!)
- For vignettes, use the shortest duration option (e.g. 10-day)
- Each vignette only needs to be listed ONCE even if multiple route segments cross that country
- Per-use tolls (tunnels, bridges) that are crossed in BOTH directions must be listed TWICE with direction noted
- Give realistic current prices in EUR
- For ${if (travelMode == TravelMode.MICROBUS) "a minibus/microbus - use the appropriate vehicle category which may be more expensive than a car" else "a regular car - use category 1 / passenger car prices"}
- Do NOT include fuel costs, only road/toll/ferry fees
- If a country has no motorway tolls or vignettes, do NOT list it

You MUST respond with ONLY a JSON array. No text before or after. No markdown formatting. Just the raw JSON array.
Each object must have exactly these fields:
- "name": descriptive name including country and direction if applicable (string)
- "price": price in EUR (number)
- "currency": "EUR" (string)

Example: [{"name":"Hungary e-Matrica 10-day vignette","price":22.0,"currency":"EUR"},{"name":"Austria 10-day vignette","price":9.90,"currency":"EUR"},{"name":"Karawanken Tunnel (Austria→Slovenia)","price":7.90,"currency":"EUR"}]

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
     * Estimate total driving distance for a trip using OpenAI GPT.
     * Returns the estimated distance in km, or null on failure.
     */
    suspend fun estimateDrivingDistance(
        startingPoint: String,
        endingPoint: String = "",
        accommodations: List<Accommodation>,
        openAiApiKey: String,
        travelMode: TravelMode = TravelMode.CAR,
        model: String = "gpt-4o-mini"
    ): Double? = withContext(Dispatchers.IO) {
        if (startingPoint.isBlank() || accommodations.isEmpty() || openAiApiKey.isBlank()) {
            return@withContext null
        }

        val sortedAccoms = accommodations.sortedBy { it.startMillis }
            .filter { it.location.isNotBlank() }

        if (sortedAccoms.isEmpty()) return@withContext null

        // Build route
        val waypoints = mutableListOf(startingPoint)
        for (accom in sortedAccoms) {
            if (waypoints.last() != accom.location) {
                waypoints.add(accom.location)
            }
        }
        val returnTo = endingPoint.ifBlank { startingPoint }
        if (waypoints.size < 2 || waypoints.last() != returnTo) {
            waypoints.add(returnTo)
        }

        val routeDescription = waypoints.zipWithNext().mapIndexed { i, (a, b) ->
            val label = if (i == waypoints.size - 2) " (return home)" else ""
            "${i + 1}. $a → $b$label"
        }.joinToString("\n")

        val vehicleType = when (travelMode) {
            TravelMode.CAR -> "a regular passenger car"
            TravelMode.MICROBUS -> "a minibus / microbus"
            TravelMode.PLANE -> "a car"
        }

        val prompt = """You are an expert on European driving routes and distances. I am driving $vehicleType on the following route. I need to know the TOTAL driving distance in kilometers for the ENTIRE route including the return journey home.

My complete route:
$routeDescription

Estimate the total driving distance for the entire route using realistic highway/motorway routes. If any leg requires a ferry crossing, do NOT count the ferry distance as driving distance — only count the actual road driving distance.

You MUST respond with ONLY a single JSON object. No text before or after. No markdown formatting.
The object must have exactly these fields:
- "totalDistanceKm": total driving distance in kilometers (number, rounded to nearest integer)
- "legs": array of objects, each with "from" (string), "to" (string), "distanceKm" (number)

Example: {"totalDistanceKm":1250,"legs":[{"from":"Budapest","to":"Vienna","distanceKm":243},{"from":"Vienna","to":"Budapest","distanceKm":243}]}"""

        try {
            val messagesJson = gson.toJson(listOf(
                mapOf("role" to "user", "content" to prompt)
            ))
            val bodyJson = """{"model":"$model","messages":$messagesJson,"temperature":0.2,"max_tokens":1000}"""

            Log.d(TAG, "Estimating driving distance...")
            Log.d(TAG, "Route: $routeDescription")

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $openAiApiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "API error ${response.code}: $responseBody")
                return@withContext null
            }

            val json = JsonParser.parseString(responseBody).asJsonObject
            val choices = json.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                Log.e(TAG, "No choices in response")
                return@withContext null
            }

            val content = choices[0].asJsonObject
                .getAsJsonObject("message")
                .get("content").asString.trim()

            Log.d(TAG, "Distance GPT response: $content")

            // Parse JSON object
            val cleaned = content
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val resultJson = try {
                JsonParser.parseString(cleaned).asJsonObject
            } catch (_: Exception) {
                // Try extracting { ... }
                val start = content.indexOf('{')
                val end = content.lastIndexOf('}')
                if (start >= 0 && end > start) {
                    try {
                        JsonParser.parseString(content.substring(start, end + 1)).asJsonObject
                    } catch (_: Exception) { null }
                } else null
            }

            resultJson?.get("totalDistanceKm")?.asDouble
        } catch (e: Exception) {
            Log.e(TAG, "Distance estimation exception: ${e.message}", e)
            null
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

    /**
     * Estimate one-way driving time between two locations using the Google Routes API
     * (computeRoutes). Falls back to OpenAI GPT if the Routes API fails.
     *
     * The Google Routes API requires the **Routes API** to be enabled in the
     * Google Cloud Console (the same project that holds the Maps / Places key).
     *
     * @param from           Origin location name (e.g. "Naples, Italy")
     * @param to             Destination location name (e.g. "Monopoli, Italy")
     * @param googleApiKey   Google Maps / Routes API key
     * @param openAiApiKey   OpenAI API key (used only as fallback)
     * @param model          OpenAI model (used only as fallback)
     */
    suspend fun estimateDrivingTime(
        from: String,
        to: String,
        googleApiKey: String = "",
        openAiApiKey: String = "",
        model: String = "gpt-4o-mini"
    ): DrivingEstimate? = withContext(Dispatchers.IO) {
        if (from.isBlank() || to.isBlank()) {
            return@withContext null
        }

        // ── Try Google Routes API first ──────────────────────────
        if (googleApiKey.isNotBlank()) {
            try {
                val result = computeRouteGoogle(from, to, googleApiKey)
                if (result != null) return@withContext result
            } catch (e: Exception) {
                Log.w(TAG, "Google Routes API failed, falling back to GPT: ${e.message}")
            }
        }

        // ── Fallback: OpenAI GPT ─────────────────────────────────
        if (openAiApiKey.isBlank()) return@withContext null
        estimateDrivingTimeGpt(from, to, openAiApiKey, model)
    }

    /**
     * Use Google Routes API (computeRoutes) to get real driving time & distance.
     * Requires the "Routes API" enabled in the GCP project.
     */
    private suspend fun computeRouteGoogle(
        from: String,
        to: String,
        apiKey: String,
    ): DrivingEstimate? {
        val routesUrl = "https://routes.googleapis.com/directions/v2:computeRoutes"

        val requestBody = """
        {
            "origin": { "address": ${gson.toJson(from)} },
            "destination": { "address": ${gson.toJson(to)} },
            "travelMode": "DRIVE",
            "routingPreference": "TRAFFIC_UNAWARE",
            "units": "METRIC"
        }
        """.trimIndent()

        val request = Request.Builder()
            .url(routesUrl)
            .header("Content-Type", "application/json")
            .header("X-Goog-Api-Key", apiKey)
            .header("X-Goog-FieldMask", "routes.duration,routes.distanceMeters")
            .post(requestBody.toRequestBody(JSON_MEDIA))
            .build()

        Log.d(TAG, "Google Routes API: $from → $to")

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            Log.w(TAG, "Google Routes API error ${response.code}: $body")
            return null
        }

        val json = JsonParser.parseString(body).asJsonObject
        val routes = json.getAsJsonArray("routes")
        if (routes == null || routes.size() == 0) {
            Log.w(TAG, "Google Routes API: no routes found")
            return null
        }

        val route = routes[0].asJsonObject
        // duration is a string like "15420s"
        val durationStr = route.get("duration")?.asString ?: return null
        val durationSeconds = durationStr.replace("s", "").toLongOrNull() ?: return null
        val distanceMeters = route.get("distanceMeters")?.asInt ?: 0

        val timeMinutes = (durationSeconds / 60).toInt()
        val distanceKm = distanceMeters / 1000.0

        Log.d(TAG, "Google Routes result: ${timeMinutes}min, ${distanceKm}km")
        return DrivingEstimate(timeMinutes, distanceKm)
    }

    /**
     * Fallback: Estimate one-way driving time between two locations using OpenAI GPT.
     */
    private suspend fun estimateDrivingTimeGpt(
        from: String,
        to: String,
        openAiApiKey: String,
        model: String = "gpt-4o-mini"
    ): DrivingEstimate? {
        val prompt = """You are an expert on driving routes and travel times. Estimate the driving time by car from "$from" to "$to" using realistic highway/motorway routes.

You MUST respond with ONLY a single JSON object. No text before or after. No markdown.
The object must have exactly these fields:
- "drivingTimeMinutes": estimated one-way driving time in minutes (integer)
- "distanceKm": estimated driving distance in km (integer)

Example: {"drivingTimeMinutes":120,"distanceKm":150}"""

        try {
            val messagesJson = gson.toJson(listOf(
                mapOf("role" to "user", "content" to prompt)
            ))
            val bodyJson = """{"model":"$model","messages":$messagesJson,"temperature":0.2,"max_tokens":200}"""

            Log.d(TAG, "GPT fallback: estimating driving time from $from to $to...")

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Authorization", "Bearer $openAiApiKey")
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "GPT API error ${response.code}: $responseBody")
                return null
            }

            val json = JsonParser.parseString(responseBody).asJsonObject
            val choices = json.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) return null

            val content = choices[0].asJsonObject
                .getAsJsonObject("message")
                .get("content").asString.trim()

            Log.d(TAG, "Driving time GPT response: $content")

            val cleaned = content
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val resultJson = try {
                JsonParser.parseString(cleaned).asJsonObject
            } catch (_: Exception) {
                val start = content.indexOf('{')
                val end = content.lastIndexOf('}')
                if (start >= 0 && end > start) {
                    try {
                        JsonParser.parseString(content.substring(start, end + 1)).asJsonObject
                    } catch (_: Exception) { null }
                } else null
            }

            return resultJson?.let {
                val time = it.get("drivingTimeMinutes")?.asInt
                val dist = it.get("distanceKm")?.asDouble
                if (time != null) DrivingEstimate(time, dist ?: 0.0) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "GPT driving time estimation exception: ${e.message}", e)
            return null
        }
    }
}
