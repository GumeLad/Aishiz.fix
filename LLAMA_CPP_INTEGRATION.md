# Llama.cpp Integration Guide

This document describes how llama.cpp is integrated into the Aishiz Android application.

## Overview

The Aishiz app uses llama.cpp as a Git submodule to provide on-device LLM inference capabilities. The integration includes:

- **Native C++ layer**: JNI bindings in `app/src/main/cpp/native-lib.cpp`
- **Kotlin bridge**: `NativeLlamaBridge.kt` for Java/Kotlin interaction
- **Build system**: CMake configuration in `app/src/main/cpp/CMakeLists.txt`

## Repository Structure

```
app/src/main/cpp/
├── CMakeLists.txt          # CMake build configuration
├── native-lib.cpp          # JNI bindings to llama.cpp
└── llama.cpp/              # Git submodule (ggerganov/llama.cpp)
```

## Setup Instructions

### 1. Clone with Submodules

When cloning the repository, use the `--recurse-submodules` flag to automatically initialize the llama.cpp submodule:

```bash
git clone --recurse-submodules https://github.com/GumeLad/Aishiz.fix.git
```

### 2. Initialize Submodule (if already cloned)

If you've already cloned the repository without submodules, initialize them:

```bash
cd Aishiz.fix
git submodule init
git submodule update --recursive
```

### 3. Build the Project

Build using Gradle:

```bash
./gradlew assembleDebug
```

## Build Requirements

- **NDK**: Version 26.1.10909125
- **CMake**: 3.22.1 or higher
- **Gradle**: 8.0 or higher
- **Target ABIs**: arm64-v8a, x86_64

## Native Integration Details

### CMakeLists.txt Configuration

The CMakeLists.txt file:
1. Configures GGML build options for Android (ARM/x86 architecture detection)
2. Disables KleidiAI and OpenMP for compatibility
3. Adds llama.cpp as a subdirectory
4. Links the native library with llama and common libraries

Key settings:
```cmake
set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/llama.cpp)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "Build common library" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "Disable CURL" FORCE)
add_subdirectory(${LLAMA_SRC} build-llama)
```

### JNI Bridge (native-lib.cpp)

The native bridge provides:
- Model loading and management
- Text generation with streaming
- Configurable sampling parameters
- Thread-safe request handling
- Generation cancellation support

### Kotlin Interface (NativeLlamaBridge.kt)

The Kotlin bridge exposes two main functions:
```kotlin
external fun startGeneration(
    modelPath: String,
    prompt: String,
    temperature: Float,
    topP: Float,
    topK: Int,
    repeatPenalty: Float,
    maxTokens: Int,
    seed: Int,
    callback: TokenCallback
): Long

external fun stopGeneration(requestId: Long)
```

## Submodule Management

### Update llama.cpp to Latest Version

```bash
cd app/src/main/cpp/llama.cpp
git fetch origin
git checkout main
git pull
cd ../../../..
git add app/src/main/cpp/llama.cpp
git commit -m "Update llama.cpp submodule"
```

### Pin to Specific llama.cpp Version

```bash
cd app/src/main/cpp/llama.cpp
git checkout <commit-hash-or-tag>
cd ../../../..
git add app/src/main/cpp/llama.cpp
git commit -m "Pin llama.cpp to version <version>"
```

### Current Version

The project currently uses llama.cpp commit: `92ac1e016b4327bb58f62a098cd6bc484d9d6cbf`

## Build Output

A successful build produces:
- **APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Native Libraries**: 
  - `libaishiz_native.so` (JNI bridge)
  - `libllama.so` (llama.cpp core)
  - `libggml.so`, `libggml-base.so`, `libggml-cpu.so` (GGML backend)

Libraries are built for both:
- `arm64-v8a` (64-bit ARM devices)
- `x86_64` (x86 emulators and tablets)

## Troubleshooting

### Submodule Not Initialized

**Error**: CMake can't find llama.cpp files

**Solution**: Initialize the submodule:
```bash
git submodule init
git submodule update
```

### Build Fails with NDK Errors

**Error**: NDK version mismatch

**Solution**: Install the exact NDK version:
```bash
sdkmanager --install "ndk;26.1.10909125"
```

### CMake Configuration Fails

**Error**: CMake version too old

**Solution**: Install CMake 3.22.1 or higher:
```bash
sdkmanager --install "cmake;3.22.1"
```

## Testing the Integration

After building, you can verify the integration by:

1. Installing the APK on a device or emulator
2. Loading a GGUF model file
3. Starting a chat conversation
4. Observing real-time token streaming

The native library will log messages to Logcat with tag `Aishiz-Native`.

## References

- [llama.cpp GitHub Repository](https://github.com/ggerganov/llama.cpp)
- [GGUF Model Format](https://github.com/ggerganov/ggml/blob/master/docs/gguf.md)
- [Android NDK Documentation](https://developer.android.com/ndk)
- [CMake Android Integration](https://developer.android.com/ndk/guides/cmake)
