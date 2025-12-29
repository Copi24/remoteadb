# Remote ADB

A stunning, polished Android app for remote ADB access over the internet using secure tunneling.

## ✨ Features

- **🌐 Remote Access**: Access your Android device from anywhere via a secure URL
- **🔒 Multiple Modes**: Manual (recommended), Cloudflare (managed), Ngrok
- **🎨 Gold Premium Design**: Beautiful Material 3 UI with gold accent theme
- **⚡ Realistic Onboarding**: Clear guidance for TCP ADB + tunnel/VPN options
- **🚀 One-Tap Connect**: Start/stop the tunnel with a single tap
- **📋 Copy URL**: Easily copy the tunnel URL to clipboard
- **🔄 Auto-start**: Option to automatically start tunnel on device boot
- **📱 Root Required**: Uses root access for reliable ADB TCP mode

## 📋 Requirements

- Android 7.0+ (API 24+)
- Rooted device (Magisk, KernelSU, etc.)

## 🚀 Setup

### Option 1: Manual (Recommended)

The app enables **ADB over TCP** (root required). You provide the network path using your preferred tool:
- Same Wi‑Fi: `adb connect <device-ip>:5555`
- VPN: Tailscale / ZeroTier / WireGuard
- SSH reverse tunnel

### Option 2: Cloudflare (Managed, default)

This is the default mode and uses the shared domain **676967.xyz** with a per-device hostname:
`<deviceId>.adb.676967.xyz`

User experience:
- Install app → tap Connect
- Run the shown command in Termux (or later: in-app runner):
  ```bash
  cloudflared tunnel run --token <token>
  ```
- On PC:
  ```bash
  cloudflared access tcp --hostname <deviceId>.adb.676967.xyz --url 127.0.0.1:15555
  adb connect 127.0.0.1:15555
  ```

Admin setup (one-time): provision API at `https://api.676967.xyz/provision` (see `cloudflare-worker/worker.js`).

### Option 3: Ngrok

Ngrok is easy but **TCP often requires a paid plan**; use only if it works for your account.

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
