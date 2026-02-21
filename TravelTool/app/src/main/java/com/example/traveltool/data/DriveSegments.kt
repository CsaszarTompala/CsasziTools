package com.example.traveltool.data

private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

data class TripDriveSegment(
    val dayMillis: Long,
    val dayNumber: Int,
    val fromLabel: String,
    val toLabel: String,
    val fromLocation: String,
    val toLocation: String,
    val distanceKm: Double,
)

/**
 * @param requireDistance when true (default) only segments with a known driving
 *   distance > 0 are included (used by the fuel breakdown).  Set to false for
 *   toll finding so that every origin→destination pair is returned even if the
 *   driving distance has not been estimated yet.
 */
fun buildTripDriveSegments(
    trip: Trip,
    requireDistance: Boolean = true,
): List<TripDriveSegment> {
    val result = mutableListOf<TripDriveSegment>()
    fun distOk(d: Double) = !requireDistance || d > 0

    val days = mutableListOf<Long>()
    var d = trip.startMillis
    while (d <= trip.endMillis) {
        days.add(d)
        d += ONE_DAY_MS
    }

    fun findAccomForDay(dayMillis: Long) = trip.accommodations.find {
        it.startMillis <= dayMillis && dayMillis < it.endMillis
    } ?: trip.accommodations.find {
        it.startMillis <= dayMillis && dayMillis <= it.endMillis
    }

    for ((index, dayMillis) in days.withIndex()) {
        val dayNumber = index + 1
        val isFinalDay = dayMillis >= trip.endMillis
        val todayAccom = findAccomForDay(dayMillis)
        val prevDayAccom = if (index > 0) findAccomForDay(days[index - 1]) else null

        val isMovingDay = when {
            index == 0 -> true
            isFinalDay -> true
            todayAccom == null -> false
            prevDayAccom == null -> true
            prevDayAccom.location != todayAccom.location -> true
            else -> false
        }

        val dayActivities = trip.activities
            .filter { it.dayMillis == dayMillis && !it.isDelay }
            .sortedBy { it.orderIndex }

        if (isMovingDay) {
            val originLabel = when {
                dayMillis == trip.startMillis -> trip.startingPoint.ifBlank { "Home" }
                prevDayAccom != null -> prevDayAccom.name.ifBlank { prevDayAccom.location }
                todayAccom != null -> todayAccom.name.ifBlank { todayAccom.location }
                else -> trip.startingPoint.ifBlank { "Home" }
            }
            val destLabel = when {
                isFinalDay -> trip.endingPoint.ifBlank { trip.startingPoint.ifBlank { "Home" } }
                todayAccom != null -> todayAccom.name.ifBlank { todayAccom.location }
                else -> "Accommodation"
            }

            val originLocation = when {
                dayMillis == trip.startMillis -> trip.startingPoint
                prevDayAccom != null -> prevDayAccom.location
                todayAccom != null -> todayAccom.location
                else -> trip.startingPoint
            }
            val destLocation = when {
                isFinalDay -> trip.endingPoint.ifBlank { trip.startingPoint }
                todayAccom != null -> todayAccom.location
                else -> ""
            }

            val beforeActivities = dayActivities.filter {
                it.travelDayPosition == TravelDayPosition.BEFORE_ARRIVAL.name
            }.sortedBy { it.orderIndex }
            val afterActivities = dayActivities.filter {
                it.travelDayPosition != TravelDayPosition.BEFORE_ARRIVAL.name
            }.sortedBy { it.orderIndex }

            if (beforeActivities.isEmpty()) {
                val dayPlan = trip.dayPlans.find { it.dayMillis == dayMillis }
                val dist = dayPlan?.movingDayDrivingDistanceKm ?: 0.0
                if (distOk(dist) && originLocation.isNotBlank() && destLocation.isNotBlank()) {
                    result.add(
                        TripDriveSegment(
                            dayMillis = dayMillis,
                            dayNumber = dayNumber,
                            fromLabel = originLabel,
                            toLabel = destLabel,
                            fromLocation = originLocation,
                            toLocation = destLocation,
                            distanceKm = dist,
                        )
                    )
                }
            } else {
                beforeActivities.forEachIndexed { i, act ->
                    val dist = act.drivingDistanceToKm ?: 0.0
                    val fromLabel = if (i == 0) originLabel else beforeActivities[i - 1].name
                    val fromLoc = if (i == 0) originLocation else beforeActivities[i - 1].location
                    if (distOk(dist) && fromLoc.isNotBlank() && act.location.isNotBlank()) {
                        result.add(
                            TripDriveSegment(
                                dayMillis = dayMillis,
                                dayNumber = dayNumber,
                                fromLabel = fromLabel,
                                toLabel = act.name,
                                fromLocation = fromLoc,
                                toLocation = act.location,
                                distanceKm = dist,
                            )
                        )
                    }
                }

                val lastBefore = beforeActivities.last()
                val returnDist = lastBefore.returnDrivingDistanceKm ?: 0.0
                if (distOk(returnDist) && lastBefore.location.isNotBlank() && destLocation.isNotBlank()) {
                    result.add(
                        TripDriveSegment(
                            dayMillis = dayMillis,
                            dayNumber = dayNumber,
                            fromLabel = lastBefore.name,
                            toLabel = destLabel,
                            fromLocation = lastBefore.location,
                            toLocation = destLocation,
                            distanceKm = returnDist,
                        )
                    )
                }
            }

            afterActivities.forEachIndexed { i, act ->
                val dist = act.drivingDistanceToKm ?: 0.0
                val fromLabel = if (i == 0) destLabel else afterActivities[i - 1].name
                val fromLoc = if (i == 0) destLocation else afterActivities[i - 1].location
                if (distOk(dist) && fromLoc.isNotBlank() && act.location.isNotBlank()) {
                    result.add(
                        TripDriveSegment(
                            dayMillis = dayMillis,
                            dayNumber = dayNumber,
                            fromLabel = fromLabel,
                            toLabel = act.name,
                            fromLocation = fromLoc,
                            toLocation = act.location,
                            distanceKm = dist,
                        )
                    )
                }

                val retDist = act.returnDrivingDistanceKm ?: 0.0
                if (distOk(retDist) && i == afterActivities.lastIndex && act.location.isNotBlank() && destLocation.isNotBlank()) {
                    result.add(
                        TripDriveSegment(
                            dayMillis = dayMillis,
                            dayNumber = dayNumber,
                            fromLabel = act.name,
                            toLabel = destLabel,
                            fromLocation = act.location,
                            toLocation = destLocation,
                            distanceKm = retDist,
                        )
                    )
                }
            }
        } else {
            val accomLabel = todayAccom?.name?.ifBlank { todayAccom.location } ?: "Accommodation"
            val accomLocation = todayAccom?.location ?: ""

            dayActivities.forEachIndexed { i, act ->
                val dist = act.drivingDistanceToKm ?: 0.0
                val fromLabel = if (i == 0) accomLabel else dayActivities[i - 1].name
                val fromLoc = if (i == 0) accomLocation else dayActivities[i - 1].location
                if (distOk(dist) && fromLoc.isNotBlank() && act.location.isNotBlank()) {
                    result.add(
                        TripDriveSegment(
                            dayMillis = dayMillis,
                            dayNumber = dayNumber,
                            fromLabel = fromLabel,
                            toLabel = act.name,
                            fromLocation = fromLoc,
                            toLocation = act.location,
                            distanceKm = dist,
                        )
                    )
                }

                val retDist = act.returnDrivingDistanceKm ?: 0.0
                if (distOk(retDist) && i == dayActivities.lastIndex && act.location.isNotBlank() && accomLocation.isNotBlank()) {
                    result.add(
                        TripDriveSegment(
                            dayMillis = dayMillis,
                            dayNumber = dayNumber,
                            fromLabel = act.name,
                            toLabel = accomLabel,
                            fromLocation = act.location,
                            toLocation = accomLocation,
                            distanceKm = retDist,
                        )
                    )
                }
            }
        }
    }

    return result
}
