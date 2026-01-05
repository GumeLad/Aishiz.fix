#!/bin/bash
# Script to install LLDB for Android debugging

set -e

# Default LLDB version (can be overridden with LLDB_VERSION env var)
LLDB_VERSION="${LLDB_VERSION:-3.1}"

echo "Installing LLDB ${LLDB_VERSION} for Android development..."

# Check if ANDROID_HOME is set
if [ -z "$ANDROID_HOME" ]; then
    echo "Error: ANDROID_HOME environment variable is not set."
    echo "Please set ANDROID_HOME to your Android SDK location."
    exit 1
fi

# Find sdkmanager - try different locations
SDKMANAGER=""
if [ -f "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
    SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
elif [ -f "$ANDROID_HOME/cmdline-tools/tools/bin/sdkmanager" ]; then
    SDKMANAGER="$ANDROID_HOME/cmdline-tools/tools/bin/sdkmanager"
elif [ -f "$ANDROID_HOME/tools/bin/sdkmanager" ]; then
    SDKMANAGER="$ANDROID_HOME/tools/bin/sdkmanager"
else
    echo "Error: sdkmanager not found in ANDROID_HOME."
    echo "Please ensure Android SDK command-line tools are installed."
    exit 1
fi

echo "Found sdkmanager at: $SDKMANAGER"

# Install LLDB using sdkmanager
echo "Installing LLDB via Android SDK Manager..."
"$SDKMANAGER" "lldb;${LLDB_VERSION}" --sdk_root="$ANDROID_HOME"

# Verify installation
if [ -d "$ANDROID_HOME/lldb/${LLDB_VERSION}" ]; then
    echo "✓ LLDB ${LLDB_VERSION} installed successfully at: $ANDROID_HOME/lldb/${LLDB_VERSION}"
else
    echo "Warning: LLDB installation directory not found at expected location."
    echo "LLDB may still be installed. Check Android Studio SDK Manager to verify."
fi

echo ""
echo "LLDB installation complete!"
echo "You can now debug native C++ code in Android Studio."
