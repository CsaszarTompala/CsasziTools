package com.example.traveltool.data

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import java.util.UUID

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
     * Add an accommodation to a trip, splitting any existing accommodation
     * whose date range overlaps.
     */
    fun addAccommodation(tripId: String, newAccom: Accommodation) {
        val trip = getTripById(tripId) ?: return
        val result = mutableListOf<Accommodation>()

        for (existing in trip.accommodations) {
            if (existing.endMillis <= newAccom.startMillis || existing.startMillis >= newAccom.endMillis) {
                result.add(existing)
                continue
            }
            if (existing.startMillis < newAccom.startMillis) {
                result.add(existing.copy(endMillis = newAccom.startMillis - ONE_DAY_MS))
            }
            if (existing.endMillis > newAccom.endMillis) {
                result.add(
                    existing.copy(
                        id = UUID.randomUUID().toString(),
                        startMillis = newAccom.endMillis + ONE_DAY_MS
                    )
                )
            }
        }

        result.add(newAccom)
        result.sortBy { it.startMillis }

        updateTrip(trip.copy(accommodations = result))
    }

    /**
     * Update an existing accommodation. Neighbours are stretched/shrunk to fill
     * any gaps or resolve overlaps caused by date changes.
     */
    fun updateAccommodation(tripId: String, updated: Accommodation) {
        val trip = getTripById(tripId) ?: return
        val others = trip.accommodations.filter { it.id != updated.id }.toMutableList()
        val result = mutableListOf<Accommodation>()

        // Remove any that are fully overlapped by the updated one
        for (existing in others) {
            if (existing.startMillis >= updated.startMillis && existing.endMillis <= updated.endMillis) {
                continue // fully covered → remove
            }
            if (existing.endMillis <= updated.startMillis || existing.startMillis >= updated.endMillis) {
                result.add(existing) // no overlap
                continue
            }
            // Partial overlap — split
            if (existing.startMillis < updated.startMillis) {
                result.add(existing.copy(endMillis = updated.startMillis - ONE_DAY_MS))
            }
            if (existing.endMillis > updated.endMillis) {
                result.add(
                    existing.copy(
                        id = UUID.randomUUID().toString(),
                        startMillis = updated.endMillis + ONE_DAY_MS
                    )
                )
            }
        }

        result.add(updated)
        result.sortBy { it.startMillis }

        // Fill gaps at trip boundaries
        val filled = fillTripBoundaryGaps(trip, result)
        updateTrip(trip.copy(accommodations = filled))
    }

    /**
     * Delete an accommodation from a trip. Neighbours are stretched to fill the gap.
     */
    fun deleteAccommodation(tripId: String, accomId: String) {
        val trip = getTripById(tripId) ?: return
        val remaining = trip.accommodations.filter { it.id != accomId }
            .sortedBy { it.startMillis }
            .toMutableList()

        if (remaining.isNotEmpty()) {
            val filled = fillGapsBetweenAccommodations(trip, remaining)
            updateTrip(trip.copy(accommodations = filled))
        } else {
            updateTrip(trip.copy(accommodations = emptyList()))
        }
    }

    /**
     * If the first accommodation starts after trip start, or the last ends before
     * trip end, create filler accommodations.
     */
    private fun fillTripBoundaryGaps(trip: Trip, accoms: MutableList<Accommodation>): List<Accommodation> {
        if (accoms.isEmpty()) return accoms
        val sorted = accoms.sortedBy { it.startMillis }.toMutableList()

        // Fill gap at start of trip
        if (sorted.first().startMillis > trip.startMillis) {
            sorted.add(
                0,
                Accommodation(
                    name = "",
                    startMillis = trip.startMillis,
                    endMillis = sorted.first().startMillis - ONE_DAY_MS,
                    location = trip.location
                )
            )
        }

        // Fill gap at end of trip
        if (sorted.last().endMillis < trip.endMillis) {
            sorted.add(
                Accommodation(
                    name = "",
                    startMillis = sorted.last().endMillis + ONE_DAY_MS,
                    endMillis = trip.endMillis,
                    location = trip.location
                )
            )
        }

        return sorted
    }

    /**
     * After deletion, stretch neighbours to fill any gaps.
     */
    private fun fillGapsBetweenAccommodations(trip: Trip, accoms: List<Accommodation>): List<Accommodation> {
        if (accoms.isEmpty()) return accoms
        val sorted = accoms.sortedBy { it.startMillis }.toMutableList()

        // Stretch first to trip start
        if (sorted.first().startMillis > trip.startMillis) {
            sorted[0] = sorted[0].copy(startMillis = trip.startMillis)
        }

        // Stretch last to trip end
        if (sorted.last().endMillis < trip.endMillis) {
            sorted[sorted.lastIndex] = sorted.last().copy(endMillis = trip.endMillis)
        }

        // Fill interior gaps by stretching the preceding accommodation
        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]
            if (current.endMillis + ONE_DAY_MS < next.startMillis) {
                sorted[i] = current.copy(endMillis = next.startMillis - ONE_DAY_MS)
            }
        }

        return sorted
    }
}
