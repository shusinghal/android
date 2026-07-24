plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.memorycurator.video_processor"
    compileSdk = 35

    defaultConfig {
        minSdk = 31
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-transformer:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    
    // For AI - LiteRT (formerly TensorFlow Lite) - 16 KB page size compatible
    implementation("com.google.ai.edge.litert:litert:2.1.6")
    implementation("com.google.ai.edge.litert:litert-api:2.1.6")
    implementation("com.google.ai.edge.litert:litert-support:1.4.2")
    implementation("com.google.ai.edge.litert:litert-gpu:1.4.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
