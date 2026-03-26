# Developer Shortcuts 🚀

## Problem Solved
You were tired of typing this repeatedly:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew installDebug 2>&1 | tail -15
```

## Solution: Simple Scripts ✅

I've created convenient scripts that handle the JAVA_HOME setup automatically. You'll find them under the `agent-tools/` directory.

---

## 📦 Available Scripts

### 1. **./install.sh** - Build & Install on Connected Devices
```bash
./install.sh
```
- Builds the debug APK
- Installs on all connected devices/emulators
- Shows last 20 lines of output
- **Most commonly used!**

### 2. **./build.sh** - Build Only (No Install)
```bash
./build.sh
```
- Just builds the debug APK
- Doesn't install anywhere
- Faster if you only need to check compilation

### 3. **./test.sh** - Run Unit Tests
```bash
./test.sh
```
- Runs all unit tests
- Shows test results
- Great for TDD workflow

### 4. **./agent-tools/run-and-log.sh** - Install, Launch & Capture Logs ⭐
```bash
./agent-tools/run-and-log.sh
```
- Builds and installs the app
- Automatically launches the app
- Captures crash logs for 10 seconds
- **Perfect for debugging crashes!**

---

## Usage Examples

### Typical Development Workflow

**1. Make some code changes**
```bash
# Edit files in your IDE
```

**2. Install and test on device**
```bash
./install.sh
```

**3. If you want to run tests first**
```bash
./test.sh && ./install.sh
```

**4. Build without installing**
```bash
./build.sh
```

---

## What These Scripts Do

All scripts automatically:
- ✅ Set `JAVA_HOME` to Android Studio's JDK
- ✅ Run the appropriate Gradle task
- ✅ Show relevant output (last 20-30 lines)
- ✅ Display success/failure status

You never need to type that long export command again! 🎉

---

## Traditional Gradle Commands Still Work

If you prefer the full output, you can still use:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew installDebug
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew connectedAndroidTest
```

But the scripts are much faster to type! 😊

---

## Troubleshooting

### Scripts not executable?
```bash
chmod +x *.sh
```

### Want to see full build output?
Edit the script and remove `| tail -20`

### Need to target a specific device?
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew installDebug
adb -s <device-id> install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## File Locations

- **install.sh** - `/Users/amunratis/AndroidStudioProjects/Arise/install.sh`
- **build.sh** - `/Users/amunratis/AndroidStudioProjects/Arise/build.sh`
- **test.sh** - `/Users/amunratis/AndroidStudioProjects/Arise/test.sh`

All scripts are in the project root directory.

---

## Quick Reference Card

| Task | Command | Time Saved |
|------|---------|------------|
| Build + Install | `./install.sh` | Type 90 chars less! |
| Build Only | `./build.sh` | Type 90 chars less! |
| Run Tests | `./test.sh` | Type 90 chars less! |

**Enjoy your streamlined workflow!** 🚀
