# TravelTool

**Android travel-planning app** built with Kotlin & Jetpack Compose.

| Info | Value |
|---|---|
| Version | 0.0.1 |
| Platform | Android (min SDK 29, target 36) |
| Language | Kotlin 2.0.21 |
| UI Framework | Jetpack Compose + Material 3 |
| Build System | Gradle 9.2.1, AGP 9.0.1 |
| Theme | Dracula |

## Features

- **Welcome screen** — CsasziTools / TravelTool branding with "+ Add Trip" button.
- **Trip name input** — text field for the trip title.
- **Date range picker** — Material 3 calendar for selecting start and end dates.
- **Location input** — text field for the destination (Google Maps integration planned).
- **Dracula colour theme** — consistent dark UI matching the rest of CsasziTools.

## Project structure

```
TravelTool/
├── app/src/main/java/com/example/traveltool/
│   ├── MainActivity.kt              # Entry point
│   ├── navigation/
│   │   ├── Screen.kt                # Route definitions
│   │   └── NavHost.kt               # Navigation graph
│   └── ui/
│       ├── screens/
│       │   ├── HomeScreen.kt        # Welcome & Add Trip
│       │   ├── TripNameScreen.kt    # Step 1 – trip name
│       │   ├── TripDatesScreen.kt   # Step 2 – date range
│       │   └── TripLocationScreen.kt # Step 3 – location
│       └── theme/
│           ├── Color.kt             # Dracula palette
│           ├── Theme.kt             # Material 3 theme
│           └── Type.kt              # Typography
├── app/build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/
```

## Building

Open in Android Studio or build from the command line:

```bash
./gradlew assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Roadmap

- [ ] Google Maps integration for the location screen
- [ ] Trip summary / overview screen
- [ ] Persistent trip storage (Room database)
- [ ] Trip list on the home screen
- [ ] Packing list per trip
- [ ] Budget tracking
