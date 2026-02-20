# TravelTool

**Android travel-planning app** built with Kotlin & Jetpack Compose.

| Info | Value |
|---|---|
| Version | 0.0.2 |
| Platform | Android (min SDK 29, target 36) |
| Language | Kotlin 2.0.21 |
| UI Framework | Jetpack Compose + Material 3 |
| Build System | Gradle 9.2.1, AGP 9.0.1 |
| Theme | Dracula |

## Features

- **Trip creation wizard** — name → date range picker (2 steps; location is set later from the trip hub).
- **Trip detail hub** — total cost summary, tap-to-edit name/location/dates, starting point, accommodations, spendings.
- **Accommodation management** — manual one-by-one adding, date-conflict detection (all occupied days blocked), per-night pricing, trip-range calendar highlighting.
- **Travel Mode** — car / microbus / plane mode, fuel, tolls, plane tickets, additional fees.
- **AI-powered toll finder** — uses OpenAI GPT to find vignettes, per-use tolls, and ferry crossings for the full route (including the return home).
- **AI fuel cost estimation** — estimates total driving distance via GPT and calculates fuel cost from consumption + price.
- **Daily Activities** — standalone per-day trip view (including travel-home day) with sunrise/sunset times (via sunrise-sunset.org API), moving/staying day detection, per-day departure time, and sequentially chained activities with AI-estimated driving times.
- **Activity categories** — Hiking, Museum, Nice sight, Beach, Wellness, Eating (Breakfast/Brunch/Lunch/Dinner).
- **Place recommendations** — Google Places API Text Search suggests nearby places by category with photos, ratings, and descriptions.
- **Edit Activity** — tap any activity to modify name, location, duration, category, or delete it.
- **Multi-currency support** — live exchange rates from Frankfurter API, manual rate editing, custom currencies.
- **Spendings** — daily and one-time expense tracking.
- **Dracula colour theme** — consistent dark UI matching the rest of CsasziTools.

## Project structure

```
TravelTool/
├── app/src/main/java/com/example/traveltool/
│   ├── MainActivity.kt              # Entry point
│   ├── data/
│   │   ├── Trip.kt                  # Data models
│   │   ├── TripRepository.kt        # JSON persistence
│   │   ├── TripViewModel.kt         # ViewModel + CRUD logic
│   │   ├── ApiKeyStore.kt           # Encrypted API key storage
│   │   ├── CurrencyManager.kt       # Currency + exchange rates
│   │   ├── DirectionsApiHelper.kt   # OpenAI toll/distance/driving-time finder
│   │   ├── PlacesApiHelper.kt       # Google Places API Text Search
│   │   └── SunriseSunsetApi.kt      # Sunrise/sunset API helper
│   ├── navigation/
│   │   ├── Screen.kt                # Route definitions
│   │   └── NavHost.kt               # Navigation graph
│   └── ui/
│       ├── components/
│       │   └── CurrencyPicker.kt    # Reusable currency dropdown
│       ├── screens/
│       │   ├── HomeScreen.kt        # Trip list & Add Trip
│       │   ├── TripNameScreen.kt    # Wizard step 1 – name
│   │   ├── TripDatesScreen.kt   # Wizard step 2 – dates (final step, creates trip)
│       │   ├── TripDetailScreen.kt  # Trip hub
│       │   ├── AccommodationListScreen.kt
│       │   ├── AddAccommodationScreen.kt
│       │   ├── EditAccommodationScreen.kt
│       │   ├── TravelSettingsScreen.kt  # Travel Mode
│       │   ├── DailyActivitiesScreen.kt # Daily Activities (standalone)
│   │   ├── DayDetailScreen.kt   # Day detail + sequential activity timeline
│       │   ├── AddActivityScreen.kt # Add planned activity with category & recommendations
│       │   ├── EditActivityScreen.kt # Edit existing activity
│       │   ├── RecommendActivityScreen.kt # Google Places recommendations
│       │   ├── SpendingsScreen.kt   # Daily & other expenses
│       │   ├── CurrencySettingsScreen.kt
│       │   └── ApiKeyScreen.kt      # OpenAI API key + model
│       └── theme/
│           ├── Color.kt             # Dracula palette
│           ├── Theme.kt             # Material 3 theme
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
- [ ] Trip summary / overview screen
- [ ] Persistent trip storage (Room database)
- [ ] Packing list per trip
- [ ] Budget tracking improvements
