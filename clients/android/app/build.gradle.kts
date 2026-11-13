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
        versionCode = 3
        versionName = "0.3.0"

        // Keep only the ABI used by current Android phones. Chaquopy's Python
        // runtime and native dependencies otherwise duplicate several MB per ABI.
        ndk {
            abiFilters += listOf("arm64-v8a")
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

chaquopy {
    defaultConfig {
        version = "3.13"
        pip {
            install("yt-dlp==2026.10.26")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // VideoDL only remuxes downloaded streams with -c copy. The minimal LGPL
    // package avoids bundling unused audio/video encoder libraries.
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-min:8.1.9")
}
