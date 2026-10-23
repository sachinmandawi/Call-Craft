# Call Craft 📱⚡

**Call Craft** (`com.callcraft.studio`) is a modern, high-performance Android Call Log Editor built with **Jetpack Compose** and **Material You (Dynamic Lilac)** design language.

It includes an interactive **React + Tailwind Web Simulator** in `preview/` for instant live preview and testing.

---

## ✨ Key Features

- **Google Phone Standard Grouping**:
  - Automatically aggregates call history by contact / clean phone number.
  - **Consecutive Missed Calls Alert**: Red alert badge `(X)` appears **only** when 2+ consecutive missed calls exist; normal answered calls display pure contact names with zero clutter numbers.
- **Contact Call History Sheet**:
  - Tap any card to open a Google Phone style bottom sheet showing complete chronological call history with per-call edit, delete, and `Log Call` actions.
- **Hardware-Aware Dual SIM Support (SIM 1 vs SIM 2)**:
  - Real-time SIM detection via `SubscriptionManager`.
  - Empty slots are disabled and styled as `SIM X (No SIM)` with reduced opacity.
  - Clean labeling: strictly `SIM 1` and `SIM 2` (zero carrier text clutter).
- **Multi-Select & Bulk Operations**:
  - Long-press to enter multi-select mode.
  - Full contact group deletion (deletes all calls in selected contacts without leaving ghost logs).
  - Bulk convert call types (Incoming, Outgoing, Missed, Rejected).
  - Bulk switch SIM card slot.
- **Clean Full-Width Search**:
  - Single-line search bar by name or phone number.
- **Resilient 3-Tier Write Engine**:
  - Safe Android `CallLog.Calls` provider integration with fallback strategies and role handling (`ROLE_DIALER`).

---

## 🛠️ Project Structure

```
Call Log editor/
├── app/                  # Android Native Jetpack Compose App
│   ├── src/main/java/com/callcraft/studio/
│   │   ├── MainActivity.kt        # Jetpack Compose UI & Screens
│   │   └── data/
│   │       ├── CallLogItem.kt     # Models & Grouping Data Structures
│   │       └── CallLogRepository.kt # 3-Tier CallLog ContentResolver Engine
│   └── build.gradle.kts
├── preview/              # Live Web Simulator (React + Vite + Tailwind)
│   ├── src/
│   │   ├── App.tsx       # Live interactive Pixel phone simulator
│   │   └── main.tsx
│   └── package.json
└── build.gradle.kts
```

---

## 🚀 Building & Running

### Android Native App
Requires Android Studio / JDK 17+.

```bash
# Debug Build
./gradlew assembleDebug

# Output APK
app/build/outputs/apk/debug/app-debug.apk
```

### Web Simulator (Preview)
Requires Node.js 18+.

```bash
cd preview
npm install
npm run dev
# Open http://localhost:5173
```

---

## 📄 License
Private project © Sachin Mandawi.
