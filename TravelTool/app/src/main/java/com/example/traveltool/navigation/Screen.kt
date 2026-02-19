package com.example.traveltool.navigation

/**
 * All navigation destinations in the app.
 */
sealed class Screen(val route: String) {
    data object Home          : Screen("home")
    data object TripName      : Screen("add_trip/name")
    data object TripDates     : Screen("add_trip/dates")
    data object TripLocation  : Screen("add_trip/location")
}
