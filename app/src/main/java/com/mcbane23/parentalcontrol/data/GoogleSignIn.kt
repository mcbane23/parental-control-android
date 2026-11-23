package com.mcbane23.parentalcontrol.data

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
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
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(activity.getString(resId))
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL,
        ) { "Unexpected sign-in response." }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return GoogleAuthProvider.getCredential(idToken, null)
    }
}
