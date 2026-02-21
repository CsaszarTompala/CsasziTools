package com.example.traveltool.data

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

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
        refreshMovingDayEstimates(tripId)
    }

    /**
     * Update an existing accommodation.
     */
    fun updateAccommodation(tripId: String, updated: Accommodation) {
        val trip = getTripById(tripId) ?: return
        val result = trip.accommodations.map { if (it.id == updated.id) updated else it }
            .sortedBy { it.startMillis }
        updateTrip(trip.copy(accommodations = result))
        refreshMovingDayEstimates(tripId)
    }

    /**
     * Delete an accommodation from a trip.
     */
    fun deleteAccommodation(tripId: String, accomId: String) {
        val trip = getTripById(tripId) ?: return
        val remaining = trip.accommodations.filter { it.id != accomId }
        updateTrip(trip.copy(accommodations = remaining))
        refreshMovingDayEstimates(tripId)
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

    // ── Moving-day driving distance auto-estimation ─────────

    /**
     * Asynchronously (re-)estimate the driving distance for every moving day
     * that doesn't have before-arrival activities.  Called automatically after
     * accommodation add / update / delete so the fuel-cost estimate on the
     * Travel Settings screen is always up-to-date.
     */
    fun refreshMovingDayEstimates(tripId: String) {
        val trip = getTripById(tripId) ?: return
        val ctx = getApplication<Application>()

        viewModelScope.launch {
            val googleKey = try {
                val appInfo = ctx.packageManager.getApplicationInfo(
                    ctx.packageName,
                    android.content.pm.PackageManager.GET_META_DATA,
                )
                appInfo.metaData?.getString("com.google.android.geo.API_KEY") ?: ""
            } catch (_: Exception) { "" }
            val openAiKey = ApiKeyStore.getOpenAiKey(ctx)
            val model = ApiKeyStore.getOpenAiModel(ctx)

            val days = mutableListOf<Long>()
            var d = trip.startMillis
            while (d <= trip.endMillis) { days.add(d); d += ONE_DAY_MS }

            fun findAccom(dayMillis: Long) = trip.accommodations.find {
                it.startMillis <= dayMillis && dayMillis < it.endMillis
            } ?: trip.accommodations.find {
                it.startMillis <= dayMillis && dayMillis <= it.endMillis
            }

            for ((index, dayMillis) in days.withIndex()) {
                val isFinalDay = dayMillis >= trip.endMillis
                val todayAccom = findAccom(dayMillis)
                val prevDayAccom = if (index > 0) findAccom(days[index - 1]) else null

                val isMovingDay = when {
                    index == 0 -> true
                    isFinalDay -> true
                    todayAccom == null -> false
                    prevDayAccom == null -> true
                    prevDayAccom.location != todayAccom.location -> true
                    else -> false
                }
                if (!isMovingDay) continue

                // Skip days with before-arrival activities (they manage their own distances)
                val hasBeforeArrival = trip.activities.any { act ->
                    act.dayMillis == dayMillis && !act.isDelay &&
                        act.travelDayPosition == TravelDayPosition.BEFORE_ARRIVAL.name
                }
                if (hasBeforeArrival) continue

                val origin = when {
                    dayMillis == trip.startMillis -> trip.startingPoint
                    prevDayAccom != null -> prevDayAccom.location
                    todayAccom != null -> todayAccom.location
                    else -> trip.startingPoint
                }
                val destination = when {
                    isFinalDay -> trip.endingPoint.ifBlank { trip.startingPoint }
                    todayAccom != null -> todayAccom.location
                    else -> ""
                }
                if (origin.isBlank() || destination.isBlank()) continue

                try {
                    val estimate = DirectionsApiHelper.estimateDrivingTime(
                        from = origin,
                        to = destination,
                        googleApiKey = googleKey,
                        openAiApiKey = openAiKey,
                        model = model,
                    )
                    if (estimate != null && estimate.distanceKm > 0) {
                        val plan = getDayPlan(tripId, dayMillis)
                        setDayPlan(tripId, plan.copy(movingDayDrivingDistanceKm = estimate.distanceKm))
                    }
                } catch (e: Exception) {
                    Log.w("TripViewModel", "Failed to estimate drive for day $dayMillis: ${e.message}")
                }
            }
        }
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
     * Add an activity at a specific position (after the given orderIndex).
     * Shifts subsequent activities' orderIndex as needed.
     */
    fun addActivityAtPosition(tripId: String, activity: Activity, afterOrderIndex: Int?) {
        val trip = getTripById(tripId) ?: return
        val dayStart = activity.dayMillis - (activity.dayMillis % ONE_DAY_MS)
        val newOrderIndex = if (afterOrderIndex != null) afterOrderIndex + 1 else 0
        // Shift activities on the same day with orderIndex >= newOrderIndex
        val shifted = trip.activities.map { a ->
            val aDayStart = a.dayMillis - (a.dayMillis % ONE_DAY_MS)
            if (aDayStart == dayStart && a.orderIndex >= newOrderIndex) {
                a.copy(orderIndex = a.orderIndex + 1)
            } else a
        }
        val withNew = shifted + activity.copy(orderIndex = newOrderIndex)
        updateTrip(trip.copy(activities = withNew))
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
