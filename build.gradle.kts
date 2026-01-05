// Top-level build file.
// Keep plugin versions explicit here so Android Studio can reliably detect AGP.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Use version catalog entries instead of hardcoded coordinates
        classpath(libs.gradle)
        classpath(libs.kotlin.gradle.plugin)
    }
}

// Provide Kotlin JVM target to subprojects via the root project's extra properties.
extra["kotlinJvmTarget"] = "11"
