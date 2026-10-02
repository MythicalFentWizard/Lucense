# Room generates code reflectively referenced by the runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**

# yt-dlp's Android wrapper reads yt-dlp's JSON into its own classes through
# Jackson, by reflection, and ships no rules of its own.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn java.beans.**
-dontwarn org.apache.commons.compress.**
# It unpacks its bundled Python with Commons Compress, which makes the zip
# extra-field classes by reflection: without these yt-dlp never starts
# ("class ... is not a concrete class").
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
