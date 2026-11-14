# Minify is off for now. These rules are ready for when it is turned on.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keep @kotlinx.serialization.Serializable class com.budget.app.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
