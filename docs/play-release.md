# Releasing through Google Play internal testing

Play Protect blocks installing the APK from a file, because the app uses an accessibility
service. Apps installed from Google Play are not blocked, and the internal testing track
gives up to 100 invited testers the app within minutes, without a store listing review.

After this one-time setup, every version bump on `main` is built, signed and sent to the
internal testing track by the Release workflow, and the phones update through Play.

The steps are in the order that works. Steps 1 to 6 get the app onto the phones. Step 7
makes later uploads automatic. Menu names in Play Console change now and then, so use the
search box at the top of Play Console if one is not where this guide says.

## 1. Create the upload key

The upload key signs what GitHub builds. It must stay private: never commit it.

1. In a terminal, in a folder outside the repository, run:

   ```
   keytool -genkeypair -v -keystore upload.jks -storetype PKCS12 -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```

   `keytool` comes with Android Studio (in its `jbr/bin` folder) if it is not on your path.
   Choose a strong password. Keep the alias `upload`. The name questions can be answered
   freely.
2. Keep `upload.jks` and the password somewhere safe, such as your password manager. If
   they are lost, Google can reset the upload key, but that takes a support request.
3. Turn the file into text for GitHub:
   - Windows PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("upload.jks")) | Set-Clipboard`
   - macOS: `base64 -i upload.jks | pbcopy`
   - Linux: `base64 -w 0 upload.jks`
4. Note the SHA-1 of the key for step 6: `keytool -list -v -keystore upload.jks -alias upload`

## 2. Add the signing secrets to GitHub

In the repository on GitHub: **Settings > Secrets and variables > Actions > New repository secret**.

| Secret | Value |
| --- | --- |
| `UPLOAD_KEYSTORE_BASE64` | the text from step 1.3 |
| `UPLOAD_KEYSTORE_PASSWORD` | the keystore password |

## 3. Build the first signed version

Merge the pull request that raises the version (or raise `versionName` and `versionCode`
in `app/build.gradle.kts` on `main`). The Release workflow publishes a GitHub release with
two files: the APK and `parental-control-<version>.aab`. It warns that nothing was sent to
Google Play, which is expected at this point. Download the `.aab` file.

If the run failed because the secrets were not there yet, add them and start it again from
**Actions > Release > Run workflow**.

## 4. Create the app in Play Console

1. Open <https://play.google.com/console> and choose **Create app**.
2. Name: for example `Parental Control`. Type: **App**. Price: **Free**. Accept the declarations.

The package name `com.bane23.parentalcontrol` is taken from the first upload and can never
be changed afterwards.

## 5. Upload the first version by hand and add the testers

Google only accepts uploads from GitHub after the first one was made in Play Console.

1. Go to **Test and release > Testing > Internal testing** and choose **Create new release**.
2. Accept **Play App Signing** when asked (Google-generated key, the default).
3. Upload the `.aab` file from step 3, give the release a name, then **Next** and **Save and publish**.
4. On the **Testers** tab, create an email list with the Google accounts used on the family
   phones (the account signed in to the Play Store on each phone) and save.
5. Copy the link under **How testers join your test**.

## 6. Let Google sign-in accept the new keys

Play signs the app again with its own key, so Firebase has to know that key, or parents
cannot sign in with Google.

1. In Play Console, open **Test and release > App integrity > Play app signing** (under
   **Settings**) and copy the **SHA-1** of the **App signing key certificate**.
2. In the Firebase console, open **Project settings > Your apps**, choose the Android app
   and **Add fingerprint**. Add that SHA-1.
3. Also add the SHA-1 of the upload key from step 1.4. It covers the APK attached to GitHub
   releases.

`app/google-services.json` does not need to be replaced.

## 7. Let GitHub upload the next versions

1. Open <https://console.cloud.google.com> and pick a project. The Firebase project is fine.
2. Search for **Google Play Android Developer API** and choose **Enable**.
3. Go to **IAM & Admin > Service accounts > Create service account**. Name it for example
   `play-upload`. It needs no roles in Google Cloud, so skip the optional steps.
4. Open the new account, then **Keys > Add key > Create new key > JSON**. A `.json` file downloads.
5. In Play Console, go to **Users and permissions > Invite new users**. Enter the service
   account's email address (it ends in `iam.gserviceaccount.com`). Under **App permissions**
   add this app and tick **Release apps to testing tracks**. Send the invite.
6. On GitHub, add the secret `PLAY_SERVICE_ACCOUNT_JSON` with the whole content of the
   `.json` file, then delete the file from your computer.

The permission can take a few hours to start working.

From now on each version bump appears in **Internal testing** as a draft release. Open it
and choose **Save and publish** to send it to the phones. Once the app is past its draft
state in Play Console, uploads can go out without that click: on GitHub, under **Settings >
Secrets and variables > Actions > Variables**, add `PLAY_RELEASE_STATUS` with the value
`completed`.

## 8. Install on the phones

The new signing key means the old app has to be removed once on every phone. That clears
its data, so parents sign in again and child phones are paired again with a new code.

1. Uninstall the current app.
2. Open the link from step 5.5 on the phone, accept the invitation and install from the
   Play Store. The first time, the link can take up to a few hours to work.
3. Turn Play Protect scanning back on.

## 9. Declarations in Play Console

Internal testing works without most of these. All of them are required before a closed
test, an open test or a production release, and Play Console lists them on the dashboard
and under **Policy and programs > App content**.

- **Accessibility API**: declare that the app uses an accessibility service, that it is not
  an accessibility tool, and describe the use: it sees which app is in the foreground to
  enforce the time limits and blocked apps a parent has set. Google asks for a short video
  that shows this, and the app must explain it and ask for consent before sending the
  person to the accessibility settings.
- **Privacy policy**: a public web page. The app handles app usage and, soon, location.
- **Data safety**: which data is collected and shared (account, app activity, location).
- **Target audience**: adults. The app is used by parents to supervise a child's phone and
  is not aimed at children.
- **Permission declarations**: background location will need one when live location is
  released.
- Ads (none), content rating, app category and contact details.

## If an upload from GitHub fails

- **Package not found**: step 5 was not done yet, or the service account has no access to
  this app (step 7.5).
- **Only releases with status draft may be created on draft app**: remove the
  `PLAY_RELEASE_STATUS` variable.
- **Version code has already been used**: raise `versionCode`.
- **Target API level**: Play raises the minimum `targetSdk` every year. Raise `targetSdk`
  and `compileSdk` in `app/build.gradle.kts`.

To send the current version to Play again after fixing the cause, run the workflow by
hand: **Actions > Release > Run workflow** with **Upload this version to Google Play
internal testing** ticked.
