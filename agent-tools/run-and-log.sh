#!/bin/bash

# Set JAVA_HOME
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCREENSHOT_DIR="$SCRIPT_DIR/screenshots"
mkdir -p "$SCREENSHOT_DIR"

# Find adb
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
if [ ! -f "$ADB" ]; then
    echo "❌ adb not found. Please ensure Android SDK is installed."
    exit 1
fi

echo "🔨 Building and installing Rise app..."
"$SCRIPT_DIR"/../gradlew installDebug 2>&1 | tail -20

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Build successful! Starting app and capturing logs..."
    echo ""
    
    # Get first device
    DEVICE=$($ADB devices | grep -v "List" | grep "device$" | head -1 | awk '{print $1}')
    echo "📱 Using device: $DEVICE"
    
    # Clear logcat
    $ADB -s $DEVICE logcat -c
    
    # Start the app
    $ADB -s $DEVICE shell am start -n com.example.rise/.ui.SplashActivity
    
    # Wait a moment for app to start
    sleep 3
    
    # Capture logs, filtering for our app and errors
    echo ""
    echo "📝 Capturing logs..."
    echo "----------------------------------------"
    
    # Capture for 10 seconds
    LOG_FILTER="(BridgeOrchestrator|ConnectorTelemetry|com.example.rise|AndroidRuntime.*FATAL|Error inflating|Caused by)"
    perl -e 'alarm shift; exec @ARGV' 10 $ADB -s $DEVICE logcat -v time 2>&1 | grep -iE "$LOG_FILTER" | head -150

    echo "----------------------------------------"
    echo "📸 Taking screenshot..."
    SCREENSHOT_PATH="$SCREENSHOT_DIR/$(date +"%Y%m%d-%H%M%S")-run.png"
    if $ADB -s $DEVICE exec-out screencap -p > "$SCREENSHOT_PATH"; then
        echo "✅ Screenshot saved to $SCREENSHOT_PATH"
    else
        echo "⚠️  Failed to capture screenshot"
    fi

    echo "✅ Log capture complete"
else
    echo "❌ Build failed"
    exit 1
fi
