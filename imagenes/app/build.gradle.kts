plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ar.trainingwraps.imagenes"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.trainingwraps.imagenes"
        minSdk = 30
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"
    }

    // Firma fija: así las próximas versiones se instalan encima sin desinstalar.
    signingConfigs {
        getByName("debug") {
            storeFile = file("firma.jks")
            storePassword = "imagenes"
            keyAlias = "imagenes"
            keyPassword = "imagenes"
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
