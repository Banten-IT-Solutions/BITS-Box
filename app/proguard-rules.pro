-repackageclasses ''
-allowaccessmodification

-keep class id.bits.box.** { *;}
-keep class id.bits.box.core.** { *;}

# Clean Kotlin
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNull(java.lang.Object);
    static void checkNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void throwUninitializedPropertyAccessException(java.lang.String);
}

# Coroutines dispatcher protection
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepclassmembers class kotlinx.** {
    *** $0;
}

# Gson fields access
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-keep class * implements com.google.gson.TypeAdapter
-keep public class * extends com.google.gson.JsonDeserializer
-keep public class * extends com.google.gson.JsonSerializer

# ini4j
-keep public class org.ini4j.spi.** { <init>(); }

# SnakeYaml
-keep class org.yaml.snakeyaml.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-dontwarn androidx.room.paging.**

# ZXing
-keep class com.google.zxing.** { *; }

# Material About Library
-keep class com.github.daniel-stoneuk.materialaboutlibrary.** { *; }

# EditorKit
-keep class com.blacksquircle.ui.** { *; }

# Kryo
-keep class com.esotericsoftware.kryo.** { *; }

-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

-dontwarn javax.net.ssl.**
-dontwarn android.webkit.WebView
-dontwarn java.beans.**
-dontwarn sun.nio.ch.**
