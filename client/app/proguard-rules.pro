-keep class com.sharegps.** { *; }
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Tink (EncryptedSharedPreferences 내부 의존)에서 참조하는 애노테이션 전용 클래스들
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**
-dontwarn org.checkerframework.**

# kotlinx.serialization runtime
-dontwarn kotlinx.serialization.**
-keepattributes *Annotation*, InnerClasses

# OkHttp / Ktor 전이 의존 (SLF4J, Guava, Animal Sniffer)
-dontwarn org.slf4j.**
-dontwarn com.google.guava.**
-dontwarn com.google.j2objc.**
-dontwarn java.lang.instrument.**
