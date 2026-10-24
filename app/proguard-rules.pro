-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.crypto.generators.Argon2BytesGenerator { *; }
-keep class org.bouncycastle.crypto.params.Argon2Parameters** { *; }

# nbvcxz loads dictionaries and resource bundles by name at runtime.
-dontwarn me.gosimple.nbvcxz.**
-keep class me.gosimple.nbvcxz.** { *; }

# Strip any debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
