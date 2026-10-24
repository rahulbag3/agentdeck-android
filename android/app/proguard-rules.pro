# AgentDeck release rules
#
# Room, OkHttp, Compose, Firebase and kotlinx-coroutines all ship consumer
# ProGuard rules. These entries cover only the app's own reflective edges.

# Keep the Room database entry point and generated implementations.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.agentdeck.app.data.local.AgentDeckDatabase_Impl { *; }

# JNI entry points are resolved by Java name; R8 must not rename them.
-keep class com.agentdeck.app.data.LocalEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
# The native layer calls TokenCallback.onToken by name, including anonymous
# implementations created for each generation.
-keep interface com.agentdeck.app.data.LocalEngine$TokenCallback { *; }
-keep class * implements com.agentdeck.app.data.LocalEngine$TokenCallback { *; }

# Keep line numbers so Crashlytics stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
