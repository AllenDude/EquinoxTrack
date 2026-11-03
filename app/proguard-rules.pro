# EquinoxTrack release shrinking rules.

# Backup payloads are serialized with kotlinx.serialization using generated serializers.
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class com.equinoxtrack.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.equinoxtrack.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.equinoxtrack.app.**$$serializer { *; }

# Room entities are referenced reflectively by generated code only through their names.
-keep class com.equinoxtrack.app.data.db.** { *; }

# Glance widget receivers and workers are instantiated by the system.
-keep class com.equinoxtrack.app.widget.** { *; }
-keep class com.equinoxtrack.app.notifications.** { *; }
