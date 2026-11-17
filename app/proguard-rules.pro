# KashVault Android ProGuard rules
-keep class com.google.android.libraries.identity.** { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.api.Scope { *; }
-keep class com.kashvault.UpiInboxListenerService { *; }
-keep class com.kashvault.InboxReminderReceiver { *; }
-keep class com.kashvault.InboxReminderBootReceiver { *; }
-keep class com.kashvault.MainActivity { *; }
-keep class com.kashvault.db.** { *; }
-keep class com.kashvault.domain.** { *; }
-keep class com.kashvault.BuildConfig { *; }
-keep class com.kashvault.shared.BuildConfig { *; }
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
