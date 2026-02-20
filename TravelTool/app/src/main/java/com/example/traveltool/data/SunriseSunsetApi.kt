package com.example.traveltool.data

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Fetches sunrise / sunset times from the free sunrise-sunset.org API.
 * No API key required.
 */
object SunriseSunsetApi {

    private const val TAG = "SunriseSunset"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class SunTimes(
        val sunriseUtc: String,   // ISO 8601 UTC
        val sunsetUtc: String,    // ISO 8601 UTC
        val dayLengthSeconds: Int,
        val sunriseFormatted: String,  // e.g. "06:54"
        val sunsetFormatted: String,   // e.g. "16:08"
    )

    /**
     * Get sunrise and sunset for a given lat/lng on a given date.
     *
     * @param lat  Latitude
     * @param lng  Longitude
     * @param dateMillis  The day (midnight UTC) to query
     * @return SunTimes or null on error
     */
    suspend fun getSunTimes(lat: Double, lng: Double, dateMillis: Long): SunTimes? =
        withContext(Dispatchers.IO) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val dateStr = dateFormat.format(Date(dateMillis))

                val url = "https://api.sunrise-sunset.org/json?lat=$lat&lng=$lng&date=$dateStr&formatted=0"

                Log.d(TAG, "Request: $url")

                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.e(TAG, "API error ${response.code}: $body")
                    return@withContext null
                }

                val json = JsonParser.parseString(body).asJsonObject
                val status = json.get("status")?.asString ?: ""
                if (status != "OK") {
                    Log.e(TAG, "API status: $status")
                    return@withContext null
                }

                val results = json.getAsJsonObject("results")
                val sunriseUtc = results.get("sunrise").asString
                val sunsetUtc = results.get("sunset").asString
                val dayLength = results.get("day_length").asInt

                // Estimate timezone offset from longitude (rough: hours = lng / 15)
                val offsetHours = Math.round(lng / 15.0).toInt()
                val offsetMs = offsetHours * 3600 * 1000L

                // Parse ISO 8601 and format in estimated local time
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone(
                        String.format("GMT%+03d:00", offsetHours)
                    )
                }

                val sunriseDate = isoFormat.parse(sunriseUtc)
                val sunsetDate = isoFormat.parse(sunsetUtc)

                val sunriseFormatted = if (sunriseDate != null) timeFormat.format(sunriseDate) else "N/A"
                val sunsetFormatted = if (sunsetDate != null) timeFormat.format(sunsetDate) else "N/A"

                Log.d(TAG, "Sunrise: $sunriseFormatted, Sunset: $sunsetFormatted (UTC$offsetHours)")

                SunTimes(
                    sunriseUtc = sunriseUtc,
                    sunsetUtc = sunsetUtc,
                    dayLengthSeconds = dayLength,
                    sunriseFormatted = sunriseFormatted,
                    sunsetFormatted = sunsetFormatted,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception: ${e.message}", e)
                null
            }
        }
}
