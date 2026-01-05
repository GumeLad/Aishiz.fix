# llama.cpp Integration Guide

This document describes the llama.cpp integration in the Aishiz project for 100% offline LLM inference.

## Overview

Aishiz now includes llama.cpp as a git submodule, enabling on-device LLM inference without any network connectivity. The integration is designed for complete offline operation.

## What Was Added

### 1. llama.cpp Submodule
- **Location**: `app/src/main/cpp/llama.cpp`
- **Source**: https://github.com/ggerganov/llama.cpp.git
- **Purpose**: Provides the core LLM inference engine with GGUF model support

### 2. CMake Build Configuration
The CMake build system has been updated to:
- Build llama.cpp alongside the native library
- Configure optimizations for Android ABIs (arm64-v8a, x86_64)
- Enable KleidiAI optimization on ARM devices
- Enable OpenMP for parallel processing on ARM
- Link the llama and common libraries to our native bridge

### 3. LLDB Support
LLDB (LLVM Debugger) configuration for native C++ debugging:
- NDK version specified: 21.4.7075529
- LLDB integration in build.gradle.kts
- Installation script: `install-lldb.sh`

### 4. Documentation
- Updated README.md with setup instructions
- Added offline operation guarantees
- Documented LLDB installation options

## Supported Architectures

The build is configured for:
- **arm64-v8a**: ARM 64-bit (most modern Android devices)
  - KleidiAI optimization: ON
  - OpenMP: ON
- **x86_64**: Intel/AMD 64-bit (emulators)
  - KleidiAI optimization: OFF
  - OpenMP: OFF

## File Structure

```
app/src/main/cpp/
├── CMakeLists.txt          # Main CMake configuration with llama.cpp integration
├── native-lib.cpp          # JNI bridge (streaming stub, ready for llama.cpp APIs)
└── llama.cpp/              # Git submodule
    ├── CMakeLists.txt      # llama.cpp build configuration
    ├── include/            # Public headers (llama.h, llama-cpp.h)
    ├── src/                # llama.cpp source files
    ├── ggml/               # GGML backend
    └── common/             # Common utilities
```

## Key Configuration Files

### app/build.gradle.kts
- Specifies NDK version
- Configures LLDB integration
- Sets up CMake path
- Defines ABI filters

### app/src/main/cpp/CMakeLists.txt
```cmake
# Adds llama.cpp as subdirectory
set(LLAMA_SRC ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)

# Links llama libraries
target_link_libraries(aishiz_native
    llama
    common
    ...
)
```

## Offline Operation Guarantees

This application maintains 100% offline operation:

1. **No Network Permissions**: AndroidManifest.xml does not request INTERNET permission
2. **No Network Dependencies**: No HTTP client libraries in dependencies
3. **Local Model Loading**: Models loaded from device storage via Android SAF
4. **On-Device Inference**: All processing via llama.cpp runs locally
5. **No Telemetry**: No analytics or crash reporting services

## Setup Instructions

### For Developers

1. **Clone with submodules**:
   ```bash
   git clone --recursive <repo-url>
   ```
   
   Or if already cloned:
   ```bash
   git submodule update --init --recursive
   ```

2. **Install LLDB** (for debugging):
   ```bash
   ./install-lldb.sh
   ```
   Or via Android Studio SDK Manager → SDK Tools → LLDB

3. **Build the project**:
   ```bash
   ./gradlew assembleDebug
   ```

### For End Users

End users do not need any special setup:
- LLDB is not required (development tool only)
- llama.cpp is built into the APK
- Just install the APK and load GGUF models via the app UI

## Next Steps for Full Integration

The current implementation provides a **streaming stub** in `native-lib.cpp`. To complete the integration:

1. **Update native-lib.cpp** to use llama.cpp APIs:
   - Include `llama.h` and `llama-cpp.h`
   - Implement model loading with `llama_load_model_from_file()`
   - Create context with `llama_new_context_with_model()`
   - Implement token generation loop
   - Stream tokens back via JNI callbacks

2. **Handle GGUF Models**:
   - Accept Android content:// URIs
   - Convert to file paths or file descriptors
   - Load GGUF format models

3. **Implement Inference Parameters**:
   - Temperature, top-p, top-k
   - Repeat penalty
   - Context length
   - Random seed

4. **Memory Management**:
   - Proper cleanup of llama contexts
   - Handle low-memory situations
   - Model caching strategies

## Debugging

With LLDB installed, you can:
- Set breakpoints in C++ code
- Inspect native stack traces
- Debug llama.cpp integration
- Profile native performance

Android Studio will automatically use LLDB when debugging native code.

## Performance Considerations

- **ARM64 Optimization**: KleidiAI provides optimized matrix operations
- **OpenMP**: Parallel processing for faster inference on ARM
- **Model Quantization**: Use quantized GGUF models (Q4, Q5, Q8) for better performance
- **Context Size**: Smaller context = faster inference and less memory

## Troubleshooting

### Submodule Not Initialized
```bash
git submodule update --init --recursive
```

### LLDB Not Found
```bash
# Install via script
./install-lldb.sh

# Or via SDK Manager
sdkmanager "lldb;3.1"
```

### Build Errors
- Ensure NDK 21.4.7075529 is installed
- Check that CMake 3.14+ is available
- Verify all submodules are initialized

## License

- Aishiz: See repository LICENSE
- llama.cpp: MIT License (see llama.cpp/LICENSE)
- GGML: MIT License (see llama.cpp/ggml/LICENSE)
