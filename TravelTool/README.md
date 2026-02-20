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

- **Trip creation wizard** — name → date range picker → location (with Google Maps).
- **Trip detail hub** — total cost summary, starting point, accommodations, spendings.
- **Accommodation management** — smart date-gap filling, overlap splitting, per-night pricing.
- **Travel Specifics** — car / microbus / plane mode, fuel, tolls, plane tickets, additional fees.
- **AI-powered toll finder** — uses OpenAI GPT to find vignettes, per-use tolls, and ferry crossings for the full route (including the return home).
- **AI fuel cost estimation** — estimates total driving distance via GPT and calculates fuel cost from consumption + price.
- **Daily Activities** — per-day trip view with sunrise/sunset times (via sunrise-sunset.org API) and moving/staying day detection.
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
│   │   ├── TripViewModel.kt         # ViewModel + accommodation logic
│   │   ├── ApiKeyStore.kt           # Encrypted API key storage
│   │   ├── CurrencyManager.kt       # Currency + exchange rates
│   │   ├── DirectionsApiHelper.kt   # OpenAI toll/distance finder
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
│       │   ├── TripDatesScreen.kt   # Wizard step 2 – dates
│       │   ├── TripLocationScreen.kt # Wizard step 3 – location
│       │   ├── TripDetailScreen.kt  # Trip hub
│       │   ├── AccommodationListScreen.kt
│       │   ├── AddAccommodationScreen.kt
│       │   ├── EditAccommodationScreen.kt
│       │   ├── TravelSettingsScreen.kt  # Travel Specifics
│       │   ├── DayDetailScreen.kt   # Daily activities detail
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
| Google Maps SDK | Map display, location picking, geocoding | Yes (in local.properties) |

## Building

Open in Android Studio or build from the command line:

```bash
./gradlew assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Roadmap

- [ ] Google Maps integration for the daily activities map
- [ ] Sights to see per day
- [ ] Trip summary / overview screen
- [ ] Persistent trip storage (Room database)
- [ ] Packing list per trip
- [ ] Budget tracking improvements
