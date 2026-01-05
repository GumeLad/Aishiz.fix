#!/bin/bash
# Build Verification Script for Aishiz Llama.cpp Integration
# This script verifies that the llama.cpp integration is properly set up and building

set -e

echo "====================================="
echo "Aishiz Build Verification Script"
echo "====================================="
echo ""

# Check if we're in the right directory
if [ ! -f "settings.gradle.kts" ]; then
    echo "❌ Error: Not in project root directory"
    echo "   Please run this script from the Aishiz project root"
    exit 1
fi
echo "✓ Found project root directory"

# Check if llama.cpp submodule exists
if [ ! -d "app/src/main/cpp/llama.cpp" ]; then
    echo "❌ Error: llama.cpp submodule directory not found"
    echo "   Run: git submodule init && git submodule update"
    exit 1
fi
echo "✓ llama.cpp submodule directory exists"

# Check if llama.cpp submodule is initialized
if [ ! -f "app/src/main/cpp/llama.cpp/CMakeLists.txt" ]; then
    echo "❌ Error: llama.cpp submodule not initialized"
    echo "   Run: git submodule init && git submodule update"
    exit 1
fi
echo "✓ llama.cpp submodule is initialized"

# Check key files exist
echo ""
echo "Checking key integration files..."

if [ ! -f "app/src/main/cpp/CMakeLists.txt" ]; then
    echo "❌ Error: CMakeLists.txt not found"
    exit 1
fi
echo "✓ CMakeLists.txt found"

if [ ! -f "app/src/main/cpp/native-lib.cpp" ]; then
    echo "❌ Error: native-lib.cpp not found"
    exit 1
fi
echo "✓ native-lib.cpp found"

if [ ! -f "app/src/main/java/com/example/aishiz/NativeLlamaBridge.kt" ]; then
    echo "❌ Error: NativeLlamaBridge.kt not found"
    exit 1
fi
echo "✓ NativeLlamaBridge.kt found"

# Check .gitmodules configuration
echo ""
echo "Verifying submodule configuration..."

if ! grep -q "llama.cpp" .gitmodules; then
    echo "❌ Error: llama.cpp not configured in .gitmodules"
    exit 1
fi
echo "✓ llama.cpp configured in .gitmodules"

# Build the project
echo ""
echo "Building project (this may take a few minutes)..."
echo "---------------------------------------------------"
./gradlew assembleDebug --console=plain

echo ""
echo "---------------------------------------------------"
echo "Build completed successfully!"
echo ""

# Verify build outputs
echo "Verifying build outputs..."

if [ ! -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo "❌ Error: APK not found"
    exit 1
fi
APK_SIZE=$(du -h "app/build/outputs/apk/debug/app-debug.apk" | cut -f1)
echo "✓ APK created: $APK_SIZE"

# Check for native libraries
echo ""
echo "Checking native libraries..."

NATIVE_LIBS=$(find app/build/intermediates/cxx -name "libllama.so" -o -name "libaishiz_native.so" 2>/dev/null | wc -l)
if [ "$NATIVE_LIBS" -lt 4 ]; then
    echo "❌ Error: Expected native libraries not found"
    echo "   Expected: libllama.so and libaishiz_native.so for arm64-v8a and x86_64"
    exit 1
fi
echo "✓ Native libraries built for both architectures"

# List libraries
echo ""
echo "Built native libraries:"
find app/build/intermediates/cxx -name "*.so" | grep -E "(libllama|libaishiz_native|libggml)" | while read lib; do
    SIZE=$(du -h "$lib" | cut -f1)
    BASENAME=$(basename "$lib")
    ARCH=$(echo "$lib" | grep -oE "(arm64-v8a|x86_64)")
    echo "  - $BASENAME ($ARCH): $SIZE"
done

echo ""
echo "====================================="
echo "✓ All verification checks passed!"
echo "====================================="
echo ""
echo "The llama.cpp integration is properly configured and building successfully."
echo ""
echo "You can now:"
echo "  1. Install the APK: adb install app/build/outputs/apk/debug/app-debug.apk"
echo "  2. Run on device/emulator to test LLM inference"
echo "  3. Check logs: adb logcat -s Aishiz-Native"
echo ""
