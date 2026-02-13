#!/bin/bash
set -e

# Ensure we are in the project root
if [ ! -d "android" ]; then
    echo "Error: Please run this script from the project root."
    exit 1
fi

echo "Building and installing Logseq Android App..."

# Build and install
cd android
./gradlew :app:installDebug

# Launch the app
echo "Launching app..."
adb shell monkey -p com.logseq.app -c android.intent.category.LAUNCHER 1

echo "Done! App should be running on your connected device/emulator."
