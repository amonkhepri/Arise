#!/bin/bash
# Quick install script for Rise app
# Usage: ./install.sh

export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
echo "🔨 Building and installing Rise app..."
./gradlew installDebug 2>&1 | tail -20
