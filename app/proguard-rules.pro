# Keep Firebase models
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep Kotlin serializable
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.sanji.bomberpro.**$$serializer { *; }
-keepclassmembers class com.sanji.bomberpro.** { *** Companion; }
-keepclasseswithmembers class com.sanji.bomberpro.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Gson
-keep class com.sanji.bomberpro.data.model.** { *; }

# Compose
-dontwarn androidx.compose.**