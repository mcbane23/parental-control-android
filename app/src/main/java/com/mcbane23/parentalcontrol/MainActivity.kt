package com.mcbane23.parentalcontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mcbane23.parentalcontrol.data.FamilyRepository
import com.mcbane23.parentalcontrol.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                // Firebase only starts when google-services.json was part of the build.
                val firebaseReady = FirebaseApp.getApps(applicationContext).isNotEmpty()
                val repo = if (firebaseReady) {
                    FamilyRepository(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance())
                } else {
                    null
                }
                return AppViewModel(repo) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot(viewModel = viewModel, activity = this)
        }
    }
}
