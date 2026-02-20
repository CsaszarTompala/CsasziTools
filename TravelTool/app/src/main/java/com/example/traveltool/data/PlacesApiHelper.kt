package com.example.traveltool.data

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Helper for Google Places API (New) Text Search.
 * Uses the same Google Maps API key already configured in the app.
 */
object PlacesApiHelper {

    private const val TAG = "PlacesApiHelper"
    private const val TEXT_SEARCH_URL = "https://places.googleapis.com/v1/places:searchText"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /**
     * A place result from the API.
     */
    data class PlaceResult(
        val name: String,
        val address: String,
        val rating: Double?,
        val description: String,
        val photoUrl: String?,
        val latitude: Double?,
        val longitude: Double?,
    )

    /**
     * Search for nearby places matching a query near [nearLocation].
     *
     * @param query        Search keywords, e.g. "hiking trails"
     * @param nearLocation The location name to search near (geocoded by Places API)
     * @param apiKey       Google Maps / Places API key
     * @param maxResults   Maximum results to return (default 10)
     * @return List of [PlaceResult]
     */
    suspend fun searchPlaces(
        query: String,
        nearLocation: String,
        apiKey: String,
        maxResults: Int = 10,
    ): List<PlaceResult> = withContext(Dispatchers.IO) {
        if (query.isBlank() || apiKey.isBlank()) return@withContext emptyList()

        val textQuery = "$query near $nearLocation"

        val requestBody = """
        {
            "textQuery": ${com.google.gson.Gson().toJson(textQuery)},
            "maxResultCount": $maxResults,
            "languageCode": "en"
        }
        """.trimIndent()

        val fieldMask = listOf(
            "places.displayName",
            "places.formattedAddress",
            "places.rating",
            "places.editorialSummary",
            "places.photos",
            "places.location",
        ).joinToString(",")

        val request = Request.Builder()
            .url(TEXT_SEARCH_URL)
            .header("Content-Type", "application/json")
            .header("X-Goog-Api-Key", apiKey)
            .header("X-Goog-FieldMask", fieldMask)
            .post(requestBody.toRequestBody(JSON_MEDIA))
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body.isNullOrBlank()) {
                Log.w(TAG, "Places API error: ${response.code} – $body")
                return@withContext emptyList()
            }

            val json = JsonParser.parseString(body).asJsonObject
            val placesArray = json.getAsJsonArray("places") ?: return@withContext emptyList()

            placesArray.mapNotNull { element ->
                try {
                    val place = element.asJsonObject

                    val displayName = place.getAsJsonObject("displayName")
                        ?.get("text")?.asString ?: return@mapNotNull null

                    val address = place.get("formattedAddress")?.asString ?: ""

                    val placeRating = place.get("rating")?.asDouble

                    val summary = place.getAsJsonObject("editorialSummary")
                        ?.get("text")?.asString ?: ""

                    // Get first photo reference
                    val photoRef = place.getAsJsonArray("photos")
                        ?.firstOrNull()?.asJsonObject
                        ?.get("name")?.asString

                    val photoUrl = if (photoRef != null) {
                        "https://places.googleapis.com/v1/$photoRef/media?maxHeightPx=400&key=$apiKey"
                    } else null

                    val location = place.getAsJsonObject("location")
                    val lat = location?.get("latitude")?.asDouble
                    val lng = location?.get("longitude")?.asDouble

                    PlaceResult(
                        name = displayName,
                        address = address,
                        rating = placeRating,
                        description = summary,
                        photoUrl = photoUrl,
                        latitude = lat,
                        longitude = lng,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing place: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Places API request failed", e)
            emptyList()
        }
    }

    /**
     * Build search query keywords for a given [ActivityCategory] and optional [EatingType].
     */
    fun buildSearchQuery(category: ActivityCategory, eatingType: EatingType? = null): String {
        return when (category) {
            ActivityCategory.HIKING  -> "hiking trails nature walks"
            ActivityCategory.MUSEUM  -> "museum exhibition gallery"
            ActivityCategory.NICE_SIGHT -> "viewpoint scenic overlook tourist attraction"
            ActivityCategory.BEACH   -> "beach seaside shore"
            ActivityCategory.WELLNESS -> "spa wellness thermal bath"
            ActivityCategory.EATING  -> {
                when (eatingType) {
                    EatingType.BREAKFAST -> "breakfast café morning"
                    EatingType.BRUNCH    -> "brunch café restaurant"
                    EatingType.LUNCH     -> "lunch restaurant"
                    EatingType.DINNER    -> "dinner restaurant fine dining"
                    null                 -> "restaurant café food"
                }
            }
            ActivityCategory.OTHER   -> "tourist attraction things to do"
        }
    }
}
