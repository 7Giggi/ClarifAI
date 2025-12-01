# ML Kit - evita rimozione annotazioni runtime
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.mlkit.** { *; }

# TensorFlow Lite - preserva modelli e interprete
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }

# CameraX - preserva use case
-keep class androidx.camera.** { *; }

# Retrofit (Translation API)
-dontwarn okhttp3.**
-keep class retrofit2.** { *; }
