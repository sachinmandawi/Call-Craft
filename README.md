# CallCraft 📱✨

[![Release](https://img.shields.io/github/v/release/sachinmandawi/Call-Craft?color=6750A4&label=Latest%20Release)](https://github.com/sachinmandawi/Call-Craft/releases/tag/v1.0.0)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/sachinmandawi/Call-Craft)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Theme](https://img.shields.io/badge/Design-Material%20You%20(Lilac)-6750A4)](https://m3.material.io)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

**CallCraft** (`com.callcraft.studio`) is an ultra-modern, privacy-first Android Call Log Editor and Manager built with **Kotlin**, **Jetpack Compose**, and the **Material You (Dynamic Lilac)** design language.

It also features a built-in **React + Vite + Tailwind** live web simulator in `preview/` for instant browser testing.

---

## 📸 Screenshots Showcase (01 to 13)

<div align="center">

| 01. Welcome Screen | 02. Organize Call Logs | 03. Missed Calls Tab | 04. Android XML Backup |
| :---: | :---: | :---: | :---: |
| <img src="playstore_market_assets/android_1_1.jpg" width="220" alt="01. Welcome to CallCraft" /> | <img src="playstore_market_assets/android_1_2.jpg" width="220" alt="02. Organize & Edit Call Logs" /> | <img src="playstore_market_assets/android_1_3.jpg" width="220" alt="03. Track Every Missed Call" /> | <img src="playstore_market_assets/android_1_4.jpg" width="220" alt="04. Backup & Restore in XML" /> |
| **01. Welcome to CallCraft**<br />*Modern, Private & Smart Studio* | **02. Organize & Edit Logs**<br />*Contact Grouping & Dual-SIM* | **03. Track Missed Calls**<br />*Instant Filters & Highlights* | **04. Backup & Restore XML**<br />*Standard Android XML Format* |

<br />

| 05. Quick Call Entry | 06. Custom Duration & Time | 07. Fake Call Simulator | 08. Schedule Timers |
| :---: | :---: | :---: | :---: |
| <img src="playstore_market_assets/android_1_5.jpg" width="220" alt="05. Quickly Add New Calls" /> | <img src="playstore_market_assets/android_1_6.jpg" width="220" alt="06. Custom Date, Time & Duration" /> | <img src="playstore_market_assets/android_1_7.jpg" width="220" alt="07. Realistic Fake Call Simulator" /> | <img src="playstore_market_assets/android_1_8.jpg" width="220" alt="08. Schedule Incoming Fake Calls" /> |
| **05. Quickly Add Calls**<br />*Contact Picker & SIM Slot* | **06. Precision Date & Time**<br />*Duration Presets (30s, 1m, 5m)* | **07. 4 Brand Themes**<br />*Pixel, Samsung, Xiaomi & iOS* | **08. Schedule Fake Calls**<br />*Custom Timer Delays & Audio* |

<br />

| 09. Incoming Call Screen | 10. Interactive In-Call | 11. Settings & Privacy | 12. Contact History Sheet |
| :---: | :---: | :---: | :---: |
| <img src="playstore_market_assets/android_1_9.jpg" width="220" alt="09. Lifelike Incoming Call Screen" /> | <img src="playstore_market_assets/android_1_10.jpg" width="220" alt="10. Interactive In-Call Screen" /> | <img src="playstore_market_assets/android_1_11.jpg" width="220" alt="11. 100% Private & Open Source" /> | <img src="playstore_market_assets/android_1_12.jpg" width="220" alt="12. Contact Call History" /> |
| **09. Lifelike Incoming UI**<br />*Slide to Answer / Decline* | **10. Active Call Controls**<br />*Live Duration & Dialpad Keypad* | **11. 100% Private & Offline**<br />*Zero Analytics • No Tracking* | **12. Contact Call History**<br />*Detailed Chronological Log* |

<br />

| 13. Multi-Select & Batch Actions |
| :---: |
| <img src="playstore_market_assets/android_1_13.jpg" width="220" alt="13. Multi-Select & Batch Actions" /> |
| **13. Multi-Select & Batch Mode**<br />*Bulk Edit Types, Switch SIM & Mass Delete* |

</div>

---

## ⚡ Key Features

- **Google Phone Standard Grouping**:
  - Automatically aggregates call history by contact name or clean normalized phone number.
  - **Consecutive Missed Calls Badge**: Alert counter `(X)` appears strictly when 2+ consecutive missed calls occur; answered calls display clean contact titles with zero clutter.
- **Contact Call History Bottom Sheet**:
  - Tap any contact card to view full chronological call history with individual duration, timestamp, SIM slot, and 1-tap edit/delete actions.
- **Hardware-Aware Dual SIM Tracking (SIM 1 vs SIM 2)**:
  - Real-time SIM slot detection via Android `SubscriptionManager`.
  - Empty slots are disabled and styled as `SIM X (No SIM)`.
- **Android Default Standard XML Backup & Restore**:
  - Generates standard Android CallLog XML files (`calls_backup_YYYY-MM-DD.xml`) compatible with tools like *SMS Backup & Restore*.
  - Full restore support with JSON fallback for complete data portability.
- **Multi-Select & Bulk Operations**:
  - Long-press to activate multi-select mode.
  - Bulk convert call types (Incoming, Outgoing, Missed, Rejected).
  - Bulk change SIM assignment across selected entries.
  - Complete contact group batch deletion with zero ghost records.
- **Realistic Fake Call Simulator**:
  - 4 authentic brand caller themes (Google Pixel, Samsung One UI, Xiaomi HyperOS, Apple iPhone).
  - Configurable countdown timer (Now 3s, 10s, 30s, 1m, 5m).
  - Working ringtone loop, vibration patterns, and interactive in-call controls.
- **100% Private & Offline**:
  - No internet permissions required for call management.
  - Zero analytics, zero telemetry, and zero ads.

---

## 📂 Project Structure

```
Call Log editor/
├── app/                                 # Native Android Application (Jetpack Compose)
│   ├── src/main/java/com/callcraft/studio/
│   │   ├── MainActivity.kt              # Compose UI, Navigation & Action Sheets
│   │   ├── data/
│   │   │   ├── CallLogItem.kt           # Models & Contact Grouping Structures
│   │   │   └── CallLogRepository.kt     # 3-Tier CallLog ContentResolver & XML Engine
│   │   └── service/
│   │       └── FakeCallHelper.kt        # Audio & Vibration Trigger System
│   └── build.gradle.kts
├── preview/                             # Live Web Simulator (React + Vite + Tailwind)
│   ├── src/
│   │   ├── App.tsx                      # Interactive Pixel Device Simulator
│   │   └── main.tsx
│   └── package.json
└── playstore_market_assets/             # High-Resolution Play Store Marketing Graphics
    ├── android_1_1.jpg ... android_1_13.jpg
```

---

## 🛠️ Building & Running

### Android Native App
Requires Android Studio / JDK 17+.

```bash
# Compile and build Debug APK
./gradlew assembleDebug

# Output APK location
app/build/outputs/apk/debug/CallCraft-v1.0.0.apk
```

### Direct APK Download
You can download the pre-built APK directly from our releases:
- [📥 Download CallCraft-v1.0.0.apk](https://github.com/sachinmandawi/Call-Craft/releases/download/v1.0.0/CallCraft-v1.0.0.apk)

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
Open source under the [MIT License](LICENSE). Built with ❤️ by **Sachin Mandawi**.
