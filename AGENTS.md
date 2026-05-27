# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

Reverse engineering and rebuilding of Sony Sound Connect (v13.0.5) — the companion app for Sony Bluetooth headphones. The goal is to extract the local Bluetooth control protocol from decompiled sources and build a clean-room implementation from scratch.

The Android rebuild lives in `app/` as a Jetpack Compose project with package `dev.ignotus.openbuds`. Git repository is at the project root. See `README.md`, `docs/DEVELOPMENT.md`, `docs/PROTOCOL_GUIDE.md`, and `docs/FEATURE_STATUS.md` before changing implementation details.

## Repository Structure

The project root is the git repository root (formerly `App/`). Subdirectories:

- **`app/`** — Android module (formerly `App/app/`), Jetpack Compose, package `dev.ignotus.openbuds`.
  - `app/src/main/java/dev/ignotus/openbuds/ble/` — BLE, GATT, SPP transport, endpoint diagnostics. Brand clients: `SonyBleClient.kt`, `QcyBleClient.kt`.
  - `app/src/main/java/dev/ignotus/openbuds/protocol/` — GATT UUIDs and Tandem V1/V2 command builders/parsers. QCY: `QcyProtocol.kt`, `QcyGatt.kt`.
  - `app/src/main/java/dev/ignotus/openbuds/data/` — Repository, UI state, model image catalog. QCY mapper: `qcy/QcyResponseMapper.kt`.
  - `app/src/main/java/dev/ignotus/openbuds/headphones/` — Adapters: `SonyTandemHeadphoneAdapter.kt`, `QcyHeadphoneAdapter.kt`. QCY profile: `qcydevices/QcyC30SProfile.kt`.
  - `app/src/main/java/dev/ignotus/openbuds/ui/` — Compose UI.
- **`docs/`** — Development documentation.
  - `DEVELOPMENT.md` — Dev guide: environment, code structure, feature workflow, UI conventions, testing.
  - `PROTOCOL_GUIDE.md` — Protocol implementation: transport layer, Tandem message format, command families, parser design.
  - `FEATURE_STATUS.md` — Feature coverage: implemented, partial, and unimplemented with priorities.
  - `analysis/REAREye_UI实现分析.md` — REAREye UI reference analysis.
  - `plan/` — Protocol reference docs and plans (full BLE protocol analysis, code review, refactor plans).
- **`references/`** — Reference projects (read-only, not buildable).
  - `SonyConnect/` — Decompiled Sony Sound Connect APK (jadx output). Original reverse engineering source.
  - `HyperPods/` — Apple headphone Xposed module (L2CAP/BLE control reference).
  - `OppoPods/` — OPPO headphone Xposed module (HyperOS integration reference).
  - `QCY/` — Decompiled QCY earphone setup APK (jadx output).
    - `QCY_C30S_PROTOCOL.md` — Full protocol reference (BLE GATT UUIDs, TLV frame format, CMDID table).
  - `REAREye/` — Backscreen Xposed module (Compose UI reference).
- **`tools/`** — Agent and development helper tools (e.g., `analyze_btsnoop.py` for BLE HCI log analysis).
- Root Gradle files — `build.gradle.kts`, `settings.gradle.kts` (rootProject.name = "OpenBuds").

## Core Architecture (3 layers)

1. **BLE GATT layer** — `com/sony/songpal/ble/client/` — ServiceUuid.java (26 services), CharacteristicUuid.java (88 characteristics). UUID format: service `5b833eXX-...`, characteristic `5b833cXX-...`.
2. **Tandem Family Protocol** — `com/sony/songpal/tandemfamily/message/mdr/` — Binary message protocol with GET/SET/NTFY semantics. Two versions: V1 (`p063v1`) for older devices, V2 (`p064v2`) for newer. Each has Table1 (HPC channel, main functions) and Table2 (MC channel, extended functions). Message format: `[DataType(1)] [Command(1)] [Payload(N)]`.
3. **Application layer** — `com/sony/songpal/mdr/j2objc/` — Shared Android/iOS business logic (j2objc bridge). `jp/co/sony/hes/autoplay/` — AutoPlay feature. The j2objc layer means protocol features are factored as platform-agnostic Java that gets translated to ObjC.

## Key Reference Files in references/SonyConnect/

| Purpose | Path |
|---------|------|
| GATT service UUIDs | `sources/com/sony/songpal/ble/client/ServiceUuid.java` |
| GATT characteristic UUIDs | `sources/com/sony/songpal/ble/client/CharacteristicUuid.java` |
| V2 Table1 protocol definitions | `sources/com/sony/songpal/tandemfamily/message/mdr/p064v2/table1/` |
| V2 Table2 protocol definitions | `sources/com/sony/songpal/tandemfamily/message/mdr/p064v2/table2/` |
| V1 protocol definitions | `sources/com/sony/songpal/tandemfamily/message/mdr/p063v1/` |
| NC/ASM parameters | `.../p064v2/table1/ncasm/param/` |
| EQ/EBB parameters | `.../p064v2/table1/eqebb/param/` |
| Power/battery parameters | `.../p064v2/table1/power/param/` |
| Playback control parameters | `.../p064v2/table1/playback/param/` |
| Sense/adaptive control parameters | `.../p064v2/table1/sense/param/` |
| SAR/AutoPlay parameters | `.../p064v2/table1/sarautoplay/param/` |
| System parameters (wearing, head gesture) | `.../p064v2/table1/system/param/` |
| Firmware update parameters | `.../p064v2/table1/updt/param/` |
| j2objc feature bridges | `sources/com/sony/songpal/mdr/j2objc/tandem/features/` |
| Model series V1/V2 mapping | `sources/com/sony/songpal/mdr/j2objc/tandem/MdlSeries.java` |
| AutoPlay BLE manager | `sources/jp/co/sony/hes/autoplay/core/ble/` |
| AndroidManifest | `resources/AndroidManifest.xml` (package: `com.sony.songpal.mdr`) |

## Protocol Key Facts

- No application-layer encryption — all Tandem Family messages are plaintext over BLE GATT
- `OnOffSettingValue` is inverted: `0x00=ON`, `0x01=OFF`
- V1 uses MC service for everything; V2 splits HPC (main) and MC (extended)
- Command byte ranges are consistent: `0xX0`=GET_CAPABILITY, `0xX1`=RET_CAPABILITY, `0xX2`=GET_STATUS, `0xX3`=RET_STATUS, `0xX4`=SET_STATUS, `0xX5`=NTFY_STATUS, `0xX6`=GET_PARAM, `0xX7`=RET_PARAM, `0xX8`=SET_PARAM, `0xX9`=NTFY_PARAM
- Connection handshake: discover services → read OPTIMAL_MTU → GATT MTU request → enable DETERMINE_MTU notify → determine MTU → disable MTU notify → read WRITABLE_VALUE_LENGTH → enable FROM_ACC notify → begin protocol communication
- LinkBuds S currently uses the SPP/Tandem path reliably. `LE_` endpoints are LE Audio endpoints and should not be treated as the primary Sony control endpoint.
- EQ custom band index `0` is Clear Bass; the visible frequency bands are `400`, `1k`, `2.5k`, `6.3k`, and `16k`.

## Build and Test

All Gradle commands run from the project root:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Logcat:

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb logcat -v time OpenBuds:I AndroidRuntime:E '*:S'
```

## Working with Decompiled Sources

Decompiled sources are in `references/SonyConnect/sources/` (read-only reference).

- All class names are obfuscated short strings (e.g., `a/`, `C8782a.java`). Package structure is preserved.
- Use Grep to search for symbols, enum values, or bytecodes rather than class names
- The j2objc bridge is the best place to understand how a feature works end-to-end (it connects protocol layer to application logic)
- `param/` subdirectories contain the enum/constant definitions for each protocol feature
- JSON assets in `resources/assets/` and `resources/res/raw/` contain Lottie animations, service configurations, and region maps

## Working with QCY Reference

QCY decompiled sources are in `references/QCY/outshell/sources/` (read-only). Key files:

| Purpose | Path |
|---------|------|
| Protocol reference (framing, CMDIDs) | `references/QCY/QCY_C30S_PROTOCOL.md` |
| BLE client (UUIDs, connection flow) | `outshell/sources/com/qcymall/qcylibrary/QCYHeadsetClient.java` |
| Frame serialize/deserialize | `outshell/sources/com/qcymall/qcylibrary/dataBean/DataAnalyse.java` |
| CMDID constants | `outshell/sources/com/qcymall/qcylibrary/dataBean/DataBean.java` |
| Complete UUID map (V1+V2) | `outshell/sources/com/qcymall/earphonesetup/model/ControlerPanl.java` |

### QCY Protocol Quick Facts

- **Frame format**: `[0xFF] [PayloadLen] [CMD_ID:1] [DATA_LEN:1] [DATA:N]...` (multi-TLV per frame)
- **Read request**: `FF 03 FE 01 <TARGET_CMD>`
- **Strict length**: parser requires `raw.size == (byte[1] & 0xFF) + 2` (exact match; frame truncated → invalid)
- **Battery**: bit 7 = charging flag, low 7 bits = level (0–100) on 0x0008 notify
- **ANC**: CMD 12 (mode: 0=off/1=ANC/2=outdoor/3=transparency) + CMD 7 (value: 0–255)
- **EQ**: CMD 32 (6B/band, old) or CMD 34 (7B/band, new) on 0x1001 → response on 0x1002
- **Service UUID**: `0000A001-0000-1000-8000-00805F9B34FB`

### Transport Layer Abstraction

OpenBuds now supports multi-brand transport through `HeadphoneTransportClient`. See:

- `ble/HeadphoneTransportClient.kt` — interface
- `ble/HeadphoneTransportSelector.kt` — client selection and scan dedup
- `docs/BRAND_INTEGRATION_GUIDE.md` — how to add a new brand
