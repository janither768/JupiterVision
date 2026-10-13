# JupiterVision 2112.18 DoTProXTraceMachine

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg)](https://developer.android.com/jetpack/compose)

JupiterVision is a futuristic Android launcher featuring a hardware-accelerated single-canvas tile grid, intelligent application categorization folders, high-performance GPU matrix focus overlays, procedural wallpaper-per-tile rendering, the DoTPro near-field acoustic reflex engine, and TraceMachine Gen 1 inertial dead-reckoning.

## ✨ Latest Release: 2112.18 DoTProXTraceMachine

Download the latest APK: **[2112.18_DoTProXTraceMachine.apk](2112.18_DoTProXTraceMachine.apk)**

### Key Highlights
- **Module Changer Plate**: Radial dial on left edge sliver; unfurls into a 140° arc (-70° to +70°) with angular selection, soft buttery haptics, and smooth bezier transition between DoTPro and TraceMachine.
- **TraceMachine Gen 1**: Inertial path dead-reckoning tracker using motion sensors (accelerometer, gyroscope, step detector) with swing-axis calibration, per-step gyro yaw integration, and automatic ZUPT stillness anchors.
- **Living Canvas Trail**: Hardware-accelerated Canvas renderer with confidence decay, concentric range rings, and heading vector.
- **Single Neutral Theme**: Pure #1A1A1A neutral background, white typography with Inter font, and zero accent colors.

## 🚀 Building & Running

```bash
# Clone the repository
git clone https://github.com/janither768/JupiterVision.git
cd JupiterVision

# Build the debug APK
gradle assembleDebug
```

Output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

## 📄 Documentation

For full architecture and subsystem breakdown, refer to [PROJECT_STATUS.md](PROJECT_STATUS.md).
