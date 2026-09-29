# JupiterVision 2112.16 II

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg)](https://developer.android.com/jetpack/compose)

JupiterVision is a futuristic Android launcher featuring a hardware-accelerated single-canvas tile grid, intelligent application categorization folders, high-performance GPU matrix focus overlays, procedural wallpaper-per-tile rendering, and modular mini-apps.

## ✨ Latest Release: 2112.16 II (Fixed)

Download the latest APK: **[Jupitervision_2112.16_II_fixed.apk](Jupitervision_2112.16_II_fixed.apk)**

### Key Highlights
- **Fluid Folder Lifecycle**: Tap to un-dim icons and slide labels away; touch any app quadrant to launch immediately. Auto-returns after 10 seconds of inactivity.
- **GPU Matrix Focus Mode (Zero Lag)**: 60/120 FPS bezier-animated focus overlay using GPU matrix scaling and translation without layout passes.
- **Borderless Floating Modern UI**: Clean floating geometry with compact dimensions.
- **Area Gesture Swiping**: Swipe left anywhere across the overlay to access dedicated light-mode mini-apps; swipe right to return to app options and notifications.
- **Wallpaper Per-Tile Engine**: Individual tile-sampled wallpaper textures with spatial scaling and accent color tinting (excluding folders).
- **Vibrant Palette Engine**: Wallpaper color extraction maintaining punchy saturation and brightness, with dedicated system app defaults.

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
