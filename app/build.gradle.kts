// build.gradle.kts (Module: app)

import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "luigi.tirocinio.clarifai"

    compileSdk = 36

    defaultConfig {
        applicationId = "luigi.tirocinio.clarifai"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // CONFIGURAZIONE API KEY GEMINI
        val localPropertiesFile = rootProject.file("local.properties")
        val properties = Properties()

        if (localPropertiesFile.exists()) {
            properties.load(FileInputStream(localPropertiesFile))
        }

        val geminiApiKey = properties.getProperty("gemini.api.key", "")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")

        buildFeatures{
            buildConfig=true
        }
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

    // Per TensorFlow Lite: non comprimere i modelli .tflite
    packaging {
        resources {
            excludes += "META-INF/**"
        }
    }

    aaptOptions {
        noCompress("tflite")
    }

    // Java/Kotlin
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    // ViewBinding per semplificare la UI
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    // --- Base Android ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // --- CameraX (preview + image analysis) ---
    val cameraXVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")



    // Gemini
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Text Recognition v2 (Latin script)
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // --- TensorFlow Lite per MiDaS (depth) ---
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // --- Network (per traduzione cloud opzionale) ---
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // --- Test ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
