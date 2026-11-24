plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}


android {
    namespace = "com.mcbane23.parentalcontrol"
    compileSdk = 35

    defaultConfig {
        // Must match the package name registered in Firebase (app/google-services.json).
        applicationId = "com.bane23.parentalcontrol"
        minSdk = 28
        targetSdk = 35
        versionCode = 6
        versionName = "0.4.0"
    }

    // Debug builds, local or on GitHub, are signed with the debug key kept in the repository.
    // Release builds are signed with the private upload key, which is never committed: the
    // Release workflow passes it in through these environment variables (docs/play-release.md).
    // Without them a release build is left unsigned.
    val uploadKeystore = System.getenv("UPLOAD_KEYSTORE_FILE").orEmpty()
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (uploadKeystore.isNotBlank()) {
            create("upload") {
                // A PKCS12 keystore uses one password for the store and the key.
                val password = System.getenv("UPLOAD_KEYSTORE_PASSWORD").orEmpty()
                storeFile = file(uploadKeystore)
                storePassword = password
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS").orEmpty().ifBlank { "upload" }
                keyPassword = password
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)

    implementation(libs.play.services.location)
    implementation(libs.osmdroid)

    testImplementation(libs.junit)
}
