# Remote ADB

A stunning, polished Android app for remote ADB access over the internet using Ngrok tunneling.

## ✨ Features

- **🌐 Remote Access**: Access your Android device from anywhere via a secure URL
- **🔒 Secure Tunneling**: Uses Ngrok's encrypted tunneling technology
- **🎨 Gold Premium Design**: Beautiful Material 3 UI with gold accent theme
- **⚡ Easy Setup**: Just paste your Ngrok token and connect
- **🚀 One-Tap Connect**: Start/stop the tunnel with a single tap
- **📋 Copy URL**: Easily copy the tunnel URL to clipboard
- **🔄 Auto-start**: Option to automatically start tunnel on device boot
- **📱 Root Required**: Uses root access for reliable ADB TCP mode

## 📋 Requirements

- Android 7.0+ (API 24+)
- Rooted device
- Ngrok account (free tier works!)

## 🚀 Setup

1. **Get Ngrok Token**
   - Sign up at [ngrok.com](https://ngrok.com)
   - Copy your auth token from the dashboard

2. **Install the App**
   - Download and install the APK
   - Grant root access when prompted

3. **Configure**
   - Paste your Ngrok auth token during onboarding
   - Or go to Settings to add it later

4. **Connect**
   - Tap "Connect" on the home screen
   - Wait for the tunnel URL to appear
   - Copy the URL and use it on any computer:
     ```bash
     adb connect tcp://0.tcp.ngrok.io:12345
     ```

## 🎨 Design

- **Material 3** design language
- **Dark theme** with gold accents
- **Smooth animations** throughout
- **Glowing cards** for active states
- **Polished onboarding** experience

## 🛠️ Building

```bash
cd remoteadb
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

## 📄 License

MIT License - Open Source

## 🙏 Credits

- Built with Kotlin + Jetpack Compose
- Tunneling powered by [Ngrok](https://ngrok.com)
