# JupiterVision — Project Status & Architecture Report

**Version:** 2112.16 II (Fixed)  
**Target Build:** `Jupitervision_2112.16_II_fixed.apk`  
**Platform:** Android (Jetpack Compose + Single Hardware Canvas Engine)  
**Repository:** `janither768/JupiterVision`  

---

## 1. Executive Summary

**JupiterVision** is a next-generation cybernetic launcher and personal computing interface for Android. Powered by a single-canvas rendering architecture, real-time procedural packing, dynamic palette color engines, and modular micro/mini-app overlays, JupiterVision reimagines the Android home screen into a cohesive visual matrix.

---

## 2. Current Architecture & Core Subsystems

### A. Single Canvas Tile Grid (`TileGrid.kt`)
- **Render Engine**: Renders all system tiles, categorized folders, and dynamic third-party applications inside a hardware-accelerated Compose `Canvas` with sub-millisecond draw overhead.
- **Dynamic Content Modes**: Supports `ICON` and `CONTENT` flips with cubic-bezier easing (`(0.4, 0, 0.2, 1)` and `(0.2, 0, 0, 1)`).
- **Wallpaper Per-Tile Engine**:
  - Sampled directly from user wallpaper with normalized UV coordinates.
  - Per-tile spatial offsets and localized scaling factors applied to adjacent tiles (excluding folder tiles).
  - High-performance tint blending with tile accent colors.
- **Ambient Ghost Echo Ping**:
  - Sonar ripple waves that pulse every 5 seconds on visible scrolled square tiles.
  - Non-intrusive ambient feedback providing life to the interface without performance degradation.

### B. Intelligent Folder Engine & Un-Dimming Animation
- **Categorization**: Groups installed applications based on intent categories, package names, and app metadata into 2×2 tile groupings.
- **Folder State Lifecycle**:
  - **Closed (Icon Mode)**: Icons inside are dimmed to 35% opacity; folder title text is clearly displayed with subtitle labels. Multi-page indicators (dots) auto-scroll every 5 seconds when pagination is available.
  - **Open (Content Mode)**: Upon tap, the folder title smoothly slides away to the right, and app icons dynamically un-dim to 100% opacity with visible app name labels.
  - **10-Second Auto-Return**: Inactive open folders automatically return to icon mode with labels sliding back into place from the left.
  - **Direct Touch Launching**: `findFolderAppAt` utilizes generous touch-padding bounds and quadrant detection to reliably launch any installed app in the folder without accidental misses.

### C. Overhauled Focus Mode & Mini-App System (`FocusOverlay.kt`)
- **Zero-Lag GPU Matrix Transform**:
  - Eliminates layout re-measurement by anchoring a fixed-dimension container at screen center and driving smooth scale and translation via GPU `graphicsLayer`.
  - Butter-smooth 60/120 FPS open and dismiss transitions.
- **Borderless Floating Card**:
  - Removed all artificial border strokes for a clean floating look.
  - Refined dimensions (up to 360dp width, ~58% screen height) matching modern ergonomics.
- **Area Gesture Swiping**:
  - Removed bulky on-screen buttons ("Swipe right for mini-app", "Swipe left for options").
  - Seamless horizontal drag detection across the entire body: swipe left to enter the **Light Mini App**, swipe right to return to **App Options & Notifications**.
  - Subtle interactive header pills (`[● ○]` and `[○ ●]`) for instant panel switching and clear orientation.
- **Dedicated Light Mini-Apps**:
  - **Music**: Track playback controls, interactive waveform visualizer, track metadata, queue jump.
  - **Gallery**: Live photo portfolio grid, quick camera trigger, aspect-ratio scaling.
  - **Messages**: Quick composer, preset responsive actions, unread indicators.
  - **Camera**: Mode switching (Photo, Video, Portrait, Macro), instant capture shortcuts.
  - **Settings**: System toggles (Wi-Fi, Bluetooth, Torch, Sound, Display).
  - **Folder**: Direct launcher grid with search and ungroup actions.

### D. Tile Properties Overlay (`TilePropertiesOverlay.kt`)
- **GPU Transform Engine**: Matrix-driven scaling and translation without layout stutter.
- **Borderless**: Clean floating card with accent color theming.
- **Functions**: Live directional resizing (1×1, 2×1, 1×2, 2×2, 0.5×0.5 micro), app swapping, folder migration, and color palette customization.

### E. Vision Home & Vision Engine (`VisionHomeRenderer.kt`, `VisionEngine.kt`)
- **Deterministic 9-State Machine**: Night, Gym, Work, Home, Settle, Transit, Focus, Learning, Idle evaluated in strict priority.
- **Silent Inference**: Zero-chatbot, zero-LLM deterministic lifestyle cycle based on usage stats, time windows, and geofence events.
- **Vision Home Layout**: Edge-to-edge canvas page with fixed top bar, cinematic center visual (radar/ghost echo, map, grids, deep space), contextual app row, and status indicator.

### F. Color Engine (`ColorEngine.kt`)
- **Vibrant Wallpaper Rules**: Color extraction with boosted HSV saturation (75%-100%) and brightness (70%-98%), strictly avoiding muddy/dimmed hues.
- **Default System Palette (When Color Engine is OFF)**:
  - Phone, Settings, Camera, Messages -> Mid Dark Grey (`#2E3038`)
  - Gallery -> Purple (`#8A2BE2`)
  - Music -> Crimson Red (`#E5093A`)
  - Clock -> Light Grey (`#D1D5DB`)
  - Flashlight -> Dark Grey (`#22242A`)

---

## 3. Release History & Changelog

### Phase 2112.16 II (Fixed) — Current Release
- **Fixed Folder App Launch & Un-Dimming**: Corrected animation loop override so folder `targetFlip` is maintained accurately. Icons un-dim and labels slide away smoothly.
- **Direct Quadrant App Launching**: Touching any app within an open folder immediately launches the corresponding application.
- **Focus Overlay Overhaul**:
  - Complete elimination of layout-driven animation lag via GPU `graphicsLayer` matrix scaling.
  - Removed border stroke for clean floating card aesthetic.
  - Replaced bulky button banners with fluid horizontal area swiping and header indicator pills.
  - Overlay footprint made compact and centered.
- **Tile Properties Overlay Overhaul**: Removed border and upgraded to GPU matrix transforms.
- **Vision Home & Vision Engine Subsystems**: Complete 9-state machine with silent anticipation and cinematic Canvas rendering.
- **Artifact Preparation**: Removed outdated APKs and generated clean **`Jupitervision_2112.16_II_fixed.apk`**.

---

## 4. Source Tree Overview

```
/
├── Jupitervision_2112.16_II_fixed.apk  # Latest release artifact
├── PROJECT_STATUS.md                  # This architecture document
├── README.md                          # Project overview and build instructions
├── metadata.json                      # AI Studio platform sync
├── build.gradle.kts                   # Project configuration
├── settings.gradle.kts                # Root project definition
└── app/
    ├── build.gradle.kts               # App module build definition
    └── src/main/java/com/jupiter/vision/
        ├── MainActivity.kt            # Main entry point & system coordinating state
        ├── engine/
        │   └── VisionEngine.kt        # Deterministic 9-state Lifestyle Cycle engine
        ├── ui/
        │   ├── VisionHomeRenderer.kt  # Edge-to-edge cinematic Vision Home canvas page
        │   ├── TileGrid.kt            # Single canvas rendering engine & gestures
        │   ├── FocusOverlay.kt        # GPU matrix focus overlay & mini-apps
        │   ├── TilePropertiesOverlay.kt # Properties, live resize & swapping
        │   ├── EdgePanels.kt          # Edge swiping navigation
        │   ├── screens/               # All Apps & Settings drawers
        │   └── theme/                 # Typography, palettes, and Theme
        ├── model/
        │   ├── TileModel.kt           # Tile data structures
        │   ├── AppInfo.kt             # Application information models
        │   └── ColorEngine.kt         # Palette and wallpaper color rules
        └── util/
            ├── Packer.kt              # Algorithmic 8-unit spatial packer
            ├── AppLoader.kt           # PackageManager asynchronous query engine
            ├── SystemControls.kt      # Hardware toggles, camera, flashlight, audio
            ├── JupiterNotificationListener.kt # Real-time notification bridge
            ├── JupiterAudioPlayer.kt  # MediaSession & audio track controller
            └── AppHistoryManager.kt   # Launch frequency & recency heuristics
```

---

*Compiled and verified for JupiterVision 2112.16 II.*
