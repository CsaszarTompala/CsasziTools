package com.example.moneysplitter.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

class TripRepository(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    private val tripsDir: File
        get() = File(context.filesDir, "trips").also { it.mkdirs() }

    fun saveTrip(trip: TripData, fileName: String) {
        val file = File(tripsDir, "$fileName.json")
        file.writeText(gson.toJson(trip))
    }

    fun loadTrip(fileName: String): TripData? {
        val file = File(tripsDir, "$fileName.json")
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), TripData::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteTrip(fileName: String): Boolean {
        val file = File(tripsDir, "$fileName.json")
        return file.delete()
    }

    fun listTrips(): List<TripSummary> {
        val files = tripsDir.listFiles { f -> f.extension == "json" } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                val trip = gson.fromJson(file.readText(), TripData::class.java)
                TripSummary(
                    fileName = file.nameWithoutExtension,
                    name = trip.name,
                    peopleCount = trip.people.size,
                    expenseCount = trip.expenses.size,
                    createdAt = trip.createdAt
                )
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.createdAt }
    }

    fun generateFileName(tripName: String): String {
        val sanitized = tripName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
        val timestamp = System.currentTimeMillis()
        return "${sanitized}_$timestamp"
    }
}
