# MiLink AirPods State Format

更新日期：2026-05-31

本文记录 OpenBuds 米链第一方适配器 M2 阶段使用的 AirPods 状态字段格式。来源为 HyperOS 3.0 `com.milink.service` 反编译代码，真机调用频率和 UI 渲染验收仍需补测。

## `MxBluetoothManager.getAirPodsState(String)`

返回值是 `String[]`，MiLink 至少读取前 9 个元素。

| Index | 字段 | M2 固定值 | 消费方 |
|---:|---|---|---|
| 0 | `isLeftWearing` | `true` | `AncBatteryController.airpodsBatteryParse()` |
| 1 | `leftBattery` | `75` | `airpodsBatteryParse()` → left |
| 2 | `isRightWearing` | `true` | `airpodsBatteryParse()` |
| 3 | `rightBattery` | `80` | `airpodsBatteryParse()` → right |
| 4 | `boxBattery` | `90` | `airpodsBatteryParse()` → box |
| 5 | `isLeftCharging` | `false` | `airpodsBatteryParse()` → left charging |
| 6 | `isRightCharging` | `false` | `airpodsBatteryParse()` → right charging |
| 7 | `isBoxCharging` | `false` | `airpodsBatteryParse()` → box charging |
| 8 | `deviceId` | `01010101` | `BluetoothServiceClient.getAirpodsDeviceId()` |

`airpodsBatteryParse()` converts battery output to `[box, left, right, isBoxCharging, isLeftCharging, isRightCharging]`. A battery value of `"-"` maps to `-1`.

## `/airpodsstate` Bundle

`ContentResolver.call(content://com.android.bluetooth.ble.app.headsetdata.provider/airpodsstate, "getAirpodsState", mac, null)` returns an 11-field Bundle.

| Bundle key | M2 fixed value | Notes |
|---|---|---|
| `device` | normalized MAC | Query argument fallback if malformed |
| `connectState` | `2` | Connected |
| `isLeftWearing` | `true` | Placeholder until M3 bridge |
| `leftBattery` | `75` | Placeholder until M3 bridge |
| `isRightWearing` | `true` | Placeholder until M3 bridge |
| `rightBattery` | `80` | Placeholder until M3 bridge |
| `boxBattery` | `90` | Placeholder until M3 bridge |
| `isLeftCharging` | `false` | Placeholder until M3 bridge |
| `isRightCharging` | `false` | Placeholder until M3 bridge |
| `isBoxCharging` | `false` | Placeholder until M3 bridge |
| `modelName` | `01010101` | Same value as `deviceId` |

The ContentObserver path reconstructs the 8 battery/wearing fields into the same order used by `airpodsBatteryParse()`. `modelName` is logged by MiLink but is not passed into that parser in the inspected HyperOS 3.0 code.

## DeviceId Choice

`BluetoothServiceClient.getAirpodsHeadsetType(deviceId)` delegates to `p321o9.AbstractC14649a.m51162b(deviceId)`.

Current M2 template:

| deviceId | Source map | Expected type | Reason |
|---|---|---:|---|
| `01010101` | `AbstractC14650b.m51179g()` | `0` | Generic earbud icon; avoids AirPods type 5/6 behavior |
| `01013400` | `AbstractC14650b.m51184l()` | `4` | Reserved open-wear template for future brand mapping |

Known AirPods ids such as `0220`, `0F20`, and `0A20` resolve to type 5 or 6 and are intentionally not used.

## Hook Behavior

The M2 hook is gated by:

```powershell
adb shell setprop debug.openbuds.milink_m1_macs "AA:BB:CC:DD:EE:FF"
adb shell setprop debug.openbuds.milink_m1_intercept true
```

For real AirPods safety, `getAirPodsState(String)` and `ContentResolver.call(...)` call the original method first. If the system returns a valid state array or Bundle, the hook returns the original result. Fixed OpenBuds M2 data is only returned when intercept mode is enabled, the MAC is allowlisted, and the original state is missing.

## M2 True-Device Validation Results (HyperOS 3.0, 小米13 Pro)

- ✅ Control center sticker renders as first-party headset card with placeholder battery (75/80/90).
- ✅ `HeadsetDetailFragment` shows stable left/right/case split battery display.
- ✅ `getAirPodsState(String)` call frequency: ~2-3 s interval, 5-6 calls per burst. Polling driven by milink UI refresh.
- ✅ `ContentResolver.call("getAirpodsState")` — **0 calls observed** during entire session. String[] path is the sole data channel for control center + detail panel.
- ✅ `ContentResolver.query` — only `/device_classify/bluetoothaddress_soundbox` seen; `/airpodsstate` and `/deviceinfo` not queried.
- ✅ Degradation: setting `debug.openbuds.milink_m1_intercept=false` restores `third_headset` path, no crash.
- ✅ Non-target devices (Redmi Buds 6) not affected; genuine AirPods transparently passed through (code path).
- `notifyChange` throttling recommendation: ≤1 Hz (M3 implementation).
