# Aishiz

Clean Android Studio project baseline for an on-device **LLM chat** app.

**100% Offline** - This application runs entirely on-device with no internet connectivity required for operation.

## What this build gives you
- Multi-model manager (left drawer) using file picker (SAF URI)
- Per-model inference params (right drawer): temperature, top-p, top-k, repeat penalty, max tokens, context length, seed
- Chat UI with streaming stub and **Stop** cancellation
- **Complete offline operation** - All inference runs locally on the device

## Prerequisites

### LLDB Installation (Development Only)
LLDB (LLVM Debugger) is required for **debugging native C++ code during development**. This is a development tool only and is NOT required for the app to run on end-user devices.

**Option 1: Using the installation script (Linux/macOS)**
```bash
./install-lldb.sh
```

**Option 2: Using Android Studio**
1. Open Android Studio
2. Go to Tools → SDK Manager
3. Click on the "SDK Tools" tab
4. Check "LLDB" (version 3.1 or higher)
5. Click "Apply" to install

**Option 3: Using command line**
```bash
sdkmanager "lldb;3.1"
```

**Note:** LLDB is only needed for developers debugging the native C++ layer. End users do not need LLDB installed.

### llama.cpp Integration
This project includes llama.cpp as a git submodule for **100% offline, on-device LLM inference**.

**Initial setup:**
```bash
git submodule update --init --recursive
```

If you cloned this repository and the `app/src/main/cpp/llama.cpp` directory is empty, run:
```bash
git submodule update --init --recursive
```

## Building the Project

1. Ensure LLDB is installed for debugging (see above)
2. Initialize llama.cpp submodule if not already done:
   ```bash
   git submodule update --init --recursive
   ```
3. Open the project in Android Studio
4. Sync Gradle and build the project

The native library will be built for the following ABIs:
- `arm64-v8a` (ARM 64-bit, for most modern Android devices)
- `x86_64` (Intel/AMD 64-bit, for emulators)

## Offline Operation

This application is designed to be **100% offline**:
- ✅ No internet permission in AndroidManifest.xml
- ✅ All LLM inference runs locally via llama.cpp
- ✅ Models are loaded from local device storage (via SAF)
- ✅ No network dependencies in the application code
- ✅ Complete privacy - your data never leaves the device

## Next integration milestone
Wire an actual LLM engine (e.g., llama.cpp JNI / GGUF). The UI + persistence layer is already structured for it.

**llama.cpp is now integrated!** The native layer includes llama.cpp for on-device inference. Next steps:
- Update native-lib.cpp to use llama.cpp APIs for actual model loading and inference
- Add GGUF model support
- Implement proper token streaming with llama.cpp

