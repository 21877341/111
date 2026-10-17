plugins {
    id("com.android.application")
}

android {
    namespace = "dev.hyperosfix.desktop"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.hyperosfix.desktop"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    // Modern libxposed API (101/102), provided by LSPosed at runtime.
    compileOnly("io.github.libxposed:api:102.0.0")
}
