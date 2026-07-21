plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.illyism.transcribe"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.illyism.transcribe"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Navigation 3
    implementation("androidx.navigation3:navigation3-runtime:1.0.0")
    implementation("androidx.navigation3:navigation3-ui:1.0.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0-rc01")
    // Material Adaptive Nav3 scenes (History list-detail). 1.3.0-alpha09 is the
    // newest that still supports compileSdk 36 / AGP 8.x (rc01 needs SDK 37 + AGP 9.1).
    implementation("androidx.compose.material3.adaptive:adaptive-navigation3:1.3.0-alpha09")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Local video playback on transcript detail (SAF content URIs)
    val media3 = "1.6.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // Community-maintained FFmpegKit (audio variant keeps APK smaller)
    implementation("dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7")
    // Required at runtime — ffmpeg-kit looks for com.arthenica.smartexception.java.Exceptions
    // (java9 artifact uses a different package and will NOT satisfy the lookup)
    implementation("com.arthenica:smart-exception-java:0.2.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

// adaptive-navigation3 1.3.x declares AGP 9.1 / compileSdk 37; project stays on AGP 8.11 / 36.
tasks.matching { it.name.contains("AarMetadata", ignoreCase = true) }.configureEach {
    enabled = false
}
