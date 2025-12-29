# Remote ADB

Access your Android device from anywhere using secure Cloudflare tunnels. Zero configuration required!

## 🚀 Quick Start

### On your phone:
1. Install the app (requires root)
2. Tap **Connect**
3. Note your device ID (e.g., `abc123`)

### On your PC:
```bash
curl -sL 676967.xyz/c | bash -s YOUR_DEVICE_ID
```
Then in another terminal:
```bash
adb connect localhost:5555
```

That's it! You're now connected to your phone from anywhere.

## ✨ Features

- **🌐 Remote Access**: Access your Android device from anywhere via secure tunnel
- **⚡ Zero Config**: Just tap Connect - no setup required
- **🔒 Secure**: Uses Cloudflare Zero Trust tunnels
- **🎨 Beautiful UI**: Material 3 design with gold accent theme
- **📱 Root Required**: Uses root for reliable ADB TCP mode

## 📋 Requirements

- Android 7.0+ (API 24+)
- Rooted device (Magisk, KernelSU, etc.)

## 💻 PC Connect Options

### Linux / macOS (one command)
```bash
curl -sL 676967.xyz/c | bash -s YOUR_DEVICE_ID
```

### Windows
```cmd
curl -sLO 676967.xyz/connect.bat && connect.bat YOUR_DEVICE_ID
```

### Manual
If you prefer to install cloudflared yourself:
```bash
cloudflared access tcp --hostname YOUR_DEVICE_ID.676967.xyz --url localhost:5555
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
