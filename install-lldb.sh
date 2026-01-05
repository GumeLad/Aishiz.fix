#!/bin/bash
# Script to install LLDB for Android debugging

set -e

echo "Installing LLDB for Android development..."

# Check if ANDROID_HOME is set
if [ -z "$ANDROID_HOME" ]; then
    echo "Error: ANDROID_HOME environment variable is not set."
    echo "Please set ANDROID_HOME to your Android SDK location."
    exit 1
fi

# Install LLDB using sdkmanager
echo "Installing LLDB via Android SDK Manager..."
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "lldb;3.1" --sdk_root="$ANDROID_HOME"

# Verify installation
if [ -d "$ANDROID_HOME/lldb/3.1" ]; then
    echo "✓ LLDB 3.1 installed successfully at: $ANDROID_HOME/lldb/3.1"
else
    echo "Warning: LLDB installation directory not found at expected location."
fi

echo ""
echo "LLDB installation complete!"
echo "You can now debug native C++ code in Android Studio."
