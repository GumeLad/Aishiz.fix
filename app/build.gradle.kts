plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.aishiz"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.aishiz"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags.addAll(listOf("-std=c++17"))
            }
        }
        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
    }


externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
    }
}

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug") {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = rootProject.extra["kotlinJvmTarget"] as String
    }
    ndkVersion = "21.4.7075529"
    buildToolsVersion = "36.0.0"
}

/**
 * Compatibility task.
 * Some IDEs / scripts try to run :app:testClasses (a Java plugin task).
 * Android projects don't have it, so we alias it to a sensible Android task.
 */
tasks.register("testClasses") {
    group = "verification"
    description = "Compatibility alias for projects expecting a Java 'testClasses' task."
    dependsOn("assembleDebug")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
