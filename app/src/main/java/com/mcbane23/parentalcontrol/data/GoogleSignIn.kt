package com.mcbane23.parentalcontrol.data

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider

object GoogleSignIn {
    /**
     * Shows the Google account picker and returns a credential Firebase can sign in with.
     *
     * The web client id comes from google-services.json. It is looked up by name because the
     * resource only exists once that file has been added to the app folder.
     */
    suspend fun requestCredential(activity: Activity): AuthCredential {
        val resId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        check(resId != 0) {
            "Google sign-in is not enabled in the Firebase project yet. See docs/firebase-setup.md."
        }
        val serverClientId = activity.getString(resId)
        val manager = CredentialManager.create(activity)

        // The bottom sheet first. It answers "no credentials" when Google has paused it after
        // it was dismissed a few times, or when it cannot match this app's signing key. The
        // full "Sign in with Google" screen has no such pause and shows the real error.
        val credential = try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .build()
            manager.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
                .credential
        } catch (e: NoCredentialException) {
            val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
            manager.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
                .credential
        }
        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
        ) { "Unexpected sign-in response." }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return GoogleAuthProvider.getCredential(idToken, null)
    }
}
