# AndroidX Media3 (ExoPlayer & MediaSession)
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Dagger / Hilt
-keep class * extends dagger.hilt.internal.GeneratedComponentManager
-keep class * implements dagger.hilt.internal.GeneratedComponentManager
-dontwarn com.google.errorprone.annotations.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*

# Coil Image Loading
-keep class coil.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
