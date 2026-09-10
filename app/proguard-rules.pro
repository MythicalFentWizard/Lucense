# Room generates code reflectively referenced by the runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Media3 / ExoPlayer
-dontwarn androidx.media3.**
