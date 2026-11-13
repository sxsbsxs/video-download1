plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.chaquo.python")
}

android {
    namespace = "io.videodl.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.videodl.android"
        minSdk = 29
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        pip { install("yt-dlp==2026.10.26") }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Stream-copy remuxing only: no optional audio/video encoders.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-min:8.1.9")
}