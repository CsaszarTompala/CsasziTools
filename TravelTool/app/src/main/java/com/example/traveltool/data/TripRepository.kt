package com.example.traveltool.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Reads and writes trips to a JSON file in internal storage.
 */
class TripRepository(context: Context) {

    private val gson = Gson()
    private val file = File(context.filesDir, "trips.json")

    fun loadTrips(): List<Trip> {
        if (!file.exists()) return emptyList()
        return try {
            val json = file.readText()
            val type = object : TypeToken<List<Trip>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTrips(trips: List<Trip>) {
        file.writeText(gson.toJson(trips))
    }
}

