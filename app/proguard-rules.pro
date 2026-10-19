# kotlinx.serialization (backup format)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.khodiyar.dairybilling.**$$serializer { *; }
-keepclassmembers class com.khodiyar.dairybilling.** {
    *** Companion;
}
-keepclasseswithmembers class com.khodiyar.dairybilling.** {
    kotlinx.serialization.KSerializer serializer(...);
}
