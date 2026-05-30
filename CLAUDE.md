# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Reverse engineering and rebuilding of Sony Sound Connect (v13.0.5) — the companion app for Sony Bluetooth headphones. The goal is to extract the local Bluetooth control protocol from decompiled sources and build a clean-room implementation from scratch.

The Android rebuild lives in `app/` as a Jetpack Compose project with package `dev.ignotus.openbuds`. Git repository is at the project root. See `README.md`, `docs/DEVELOPMENT.md`, `docs/PROTOCOL_GUIDE.md`, and `docs/FEATURE_STATUS.md` before changing implementation details. UI/特效改动还需参考 `docs/analysis/REAREye_UI实现分析.md`。协议改动参考 `docs/plan/Sony耳机BLE协议完整分析.md`。

## Repository Structure

项目根目录即为 git 仓库根目录（原 `App/`），包含以下子目录：

- **`app/`** — Android 模块（原 `App/app/`），Jetpack Compose 项目，package `dev.ignotus.openbuds`。
  - `app/src/main/java/dev/ignotus/openbuds/ble/` — BLE客户端、SPP传输、端点诊断。
  - `app/src/main/java/dev/ignotus/openbuds/protocol/` — GATT UUID定义 + Tandem V1/V2命令构造和解析。
  - `app/src/main/java/dev/ignotus/openbuds/data/` — Repository、UI状态聚合、产品图片目录。
  - `app/src/main/java/dev/ignotus/openbuds/headphones/` — 设备adapter/profile/capability抽象。
  - `app/src/main/java/dev/ignotus/openbuds/ui/` — Compose UI（Home/Device/Settings/About四页）。
- **`docs/`** — 开发文档。
  - `DEVELOPMENT.md` — 开发指引：环境、代码结构、新增功能流程、UI约定、测试要求。
  - `PROTOCOL_GUIDE.md` — 协议实现说明：传输层(GATT/SPP)、Tandem V2消息格式、已实现命令族、parser设计原则。
  - `FEATURE_STATUS.md` — 功能覆盖状态：已实现/部分实现/未实现的功能清单和优先级建议。
  - `analysis/` — 分析文档。
    - `REAREye_UI实现分析.md` — REAREye UI参考分析：backdrop拓扑、导航栏模式、毛玻璃实现。
  - `plan/` — 计划和协议参考文档。
    - `Sony耳机BLE协议完整分析.md` — 完整BLE协议参考（必读）。涵盖所有GATT服务/特征、Tandem V1/V2命令族、各功能枚举值、设备型号映射。
    - `App代码Review-2026-05-21.md` — App代码审查报告，对照文档验证实现的正确性，列出已知问题和测试缺口。
    - `PROTOCOL_COMPATIBILITY_REFACTOR_PLAN.md` — 协议兼容性重构计划。
    - `LSPOSED_SYSTEM_INTEGRATION_TASKS.md` — LSPosed 系统集成任务。
    - `播放状态不同步Bug修复计划.md` — 播放状态同步修复计划。
    - `液态玻璃性能优化-2026-05-24.md` — 液态玻璃性能优化记录。
- **`references/`** — 参考项目（只读，不参与构建）。
  - `SonyConnect/` — 索尼官方 Sound Connect APK 逆向代码（jadx output）。原 `Reverse/` 目录内容。
    - `sources/` — 1640+ decompiled Java/Kotlin source packages。类名被 jadx 重命名（如 `C8782a.java`），使用 Grep 搜索。
    - `resources/` — AndroidManifest.xml, layouts, drawables, JSON assets, Lottie animations。
  - `HyperPods/` — 苹果耳机 Xposed 模块，参考其 L2CAP/BLE 控制和系统 hook 方式。
  - `OppoPods/` — OPPO 耳机 Xposed 模块，参考其 HyperOS 系统集成方式。
  - `REAREye/` — 背屏 Xposed 模块，主要参考其 Compose UI 实现（Miuix、haze、kyant backdrop）。
- **`tools/`** — Agent 和开发辅助工具。
  - `analyze_btsnoop.py` — BLE btsnoop HCI 日志分析脚本，用于解析蓝牙通信包。
- **`gradle/`** — Gradle wrapper 和版本目录（`libs.versions.toml`）。
- 根目录 Gradle 文件 — `build.gradle.kts`（根构建配置）、`settings.gradle.kts`（项目设置，rootProject.name = "OpenBuds"）。

## Core Architecture (4 layers + adapter pattern + EQ engine)

1. **BLE GATT 传输层** — `SonyBleClient.kt` + `SonySppTransport.kt` + `TandemTransportRouting.kt` — GATT握手、SPP帧封装/拆包、GATT endpoint spec（service/to-acc/from-acc UUID 三元组）、SPP payload 双向映射、设备发现和连接生命周期。不直接理解UI业务。
2. **Tandem 协议消息层** — `protocol/SonyTandemV1Table1Protocol.kt`, `SonyTandemV2Table1Protocol.kt`, `SonyTandemV1Table2Protocol.kt`, `SonyTandemV2Table2Protocol.kt` — 命令构造和响应解析，不持有Android Context和UI状态。消息格式: `[DataType(1)] [Command(1)] [Payload(N)]`，DataType: `0x0E`=DATA_MDR(Table1), `0x0F`=DATA_MDR_NO2(Table2)。枚举和类型定义在 `SonyTandemEnums.kt`/`SonyTandemTypes.kt`/`SonyTandemConstants.kt`，EQ/EBB payload 解析由 `SonyEqEbbPayloadParser.kt` 统一处理。
3. **Codec/Adapter/profile 层** — `TandemCodecRegistry.kt` 将 4 种 protocol variant 包装为统一 `TandemCodec` 接口；`HeadphoneAdapter.kt` 声明 `ProfileTemplate`、`FeatureProtocolBinding`、`HeadphoneCapabilities` 等类型；`SonyTandemHeadphoneAdapter.kt` 按品牌/型号选择 adapter、按功能粒度选择协议版本（`featureProtocolMap`）。新增设备型号只需增加 `ProfileTemplate`。`EqProtocolEngine.kt` 是 EQ 写入/刷新/解析的单一入口，消费 `EqDeviceConfig`（定义在各 device profile）输出 `EqUiCapability`。
4. **应用层** — `data/HeadphoneRepository.kt` — 状态聚合，不按型号直接构造协议字节。UI只消费state调用repository action。

关键：Repository不直接构造协议字节，UI不持有BLE状态，协议codec独立于Android框架，EQ所有路径通过EqProtocolEngine进入。

## Core Code Structure (actual files)

```text
app/src/main/java/dev/ignotus/openbuds/
├── MainActivity.kt
├── QuickPopupActivity.kt       # 对话框主题 Activity，承载 QuickPopupScreen
├── ble/
│   ├── SonyBleClient.kt          # 设备发现、GATT 握手、SPP 选择、诊断
│   ├── SonySppTransport.kt       # Sony SPP 帧、ACK、转义、校验和
│   └── TandemTransportRouting.kt # GATT endpoint spec、SPP payload 映射、channel 路由
├── data/
│   ├── HeadphoneRepository.kt
│   ├── sony/
│   │   └── SonyModelImageCatalog.kt
│   └── qcy/
│       └── QcyResponseMapper.kt
├── headphones/
│   ├── HeadphoneAdapter.kt       # 接口、ProfileTemplate、FeatureProtocolBinding、HeadphoneCommand、HeadphoneCapabilities
│   ├── EqProtocolEngine.kt       # 设备无关 EQ 引擎：EqDeviceConfig → EqUiCapability、写入/刷新/解析
│   ├── SonyTandemHeadphoneAdapter.kt
│   ├── TandemCodecRegistry.kt    # 4 种 protocol variant 的统一 TandemCodec wrapper
│   └── sonydevices/
│       ├── LinkBudsSProfile.kt
│       ├── Wf1000Xm5Profile.kt
│       └── Wh1000Xm4Profile.kt
├── lsposed/                      # LSPosed 模块（可选系统集成层）
│   ├── ModuleMain.kt             # XposedModule 入口，按进程分发 probe + hook
│   ├── BluetoothProcessHook.kt   # com.android.bluetooth 进程探测
│   ├── XiaomiBluetoothHook.kt    # com.xiaomi.bluetooth hook（MiuiBluetoothNotification 构造函数 + HyperOsBatteryReceiver 注册）
│   ├── SystemUiHook.kt           # com.android.systemui 进程探测 + PluginInstance.loadPlugin hook
│   ├── DeviceCardHook.kt         # 控制中心设备卡片点击拦截：DeviceInfoWrapper.performClicked → MAC 匹配 → QuickPopup
│   ├── MainPanelControllerProxy.kt # 反射封装 exitOrHide()，隐藏控制中心
│   ├── CrossProcessActions.kt    # 跨进程 action 常量和 extra key 定义
│   ├── HyperOsBatteryNotification.kt # HyperOS 风格电量通知 BroadcastReceiver
│   └── ProbeResultCache.kt       # 类存在性 JSON 持久化，供 Settings 页读取
├── media/
│   └── MediaPlaybackController.kt
├── protocol/
│   ├── SonyGatt.kt
│   ├── SonyTandemConstants.kt    # DATA_MDR/DATA_MDR_NO2 等共享常量
│   ├── SonyTandemEnums.kt        # 所有协议枚举（CommonInquiredType、PowerInquiredType、EqPresetId 等）
│   ├── SonyTandemTypes.kt        # ParsedTandemResponse sealed class 及其变体
│   ├── SonyEqEbbPayloadParser.kt # V1/V2 共享 EQ/EBB payload 解析器（preset、Clear Bass、band info）
│   ├── SonyTandemV1Table1Protocol.kt
│   ├── SonyTandemV1Table2Protocol.kt
│   ├── SonyTandemV2Table1Protocol.kt  # 含 SonyTandemFrame + TandemMessage 定义
│   └── SonyTandemV2Table2Protocol.kt
├── receiver/
│   └── SystemIntegrationReceiver.kt # 跨进程广播接收器，处理系统进程→App 通信
├── service/
│   ├── SonyControlService.kt       # 前台 Service，持有 Repository，暴露 LiveData 状态
│   ├── DeviceStateSnapshot.kt      # 精简状态 DTO，支持 Bundle 序列化
│   └── ControlCommand.kt           # sealed class：SetNoiseControl / Playback / Refresh
├── theme/
│   └── Theme.kt
└── ui/
    ├── AppUiSettingsStore.kt
    ├── LiquidNavigationDrag.kt
    ├── OpenBudsApp.kt
    ├── OpenBudsComponents.kt
    ├── UiBlurEffects.kt
    ├── UiEffectsPolicy.kt        # UI渲染能力唯一入口
    └── screen/
        ├── AboutScreen.kt
        ├── DeviceScreen.kt
        ├── HomeScreen.kt
        ├── QuickPopupScreen.kt     # 连接弹窗 Compose 布局
        └── SettingsScreen.kt
```

注意：`SonyTandemFrame` 和 `TandemMessage` 定义在 `SonyTandemV2Table1Protocol.kt` 底部，不存在独立的 `SonyTandemFrame.kt` 文件。协议枚举和常量已拆分为独立的 `SonyTandemConstants.kt`、`SonyTandemEnums.kt`、`SonyTandemTypes.kt`。EQ/EBB payload 解析由 `SonyEqEbbPayloadParser.kt` 统一处理，供 V1/V2 codec 共享。

## Tandem V2 命令族字节范围

| 功能 | 字节范围 | DataType | 通道 |
|------|---------|----------|------|
| CONNECT (协议/设备信息) | 0x00-0x07 | 0x0E | HPC |
| POWER (电量/电源) | 0x20-0x29 | 0x0E | HPC |
| UPDT (固件升级) | 0x30-0x3F | 0x0E | HPC |
| LEA_STATUS (LE Audio状态查询) | 0x40-0x49 | 0x0E | HPC |
| EQEBB (EQ/音效) | 0x50-0x5B | 0x0E | HPC |
| NCASM (降噪/环境声) | 0x60-0x69 | 0x0E | HPC |
| SENSE (自适应控制) | 0x70-0x7B | 0x0E | HPC |
| PLAY (播放控制) | 0xA0-0xA9 | 0x0E | HPC |
| SAR_AUTO_PLAY | 0xB0-0xB9 | 0x0E | HPC |
| SYSTEM (佩戴/手势) | 0xF0-0xFD | 0x0E | HPC |
| PERIPHERAL (多点) | 0x30-0x3D | 0x0F | MC (Table2) |
| VOICE_GUIDANCE | 0x40-0x49 | 0x0F | MC (Table2) |
| SAFE_LISTENING | 0x50-0x5B | 0x0F | MC (Table2) |
| LEA (LE Audio连接策略) | 0x60-0x69 | 0x0F | MC (Table2) |
| PARTY | 0x70-0x79 | 0x0F | MC (Table2) |

命令字节操作码模式：`0xX0`=GET_CAPABILITY, `0xX1`=RET_CAPABILITY, `0xX2`=GET_STATUS, `0xX3`=RET_STATUS, `0xX4`=SET_STATUS, `0xX5`=NTFY_STATUS, `0xX6`=GET_PARAM, `0xX7`=RET_PARAM, `0xX8`=SET_PARAM, `0xX9`=NTFY_PARAM。

## 型号系列与协议版本映射

| 系列 | 持久化ID | V1 | V2 | 代表型号 |
|------|---------|----|----|---------|
| EXTRA_BASS | 16 | ✓ | ✓ | WH-XB |
| ULT_POWER_SOUND | 17 | ✗ | ✓ | WH-ULT |
| HEAR | 32 | ✓ | ✓ | WH-H |
| PREMIUM | 48 | ✓ | ✓ | WH-1000X, WF-1000XM5 |
| SPORTS | 64 | ✓ | ✓ | WF-SP |
| CASUAL | 80 | ✓ | ✓ | WH-CH |
| LINK_BUDS | 96 | ✗ | ✓ | LinkBuds |
| NECKBAND | 112 | ✗ | ✓ | WI |

功能级协议选择在 `ConnectedHeadphoneProfile.featureProtocolMap` 中配置。例如 WH-1000XM4: 全部 feature 走 V1_TABLE1（单 profile 全 V1）；LinkBuds S: 全部 feature 走 V2_TABLE1。

## SPP 传输帧格式

LinkBuds S 当前主要依赖 SPP 传输（非 GATT）。帧格式：

```
FRAME_START(0x3E) + escaped(body) + FRAME_END(0x3C)
body: dataType(1) + sequence(1) + length(4,BE) + payload(N) + checksum(1)
```

转义表：`0x3C→0x3D+0x2C`, `0x3D→0x3D+0x2D`, `0x3E→0x3D+0x2E`。Unescape 使用 `or 0x10` 反转。

SPP data types: `0x0C`=DATA_MDR, `0x0E`=DATA_MDR_NO2, `0x01`=ACK, `0x1C`=SHOT_MDR, `0x1E`=SHOT_MDR_NO2, `0x2C`=LARGE_DATA_MDR。

SPP 接收后规范化为 `[0x0E, command, payload...]`，使 GATT 和 SPP 路径共享同一个 parser。

## Protocol Key Facts

- 无应用层加密 — Tandem Family 消息以明文通过 BLE GATT 传输
- `OnOffSettingValue` 反转：`0x00=ON`, `0x01=OFF`
- V1 使用 MC service 处理一切；V2 分离 HPC（主功能）和 MC（扩展功能）
- 连接握手流程：discover services → read OPTIMAL_MTU → GATT MTU request → enable DETERMINE_MTU notify → determine MTU → disable MTU notify → read WRITABLE_VALUE_LENGTH → enable FROM_ACC notify → begin protocol communication
- LinkBuds S 当前使用 SPP/Tandem 路径。`LE_` 端点是 LE Audio 端点，不应作为 Sony 主控制端点
- EQ custom band index `0` = Clear Bass；可见频段：`400`, `1k`, `2.5k`, `6.3k`, `16k`
- EQ UI display step = `rawStep - 10`（raw 范围 0-20，display 范围 -10..+10）
- V1 NC/ASM 使用 `GET_PARAM(0x66)` / `SET_PARAM(0x68)`，V2 额外支持 `SET_STATUS(0x64)`
- WH-1000XM4 电池查询使用 V1 `COMMON_GET_BATTERY_LEVEL(0x10)`
- Sony Audio AD 支持 V1/V2 组合 payload 解析 (`SonyBleClient`)
- `DeviceInfo.state` 中的 `protocolReady` 表示传输层就绪状态（非设备信息语义）

## Key Reference Files

### references/SonyConnect/ (逆向源码，原 Reverse/)
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

### docs/plan/ (协议分析文档)
| Purpose | Path |
|---------|------|
| 完整协议文档 | `docs/plan/Sony耳机BLE协议完整分析.md` |
| App代码审查报告 | `docs/plan/App代码Review-2026-05-21.md` |
| 协议兼容性重构计划 | `docs/plan/PROTOCOL_COMPATIBILITY_REFACTOR_PLAN.md` |

## Build and Test

环境：JDK 17, Android Gradle Plugin 9.1.1, Kotlin 2.3.21, Compose BOM 2026.05.00, compileSdk/targetSdk 37, minSdk 31。真机要求 Android 12+。所有 gradle 命令在项目根目录执行。

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

完整验证（协议+UI+androidTest编译）：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug :app:compileDebugAndroidTestKotlin
```

adb 路径：

```powershell
$adb="C:\Software\platform-tools\adb.exe"
& $adb install -r "app\build\outputs\apk\debug\app-debug.apk"
& $adb logcat -v time OpenBuds:I AndroidRuntime:E '*:S'
```

真机冷启动验证（清空crash buffer后启动）：

```powershell
& $adb -s <serial> logcat -c
& $adb -s <serial> shell am force-stop dev.ignotus.openbuds
& $adb -s <serial> shell am start -n dev.ignotus.openbuds/dev.ignotus.openbuds.MainActivity
Start-Sleep -Seconds 5
& $adb -s <serial> logcat -b crash -d -v time
& $adb -s <serial> logcat -d -v time OpenBuds:I AndroidRuntime:E '*:S'
```

### 测试覆盖

已有测试：
- `SonyTandemV2Table1ProtocolTest` — 42+ 用例：命令编码、响应解析、边界payload、SPP归一化、LEA/Quick Access/Wearing Detection parser
- `SonyTandemTable2ProtocolTest` — 6 用例：Table2 parser 覆盖
- `SonyTandemCommandCollisionTest` — 3 用例：0x13 三路分歧（V2 COMMON_RET_STATUS vs V1 COMMON_NTFY_BATTERY_LEVEL）
- `SonyBleClientChannelTest` — GATT 通道路由
- `SonySppPayloadMapperTest` — SPP payload 双向映射
- `SonyTandemProfileRoutingTest` — Profile 匹配、normalizedModelName、feature→protocol 绑定
- `SonyTandemHeadphoneAdapterTest` — 10+ 用例：Profile匹配、refresh命令计划、NC/ASM路由、EQ引擎构建
- `ProtocolCompatibilityArchitectureTest` — 架构约束：静态 profile 完整性、codec 注册
- `SonyTandemTable2RoutingTest` — Table2 路由
- `EqStateTest` — EQ state 合并
- `Table2DiagnosticStateTest` — Table2 诊断状态
- `UiEffectsPolicyTest` — 3 用例：四种底栏模式 × 特效开关矩阵
- `AppColorModeTest` — 颜色模式主题重组

测试缺口（按优先级）：
- `SonySppTransport` 帧编码/解码/转义/校验和/ACK重试（高）
- `SonyBleClient` Sony Audio AD解析（高）
- `HeadphoneRepository` 状态管理 onMessage→state更新链（中）
- NC/ASM全部8种子类型parser测试（中）
- V2 Table2扩展入口（低）

## Known Issues & Risks

1. **SPP ACK超时线程泄漏**：`scheduleAckTimeout()` 每次创建裸 `Thread`，高丢包场景下可能累积大量超时线程。建议改用 `ScheduledExecutorService` 或 coroutine `withTimeout`。
2. **乐观UI更新无回滚**：`setNoiseControlMode()` 先更新UI state再发命令，命令失败时UI短暂不一致。
3. **`parseNoiseControl()` 过长**：100+行含8种switch-case，建议按inquired type拆分。
4. **`OnOffSettingValue` 反转**：`0x00=ON, 0x01=OFF`，不要按布尔直觉写。
5. **EQ raw band 0 = Clear Bass**，非可见频段。
6. **LE Audio切换会触发蓝牙重连**，不能按普通开关实现。
7. **SonyTandemFrame.kt 不存在**：TandemMessage定义在 `SonyTandemV2Table1Protocol.kt` 底部，协议枚举在 `SonyTandemEnums.kt`，常量在 `SonyTandemConstants.kt`，响应类型在 `SonyTandemTypes.kt`。文档中如有引用需注意。
8. **SystemUiHook probe 只检测 PluginInstance**：`MainPanelController` 和 `DeviceInfoWrapper` 位于 `miui.systemui.plugin` 的 ClassLoader 中，SystemUI ClassLoader 无法加载。probe 阶段仅检测 `PluginInstance`（SystemUI ClassLoader 中），插件类存在性在 hook 时动态判断（不存在则跳过并记录日志）。
9. **控制中心 hook 依赖 HyperOS 版本**：`PluginInstance.mPluginFactory.mClassLoaderFactory` 反射链和 `MainPanelController.exitOrHide()` / `DeviceInfoWrapper.performClicked` 在不同 HyperOS 版本中可能变化。未知版本默认 hook 会跳过（try/catch 保护），不影响控制中心稳定性。禁用模块或关闭 toggle 后，点击 third_headset 卡片有 200ms 延迟（MAC 查询超时）后正常透传。

## 已解决的重大风险（供参考）

- ~~**播放控制路径不完整**~~（已修复）：Repository playback 现在使用 adapter `buildPlaybackCommands()` 走 Tandem-first dispatch。播放 session 有心跳维持（30s 间隔 `GET playback status`），避免耳机 watchdog 超时误报 PAUSED。非 Tandem 设备 fallback 到 Android media key。
- ~~**V1协议类EQ方法不当委托**~~（已修复）：V1 EQ 方法不再委托给 V2。`EqProtocolEngine` 统一按设备 `EqDeviceConfig` 路由，V1/V2 各有独立 codec，adapter 根据 `featureProtocolMap` 选择正确路径。
- ~~**`buildSetClearBass` 双路径隐式依赖**~~（已修复）：`EqProtocolEngine` + `ClearBassWriteMode` 消除隐式依赖。XM4/LinkBuds S/WF-1000XM5 的 Clear Bass 统一通过 `PRESET_EQ_BANDS` 模式写入（合并到 EQ band 数组），Wf-1000Xm5 备选 `EBB_PARAM` 模式。

## Working with Decompiled Sources

逆向源码位于 `references/SonyConnect/sources/`（只读参考，不参与构建）。

- All class names are obfuscated short strings (e.g., `a/`, `C8782a.java`). Package structure is preserved.
- Use Grep to search for symbols, enum values, or bytecodes rather than class names
- The j2objc bridge is the best place to understand how a feature works end-to-end (it connects protocol layer to application logic)
- `param/` subdirectories contain the enum/constant definitions for each protocol feature
- JSON assets in `resources/assets/` and `resources/res/raw/` contain Lottie animations, service configurations, and region maps
- 反编译类名不可靠，优先相信enum名、byte code、payload校验逻辑和j2objc feature调用链

## 新增功能流程（摘要）

详见 `docs/DEVELOPMENT.md` 完整流程。简要步骤：
1. 在 `references/SonyConnect/sources/` 和 `docs/plan/` 确认原始协议定义
2. 在 `headphones/` 确认或新增 profile/capability
3. 在对应协议codec增加 enum、builder、parser
4. 在测试中写死期望字节（编码测试）+ 覆盖正常/边界/未知payload（解析测试）
5. 在 Repository 增加 state 聚合，刷新命令来自 adapter
6. 在 UI 增加展示/控制。未确认写入安全前只显示状态不提供开关
7. 所有状态合并使用 `copy(...)` 模式，避免多响应互相覆盖
8. parser 对未知 command 不崩溃，返回 `Unknown` 保留 raw payload
