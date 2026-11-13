plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "io.videodl.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.videodl.android"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
        ndk { abiFilters += listOf("arm64-v8a") }
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
    implementation("androidx.activity:activity-ktx:1.10.0")
    // Stream-copy remuxing only: no optional audio/video encoders.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-min:8.1.9")
}