# parental-control-android

Parental control application for Android phones. One app with two modes: a parent dashboard
and a protected child mode.

## Status

Step 2 of the plan, the project backbone, is in place:

- Kotlin, Jetpack Compose and Material 3
- Firebase sign-in (Google for parents, anonymous for child phones)
- Families, with a main parent who can give other parents full control
- Pairing a child phone or a second parent with a one-time code

Step 3 has started:

- The child phone counts app usage and reports it to the family
- Parents set a daily limit, per-app limits and blocked apps from their phone
- The child phone enforces them with an accessibility service, also when offline

- The child phone shares its location from a foreground service, and parents see it on an
  OpenStreetMap map with a refresh button

Keeping mobile data on and tamper protection come next.

## Building

1. The Firebase config `app/google-services.json` is in the repository. [docs/firebase-setup.md](docs/firebase-setup.md) describes how the Firebase project was set up.
2. Open the project in Android Studio and run the `app` configuration, or run `./gradlew assembleDebug`.

Requires JDK 17 and Android SDK 35. Minimum Android version is 9 (API 28).

## Releases

The version lives in `app/build.gradle.kts` (`versionName` and `versionCode`). When a change
to `versionName` reaches `main`, GitHub builds the app, signs it with the private upload key
and publishes it twice:

- as a GitHub release named `v<versionName>`, with the APK and the AAB attached
- to the internal testing track of Google Play, where the family phones get it as a normal
  Play update

Raise `versionCode` at the same time, because Google Play accepts each number only once.

[docs/play-release.md](docs/play-release.md) describes the one-time setup: the upload key,
the GitHub secrets, the app in Play Console and the testers. The phones install the app from
Play, because Play Protect blocks installing this APK from a file.

Debug builds are still signed with the debug key in `keystore/debug.keystore`, so a local
build works without any secret. A debug build and a Play build cannot replace each other on
a phone, because their signatures differ.

The test build that runs on pull requests uploads nothing to GitHub.

## Layout

- `app/src/main/java/.../data` holds the Firebase access and data models.
- `app/src/main/java/.../ui` holds the Compose screens.
- `firestore.rules` holds the database security rules.
