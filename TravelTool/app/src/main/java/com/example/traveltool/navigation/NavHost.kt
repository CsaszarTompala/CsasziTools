package com.example.traveltool.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.traveltool.ui.screens.HomeScreen
import com.example.traveltool.ui.screens.TripDatesScreen
import com.example.traveltool.ui.screens.TripLocationScreen
import com.example.traveltool.ui.screens.TripNameScreen

@Composable
fun TravelToolNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onAddTrip = { navController.navigate(Screen.TripName.route) }
            )
        }

        composable(Screen.TripName.route) {
            TripNameScreen(
                onNext = { name ->
                    navController.navigate("${Screen.TripDates.route}/$name")
                },
                onBack = { navController.popBackStack() }
            )
        }

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

        composable(
            "${Screen.TripLocation.route}/{tripName}/{startMillis}/{endMillis}"
        ) { backStackEntry ->
            val tripName   = backStackEntry.arguments?.getString("tripName") ?: ""
            val startMillis = backStackEntry.arguments?.getString("startMillis")?.toLongOrNull() ?: 0L
            val endMillis   = backStackEntry.arguments?.getString("endMillis")?.toLongOrNull() ?: 0L
            TripLocationScreen(
                tripName = tripName,
                startMillis = startMillis,
                endMillis = endMillis,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
