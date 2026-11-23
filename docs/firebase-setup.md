# Firebase setup

The app uses Firebase for sign-in and for syncing data between the parent and child phones.
This is a one-time setup and takes about ten minutes. The free Spark plan is enough for now.

## 1. Create the project

1. Open <https://console.firebase.google.com> and sign in with your Google account.
2. Choose **Create a project** (or **Add project**), give it a name such as `parental-control`, and finish the wizard. Google Analytics is not needed.

## 2. Add the Android app

1. On the project overview, choose the Android icon (**Add app**).
2. Android package name: `com.mcbane23.parentalcontrol`
3. Debug signing certificate SHA-1: required for Google sign-in. In Android Studio open the Gradle panel and run `app > Tasks > android > signingReport`, or run `./gradlew signingReport` in a terminal, and copy the `SHA1` value of the `debug` variant.
4. Register the app. Do not download `google-services.json` yet, because step 3 adds information to it.

Each computer that builds the app has its own debug certificate. If you build on a second computer, add its SHA-1 under **Project settings > Your apps > Add fingerprint**.

## 3. Turn on sign-in

1. Go to **Build > Authentication > Get started**.
2. Under **Sign-in method**, enable **Google** and pick a support email.
3. Also enable **Anonymous**. Child phones use it, so your child does not need a Google account in the app.

## 4. Create the database

1. Go to **Build > Firestore Database > Create database**.
2. Pick a location close to you (for example `eur3` in Europe). This cannot be changed later.
3. Start in **production mode**.
4. Open the **Rules** tab, replace the contents with the file `firestore.rules` from this repository, and choose **Publish**.

## 5. Add the config file to the app

1. Go to **Project settings** (the gear icon) **> Your apps**.
2. Download `google-services.json`.
3. Put it in the `app/` folder of this project, next to `build.gradle.kts`.
4. Sync and run the app from Android Studio.

The file is listed in `.gitignore`, so it stays on your computer and is not pushed to GitHub.
Without it the app still builds, and shows a "Firebase is not set up" screen.

## Trying it out

1. On your phone: **I'm a parent**, sign in with Google, create the family.
2. Tap **Add child's phone** to get a code.
3. On your son's phone: **This is my child's phone**, enter his name and the code.
4. He should appear under **Children** on your phone within a few seconds.
5. To add your wife: tap **Invite another parent**. She signs in with Google on her phone and enters the code under **Or join one with an invite code**. Use the switch next to her name to give her full control.
