# Firebase setup

The app uses Firebase for sign-in and for syncing data between the parent and child phones.
This is a one-time setup and takes about ten minutes. The free Spark plan is enough for now.

## 1. Create the project

1. Open <https://console.firebase.google.com> and sign in with your Google account.
2. Choose **Create a project** (or **Add project**), give it a name such as `parental-control`, and finish the wizard. Google Analytics is not needed.

## 2. Add the Android app

1. On the project overview, choose the Android icon (**Add app**).
2. Android package name: `com.bane23.parentalcontrol`
3. Debug signing certificate SHA-1: `8A:F9:1F:3E:1A:EE:6E:E6:94:D4:E1:DC:15:18:F6:6A:E2:C8:1E:1C`
   This is the fingerprint of the debug key in `keystore/debug.keystore`, which signs debug builds on any computer. The app installed from Google Play is signed with other keys; [play-release.md](play-release.md) step 6 adds their fingerprints.
4. Register the app. Do not download `google-services.json` yet, because step 3 adds information to it.

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
3. Put it in the `app/` folder of this project, next to `build.gradle.kts`, and commit it.

The file is kept in the repository, so local builds and GitHub releases all use the same
Firebase project. It identifies the project but grants no access by itself. Access is
controlled by the sign-in settings and by `firestore.rules`, so keep those rules published.

## Trying it out

1. On your phone: **I'm a parent**, sign in with Google, create the family.
2. Tap **Add child's phone** to get a code.
3. On your son's phone: **This is my child's phone**, enter his name and the code.
4. He should appear under **Children** on your phone within a few seconds.
5. To add your wife: tap **Invite another parent**. She signs in with Google on her phone and enters the code under **Or join one with an invite code**. Use the switch next to her name to give her full control.
