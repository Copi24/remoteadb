# Remote ADB

Access your Android device from anywhere using secure Cloudflare tunnels. Works with **root** (full ADB) or **Shizuku** (no root)!

## 🚀 Quick Start

### On your phone:
1. Install the app
2. Tap **Connect**
3. Note your device ID (e.g., `abc123`)

### On your PC:

**Root mode (full ADB):**
```bash
curl -sL 676967.xyz/c | bash -s YOUR_DEVICE_ID
adb connect localhost:5555
```

**Shizuku mode (no root):**
```bash
curl -sLO 676967.xyz/radb.py
python radb.py YOUR_DEVICE_ID shell
```

## ✨ Features

- **🌐 Remote Access**: Access your Android device from anywhere
- **⚡ Zero Config**: Just tap Connect - no setup required
- **🔒 Secure**: Uses Cloudflare Zero Trust tunnels
- **📱 Two Modes**: 
  - **Root**: Full ADB protocol (adb connect)
  - **Shizuku**: No root needed, shell commands + file transfer

## 📋 Requirements

- Android 7.0+ (API 24+)
- **Root mode**: Rooted device (Magisk, KernelSU, etc.)
- **Shizuku mode**: Shizuku app installed, or Android 11+ with Wireless Debugging

## 🔧 Modes Comparison

| Feature | Root Mode | Shizuku Mode |
|---------|-----------|--------------|
| Shell commands | ✅ | ✅ |
| Install apps | ✅ | ✅ |
| File push/pull | ✅ | ✅ |
| Logcat | ✅ | ✅ |
| Port forwarding | ✅ | ❌ |
| Screen mirror | ✅ | ❌ |
| Survives reboot | ✅ | ⚠️ (need re-enable) |
| Requires | Root | Shizuku or Android 11+ |

## 💻 PC Client Options

### Root Mode

**Linux / macOS:**
```bash
curl -sL 676967.xyz/c | bash -s YOUR_DEVICE_ID
adb connect localhost:5555
```

**Windows:**
```cmd
curl -sLO 676967.xyz/connect.bat && connect.bat YOUR_DEVICE_ID
adb connect localhost:5555
```

### Shizuku Mode

```bash
# Download client
curl -sLO 676967.xyz/radb.py

# Interactive shell
python radb.py DEVICE_ID shell

# Single command
python radb.py DEVICE_ID "pm list packages"

# Push file
python radb.py DEVICE_ID push local.apk /sdcard/app.apk

# Pull file
python radb.py DEVICE_ID pull /sdcard/photo.jpg ./photo.jpg

# Stream logcat
python radb.py DEVICE_ID logcat
```

## 🛠️ Building

### Download
Get the latest APK from [GitHub Releases](https://github.com/Copi24/remoteadb/releases) or the Actions tab.

### Build from source
```bash
./gradlew assembleDebug
```

## 📄 License

MIT License - Open Source

## 🙏 Credits

- Built with Kotlin + Jetpack Compose
- Tunneling powered by [Cloudflare](https://cloudflare.com)
- Non-root access via [Shizuku](https://shizuku.rikka.app/)
