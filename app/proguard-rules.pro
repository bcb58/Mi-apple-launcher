# Keep the launcher entry points referenced only from the manifest.
-keep class com.bcb58.mialauncher.MainActivity { *; }
-keep class com.bcb58.mialauncher.LauncherApp { *; }
-keep class com.bcb58.mialauncher.ui.AllAppsActivity { *; }
-keep class com.bcb58.mialauncher.ui.SettingsActivity { *; }
-keep class com.bcb58.mialauncher.data.PackageWatcher { *; }

# Custom views are inflated by name from XML.
-keep class com.bcb58.mialauncher.ui.** extends android.view.View { *; }
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Smaller, faster code on low-end Amlogic hardware.
-optimizationpasses 3
-allowaccessmodification
-repackageclasses ''
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}
