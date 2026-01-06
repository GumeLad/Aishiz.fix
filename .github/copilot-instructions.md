# Aishiz - On-Device LLM Chat App

This is an Android application that provides on-device LLM (Large Language Model) inference using llama.cpp. The app features a clean chat UI with streaming responses, multi-model management, and configurable inference parameters.

## Repository Overview

- **Language**: Kotlin (Android), C++ (Native layer via JNI)
- **Build System**: Gradle with Kotlin DSL
- **NDK Version**: 26.1.10909125
- **CMake Version**: 3.22.1 or higher
- **Minimum SDK**: 24
- **Target SDK**: 34
- **Compile SDK**: 36
- **Java Version**: 11
- **Gradle Version**: 8.14.3

## Building the Project

### Prerequisites
1. Android SDK with NDK version 26.1.10909125
2. CMake 3.22.1 or higher
3. JDK 17 (for CI) or JDK 11+ (for local development)
4. Git submodules initialized (llama.cpp)

### Build Commands

**Initialize submodules** (required before first build):
```bash
git submodule init
git submodule update --recursive
```

**Build the app**:
```bash
./gradlew assembleDebug
```

**Build release version**:
```bash
./gradlew assembleRelease
```

**Clean build**:
```bash
./gradlew clean assembleDebug
```

## Testing the Project

### Unit Tests
```bash
./gradlew test
```

### Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

Note: Instrumented tests require a connected Android device or emulator.

### Test Location
- Unit tests: `app/src/test/java/com/example/aishiz/`
- Instrumented tests: `app/src/androidTest/java/com/example/aishiz/`

## Repository Structure

```
.
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/aishiz/     # Kotlin source files
│   │   │   │   ├── MainActivity.kt           # Main activity with chat UI
│   │   │   │   ├── NativeLlamaBridge.kt      # JNI bridge to C++
│   │   │   │   ├── ModelStorage.kt           # Model management
│   │   │   │   ├── ChatAdapter.kt            # Chat RecyclerView adapter
│   │   │   │   ├── ModelAdapter.kt           # Model list adapter
│   │   │   │   ├── AishizPrefs.kt            # SharedPreferences wrapper
│   │   │   │   ├── InferenceParams.kt        # Model inference parameters
│   │   │   │   ├── ChatMessage.kt            # Chat message data class
│   │   │   │   └── ModelInfo.kt              # Model info data class
│   │   │   ├── cpp/                          # Native C++ code
│   │   │   │   ├── CMakeLists.txt            # CMake build configuration
│   │   │   │   ├── native-lib.cpp            # JNI bindings to llama.cpp
│   │   │   │   └── llama.cpp/                # Git submodule (ggerganov/llama.cpp)
│   │   │   ├── res/                          # Android resources (layouts, strings, etc.)
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                             # Unit tests
│   │   └── androidTest/                      # Instrumented tests
│   ├── build.gradle.kts                      # App-level Gradle build file
│   └── proguard-rules.pro                    # ProGuard configuration
├── gradle/
│   └── libs.versions.toml                    # Version catalog for dependencies
├── build.gradle.kts                          # Root-level Gradle build file
├── settings.gradle.kts                       # Gradle settings
├── README.md                                 # Project README
├── LLAMA_CPP_INTEGRATION.md                  # Detailed llama.cpp integration guide
└── .github/
    └── workflows/                            # GitHub Actions CI workflows
```

## Code Standards and Conventions

### Kotlin Code Style
1. **Follow Android Kotlin Style Guide**: Use standard Kotlin conventions for Android development
2. **ViewBinding**: Use ViewBinding for view access (already configured in the project)
3. **Coroutines**: Use Kotlin Coroutines for asynchronous operations (already configured)
4. **Lifecycle-aware components**: Use lifecycle-aware coroutines (`lifecycleScope`) in Activities

### Naming Conventions
- **Package**: `com.example.aishiz`
- **Classes**: PascalCase (e.g., `MainActivity`, `ChatAdapter`)
- **Functions/Variables**: camelCase (e.g., `startGeneration`, `activeNativeRequestId`)
- **Constants**: UPPER_SNAKE_CASE (if used)
- **Layout files**: snake_case (e.g., `activity_main.xml`, `item_chat_message.xml`)

### Android Development Practices
1. **Use AndroidX libraries**: The project uses AndroidX, not legacy support libraries
2. **Minimum SDK considerations**: Target API 24+ (Android 7.0)
3. **Material Design**: Use Material Design components from `com.google.android.material`
4. **Thread safety**: Native calls are thread-safe; use proper synchronization for shared state

### Native (C++) Code Standards
1. **C++ Standard**: C++17 (configured in CMakeLists.txt)
2. **JNI naming**: Follow JNI naming conventions for native functions (e.g., `Java_com_example_aishiz_NativeLlamaBridge_startGeneration`)
3. **Memory management**: Properly manage JNI references and native resources
4. **Logging**: Use Android Log functions (`__android_log_print`) with tag `Aishiz-Native`

## Dependencies

### Dependency Management
- Dependencies are managed using a **version catalog** in `gradle/libs.versions.toml`
- Always update the version catalog when adding or updating dependencies
- Check for security vulnerabilities before adding new dependencies

### Current Major Dependencies
- **Android Gradle Plugin**: 8.9.1
- **Kotlin**: 2.1.0
- **AndroidX Core**: 1.17.0
- **AppCompat**: 1.7.1
- **Material Components**: 1.13.0
- **ConstraintLayout**: 2.2.1
- **Lifecycle**: 2.10.0
- **Coroutines**: 1.10.2
- **llama.cpp**: Git submodule at commit `92ac1e016b4327bb58f62a098cd6bc484d9d6cbf`

### Adding Dependencies
When adding new dependencies:
1. Add version to `[versions]` section in `gradle/libs.versions.toml`
2. Add library definition to `[libraries]` section
3. Reference it in `app/build.gradle.kts` using `implementation(libs.library.name)`

## Native Development (NDK/CMake)

### llama.cpp Integration
- The project uses **llama.cpp as a Git submodule** located at `app/src/main/cpp/llama.cpp`
- Always ensure submodules are initialized before building
- See `LLAMA_CPP_INTEGRATION.md` for detailed integration information

### CMake Configuration
- CMakeLists.txt is located at `app/src/main/cpp/CMakeLists.txt`
- Configures GGML build options for Android ARM/x86 architectures
- Disables KleidiAI and OpenMP for compatibility
- Links with llama and common libraries from llama.cpp

### Supported ABIs
- `arm64-v8a` (64-bit ARM devices)
- `x86_64` (x86 emulators and tablets)

### Updating llama.cpp Submodule
```bash
cd app/src/main/cpp/llama.cpp
git fetch origin
git checkout main  # or specific commit/tag
git pull
cd ../../../..
git add app/src/main/cpp/llama.cpp
git commit -m "Update llama.cpp submodule"
```

## Troubleshooting

### Common Build Issues

**Submodule not initialized**:
```bash
git submodule init
git submodule update --recursive
```

**NDK not found**:
Install the exact NDK version using Android SDK Manager:
```bash
sdkmanager --install "ndk;26.1.10909125"
```

**CMake version issues**:
```bash
sdkmanager --install "cmake;3.22.1"
```

**Maven Central 403 errors**:
This is typically a temporary network issue with Maven Central. Try:
1. Wait a few minutes and retry
2. Clear Gradle cache: `./gradlew clean --refresh-dependencies`
3. Check Maven Central status at https://status.maven.org/

## CI/CD

### GitHub Actions Workflows
- **gradle.yml**: Main CI workflow that builds the project and submits dependency graph
- Runs on push to `master` and pull requests
- Uses JDK 17 and Gradle wrapper
- Automatically initializes Git submodules

### CI Build Command
```bash
./gradlew build
```

## Key Guidelines for Contributors

1. **Keep dependencies up to date**: Regularly update dependencies to their latest stable versions
2. **Fix build errors**: Address any build errors that conflict with changes
3. **Maintain compatibility**: Ensure changes work on minimum SDK 24
4. **Test on multiple ABIs**: Verify native code changes on both arm64-v8a and x86_64
5. **Document native changes**: Update LLAMA_CPP_INTEGRATION.md if modifying native integration
6. **Use existing patterns**: Follow the existing code structure and patterns in the project
7. **Submodule awareness**: Always remember to initialize and update submodules
8. **View binding**: Use view binding for new activities/fragments (don't use findViewById)
9. **Coroutines for async**: Use Kotlin Coroutines for asynchronous operations, not callbacks
10. **Material Design**: Follow Material Design guidelines for UI components

## Additional Resources

- [Android Developers Documentation](https://developer.android.com/)
- [Kotlin for Android Documentation](https://kotlinlang.org/docs/android-overview.html)
- [llama.cpp GitHub Repository](https://github.com/ggerganov/llama.cpp)
- [Android NDK Documentation](https://developer.android.com/ndk)
- [CMake Android Integration](https://developer.android.com/ndk/guides/cmake)
