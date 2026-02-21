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
- **Trip detail hub** — total cost summary, tap-to-edit name/dates, start point, end point, accommodations, spendings.
- **Accommodation management** — manual one-by-one adding, date-conflict detection (boundary dates shareable), per-night pricing, trip-range calendar highlighting. Location is entered first; the name auto-fills to "Staying at &lt;location&gt;" — tapping the name field clears it for custom input, leaving it empty restores the auto-generated name. Adding, editing, or deleting an accommodation automatically re-estimates all moving-day driving distances in the background so the fuel cost in Travel Mode is always up-to-date.
- **Travel Mode** — car / microbus / plane mode, fuel, tolls, plane tickets (with airport autocomplete search & ticket quantity), car rentals (plane mode), public transport fees, additional fees.
- **Airport autocomplete** — built-in database of ~140 major world airports, searchable by city, name, or IATA code.
- **Automatic toll finder (exact-route GPT + Routes fallback)** — on "Find tolls", Travel Mode builds the exact chronological drive segments of the trip (same segment set as Fuel Breakdown), then asks OpenAI GPT for tolls/vignettes/ferries strictly for those listed routes only (to avoid unrelated extras). If no OpenAI key is set, it falls back to Google Routes API toll computation. Auto-generated toll prices are stored in the trip's selected display currency.
- **Start / End point** — separate start and end points for the trip (both default to current location). The first day's drive goes from the start point to the accommodation; the last day's drive goes from accommodation to the end point.
- **Fuel cost estimation** — calculates fuel cost from total driving distance (summed automatically from activity drives and moving-day auto-drives via Google Routes API), fuel consumption, and fuel price per litre. The "Estimated Fuel Cost" card in Travel Mode is clickable and opens a chronological fuel breakdown page that lists each drive segment (from → to), distance, estimated fuel used, and segment cost.
- **Daily Activities** — standalone per-day trip view (including travel-home day) with sunrise/sunset times (via sunrise-sunset.org API), moving/staying day detection, inline scroll-wheel departure time picker (hour & minute) with explicit "Set" button, and sequentially chained activities with Google Routes API driving times (GPT fallback). The return drive from the last activity back to the accommodation is automatically estimated and shown in the timeline. On travel days (first & last), activities are split into "before arrival" (on the way) and "after arrival" groups so detour stops can be planned along the route. Moving days always show a visual "Drive" card from origin to destination with real-time driving time and distance estimation from Google Routes API — departure time is taken from the clock dial and the estimated arrival time is computed and displayed. The auto-drive card is replaced when on-the-road activities are added. Activity location is entered first; the name auto-fills to "Activity at &lt;location&gt;" — tapping the name field clears it for custom input, leaving it empty restores the auto-generated name. An "After activity" dropdown lets the user insert new activities at any position in the day's sequence — defaults to the last activity (append), but selecting "Departure" or any earlier activity inserts in between and automatically re-indexes the chain and recalculates driving times for neighbours. Each activity has an optional cost field (default 0) with a ×multiplier (1–30) dropdown and currency picker; activity costs are included in the total trip cost.
- **Delay activities** — when adding an activity, a "Delay" checkbox creates a wait/pause at the current location with no driving. Only the name and duration can be set; the location is inherited from the previous activity.
- **Smart currency formatting** — currencies worth less than 1/100 of a EUR (e.g. HUF, JPY) are displayed as whole integers; other currencies show two decimal places.
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
│       │   ├── FuelBreakdownScreen.kt # Chronological per-drive fuel breakdown
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
| OpenAI GPT | Driving-time fallback and exact-route toll/vignette/ferry identification | Yes (user-provided) |
| Frankfurter | Live currency exchange rates | No |
| sunrise-sunset.org | Sunrise/sunset times for daily activities | No |
| Google Maps SDK | Map display, location picking, geocoding | Yes (in AndroidManifest.xml) |
| Google Routes API | Driving time & distance estimation (computeRoutes), plus fallback toll computation when no OpenAI key is set | Yes (same API key) |
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
