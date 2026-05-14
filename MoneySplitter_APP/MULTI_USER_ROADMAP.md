# MoneySplitter — Multi-User & Play Store Roadmap

> Living plan for turning MoneySplitter from a local-only app into a shared, multi-user expense splitter available on Google Play.

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture Decisions](#architecture-decisions)
3. [Phase 1 — Authentication (Google Sign-In)](#phase-1--authentication-google-sign-in)
4. [Phase 2 — Cloud Backend & Data Model](#phase-2--cloud-backend--data-model)
5. [Phase 3 — Friend System](#phase-3--friend-system)
6. [Phase 4 — Shared Trips (Real-Time Sync)](#phase-4--shared-trips-real-time-sync)
7. [Phase 5 — Play Store Release](#phase-5--play-store-release)
8. [Phase 6 — Post-Launch Improvements](#phase-6--post-launch-improvements)
9. [Skipping the Play Store — APK Sideloading](#skipping-the-play-store--apk-sideloading)
10. [iOS — Making It Cross-Platform](#ios--making-it-cross-platform)
11. [Complete Cost Estimate](#complete-cost-estimate)
12. [Tech Stack Summary](#tech-stack-summary)
13. [Checklist](#checklist)

---

## Overview

**Current state:** Fully local Android app. Data saved as JSON on-device. Single user edits trips alone.

**Target state:** Multi-user app where:
- Users sign in with Google (Gmail)
- Users search for friends by email and maintain a friend list
- Trips can be shared; all members see the same trip and can add/edit expenses
- App is on Google Play Store and can be updated over-the-air

---

## Architecture Decisions

| Decision | Recommended choice | Why |
|---|---|---|
| Backend | **Firebase** (Auth + Firestore + Cloud Messaging) | Zero server to manage, generous free tier, native Android SDK, real-time sync built in |
| Auth | **Firebase Authentication** with Google Sign-In provider | One-tap login, handles tokens/refresh automatically |
| Database | **Cloud Firestore** | Real-time listeners, offline support, document-based (maps well to current JSON model) |
| Push notifications | **Firebase Cloud Messaging (FCM)** | Notify members when someone adds an expense |
| File storage (PDF exports) | Keep local — generate on device | No change needed |
| Alternative backend | Supabase (Postgres + Auth + Realtime) | Good if you prefer SQL, but more setup work |

> **Why not a custom backend?** For a trip expense app the data model is simple. Firebase gives you auth, real-time sync, offline cache, and push notifications without writing or hosting a single server. You can always migrate later.

---

## Phase 1 — Authentication (Google Sign-In)

### What's needed
- **Google Cloud project** (free) with OAuth 2.0 client IDs
- **Firebase project** linked to your Android app
- `google-services.json` added to `app/`
- Firebase Auth SDK + Google Sign-In SDK

### Implementation steps
1. Create Firebase project at [console.firebase.google.com](https://console.firebase.google.com)
2. Enable **Google** sign-in provider in Firebase Console → Authentication → Sign-in methods
3. Register app package `com.example.moneysplitter` and download `google-services.json`
4. Add Gradle dependencies:
   ```kotlin
   // build.gradle.kts (project)
   id("com.google.gms.google-services") version "4.4.2" apply false
   
   // build.gradle.kts (app)
   implementation(platform("com.google.firebase:firebase-bom:33.8.0"))
   implementation("com.google.firebase:firebase-auth")
   implementation("com.google.android.gms:play-services-auth:21.3.0")
   implementation("androidx.credentials:credentials:1.5.0")
   implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
   ```
5. Create `LoginScreen` composable with Google One-Tap sign-in
6. Store auth state in a shared `AuthViewModel`:
   - `currentUser: StateFlow<FirebaseUser?>`
   - `isLoggedIn: StateFlow<Boolean>`
7. Wrap `NavHost` with auth check — show `LoginScreen` if not signed in
8. Create user profile document in Firestore on first sign-in

### New files
| File | Purpose |
|---|---|
| `ui/screens/LoginScreen.kt` | Google sign-in UI |
| `viewmodel/AuthViewModel.kt` | Auth state management |
| `data/UserProfile.kt` | Data class for user profile |

### Effort: ~2-3 days

---

## Phase 2 — Cloud Backend & Data Model

### Firestore data model

```
/users/{uid}
    displayName: String
    email: String
    photoUrl: String?
    friends: [uid, uid, ...]          // friend list
    createdAt: Timestamp

/trips/{tripId}
    name: String
    members: [uid, uid, ...]          // who can see/edit
    memberEmails: [email, email, ...] // for display
    startDate: String?
    endDate: String?
    people: [String, ...]             // "display names" used in expenses
    baseCurrency: String
    currencies: [String, ...]
    conversionRates: Map<String, Double>
    resultCurrency: String
    createdBy: uid
    createdAt: Timestamp

/trips/{tripId}/expenses/{expenseId}
    name: String?
    description: String
    notes: String?
    amount: Double
    currency: String
    paidBy: String
    splitAmong: [String, ...]
    date: String?
    settled: Boolean
    addedBy: uid
    createdAt: Timestamp
```

### Implementation steps
1. Add Firestore dependency:
   ```kotlin
   implementation("com.google.firebase:firebase-firestore")
   ```
2. Create `FirestoreRepository` to replace local `TripRepository` for cloud trips
3. Keep local `TripRepository` working for offline/"personal" trips (graceful degradation)
4. Migrate `TripViewModel` to use Firestore snapshot listeners for real-time updates
5. Add offline persistence (Firestore has this built-in — just enable it)
6. Handle merge conflicts: last-write-wins for simple fields, Firestore transactions for balance-critical ops

### Security rules (Firestore)
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid} {
      allow read: if request.auth != null;
      allow write: if request.auth.uid == uid;
    }
    match /trips/{tripId} {
      allow read, write: if request.auth.uid in resource.data.members;
      allow create: if request.auth != null;
      match /expenses/{expenseId} {
        allow read, write: if request.auth.uid in get(/databases/$(database)/documents/trips/$(tripId)).data.members;
      }
    }
  }
}
```

### Effort: ~4-5 days

---

## Phase 3 — Friend System

### How it works
1. User goes to **Friends** screen
2. Types an email address to search
3. If a user with that email exists in Firestore `/users/`, show their name + photo
4. Send a "friend request" (or directly add — simpler for v1)
5. Friends appear in a list; can be invited to trips quickly

### Implementation steps
1. Add Firestore index on `users.email` for querying
2. Create `FriendsScreen` with search bar
3. Query: `db.collection("users").whereEqualTo("email", searchEmail)`
4. Add/remove friends by updating the `friends` array in the user's doc
5. When creating/editing a trip, show a "Add from friends" picker

### New files
| File | Purpose |
|---|---|
| `ui/screens/FriendsScreen.kt` | Friend search + list UI |
| `viewmodel/FriendsViewModel.kt` | Friend operations |

### Effort: ~2 days

---

## Phase 4 — Shared Trips (Real-Time Sync)

### How it works
1. Trip creator adds members (from friend list or by email)
2. All members see the trip on their home screen
3. Any member can add/edit/settle expenses — changes appear in real-time
4. Optional: push notification when someone adds an expense

### Implementation steps
1. Home screen query: `db.collection("trips").whereArrayContains("members", currentUid)`
2. Trip screen: attach a Firestore snapshot listener on the trip doc + expenses subcollection
3. Expense add/edit: write to Firestore instead of local JSON
4. FCM integration (optional for v1):
   - Cloud Function triggers on new expense → sends notification to other members
   - Requires Firebase Blaze plan (pay-as-you-go, still very cheap)

### Key considerations
- **Conflict resolution:** Firestore handles concurrent writes. For simple fields, last-write-wins is fine. For "settled" toggle use a transaction.
- **Offline:** Firestore queues writes locally and syncs when back online.
- **Permissions model (v1):** Everyone can edit everything in a shared trip (simplest).
- **Permissions model (v2, later):** Trip owner can lock expenses, restrict who can settle, etc.

### Effort: ~3-4 days

---

## Phase 5 — Play Store Release

### One-time setup
| Item | Cost | Notes |
|---|---|---|
| Google Play Developer account | **$25 USD** (one-time) | [play.google.com/console](https://play.google.com/console) |
| App signing key | Free | Google manages it (App Signing by Google Play) |
| Privacy policy page | Free | Required — host on GitHub Pages or similar |
| Firebase Spark plan | Free | Auth + Firestore free tier is generous |

### Steps to publish
1. **Change package name** from `com.example.moneysplitter` to a real one (e.g. `com.csaszitools.moneysplitter`)
   - Update in `build.gradle.kts (app)` → `applicationId`
   - Update `AndroidManifest.xml`, `google-services.json`
2. **Create a signed release build:**
   ```bash
   # Generate upload keystore (once)
   keytool -genkey -v -keystore upload-keystore.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   
   # Build release AAB (Android App Bundle — required by Play Store)
   ./gradlew bundleRelease
   ```
3. **Prepare store listing:**
   - App name, short description, full description
   - Screenshots (phone + tablet if possible): at least 2
   - Feature graphic (1024×500 px)
   - App icon (512×512 px)
   - App category: Finance
   - Content rating questionnaire
   - Privacy policy URL
4. **Upload AAB** to Play Console → Production (or Internal Testing first)
5. **Review process:** Google reviews the app — typically 1-3 days for new apps
6. **App updates:** Just build a new AAB with a bumped `versionCode`/`versionName` and upload

### Release tracks (recommended order)
1. **Internal testing** — up to 100 testers, instant approval, use this first
2. **Closed testing** — invite-only, good for beta
3. **Open testing** — anyone can join
4. **Production** — public on Play Store

### Effort: ~1-2 days (mostly store listing prep)

---

## Phase 6 — Post-Launch Improvements

These are not needed for launch, but good to have on the roadmap:

| Feature | Description | Complexity |
|---|---|---|
| Push notifications | "Tomi added 60 EUR expense in Dolomites trip" | Medium (requires Cloud Functions) |
| Trip invitations | Deep link / QR code to join a trip | Medium |
| Expense photos | Attach receipt photos (Firebase Storage) | Medium |
| Per-expense permissions | Only creator can edit their expense | Low |
| Trip archive/export | Cloud-stored PDF history | Low |
| Dark mode PDF | Match app theme in export | Low |
| Currency auto-detect | Based on device location | Low |
| Widgets | Home screen balance summary | Medium |
| iOS version | Kotlin Multiplatform or separate Swift app | High |

---

## Skipping the Play Store — APK Sideloading

**Yes, you can absolutely skip the Play Store** and just send the APK file to your friends. This is the simplest option if you only want to share with a small group.

### How it works

1. Build a release APK (not AAB):
   ```bash
   ./gradlew assembleRelease
   # Output: app/build/outputs/apk/release/app-release.apk
   ```
2. Sign it with a keystore (can be a debug keystore for friends-only use)
3. Send the `.apk` file to your friends via Messenger, Google Drive, email, etc.
4. Friends open the file on their Android phone → tap Install
5. Android will warn "Install from unknown sources" — they allow it once and it installs

### Pros
- **Free** — no $25 developer account needed
- **Instant** — no review process, no store listing, no screenshots needed
- **Simple** — just share a file
- **Updates** — build a new APK and send it again

### Cons
- Friends must enable "Install from unknown sources" in their phone settings
- No auto-updates — you have to resend the APK and friends reinstall manually
- Google Play Protect may flag the APK as "unverified" (not dangerous, just annoying)
- No crash reporting or analytics unless you add Firebase Crashlytics yourself

### When to use this approach
- You just want to split expenses with 5-10 friends
- You don't care about public distribution
- You want to move fast without store bureaucracy

> **Recommendation:** Start with APK sideloading. If the app grows beyond your friend group, publish to Play Store later. The Firebase backend works the same either way.

---

## iOS — Making It Cross-Platform

### The Problem

iOS and Android are completely different platforms. Your current Kotlin + Jetpack Compose app **cannot run on iPhone** as-is. You need to either:

1. **Rewrite for iOS** (native Swift/SwiftUI)
2. **Use a cross-platform framework** (rewrite once, runs on both)
3. **Share backend logic, separate UI** (Kotlin Multiplatform)

### Option A — Kotlin Multiplatform (KMP) + Compose Multiplatform

**Best option for your situation** since you already have Kotlin code.

| Aspect | Details |
|---|---|
| What it is | Share Kotlin business logic (ViewModels, data, Firebase calls) between Android and iOS. UI can also be shared via Compose Multiplatform (experimental on iOS) |
| What you keep | All your data models, ViewModels, Calculator, PdfExporter logic |
| What you rewrite | Gradle setup, some platform-specific code (file I/O, PDF on iOS) |
| iOS UI | Compose Multiplatform (shared) or SwiftUI (native, prettier) |
| Learning curve | Medium — you already know Kotlin, just need Xcode basics |
| Maturity | Compose Multiplatform for iOS is stable as of 2025, used in production by JetBrains |

**Steps:**
1. Restructure project into KMP modules:
   - `shared/` — data models, ViewModels, Firebase logic, Calculator
   - `androidApp/` — your current Compose UI (mostly unchanged)
   - `iosApp/` — Compose Multiplatform UI or SwiftUI
2. Add iOS target in Gradle
3. Set up Xcode project that depends on the shared Kotlin framework
4. Implement iOS-specific code (PDF via UIKit, file paths, etc.)

**Effort:** ~2-3 weeks for someone familiar with KMP; ~4-6 weeks learning as you go

### Option B — Flutter (Full Rewrite)

| Aspect | Details |
|---|---|
| Language | Dart |
| UI | Single codebase for Android + iOS |
| Firebase support | Excellent (FlutterFire plugins) |
| Downside | **Complete rewrite** — none of your Kotlin code is reusable |
| Learning curve | High — new language, new framework |

**Effort:** ~3-4 weeks full rewrite

### Option C — React Native (Full Rewrite)

| Aspect | Details |
|---|---|
| Language | TypeScript/JavaScript |
| UI | Single codebase for Android + iOS |
| Firebase support | Good (React Native Firebase) |
| Downside | **Complete rewrite**, JavaScript performance overhead |

**Effort:** ~3-4 weeks full rewrite

### Option D — Native Swift/SwiftUI (iOS Only)

| Aspect | Details |
|---|---|
| Language | Swift |
| UI | SwiftUI (Apple-native, looks best on iPhone) |
| Firebase support | Excellent (official Apple SDK) |
| Downside | Must maintain two separate codebases forever |
| Upside | Best iOS look & feel, no cross-platform quirks |

**Effort:** ~3-4 weeks for the iOS app alone

### Recommended approach

**Option A (KMP)** — You keep your Kotlin skills, reuse 60-70% of your existing code, and get both platforms with one language.

### Distributing to iPhone Users

This is where iOS is **much harder** than Android:

#### Can you just send an IPA file to friends? (Like APK sideloading)

**Not really.** Apple makes this extremely difficult:

| Method | Cost | Limit | How it works |
|---|---|---|---|
| **AltStore / Sideloadly** | Free | Re-sign every 7 days | Friends install AltStore on their PC/Mac, load the IPA. App expires weekly and must be re-signed. Very annoying. |
| **Apple Developer Account (personal)** | **$99/year** | 100 devices/year | Register each friend's device UUID. Build IPA signed with your certificate. Send to them. Works for 1 year. |
| **TestFlight** | **$99/year** (same account) | 10,000 testers | Best option for friends. Upload to App Store Connect → TestFlight. Friends install TestFlight app, tap a link, done. Auto-updates. No "unknown sources" nonsense. |
| **App Store** | **$99/year** (same account) | Unlimited | Full public release. Apple review required (stricter than Google). |

> **Key difference from Android:** On iOS you **cannot** just send a file and have friends install it. Apple requires either a developer account ($99/year) or the janky AltStore workaround.

#### Recommended path for iPhone friends

1. **Pay $99/year** for Apple Developer Program
2. **Use TestFlight** — it's dead simple:
   - Upload your build to App Store Connect
   - Add friends by email
   - They get a TestFlight notification → tap Install
   - You can push updates and they auto-install
   - Supports up to 10,000 testers
   - No App Store review needed for TestFlight (only light review)
3. Later, optionally publish to the App Store from the same account

### iOS-Specific Requirements

| Requirement | Details |
|---|---|
| **Mac computer** | Required. You CANNOT build iOS apps on Windows. Need macOS + Xcode. |
| **Xcode** | Free from Mac App Store (requires macOS 14+) |
| **Apple Developer Account** | $99/year |
| **Physical iPhone for testing** | Optional (Xcode has simulator) but recommended |
| **CocoaPods or SPM** | For Firebase iOS SDK dependencies |

> ⚠️ **You need a Mac.** There is no way around this for iOS development. Even KMP/Flutter/React Native require Xcode on macOS to build the iOS binary. A used Mac Mini works fine (~$300-400).

---

## Complete Cost Estimate

### Scenario 1: Android only, friends only (APK sideloading)

| Item | Cost | Frequency |
|---|---|---|
| Firebase (Spark plan) | **$0** | Monthly — free tier covers small groups |
| Android development tools | **$0** | You already have these |
| APK distribution | **$0** | Send the file directly |
| **TOTAL** | **$0** | |

### Scenario 2: Android on Google Play Store

| Item | Cost | Frequency |
|---|---|---|
| Firebase (Spark plan) | **$0** | Monthly |
| Google Play Developer account | **$25** | One-time |
| **TOTAL** | **$25 once, then $0/month** | |

### Scenario 3: Android + iOS for friends (no stores)

| Item | Cost | Frequency |
|---|---|---|
| Firebase (Spark plan) | **$0** | Monthly |
| Apple Developer Account | **$99** | Yearly |
| Mac computer (if you don't have one) | **$300-400** | One-time (used Mac Mini) |
| Android APK distribution | **$0** | Direct sharing |
| iOS via TestFlight | **$0** | Included in Developer Account |
| **TOTAL (have Mac)** | **$99/year** | |
| **TOTAL (need Mac)** | **$400-500 first year, then $99/year** | |

### Scenario 4: Both stores (public release)

| Item | Cost | Frequency |
|---|---|---|
| Firebase (Spark plan) | **$0** | Monthly (free tier) |
| Firebase (Blaze plan — if you need Cloud Functions for push notifications) | **~$0-5** | Monthly (pay-as-you-go, very cheap for small apps) |
| Google Play Developer account | **$25** | One-time |
| Apple Developer Account | **$99** | Yearly |
| Mac computer | **$300-400** | One-time (if needed) |
| Privacy policy hosting | **$0** | GitHub Pages |
| Domain name (optional, for website) | **~$10-15** | Yearly |
| **TOTAL first year** | **~$135-540** | Depends on Mac ownership |
| **TOTAL ongoing** | **~$99-115/year** | |

### Firebase usage breakdown (free tier limits)

| Resource | Free limit | Enough for… |
|---|---|---|
| Auth users | 10,000 MAU | ✅ Way more than you'll ever need with friends |
| Firestore reads | 50,000/day | ✅ ~50 active users checking trips frequently |
| Firestore writes | 20,000/day | ✅ ~200+ expenses added per day |
| Firestore storage | 1 GB | ✅ ~100,000+ trips worth of data |
| FCM notifications | Unlimited | ✅ Always free |
| Storage (receipts) | 5 GB | ✅ ~10,000 receipt photos |

> **Bottom line:** For sharing with friends, you will likely **never exceed the free tier**. Firebase starts charging only when you hit real scale (thousands of daily active users).

### Cost comparison with alternatives

| Solution | Monthly cost | Notes |
|---|---|---|
| **Firebase (recommended)** | **$0** | Free tier covers everything for friend groups |
| Supabase | $0 (free tier) or $25/mo | Free tier is limited (500 MB, 50K rows) |
| Custom VPS (DigitalOcean) | $6-12/mo | Must manage server yourself |
| AWS Amplify | $0-5/mo | More complex setup than Firebase |
| Appwrite (self-hosted) | $6/mo + VPS | Open source, more control |

---

## Tech Stack Summary

### Android only (current path)
```
┌─────────────────────────────────────────┐
│  Android App (Kotlin + Jetpack Compose) │
├─────────────────────────────────────────┤
│  Firebase Auth (Google Sign-In)         │
│  Cloud Firestore (real-time DB)         │
│  Firebase Cloud Messaging (push)        │
│  Firebase Storage (receipt photos)      │
├─────────────────────────────────────────┤
│  APK Sideloading / Google Play Store    │
└─────────────────────────────────────────┘
```

### Cross-platform with KMP (future)
```
┌──────────────────────┬──────────────────────┐
│  Android App         │  iOS App             │
│  (Compose)           │  (Compose / SwiftUI) │
├──────────────────────┴──────────────────────┤
│  Shared Kotlin Module (KMP)                 │
│  Models, ViewModels, Calculator, Firebase   │
├─────────────────────────────────────────────┤
│  Firebase Auth + Firestore + FCM            │
├─────────────────────────────────────────────┤
│  Google Play / APK  │  App Store / TestFlight│
└──────────────────────┴───────────────────────┘
```

---

## Checklist

### Phase 1 — Auth
- [ ] Create Firebase project
- [ ] Enable Google sign-in provider
- [ ] Add `google-services.json` to app
- [ ] Add Firebase + Google Sign-In dependencies
- [ ] Implement `LoginScreen`
- [ ] Implement `AuthViewModel`
- [ ] Create user profile in Firestore on first login
- [ ] Gate app navigation behind auth

### Phase 2 — Cloud Backend
- [ ] Add Firestore dependency
- [ ] Design and create Firestore collections
- [ ] Implement `FirestoreRepository`
- [ ] Deploy Firestore security rules
- [ ] Migrate `TripViewModel` to Firestore listeners
- [ ] Test offline → online sync

### Phase 3 — Friends
- [ ] Add email index in Firestore
- [ ] Implement `FriendsScreen` + search
- [ ] Implement add/remove friend
- [ ] Show friend picker when creating trips

### Phase 4 — Shared Trips
- [ ] Query trips by membership
- [ ] Real-time expense sync via snapshot listeners
- [ ] Handle concurrent edits
- [ ] (Optional) FCM notifications via Cloud Function

### Phase 5 — Play Store
- [ ] Register Google Play Developer account ($25)
- [ ] Change package name from `com.example.*`
- [ ] Create upload keystore
- [ ] Build signed release AAB
- [ ] Write Privacy Policy
- [ ] Prepare store listing assets (screenshots, icon, descriptions)
- [ ] Upload to Internal Testing track
- [ ] Test on multiple devices
- [ ] Promote to Production
- [ ] Submit for review

### Phase 6 — iOS (if pursuing)
- [ ] Get access to a Mac
- [ ] Install Xcode
- [ ] Register Apple Developer Account ($99/year)
- [ ] Set up KMP project structure (shared + androidApp + iosApp)
- [ ] Move models, ViewModels, Calculator to shared module
- [ ] Implement iOS UI (Compose Multiplatform or SwiftUI)
- [ ] Implement iOS-specific PDF generation
- [ ] Add Firebase iOS SDK
- [ ] Test on iPhone simulator + real device
- [ ] Upload to TestFlight for friend distribution
- [ ] (Optional) Submit to App Store

---

*Created: April 8, 2026 · Updated: April 8, 2026*
