# TravelTool

**Android travel-planning app** built with Kotlin & Jetpack Compose.

| Info | Value |
|---|---|
| Version | 0.0.3 |
| Platform | Android (min SDK 29, target 36) |
| Language | Kotlin 2.0.21 |
| UI Framework | Jetpack Compose + Material 3 |
| Build System | Gradle 9.2.1, AGP 9.0.1 |
| Theme | Dracula (default), Dark, Bright |

## Features

- **Trip creation wizard** — name → date range picker (2 steps; location is set later from the trip hub).
- **Trip detail hub** — total cost summary, tap-to-edit name/location/dates, start point, end point, accommodations, spendings.
- **Accommodation management** — manual one-by-one adding, date-conflict detection (boundary dates shareable), per-night pricing, trip-range calendar highlighting.
- **Travel Mode** — car / microbus / plane mode, fuel, tolls, plane tickets (with airport autocomplete search & ticket quantity), car rentals (plane mode), public transport fees, additional fees.
- **Airport autocomplete** — built-in database of ~140 major world airports, searchable by city, name, or IATA code.
- **AI-powered toll finder** — uses OpenAI GPT to find vignettes, per-use tolls, and ferry crossings for the full route (including the return home).
- **Start / End point** — separate start and end points for the trip (both default to current location). The first day's drive goes from the start point to the accommodation; the last day's drive goes from accommodation to the end point.
- **AI fuel cost estimation** — estimates total driving distance via GPT and calculates fuel cost from consumption + price. Activity-related drives (to each activity and the return to accommodation) are included in the total distance.
- **Daily Activities** — standalone per-day trip view (including travel-home day) with sunrise/sunset times (via sunrise-sunset.org API), moving/staying day detection, inline scroll-wheel departure time picker (hour & minute) with explicit "Set" button, and sequentially chained activities with Google Routes API driving times (GPT fallback). The return drive from the last activity back to the accommodation is automatically estimated and shown in the timeline. On travel days (first & last), activities are split into "before arrival" (on the way) and "after arrival" groups so detour stops can be planned along the route.
- **Activity categories** — Hiking, Museum, Nice sight, Beach, Wellness, Eating (Breakfast/Brunch/Lunch/Dinner) — selectable in the recommendation screen.
- **Place recommendations** — Google Places API Text Search suggests nearby places by category with photos, ratings, and descriptions. On staying days results are centred on the day's accommodation; on travel days the user can search near departure, destination, or along the way. Each result shows its distance from the relevant accommodation(s).
- **Edit Activity** — tap any activity to modify name, location, duration, or delete it.
- **Multi-currency support** — live exchange rates from Frankfurter API, manual rate editing, custom currencies.
- **Spendings** — daily and one-time expense tracking.
- **Three themes** — Dracula (default), Dark, and Bright switchable from Settings. All screens use `LocalAppColors` for consistent theming.
- **Custom app icon & logo** — `icon.png` used as launcher icon, `logo_main.png` displayed on the home screen.

## Project structure

```
TravelTool/
├── app/src/main/java/com/example/traveltool/
│   ├── MainActivity.kt              # Entry point
│   ├── data/
│   │   ├── Trip.kt                  # Data models (Trip, PlaneTicket, CarRental, PublicTransportFee, etc.)
│   │   ├── TripRepository.kt        # JSON persistence
│   │   ├── TripViewModel.kt         # ViewModel + CRUD logic
│   │   ├── AirportDatabase.kt       # ~140 airports, search by city/name/IATA
│   │   ├── ApiKeyStore.kt           # Encrypted API key storage
│   │   ├── CurrencyManager.kt       # Currency + exchange rates
│   │   ├── DirectionsApiHelper.kt   # OpenAI toll/distance/driving-time finder
│   │   ├── PlacesApiHelper.kt       # Google Places API Text Search
│   │   ├── SunriseSunsetApi.kt      # Sunrise/sunset API helper
│   │   └── ThemeStore.kt            # SharedPreferences persistence for theme choice
│   ├── navigation/
│   │   ├── Screen.kt                # Route definitions
│   │   └── NavHost.kt               # Navigation graph
│   └── ui/
│       ├── components/
│       │   ├── CompactTimePicker.kt # Inline scroll-wheel hour:minute picker
│       │   └── CurrencyPicker.kt    # Reusable currency dropdown
│       ├── screens/
│       │   ├── HomeScreen.kt        # Trip list & Add Trip (with logo)
│       │   ├── TripNameScreen.kt    # Wizard step 1 – name
│   │   ├── TripDatesScreen.kt   # Wizard step 2 – dates (final step, creates trip)
│       │   ├── TripDetailScreen.kt  # Trip hub
│       │   ├── AccommodationListScreen.kt
│       │   ├── AddAccommodationScreen.kt
│       │   ├── EditAccommodationScreen.kt
│       │   ├── TravelSettingsScreen.kt  # Travel Mode (plane tickets, car rental, public transport)
│       │   ├── DailyActivitiesScreen.kt # Daily Activities (standalone)
│   │   ├── DayDetailScreen.kt   # Day detail + sequential activity timeline
│       │   ├── AddActivityScreen.kt # Add planned activity (category via recommendations)
│       │   ├── EditActivityScreen.kt # Edit existing activity
│       │   ├── RecommendActivityScreen.kt # Google Places recommendations
│       │   ├── SpendingsScreen.kt   # Daily & other expenses
│       │   ├── CurrencySettingsScreen.kt
│       │   └── ApiKeyScreen.kt      # Settings: API key, model, theme choice
│       └── theme/
│           ├── Color.kt             # Dracula/Dark/Bright palettes
│           ├── Theme.kt             # Material 3 theme, AppColors, LocalAppColors
│           └── Type.kt              # Typography
├── app/build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

## APIs used

| API | Purpose | Key required |
|---|---|---|
| OpenAI GPT | Toll/vignette/ferry finder, driving distance estimation | Yes (user-provided) |
| Frankfurter | Live currency exchange rates | No |
| sunrise-sunset.org | Sunrise/sunset times for daily activities | No |
| Google Maps SDK | Map display, location picking, geocoding | Yes (in AndroidManifest.xml) |
| Google Routes API | Driving time & distance estimation (computeRoutes) | Yes (same API key) |
| Google Places API (New) | Place recommendations by category | Yes (same API key) |

## Building

Open in Android Studio or build from the command line:

```bash
./gradlew assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Roadmap

- [ ] Google Maps integration for the daily activities map
- [x] Sights to see per day (via Google Places recommendations)
- [x] Three themes (Dracula, Dark, Bright)
- [x] Plane ticket airport search & car rental
- [ ] Trip summary / overview screen
- [ ] Persistent trip storage (Room database)
- [ ] Packing list per trip
- [ ] Budget tracking improvements
