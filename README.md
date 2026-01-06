# Aishiz

Clean Android Studio project baseline for an on-device **LLM chat** app.

## What this build gives you
- Multi-model manager (left drawer) using file picker (SAF URI)
- Per-model inference params (right drawer): temperature, top-p, top-k, repeat penalty, max tokens, context length, seed
- Chat UI with streaming and **Stop** cancellation
- **Integrated llama.cpp engine** for GGUF model inference on-device

## LLM Engine Integration
This app now includes **llama.cpp** as the inference engine, providing:
- Native C++ performance for on-device inference
- Support for GGUF quantized models
- Real-time token streaming
- Configurable sampling parameters (temperature, top-p, top-k, etc.)
- Multi-threaded execution
- ARM64 and x86_64 Android support

## Building the Project
The project uses:
- NDK version 26.1.10909125
- CMake 3.22.1 or higher
- llama.cpp as a Git submodule

To build:
```bash
git clone --recurse-submodules https://github.com/GumeLad/Aishiz.fix.git
cd Aishiz.fix
./gradlew assembleDebug
```

## Using GGUF Models
1. Download a GGUF model (e.g., from Hugging Face)
2. Use the model picker in the app to select your GGUF file
3. Configure inference parameters in the right drawer
4. Start chatting!

The app will load the model on-demand and stream tokens back to the UI in real-time.
