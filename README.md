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

1. The Firebase config `app/google-services.json` is in the repository. [docs/firebase-setup.md](docs/firebase-setup.md) describes how the Firebase project was set up.
2. Open the project in Android Studio and run the `app` configuration, or run `./gradlew assembleDebug`.

Requires JDK 17 and Android SDK 35. Minimum Android version is 9 (API 28).

## Releases

The version lives in `app/build.gradle.kts` (`versionName` and `versionCode`). When a change
to `versionName` reaches `main`, GitHub builds the app and publishes a release named
`v<versionName>` with the APK attached. Raise `versionCode` at the same time, or Android
will refuse to install the new APK over the old one.

All builds are signed with the debug key in `keystore/debug.keystore`. That is fine for
family use. A Play Store release will need a private key that is not in the repository.

The test build that runs on pull requests uploads nothing to GitHub.

## Layout

- `app/src/main/java/.../data` holds the Firebase access and data models.
- `app/src/main/java/.../ui` holds the Compose screens.
- `firestore.rules` holds the database security rules.
