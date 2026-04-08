# MoneySplitter (Android)

Android phone version of the [MoneySplitter desktop app](../MoneySplitter/Python/README.md), built with Kotlin and Jetpack Compose.

## Features

- **Trip management** — create, open, and delete trips from the home screen
- **People** — add and remove people in each trip
- **Expense tracking** — log expenses with amount, currency, payer, optional description, and partial splits
- **Multi-currency support** — add/remove currencies, edit conversion rates, live-fetch from open.er-api.com
- **Balance calculation** — per-person balances converted to a chosen result currency
- **Settlement optimizer** — minimum-transfer plan to settle all debts
- **Undo/redo** — 50-step undo stack for all trip modifications
- **Auto-save** — every change is persisted to JSON in app storage
- **Material You** — dynamic colour theming (Android 12+), full light/dark mode support

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| State | ViewModel + StateFlow |
| Persistence | Gson → JSON files in app internal storage |
| Exchange rates | open.er-api.com REST API |
| Min SDK | 29 (Android 10) |
| Target SDK | 36 |

## Project Structure

```
app/src/main/java/com/example/moneysplitter/
├── MainActivity.kt            # Entry point + navigation
├── data/
│   ├── Models.kt              # Expense, TripData, Settlement, TripSummary
│   └── TripRepository.kt      # JSON persistence layer
├── logic/
│   ├── Calculator.kt           # Balance & settlement calculation
│   └── RateFetcher.kt          # Live exchange rate API
├── ui/
│   ├── theme/                  # Material 3 Color, Theme, Typography
│   ├── screens/
│   │   ├── HomeScreen.kt       # Trip list + create/delete
│   │   ├── TripScreen.kt       # Bottom-nav shell with 4 tabs
│   │   ├── ExpensesTab.kt      # Expense list + add/edit/delete
│   │   ├── PeopleTab.kt        # People management
│   │   ├── CurrenciesTab.kt    # Currency + rate management
│   │   └── ResultsTab.kt       # Balances + settlements
│   └── components/
│       └── AddExpenseSheet.kt   # Modal bottom sheet for expense entry
└── viewmodel/
    ├── HomeViewModel.kt         # Home screen state
    └── TripViewModel.kt         # Trip state, undo/redo, calculations
```

## Building

Open the `MoneySplitter2/` folder in Android Studio and run on a device or emulator (minSdk 29).

## Customisation

- Replace `app/src/main/res/mipmap-*/ic_launcher*` with your own app icon
- Add a logo drawable for the home screen header
