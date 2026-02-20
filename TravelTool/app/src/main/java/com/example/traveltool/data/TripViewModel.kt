package com.example.traveltool.data

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel

/**
 * Shared ViewModel that holds the list of saved trips with JSON persistence.
 */
class TripViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TripRepository(application)
    private val _trips = mutableStateListOf<Trip>()
    val trips: List<Trip> get() = _trips

    init {
        _trips.addAll(repository.loadTrips())
    }

    private fun persist() = repository.saveTrips(_trips.toList())

    fun addTrip(trip: Trip) {
        _trips.add(trip)
        persist()
    }

    fun deleteTrip(id: String) {
        _trips.removeAll { it.id == id }
        persist()
    }

    fun updateTrip(updated: Trip) {
        val idx = _trips.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            _trips[idx] = updated
            persist()
        }
    }

    fun getTripById(id: String): Trip? = _trips.find { it.id == id }

    companion object {
        const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
    }

    /**
     * Add an accommodation to a trip.
     */
    fun addAccommodation(tripId: String, newAccom: Accommodation) {
        val trip = getTripById(tripId) ?: return
        val result = (trip.accommodations + newAccom).sortedBy { it.startMillis }
        updateTrip(trip.copy(accommodations = result))
    }

    /**
     * Update an existing accommodation.
     */
    fun updateAccommodation(tripId: String, updated: Accommodation) {
        val trip = getTripById(tripId) ?: return
        val result = trip.accommodations.map { if (it.id == updated.id) updated else it }
            .sortedBy { it.startMillis }
        updateTrip(trip.copy(accommodations = result))
    }

    /**
     * Delete an accommodation from a trip.
     */
    fun deleteAccommodation(tripId: String, accomId: String) {
        val trip = getTripById(tripId) ?: return
        val remaining = trip.accommodations.filter { it.id != accomId }
        updateTrip(trip.copy(accommodations = remaining))
    }

    // ── Day Plans ─────────────────────────────────────────────

    /**
     * Get the day plan for a specific day, or create a default one.
     */
    fun getDayPlan(tripId: String, dayMillis: Long): DayPlan {
        val trip = getTripById(tripId) ?: return DayPlan(dayMillis = dayMillis)
        val dayStart = dayMillis - (dayMillis % ONE_DAY_MS)
        return trip.dayPlans.find {
            val planStart = it.dayMillis - (it.dayMillis % ONE_DAY_MS)
            planStart == dayStart
        } ?: DayPlan(dayMillis = dayStart)
    }

    /**
     * Set/update the day plan for a specific day.
     */
    fun setDayPlan(tripId: String, dayPlan: DayPlan) {
        val trip = getTripById(tripId) ?: return
        val dayStart = dayPlan.dayMillis - (dayPlan.dayMillis % ONE_DAY_MS)
        val normalised = dayPlan.copy(dayMillis = dayStart)
        val existing = trip.dayPlans.any {
            val planStart = it.dayMillis - (it.dayMillis % ONE_DAY_MS)
            planStart == dayStart
        }
        val updated = if (existing) {
            trip.dayPlans.map {
                val planStart = it.dayMillis - (it.dayMillis % ONE_DAY_MS)
                if (planStart == dayStart) normalised else it
            }
        } else {
            trip.dayPlans + normalised
        }
        updateTrip(trip.copy(dayPlans = updated))
    }

    // ── Activities ──────────────────────────────────────────────

    /**
     * Add an activity to a trip, auto-assigning the next orderIndex.
     */
    fun addActivity(tripId: String, activity: Activity) {
        val trip = getTripById(tripId) ?: return
        val existing = getActivitiesForDay(tripId, activity.dayMillis)
        val nextIndex = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val withIndex = activity.copy(orderIndex = nextIndex)
        updateTrip(trip.copy(activities = trip.activities + withIndex))
    }

    /**
     * Update an existing activity.
     */
    fun updateActivity(tripId: String, updated: Activity) {
        val trip = getTripById(tripId) ?: return
        val result = trip.activities.map { if (it.id == updated.id) updated else it }
        updateTrip(trip.copy(activities = result))
    }

    /**
     * Delete an activity from a trip.
     */
    fun deleteActivity(tripId: String, activityId: String) {
        val trip = getTripById(tripId) ?: return
        val remaining = trip.activities.filter { it.id != activityId }
        updateTrip(trip.copy(activities = remaining))
    }

    /**
     * Get all activities for a specific day, sorted by orderIndex.
     */
    fun getActivitiesForDay(tripId: String, dayMillis: Long): List<Activity> {
        val trip = getTripById(tripId) ?: return emptyList()
        val dayStart = dayMillis - (dayMillis % ONE_DAY_MS)
        return trip.activities.filter {
            val actDayStart = it.dayMillis - (it.dayMillis % ONE_DAY_MS)
            actDayStart == dayStart
        }.sortedBy { it.orderIndex }
    }
}
