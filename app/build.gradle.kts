plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tradelock.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tradelock.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    // One fixed signing key, so every new build installs as an update over the old one.
    signingConfigs {
        getByName("debug") {
            storeFile = file("tradelock.keystore")
            storePassword = "tradelock"
            keyAlias = "tradelock"
            keyPassword = "tradelock"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
