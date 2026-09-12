# CoInterpreter release ProGuard/R8 rules.
# Keep kotlinx.serialization models used for OpenAI Realtime event (de)serialization.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.cointerpreter.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.cointerpreter.app.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
