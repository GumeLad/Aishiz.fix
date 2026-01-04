# Aishiz

Clean Android Studio project baseline for an on-device **LLM chat** app.

## What this build gives you
- Multi-model manager (left drawer) using file picker (SAF URI)
- Per-model inference params (right drawer): temperature, top-p, top-k, repeat penalty, max tokens, context length, seed
- Chat UI with streaming stub and **Stop** cancellation

## Next integration milestone
Wire an actual LLM engine (e.g., llama.cpp JNI / GGUF). The UI + persistence layer is already structured for it.
