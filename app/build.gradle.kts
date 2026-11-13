plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ar.trainingwraps.corrector"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.trainingwraps.corrector"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
    }

    // Firma fija: así las próximas versiones se instalan encima sin desinstalar.
    signingConfigs {
        getByName("debug") {
            storeFile = file("firma.jks")
            storePassword = "corrector"
            keyAlias = "corrector"
            keyPassword = "corrector"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
