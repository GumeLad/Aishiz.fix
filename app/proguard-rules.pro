# Aishiz release rules.
# JNI resolves NativeLlamaBridge and callback methods by their exact class/method names.
-keep class com.example.aishiz.NativeLlamaBridge { *; }
-keep class com.example.aishiz.NativeLlamaBridge$TokenCallback { *; }

# Retain useful source/line metadata in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
