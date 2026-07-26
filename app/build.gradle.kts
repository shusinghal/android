plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
}

android {
    namespace = "com.memorycurator.app"
    compileSdk = 35


    defaultConfig {
        applicationId = "com.memorycurator.app"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    // Compose Compiler is now handled by the 'org.jetbrains.kotlin.plugin.compose' plugin

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }

    packaging {
        jniLibs {
            // Ensures compatibility with 16 KB page size devices by extracting native libraries
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":video-processor"))
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.glance:glance-preview:1.1.1")
        // ... other dependencies
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
        // ...
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.paging:paging-runtime:3.3.2")
    implementation("androidx.paging:paging-compose:3.3.2")
    implementation("com.github.Dimezis:BlurView:version-2.0.6")
    implementation("com.google.firebase:protolite-well-known-types:18.0.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-video:2.6.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.compose.material:material-icons-extended")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.exifinterface:exifinterface:1.4.2")

    // ML Kit for on-device AI
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
}
