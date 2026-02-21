package com.example.traveltool.navigation

/**
 * All navigation destinations in the app.
 */
sealed class Screen(val route: String) {
    data object Home               : Screen("home")
    data object TripName           : Screen("add_trip/name")
    data object TripDates          : Screen("add_trip/dates")
    data object TripDetail         : Screen("trip_detail")
    data object AccommodationList  : Screen("accommodation")
    data object AddAccommodation   : Screen("add_accommodation")
    data object EditAccommodation  : Screen("edit_accommodation")
    data object TravelSettings     : Screen("travel_settings")
    data object CurrencySettings   : Screen("currency_settings")
    data object Spendings          : Screen("spendings")
    data object ApiKey             : Screen("api_key")
    data object DayDetail          : Screen("day_detail")
    data object DailyActivities    : Screen("daily_activities")
    data object AddActivity        : Screen("add_activity")
    data object EditActivity       : Screen("edit_activity")
    data object RecommendActivity  : Screen("recommend_activity")
    data object FuelBreakdown      : Screen("fuel_breakdown")
}
