// build.gradle.kts (Module: app)

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
        // mlModelBinding = false // puoi lasciarlo disattivato
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
    val cameraXVersion = "1.5.1" // ultima stabile 1.4.x [web:44][web:130]
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:1.5.1")

    // --- ML Kit Vision: Object Detection + Text Recognition v2 ---
    // Rilevamento oggetti [web:6][web:32]
    implementation("com.google.mlkit:object-detection:17.0.2")

    // Text Recognition v2 (Latin script) [web:97][web:139][web:136]
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    // implementation("com.google.mlkit:text-recognition-japanese:16.0.1")


    // --- TensorFlow Lite per MiDaS (depth) ---
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")
    // opzionale: delegato GPU se vuoi
    // implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")

    // --- Coroutines (per lavorare bene con thread e ML) ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // --- Network (per traduzione cloud, se la userai) ---
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")

    // --- Test ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
