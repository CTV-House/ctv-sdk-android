# Keep kotlinx.serialization generated serializers for OpenRTB models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ctvhouse.ctvads.openrtb.** {
    *** Companion;
}
-keepclasseswithmembers class com.ctvhouse.ctvads.openrtb.** {
    kotlinx.serialization.KSerializer serializer(...);
}
