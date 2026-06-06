plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.glance:glance-preview:1.1.1")
        // ... other dependencies
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
        // ...
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.paging:paging-runtime:3.3.2")
    implementation("androidx.paging:paging-compose:3.3.2")
    implementation("com.github.Dimezis:BlurView:version-2.0.6")
    implementation("com.google.firebase:protolite-well-known-types:18.0.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.compose.material:material-icons-extended")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // AI - Face & Subject Analysis
    implementation("com.google.mlkit:face-detection:16.1.6")

    // AI - Custom Aesthetic & Similarity Models
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4") // Makes handling images for TFLite much easier

    // Background Processing
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation(platform("androidx.compose:compose-bom:2024.04.01"))
    implementation("androidx.compose.ui:ui-graphics")

    // Lifecycle & Architecture
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")

    // Asynchronous Execution
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Async Image Loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // On-Device AI: MediaPipe for Feature Extraction & Similarity
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // TensorFlow Lite: Custom Image Quality Inference with GPU delegation
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")

}