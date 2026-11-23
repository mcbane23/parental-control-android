# parental-control-android

Parental control application for Android phones. One app with two modes: a parent dashboard
and a protected child mode.

## Status

Step 2 of the plan, the project backbone, is in place:

- Kotlin, Jetpack Compose and Material 3
- Firebase sign-in (Google for parents, anonymous for child phones)
- Families, with a main parent who can give other parents full control
- Pairing a child phone or a second parent with a one-time code

Time limits, app blocking, location and tamper protection come next.

## Building

1. Follow [docs/firebase-setup.md](docs/firebase-setup.md) and put `google-services.json` in `app/`.
2. Open the project in Android Studio and run the `app` configuration, or run `./gradlew assembleDebug`.

Requires JDK 17 and Android SDK 35. Minimum Android version is 9 (API 28).

## Layout

- `app/src/main/java/.../data` holds the Firebase access and data models.
- `app/src/main/java/.../ui` holds the Compose screens.
- `firestore.rules` holds the database security rules.
