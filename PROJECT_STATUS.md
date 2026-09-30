# JupiterVision — Project Status & Architecture Report

**Version:** 2112.16 VisionEngine  
**Target Build:** `2112.16_VisionEngine.apk` / `Jupitervision_2112.16_II_fixed.apk`  
**Platform:** Android (Jetpack Compose + Single Hardware Canvas Engine)  
**Repository:** `janither768/JupiterVision`  

---

## 1. Executive Summary

**JupiterVision** is a next-generation cybernetic launcher and personal computing interface for Android. Powered by a single-canvas rendering architecture, real-time procedural packing, dynamic palette color engines, modular micro/mini-app overlays, and the Vision Home + Vision Engine ecosystem, JupiterVision reimagines the Android home screen into a cohesive visual matrix.

---

## 2. Current Architecture & Core Subsystems

### A. Single Canvas Tile Grid (`TileGrid.kt`)
- **Continuous Scroll Architecture**: Vision Home is page 1 (viewport height `canvasH`), followed immediately downward by the 4-column Canvas grid. There are no separate activities or swipe gestures; it is one continuous scroll container with zero-latency 60/120 FPS hardware-accelerated translation.
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

### D. Vision Home & Vision Engine (`VisionHomeRenderer.kt`, `VisionEngine.kt`, `VisionProfile.kt`)
- **Deterministic 9-State Machine**: Night, Gym, Work, Home, Settle, Transit, Focus, Learning, Idle evaluated in strict priority order.
- **Silent Inference & Antcipation**: Zero-chatbot, zero-LLM deterministic lifestyle cycle driven by geofencing, usage statistics, time windows, and user profile data.
- **Vision Home Layout**: Edge-to-edge canvas page with fixed top bar, cinematic center visual (radar/ghost echo, tactical map, grids, deep space), contextual app row, and status indicator.
- **First Run Experience**:
  - Full-screen dark canvas statement: "WELCOME TO VISION HOME" with philosophy introduction.
  - Industrial sharp `[ BEGIN ]` button triggering 300ms forward slide into Vision Profile Interview.
  - Secondary `[ Let the engine learn by itself. (7-day silent learning) ]` action.
- **Vision Profile Living Document**:
  - Background `#1A1A1A`, left-aligned header, screen-width uppercase `NAME` input with 1px white bottom border.
  - 2-column rectangular buttons with 0.dp corners, black separator line: `BIRTHDAY`, `VENUES`, `WEEK CALENDAR`.
  - Birthday modal pop-up with 3 vertical scroll wheels (Day, Month, Year) with white highlighted selection.
  - Venues page with desaturated satellite map snippets, venue editor with tactical map and pin dropper, type selector (`[JOB] [HOUSE] [GYM] [OTHER]`), and 7-day leave/arrive schedule pickers. Home is pre-populated.
  - Week Calendar with 7-day rows for `WAKE`, `SLEEP`, and `OTHER` custom routines.
  - `[ SAVE & EXIT ]` triggers the cinematic 2.5s "VISION ENGINE IS WORKING..." radar pulse transition.
- **Always-Accessible Vision Home Long-Press Menu**:
  - `[ VISION PROFILE ]`: slide in from right with bezier curve (0.25, 0.1, 0.25, 1.0)
  - `[ VISION ENGINE STATUS ]`: diagnostic dashboard with active state, learning day, and inferred routines
  - `[ CORRECT ENGINE ]`: state correction dialog ("Is this correct? [YES] [NO]")
  - `[ DISABLE FOR TODAY ]`: silence engine until midnight, reverting to Idle with Ghost Echo

### E. Tile Properties Overlay (`TilePropertiesOverlay.kt`)
- **GPU Transform Engine**: Matrix-driven scaling and translation without layout stutter.
- **Borderless**: Clean floating card with accent color theming.
- **Functions**: Live directional resizing (1×1, 2×1, 1×2, 2×2, 0.5×0.5 micro), app swapping, folder migration, and color palette customization.

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

### Version 2112.16 VisionEngine — Current Release
- **Vision Home & Vision Engine Layout Model**: Implemented continuous vertical scroll architecture with Vision Home as the top child (viewport height) and Tile Grid directly beneath.
- **First Run Experience**: Welcome screen with sharp `[ BEGIN ]` button and silent learning fallback.
- **Vision Profile Interview**: Screen-width NAME input, 2-column industrial button grid, Birthday 3-wheel modal, Venues manager with tactical satellite maps and 7-day schedules, and Week Calendar.
- **Save & Exit Sequence**: "VISION ENGINE IS WORKING..." with 2.5-second Ghost Echo radar pulse transition.
- **Vision Home Long-Press Menu**: Instant access to Vision Profile, Diagnostic Status, Engine Correction, and Disable For Today until midnight.
- **Folder and Focus Fixes**: Quadrant hit-testing for 100% reliable app launches inside open folders, sliding title animations, un-dimming icons, and GPU-matrix focus overlay.
- **Release Deliverables**: Built and verified **`2112.16_VisionEngine.apk`** and updated **`Jupitervision_2112.16_II_fixed.apk`**.

---

## 4. Source Tree Overview

```
/
├── 2112.16_VisionEngine.apk           # Latest release artifact (Vision Engine build)
├── Jupitervision_2112.16_II_fixed.apk  # Compatible fixed release artifact
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
        ├── model/
        │   ├── VisionProfile.kt       # Profile preferences, venues, and calendar repository
        │   ├── TileModel.kt           # Tile data structures
        │   ├── AppInfo.kt             # Application information models
        │   └── ColorEngine.kt         # Palette and wallpaper color rules
        ├── ui/
        │   ├── VisionHomeRenderer.kt  # Edge-to-edge cinematic Vision Home canvas page
        │   ├── vision/
        │   │   └── VisionProfileScreens.kt # Welcome, Profile, Venues, Calendar, Diagnostic
        │   ├── TileGrid.kt            # Single canvas rendering engine & gestures
        │   ├── FocusOverlay.kt        # GPU matrix focus overlay & mini-apps
        │   ├── TilePropertiesOverlay.kt # Properties, live resize & swapping
        │   ├── EdgePanels.kt          # Edge swiping navigation
        │   ├── screens/               # All Apps & Settings drawers
        │   └── theme/                 # Typography, palettes, and Theme
        └── util/
            ├── Packer.kt              # Algorithmic 8-unit spatial packer
            ├── AppLoader.kt           # PackageManager asynchronous query engine
            ├── SystemControls.kt      # Hardware toggles, camera, flashlight, audio
            ├── JupiterNotificationListener.kt # Real-time notification bridge
            ├── JupiterAudioPlayer.kt  # MediaSession & audio track controller
            └── AppHistoryManager.kt   # Launch frequency & recency heuristics
```

---

*Compiled and verified for JupiterVision 2112.16 VisionEngine.*
