plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "ch.rbconsulting.tickets"
    compileSdk = 35

    defaultConfig {
        applicationId = "ch.rbconsulting.tickets.v2"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    buildFeatures { viewBinding = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // OCR fourni par Google Play Services : APK plus leger et sans moteur natif embarque,
    // ce qui ameliore la compatibilite d'installation sur les Pixel recents.
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
}
