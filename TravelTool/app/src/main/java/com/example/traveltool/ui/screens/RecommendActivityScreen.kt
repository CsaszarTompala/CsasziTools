package com.example.traveltool.ui.screens

import android.location.Geocoder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.traveltool.data.*
import com.example.traveltool.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.*

private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

/** Search area options for travel (moving) days. */
private enum class TravelSearchArea(val label: String, val emoji: String) {
    NEAR_DEPARTURE("Departure", "\uD83C\uDFE0"),
    NEAR_DESTINATION("Destination", "\uD83C\uDFE8"),
    ALONG_THE_WAY("Along the way", "\uD83D\uDEE3\uFE0F"),
}

/** Distance from a named reference point. */
private data class DistanceInfo(
    val label: String,
    val distanceKm: Double,
)

/** Simple lat/lng holder. */
private data class SimpleLatLng(val lat: Double, val lng: Double)

/**
 * Screen that lets the user pick a category, then shows Google Places
 * recommendations near the day's accommodation. On travel (moving) days
 * the user can choose to search near the departure, the destination,
 * or along the way between the two.
 *
 * Each result shows how far it is from the relevant accommodation(s).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecommendActivityScreen(
    tripId: String,
    dayMillis: Long,
    tripViewModel: TripViewModel,
    apiKey: String,
    onSelectPlace: (
        name: String,
        location: String,
        description: String,
        rating: Double?,
        photoUrl: String?,
        category: String,
        eatingType: String,
    ) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val trip = tripViewModel.getTripById(tripId)
    if (trip == null) { onBack(); return }

    val scope = rememberCoroutineScope()

    // ── Day accommodation & travel-day detection ────────────────

    val todayAccom = remember(trip.accommodations, dayMillis) {
        findAccomForDay(trip.accommodations, dayMillis)
    }
    val prevDayAccom = remember(trip.accommodations, dayMillis) {
        if (dayMillis > trip.startMillis)
            findAccomForDay(trip.accommodations, dayMillis - ONE_DAY_MS)
        else null
    }

    val isFinalDay = dayMillis >= trip.endMillis

    val isMovingDay = remember(todayAccom, prevDayAccom, dayMillis, trip.startMillis, isFinalDay) {
        when {
            dayMillis == trip.startMillis -> true
            isFinalDay -> true
            todayAccom == null -> false
            prevDayAccom == null -> true
            prevDayAccom.location != todayAccom.location -> true
            else -> false
        }
    }

    // ── Departure / destination location names ──────────────────

    val fromLocationName = remember(trip, prevDayAccom, todayAccom, dayMillis, isFinalDay) {
        when {
            dayMillis == trip.startMillis -> trip.startingPoint.ifBlank { "home" }
            isFinalDay -> todayAccom?.location ?: trip.location
            else -> prevDayAccom?.location ?: trip.startingPoint.ifBlank { "home" }
        }
    }
    val fromAccomLabel = remember(trip, prevDayAccom, todayAccom, dayMillis, isFinalDay) {
        when {
            dayMillis == trip.startMillis -> trip.startingPoint.ifBlank { "Home" }
            isFinalDay -> todayAccom?.name?.ifBlank { todayAccom.location } ?: "Accommodation"
            else -> prevDayAccom?.name?.ifBlank { prevDayAccom.location } ?: "Starting point"
        }
    }
    val toLocationName = remember(trip, todayAccom, dayMillis, isFinalDay) {
        when {
            isFinalDay -> trip.endingPoint.ifBlank { trip.startingPoint.ifBlank { "home" } }
            else -> todayAccom?.location ?: trip.location.ifBlank { "destination" }
        }
    }
    val toAccomLabel = remember(trip, todayAccom, dayMillis, isFinalDay) {
        when {
            isFinalDay -> trip.endingPoint.ifBlank { trip.startingPoint.ifBlank { "Home" } }
            else -> todayAccom?.name?.ifBlank { todayAccom.location } ?: "Destination"
        }
    }

    val stayAccomLabel = todayAccom?.name?.ifBlank { todayAccom.location } ?: "Accommodation"

    // ── Geocode accommodation locations for distance calculation ─

    var fromCoords by remember { mutableStateOf<SimpleLatLng?>(null) }
    var toCoords   by remember { mutableStateOf<SimpleLatLng?>(null) }
    var stayCoords by remember { mutableStateOf<SimpleLatLng?>(null) }

    LaunchedEffect(fromLocationName, toLocationName, isMovingDay, todayAccom?.location) {
        withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                val geocoder = Geocoder(context, Locale.getDefault())
                if (isMovingDay) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(fromLocationName, 1)?.firstOrNull()?.let {
                        fromCoords = SimpleLatLng(it.latitude, it.longitude)
                    }
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(toLocationName, 1)?.firstOrNull()?.let {
                        toCoords = SimpleLatLng(it.latitude, it.longitude)
                    }
                } else {
                    val loc = todayAccom?.location ?: trip.location
                    if (loc.isNotBlank()) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocationName(loc, 1)?.firstOrNull()?.let {
                            stayCoords = SimpleLatLng(it.latitude, it.longitude)
                        }
                    }
                }
            } catch (_: Exception) { /* distances won't show */ }
        }
    }

    // ── UI state ────────────────────────────────────────────────

    var selectedCategory   by remember { mutableStateOf<ActivityCategory?>(null) }
    var selectedEatingType by remember { mutableStateOf<EatingType?>(null) }
    var results            by remember { mutableStateOf<List<PlacesApiHelper.PlaceResult>>(emptyList()) }
    var isSearching        by remember { mutableStateOf(false) }
    var searchDone         by remember { mutableStateOf(false) }
    var selectedSearchArea by remember { mutableStateOf(TravelSearchArea.NEAR_DESTINATION) }

    // ── Compute search location text ────────────────────────────

    fun getSearchLocation(area: TravelSearchArea? = null): String {
        return if (isMovingDay) {
            when (area ?: selectedSearchArea) {
                TravelSearchArea.NEAR_DEPARTURE   -> fromLocationName
                TravelSearchArea.NEAR_DESTINATION  -> toLocationName
                TravelSearchArea.ALONG_THE_WAY     -> "between $fromLocationName and $toLocationName"
            }
        } else {
            todayAccom?.location?.ifBlank { null }
                ?: trip.location.ifBlank { null }
                ?: trip.startingPoint.ifBlank { "Europe" }
        }
    }

    fun doSearch(cat: ActivityCategory, et: EatingType? = null, area: TravelSearchArea? = null) {
        isSearching = true
        searchDone = false
        results = emptyList()
        scope.launch {
            val query = PlacesApiHelper.buildSearchQuery(cat, et)

            // Pick the best geocoded coordinates for the selected search area
            val coords: SimpleLatLng? = if (isMovingDay) {
                when (area ?: selectedSearchArea) {
                    TravelSearchArea.NEAR_DEPARTURE  -> fromCoords
                    TravelSearchArea.NEAR_DESTINATION -> toCoords
                    TravelSearchArea.ALONG_THE_WAY   -> {
                        // Midpoint between departure and destination
                        if (fromCoords != null && toCoords != null) {
                            SimpleLatLng(
                                (fromCoords!!.lat + toCoords!!.lat) / 2.0,
                                (fromCoords!!.lng + toCoords!!.lng) / 2.0,
                            )
                        } else fromCoords ?: toCoords
                    }
                }
            } else {
                stayCoords
            }

            results = PlacesApiHelper.searchPlaces(
                query = query,
                nearLocation = getSearchLocation(area),
                apiKey = apiKey,
                latitude = coords?.lat,
                longitude = coords?.lng,
            )
            isSearching = false
            searchDone = true
        }
    }

    fun computeDistances(place: PlacesApiHelper.PlaceResult): List<DistanceInfo> {
        val pLat = place.latitude ?: return emptyList()
        val pLng = place.longitude ?: return emptyList()
        val list = mutableListOf<DistanceInfo>()
        if (isMovingDay) {
            fromCoords?.let { list.add(DistanceInfo(fromAccomLabel, haversineKm(it.lat, it.lng, pLat, pLng))) }
            toCoords?.let   { list.add(DistanceInfo(toAccomLabel,   haversineKm(it.lat, it.lng, pLat, pLng))) }
        } else {
            stayCoords?.let { list.add(DistanceInfo(stayAccomLabel, haversineKm(it.lat, it.lng, pLat, pLng))) }
        }
        return list
    }

    // ── Scaffold ────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recommend Activity") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Travel-day banner ───────────────────────
            if (isMovingDay) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.orange.copy(alpha = 0.15f)),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("\uD83D\uDE97", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "Travel Day",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.orange,
                            )
                            Text(
                                text = "$fromLocationName \u2192 $toLocationName",
                                fontSize = 12.sp,
                                color = colors.comment,
                            )
                        }
                    }
                }
            }

            // ── Category Selection ──────────────────────
            Text(
                text = "What are you looking for?",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colors.foreground,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ActivityCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            selectedCategory = if (selectedCategory == cat) null else cat
                            selectedEatingType = null
                            results = emptyList()
                            searchDone = false
                            if (selectedCategory != null && selectedCategory != ActivityCategory.EATING) {
                                doSearch(cat)
                            }
                        },
                        label = { Text("${cat.emoji} ${cat.label}", fontSize = 14.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.primary.copy(alpha = 0.3f),
                            selectedLabelColor = colors.foreground,
                        ),
                    )
                }
            }

            // ── Eating Sub-Selection ────────────────────
            if (selectedCategory == ActivityCategory.EATING) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "What kind of meal?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.comment,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EatingType.entries.forEach { et ->
                        FilterChip(
                            selected = selectedEatingType == et,
                            onClick = {
                                selectedEatingType = if (selectedEatingType == et) null else et
                                if (selectedEatingType != null) {
                                    doSearch(ActivityCategory.EATING, et)
                                } else {
                                    results = emptyList()
                                    searchDone = false
                                }
                            },
                            label = { Text(et.label, fontSize = 14.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.accent.copy(alpha = 0.3f),
                                selectedLabelColor = colors.foreground,
                            ),
                        )
                    }
                }
            }

            // ── Travel-day search area selector ─────────
            if (isMovingDay && selectedCategory != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Search area",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.comment,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TravelSearchArea.entries.forEach { area ->
                        FilterChip(
                            selected = selectedSearchArea == area,
                            onClick = {
                                if (selectedSearchArea != area) {
                                    selectedSearchArea = area
                                    val cat = selectedCategory ?: return@FilterChip
                                    if (cat == ActivityCategory.EATING) {
                                        selectedEatingType?.let { doSearch(cat, it, area) }
                                    } else {
                                        doSearch(cat, area = area)
                                    }
                                }
                            },
                            label = { Text("${area.emoji} ${area.label}", fontSize = 14.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.orange.copy(alpha = 0.3f),
                                selectedLabelColor = colors.foreground,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Search near info ────────────────────────
            if (selectedCategory != null) {
                Text(
                    text = "\uD83D\uDCCD Searching near: ${getSearchLocation()}",
                    fontSize = 12.sp,
                    color = colors.comment,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Loading ─────────────────────────────────
            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = colors.primary,
                            strokeWidth = 3.dp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Searching places\u2026", fontSize = 14.sp, color = colors.comment)
                    }
                }
            }

            // ── No results ──────────────────────────────
            if (searchDone && results.isEmpty() && !isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No places found. Try a different category.",
                        fontSize = 14.sp,
                        color = colors.comment,
                    )
                }
            }

            // ── Results List (sorted: closest first, then highest rated) ──
            if (results.isNotEmpty()) {
                val sortedResults = remember(results) {
                    results.sortedWith(
                        compareBy<PlacesApiHelper.PlaceResult> { place ->
                            val pLat = place.latitude
                            val pLng = place.longitude
                            if (pLat != null && pLng != null) {
                                val dists = mutableListOf<Double>()
                                if (isMovingDay) {
                                    fromCoords?.let { dists.add(haversineKm(it.lat, it.lng, pLat, pLng)) }
                                    toCoords?.let   { dists.add(haversineKm(it.lat, it.lng, pLat, pLng)) }
                                } else {
                                    stayCoords?.let { dists.add(haversineKm(it.lat, it.lng, pLat, pLng)) }
                                }
                                dists.minOrNull() ?: Double.MAX_VALUE
                            } else Double.MAX_VALUE
                        }.thenByDescending { it.rating ?: 0.0 }
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sortedResults) { place ->
                        PlaceResultCard(
                            place = place,
                            distances = computeDistances(place),
                            onClick = {
                                onSelectPlace(
                                    place.name,
                                    place.address,
                                    place.description,
                                    place.rating,
                                    place.photoUrl,
                                    selectedCategory?.name ?: "",
                                    selectedEatingType?.name ?: "",
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─── Result card ────────────────────────────────────────────────────

@Composable
private fun PlaceResultCard(
    place: PlacesApiHelper.PlaceResult,
    distances: List<DistanceInfo> = emptyList(),
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.current),
    ) {
        Column {
            // Photo
            if (place.photoUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(place.photoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = place.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Name and rating
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = place.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.foreground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (place.rating != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "\u2B50 ${String.format(Locale.US, "%.1f", place.rating)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.yellow,
                        )
                    }
                }

                // Address
                if (place.address.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = place.address,
                        fontSize = 12.sp,
                        color = colors.comment,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Distance from accommodation(s)
                if (distances.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    distances.forEach { d ->
                        Text(
                            text = "\uD83D\uDCCD ${formatDistanceKm(d.distanceKm)} from ${d.label}",
                            fontSize = 12.sp,
                            color = colors.primary,
                        )
                    }
                }

                // Description
                if (place.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = place.description,
                        fontSize = 13.sp,
                        color = colors.foreground.copy(alpha = 0.8f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Tap hint
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap to add this activity",
                    fontSize = 11.sp,
                    color = colors.accent,
                )
            }
        }
    }
}

// ─── Helpers ────────────────────────────────────────────────────────

/** Find the accommodation covering the given day. */
private fun findAccomForDay(accommodations: List<Accommodation>, dayMillis: Long): Accommodation? {
    return accommodations.find { it.startMillis <= dayMillis && dayMillis < it.endMillis }
        ?: accommodations.find { it.startMillis <= dayMillis && dayMillis <= it.endMillis }
}

/** Haversine distance in kilometres. */
private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371.0 // Earth radius in km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2).pow(2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** Format distance nicely. */
private fun formatDistanceKm(km: Double): String {
    return if (km < 1) String.format(Locale.US, "%.1f km", km)
    else String.format(Locale.US, "%.0f km", km)
}
