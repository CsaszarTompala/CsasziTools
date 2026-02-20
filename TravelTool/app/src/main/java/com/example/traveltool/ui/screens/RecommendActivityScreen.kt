package com.example.traveltool.ui.screens

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
import kotlinx.coroutines.launch
import java.util.*

private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

/**
 * Screen that lets the user pick a category, then shows Google Places
 * recommendations near the trip's location (accommodation or starting point).
 *
 * When the user taps a result, it navigates back to AddActivity with
 * the selected place's data pre-filled.
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
    val trip = tripViewModel.getTripById(tripId)
    if (trip == null) { onBack(); return }

    val scope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf<ActivityCategory?>(null) }
    var selectedEatingType by remember { mutableStateOf<EatingType?>(null) }
    var results by remember { mutableStateOf<List<PlacesApiHelper.PlaceResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchDone by remember { mutableStateOf(false) }

    // Determine the search location — today's accommodation or starting point
    val searchNear = remember(trip, dayMillis) {
        val todayAccom = trip.accommodations.find {
            it.startMillis <= dayMillis && dayMillis < it.endMillis
        } ?: trip.accommodations.find {
            it.startMillis <= dayMillis && dayMillis <= it.endMillis
        }
        todayAccom?.location?.ifBlank { null }
            ?: trip.location.ifBlank { null }
            ?: trip.startingPoint.ifBlank { "Europe" }
    }

    fun doSearch(cat: ActivityCategory, et: EatingType? = null) {
        isSearching = true
        searchDone = false
        results = emptyList()
        scope.launch {
            val query = PlacesApiHelper.buildSearchQuery(cat, et)
            results = PlacesApiHelper.searchPlaces(
                query = query,
                nearLocation = searchNear,
                apiKey = apiKey,
            )
            isSearching = false
            searchDone = true
        }
    }

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
            // ── Category Selection ──────────────────────
            Text(
                text = "What are you looking for?",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = DraculaForeground,
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
                            selectedContainerColor = DraculaPurple.copy(alpha = 0.3f),
                            selectedLabelColor = DraculaForeground,
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
                    color = DraculaComment,
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
                                selectedContainerColor = DraculaCyan.copy(alpha = 0.3f),
                                selectedLabelColor = DraculaForeground,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Search near info ────────────────────────
            if (selectedCategory != null) {
                Text(
                    text = "\uD83D\uDCCD Searching near: $searchNear",
                    fontSize = 12.sp,
                    color = DraculaComment,
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
                            color = DraculaPurple,
                            strokeWidth = 3.dp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Searching places\u2026", fontSize = 14.sp, color = DraculaComment)
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
                        color = DraculaComment,
                    )
                }
            }

            // ── Results List ────────────────────────────
            if (results.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(results) { place ->
                        PlaceResultCard(
                            place = place,
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

@Composable
private fun PlaceResultCard(
    place: PlacesApiHelper.PlaceResult,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DraculaCurrent),
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
                        color = DraculaForeground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (place.rating != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "⭐ ${String.format(Locale.US, "%.1f", place.rating)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = DraculaYellow,
                        )
                    }
                }

                // Address
                if (place.address.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = place.address,
                        fontSize = 12.sp,
                        color = DraculaComment,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Description
                if (place.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = place.description,
                        fontSize = 13.sp,
                        color = DraculaForeground.copy(alpha = 0.8f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Tap hint
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap to add this activity",
                    fontSize = 11.sp,
                    color = DraculaCyan,
                )
            }
        }
    }
}
