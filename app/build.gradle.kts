plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.harivas.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.harivas.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "SITE_URL", "\"https://gkgos.in\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ── 3 separate APKs via product flavors ──────────────────────────────────
    flavorDimensions += "portal"
    productFlavors {
        create("store") {
            dimension = "portal"
            applicationIdSuffix = ".store"
            versionNameSuffix = "-store"
            resValue("string", "app_name", "HARIVAS Store")
            buildConfigField("String", "PORTAL_PATH", "\"/harivas/\"")
        }
        create("rider") {
            dimension = "portal"
            applicationIdSuffix = ".rider"
            versionNameSuffix = "-rider"
            resValue("string", "app_name", "HARIVAS Rider")
            buildConfigField("String", "PORTAL_PATH", "\"/harivas-rider/\"")
        }
        create("vendor") {
            dimension = "portal"
            applicationIdSuffix = ".vendor"
            versionNameSuffix = "-vendor"
            resValue("string", "app_name", "HARIVAS Vendor")
            buildConfigField("String", "PORTAL_PATH", "\"/harivas-vendor/\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.analytics)
}
