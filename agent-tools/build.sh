#!/bin/bash
# Build script for Rise app (without installing)
# Usage: ./build.sh

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
echo "🔨 Building Rise app..."
./gradlew assembleDebug 2>&1 | tail -20
