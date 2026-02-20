package com.example.traveltool.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.traveltool.data.Accommodation
import com.example.traveltool.data.Trip
import com.example.traveltool.data.TripViewModel
import com.example.traveltool.ui.screens.*
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun TravelToolNavHost(navController: NavHostController) {
    val tripViewModel: TripViewModel = viewModel()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        // ── Home ────────────────────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(
                trips = tripViewModel.trips,
                onAddTrip = { navController.navigate(Screen.TripName.route) },
                onTripClick = { tripId ->
                    navController.navigate("${Screen.TripDetail.route}/$tripId")
                },
                onDeleteTrip = { tripId -> tripViewModel.deleteTrip(tripId) },
                onSettings = { navController.navigate(Screen.ApiKey.route) }
            )
        }

        // ── Add Trip: Name ──────────────────────────────────────
        composable(Screen.TripName.route) {
            TripNameScreen(
                onNext = { name ->
                    navController.navigate("${Screen.TripDates.route}/$name")
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Add Trip: Dates ─────────────────────────────────────
        composable("${Screen.TripDates.route}/{tripName}") { backStackEntry ->
            val tripName = backStackEntry.arguments?.getString("tripName") ?: ""
            TripDatesScreen(
                tripName = tripName,
                onNext = { startMillis, endMillis ->
                    navController.navigate(
                        "${Screen.TripLocation.route}/$tripName/$startMillis/$endMillis"
                    )
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Add Trip: Location ──────────────────────────────────
        composable(
            "${Screen.TripLocation.route}/{tripName}/{startMillis}/{endMillis}"
        ) { backStackEntry ->
            val tripName    = backStackEntry.arguments?.getString("tripName") ?: ""
            val startMillis = backStackEntry.arguments?.getString("startMillis")?.toLongOrNull() ?: 0L
            val endMillis   = backStackEntry.arguments?.getString("endMillis")?.toLongOrNull() ?: 0L
            TripLocationScreen(
                tripName = tripName,
                startMillis = startMillis,
                endMillis = endMillis,
                onSave = { location ->
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    val trip = Trip(
                        name = tripName,
                        startMillis = startMillis,
                        endMillis = endMillis,
                        location = location,
                        accommodations = listOf(
                            Accommodation(
                                name = location,
                                startMillis = startMillis,
                                endMillis = endMillis,
                                pricePerNight = null,
                                location = location
                            )
                        )
                    )
                    tripViewModel.addTrip(trip)

                    // Try to auto-set starting point in background
                    if (hasPerm) {
                        autoDetectStartingPoint(context, trip.id, tripViewModel)
                    }

                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Trip Detail ─────────────────────────────────────────
        composable("${Screen.TripDetail.route}/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            TripDetailScreen(
                tripId = tripId,
                tripViewModel = tripViewModel,
                onBack = { navController.popBackStack() },
                onAccommodation = { id ->
                    navController.navigate("${Screen.AccommodationList.route}/$id")
                },
                onTravelSettings = { id ->
                    navController.navigate("${Screen.TravelSettings.route}/$id")
                },
                onCurrencySettings = { id ->
                    navController.navigate("${Screen.CurrencySettings.route}/$id")
                },
                onSpendings = { id ->
                    navController.navigate("${Screen.Spendings.route}/$id")
                }
            )
        }

        // ── Accommodation List ──────────────────────────────────
        composable("${Screen.AccommodationList.route}/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            AccommodationListScreen(
                tripId = tripId,
                tripViewModel = tripViewModel,
                onAddAccommodation = { id ->
                    navController.navigate("${Screen.AddAccommodation.route}/$id")
                },
                onEditAccommodation = { tId, aId ->
                    navController.navigate("${Screen.EditAccommodation.route}/$tId/$aId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Add Accommodation ───────────────────────────────────
        composable("${Screen.AddAccommodation.route}/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            AddAccommodationScreen(
                tripId = tripId,
                tripViewModel = tripViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Edit Accommodation ──────────────────────────────────
        composable("${Screen.EditAccommodation.route}/{tripId}/{accomId}") { backStackEntry ->
            val tripId  = backStackEntry.arguments?.getString("tripId") ?: ""
            val accomId = backStackEntry.arguments?.getString("accomId") ?: ""
            EditAccommodationScreen(
                tripId = tripId,
                accomId = accomId,
                tripViewModel = tripViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // ── Travel Settings ─────────────────────────────────────
        composable("${Screen.TravelSettings.route}/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            TravelSettingsScreen(
                tripId = tripId,
                tripViewModel = tripViewModel,
                onApiKeySettings = {
                    navController.navigate(Screen.ApiKey.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Currency Settings ───────────────────────────────────
        composable("${Screen.CurrencySettings.route}/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            val trip = tripViewModel.getTripById(tripId)
            CurrencySettingsScreen(
                baseCurrency = trip?.displayCurrency ?: "EUR",
                onBack = { navController.popBackStack() }
            )
        }

        // ── Spendings ──────────────────────────────────────────
        composable("${Screen.Spendings.route}/{tripId}") { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: ""
            SpendingsScreen(
                tripId = tripId,
                tripViewModel = tripViewModel,
                onBack = { navController.popBackStack() }
            )
        }

        // ── API Key Settings ───────────────────────────────────
        composable(Screen.ApiKey.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Auto-detect current location and set it as the trip's starting point.
 */
@Suppress("MissingPermission")
private fun autoDetectStartingPoint(
    context: android.content.Context,
    tripId: String,
    tripViewModel: TripViewModel
) {
    val fusedClient = LocationServices.getFusedLocationProviderClient(context)
    fusedClient.lastLocation.addOnSuccessListener { location ->
        if (location != null) {
            CoroutineScope(Dispatchers.IO).launch {
                val name = try {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        listOfNotNull(addr.locality, addr.adminArea, addr.countryName)
                            .joinToString(", ")
                    } else {
                        "${location.latitude}, ${location.longitude}"
                    }
                } catch (_: Exception) {
                    "${location.latitude}, ${location.longitude}"
                }
                withContext(Dispatchers.Main) {
                    val trip = tripViewModel.getTripById(tripId)
                    if (trip != null && trip.startingPoint.isBlank()) {
                        tripViewModel.updateTrip(trip.copy(startingPoint = name))
                    }
                }
            }
        }
    }
}
