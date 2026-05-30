# OpenBuds

OpenBuds is an Android application project built with Kotlin, Jetpack Compose, and Gradle.
It reverse-engineers headphone companion apps (Sony Sound Connect, QCY) to build
clean-room protocol implementations, and integrates with Xiaomi HyperOS through
LSPosed modules for system-level headphone control.

## Supported Headphones

| Brand | Protocol | Status |
|-------|----------|--------|
| Sony (WF-1000XM5, WH-1000XM4, LinkBuds S, ...) | Tandem V2/V1 BLE GATT | ✅ Core protocol, ✅ ANC, ✅ EQ, ✅ Battery, ✅ Wearing detection |
| QCY (C30S, ...) | GATT-TLV | ✅ Core protocol, ✅ ANC |
| Generic Bluetooth (via MiLink adaptation) | Standard BT + custom GATT | 🔧 LSPosed module planned |

## Project Structure

- `app/` - Android application module (Jetpack Compose).
  - `ble/` - BLE GATT transport clients (Sony, QCY).
  - `protocol/` - Protocol command builders and response parsers.
  - `headphones/` - Device adapters, profiles, EQ engine.
  - `data/` - Repository, state aggregation.
  - `lsposed/` - LSPosed module for HyperOS system integration.
- `docs/` - Development guides, protocol references, feature status.
  - `plan/` - Protocol analysis and implementation plans.
  - `analysis/` - UI reference analysis.
- `references/` - Read-only reference code and analysis.
  - `SonyConnect/` - Sony Sound Connect APK decompiled sources.
  - `mi/` - MiLink Fusion Device Center reverse engineering & adaptation plan.
  - `HyperPods/` / `OppoPods/` - Reference Xposed modules.
  - `REAREye/` - UI reference (Compose, Miuix, haze).
  - `QCY/` - QCY APK decompiled sources.
- `gradle/` - Gradle wrapper and version catalog files.

## Requirements

- JDK 17
- Android SDK with the configured compile SDK installed
- Android Studio or the included Gradle wrapper

## Common Commands

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
.\gradlew.bat testDebugUnitTest assembleDebug :app:compileDebugAndroidTestKotlin
```

## Appearance Settings

The app persists UI appearance settings through DataStore. The Appearance screen
keeps color mode separate from the visual theme style.

## Notes

Local machine settings, build outputs, IDE caches, and signing keys are intentionally excluded from version control.
