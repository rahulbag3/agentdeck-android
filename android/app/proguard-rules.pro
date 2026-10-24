# AgentDeck release rules
#
# Room, OkHttp, Compose, Firebase and kotlinx-coroutines all ship consumer
# ProGuard rules. These entries cover only the app's own reflective edges.

# Keep the Room database entry point and generated implementations.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.agentdeck.app.data.local.AgentDeckDatabase_Impl { *; }

# Keep line numbers so Crashlytics stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
