# http://developer.android.com/guide/developing/tools/proguard.html

# 基础配置
-dontwarn **

# 保留反射调用的目标类（系统内部类）
-keep class com.android.server.display.** { *; }

# 保留序列化相关的属性
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses

# Kotlin 序列化支持
-keepnames class kotlinx.serialization.internal.** { *; }
-keep class kotlinx.serialization.json.** { *; }
