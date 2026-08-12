# Tennis League

A Kotlin/Jetpack Compose Android app for running a company tennis league: players join a
division, propose and score matches in real time, and track rankings — with a companion Wear OS
app for live score syncing on the wrist.

## Features

- **Auth & onboarding** — Google Sign-In via Firebase Auth, with a division join/create flow and
  a first-run tutorial.
- **Divisions & rankings** — Players belong to a division; standings are computed from match
  results (matches/sets/games won-lost, game differential) and shown on a live rankings board.
- **Match management** — Propose, schedule, and score matches, view match history, and drill into
  match detail screens.
- **Real-time sync** — Match and division data sync live via Firestore.
- **Wear OS companion** — A standalone Wear module (`wear/`) that receives live score updates from
  the phone app over the Wearable Data Layer.
- **Messaging & notifications** — In-app messages and push notifications via Firebase Cloud
  Messaging.
- **Admin tools** — Division leaders/admins get a match scheduler and admin dashboard.
- **Feedback** — Built-in screen for users to send feedback.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Jetpack Navigation Compose
- Firebase: Auth, Firestore, Cloud Messaging, Functions, Storage, App Check, AI
- Room (local persistence), Retrofit + Moshi + OkHttp (networking)
- Wear OS: Compose for Wear, Wearable Data Layer (`play-services-wearable`)
- Testing: JUnit, Robolectric, Espresso, Roborazzi (screenshot tests)

## Project structure

```
app/    Phone application module (com.example)
  data/models        Domain models (User, Division, Match, Ranking, ...)
  data/repository     TennisRepository — app-wide state & Firestore sync
  scoring             Tennis scoring/ranking engine
  firebase            Firebase init & messaging service
  service              Wearable listener service (receives score updates)
  ui/                 Compose screens: auth, onboarding, home, matches,
                       rankings, messages, profile, admin, feedback
wear/   Wear OS companion module (com.example.wear)
  WearScoreActivity    Live match score display on the watch
  WearOsManager/Components  Data Layer communication helpers
```

## Getting started

### Prerequisites

- Android Studio (latest stable)
- JDK 17
- A Firebase project with `google-services.json` placed in `app/`

### Configure secrets

Copy the example env file and fill in your project's values:

```
cp .env.example .env
```

`.env` is read by the Secrets Gradle Plugin and is not committed to version control.

### Build & run

```
./gradlew :app:assembleDebug
```

Open the project in Android Studio and run the `app` configuration on a device/emulator, or the
`wear` configuration on a Wear OS device/emulator for the companion app.

### Tests

```
./gradlew test
```
