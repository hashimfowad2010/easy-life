# Easy Life R8 rules. Most libraries (Room, Hilt, Firebase, ML Kit, kotlinx.serialization, Navigation) ship
# their own consumer rules; only what they don't cover is here.

# Readable Crashlytics stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# SQLCipher: classes are used from native code (JNI).
-keep class net.zetetic.database.** { *; }
-keep interface net.zetetic.database.** { *; }

# kotlinx.serialization: keep serializers of our @Serializable classes (AI schemas, routes, config, drafts).
-keepclassmembers @kotlinx.serialization.Serializable class com.easylife.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.easylife.app.**$$serializer { *; }

# Type-safe navigation routes are looked up by class.
-keep class com.easylife.app.navigation.** { *; }

# Enums restored by name from the database / preferences.
-keepclassmembers enum com.easylife.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
