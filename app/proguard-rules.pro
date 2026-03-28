# Keep stack traces readable in crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep system-facing components (accessed by OS via manifest)
-keep public class com.vi5hnu.nightshield.NightShieldService { *; }
-keep public class com.vi5hnu.nightshield.NightShieldWidgetProvider { *; }
-keep public class com.vi5hnu.nightshield.NightShieldTileService { *; }
-keep public class com.vi5hnu.nightshield.OverlayAlarmReceiver { *; }
-keep public class com.vi5hnu.nightshield.MainActivity { *; }

# Keep NightShieldManager + models
-keep class com.vi5hnu.nightshield.NightShieldManager { *; }
-keep class com.vi5hnu.nightshield.NightShieldManager$TemperaturePreset { *; }
-keep class com.vi5hnu.nightshield.ScheduleEntry { *; }
-keep class com.vi5hnu.nightshield.ScheduleAction { *; }
-keep class com.vi5hnu.nightshield.AppFilterConfig { *; }
-keep public class com.vi5hnu.nightshield.BootReceiver { *; }
-keep public class com.vi5hnu.nightshield.NightShieldAccessibilityService { *; }

# Keep OverlayHelpers and AlarmHelpers (used by multiple components)
-keep class com.vi5hnu.nightshield.OverlayHelpers { *; }
-keep class com.vi5hnu.nightshield.AlarmHelpers { *; }

# Color picker library
-keep class com.github.skydoves.colorpicker.** { *; }
-dontwarn com.github.skydoves.colorpicker.**

# AdMob / Google Mobile Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Jetpack Compose — keep stable annotations
-keep class androidx.compose.runtime.** { *; }
