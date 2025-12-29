# Remote ADB

A stunning, polished Android app for remote ADB access over the internet using secure tunneling.

## ✨ Features

- **🌐 Remote Access**: Access your Android device from anywhere via a secure URL
- **🔒 Secure Tunneling**: Choose between Cloudflare (free!) or Ngrok
- **🎨 Gold Premium Design**: Beautiful Material 3 UI with gold accent theme
- **⚡ Easy Setup**: Cloudflare requires no account - just connect!
- **🚀 One-Tap Connect**: Start/stop the tunnel with a single tap
- **📋 Copy URL**: Easily copy the tunnel URL to clipboard
- **🔄 Auto-start**: Option to automatically start tunnel on device boot
- **📱 Root Required**: Uses root access for reliable ADB TCP mode

## 📋 Requirements

- Android 7.0+ (API 24+)
- Rooted device (Magisk, KernelSU, etc.)

## 🚀 Setup

### Option 1: Cloudflare (Recommended - 100% FREE)

1. **Install the App**
   - Download the APK from [Releases](../../releases) or build it yourself
   - Grant root access when prompted

2. **Download Cloudflared**
   - The app will prompt you to download the cloudflared binary
   - This is a one-time ~25MB download

3. **Connect**
   - Tap "Connect" on the home screen
   - Wait for the tunnel URL to appear
   - Use the URL on any computer:
     ```bash
     adb connect your-tunnel-url.trycloudflare.com:port
     ```

### Option 2: Ngrok (Requires Account)

1. **Get Ngrok Token**
   - Sign up at [ngrok.com](https://ngrok.com)
   - Copy your auth token from the dashboard
   - Note: TCP tunnels may require a paid plan

2. **Configure**
   - Select Ngrok during onboarding
   - Paste your auth token

3. **Connect**
   - Tap "Connect" and use the provided URL

## 🎨 Design

- **Material 3** design language
- **Dark theme** with gold accents
- **Smooth animations** throughout
- **Glowing cards** for active states
- **Polished onboarding** experience

## 🛠️ Building

### Local Build
```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions
This repo includes a GitHub Actions workflow that automatically builds APKs on push. Download artifacts from the Actions tab.

## 📄 License

MIT License - Open Source

## 🙏 Credits

- Built with Kotlin + Jetpack Compose
- Tunneling powered by [Cloudflare](https://cloudflare.com) and [Ngrok](https://ngrok.com)
