# 开发指引

本文档面向后续维护 OpenBuds 的开发者，说明当前 App 的结构、调试方式和新增功能流程。

## 环境要求

- Windows + PowerShell。
- Android Studio 或可用的 Android SDK/Gradle 环境。
- JDK 17。
- Android Gradle Plugin 9.1.1、Kotlin 2.3.21、Compose BOM 2026.05.00。
- compileSdk/targetSdk 37，minSdk 31。
- Android 12+ 真机。BLE/SPP 控制和媒体状态不以模拟器作为验收标准。
- 支持 Sony Tandem 的 Sony 耳机。当前主要测试对象是 LinkBuds S 和 WH-1000XM4。
- 固定 adb 路径：`C:\Software\platform-tools\adb.exe`。
- REAREye UI 参考仓库：upstream 为 `https://github.com/killerprojecte/REAREye`，当前本地参考克隆为 `references/REAREye/`。

常用命令（git 仓库位于项目根目录，所有 git 和 gradle 命令在根目录下执行）：

```powershell
# 构建和测试
.\gradlew.bat testDebugUnitTest assembleDebug

# git 操作
git status
git diff
```

```powershell
$adb="C:\Software\platform-tools\adb.exe"
& $adb devices
& $adb install -r "app\build\outputs\apk\debug\app-debug.apk"
& $adb logcat -v time OpenBuds:I AndroidRuntime:E '*:S'
```

**LSPosed 模块安装注意**：LSPosed 框架要求禁用 Android Studio 的部署优化，否则模块更新不会生效。必须使用 `gradlew installDebug` 安装，或关闭 IDE 的 "Deploy Optimization" 选项。

```powershell
.\gradlew.bat installDebug
```

## 开发流程

1. 开始前先读 `README.md`、本文档、`docs/FEATURE_STATUS.md`，协议改动再读 `docs/PROTOCOL_GUIDE.md` 和 `docs/plan/Sony耳机BLE协议完整分析.md`。
2. UI / 动效 / 导航改动先对照 `docs/analysis/REAREye_UI实现分析.md`，必要时直接查看 `references/REAREye/`；不要只凭外观复刻，要确认 source、backdrop、consumer 的层级关系。
3. 先用 `rg` 找当前实现入口，再做小范围修改。协议代码优先改 `protocol/`、`headphones/`、Repository；UI 代码优先改 `ui/`，并保持 `UiEffectsPolicy.kt` 作为渲染能力入口。
4. UI 特效改动必须保持 REAREye 的单向 backdrop 拓扑：source-only 背景层提供 Miuix blur source，前景卡片和底栏只消费 backdrop。
5. 本地验证先跑 Gradle，再装到真机；真机验证时保留 DataStore，尤其要覆盖用户已经开启的 `FloatingGlass + Miuix + UI effects` 配置。
6. 修复启动崩溃或 native 渲染问题时，先清空 crash buffer，再启动 App，并用 `logcat -b crash -d` 确认没有新的 crash。
7. 行为或架构结论变更后，同步更新 `README.md`、`docs/DEVELOPMENT.md`、`docs/FEATURE_STATUS.md`，UI 对照结论同步更新 `docs/analysis/REAREye_UI实现分析.md`。

## 代码结构

```text
app/src/main/java/dev/ignotus/openbuds/
├── MainActivity.kt
├── ble/
│   ├── SonyBleClient.kt          # 设备发现、GATT 握手、SPP 选择、诊断
│   ├── SonySppTransport.kt       # Sony SPP 帧、ACK、转义、校验和
│   └── TandemTransportRouting.kt # GATT endpoint spec、SPP payload 映射、channel 路由
├── data/
│   ├── SonyHeadphoneRepository.kt
│   └── SonyModelImageCatalog.kt
├── headphones/                   # 设备/品牌 profile、capability、adapter、EQ 引擎
│   ├── HeadphoneAdapter.kt       # 接口、ProfileTemplate、FeatureProtocolBinding、HeadphoneCapabilities
│   ├── EqProtocolEngine.kt       # 设备无关 EQ 引擎：EqDeviceConfig → EqUiCapability、写入/刷新/解析
│   ├── SonyTandemHeadphoneAdapter.kt
│   ├── TandemCodecRegistry.kt    # 4 种 protocol variant 的统一 TandemCodec wrapper
│   └── sonydevices/
│       ├── LinkBudsSProfile.kt
│       ├── Wf1000Xm5Profile.kt
│       └── Wh1000Xm4Profile.kt
├── media/
│   └── MediaPlaybackController.kt
├── protocol/
│   ├── SonyGatt.kt
│   ├── SonyTandemConstants.kt    # DATA_MDR/DATA_MDR_NO2 等共享常量
│   ├── SonyTandemEnums.kt        # 所有协议枚举
│   ├── SonyTandemTypes.kt        # ParsedTandemResponse sealed class 及其变体
│   ├── SonyEqEbbPayloadParser.kt # V1/V2 共享 EQ/EBB payload 解析器
│   ├── SonyTandemV1Table1Protocol.kt
│   ├── SonyTandemV1Table2Protocol.kt
│   ├── SonyTandemV2Table1Protocol.kt
│   └── SonyTandemV2Table2Protocol.kt
├── theme/
│   └── Theme.kt
└── ui/
    ├── AppUiSettingsStore.kt
    ├── LiquidNavigationDrag.kt
    ├── OpenBudsApp.kt
    ├── OpenBudsComponents.kt
    ├── UiBlurEffects.kt
    ├── UiEffectsPolicy.kt
    └── screen/
        ├── AboutScreen.kt
        ├── DeviceScreen.kt
        ├── HomeScreen.kt
        └── SettingsScreen.kt
```

职责边界：

- `SonyBleClient` 只负责传输层、设备发现、连接生命周期和底层日志，不直接理解 UI 业务。GATT 写入必须通过 `HeadphoneCommand.channel` 路由到对应 Tandem endpoint。
- `SonySppTransport` 只负责 SPP 帧封装和拆包。传给上层的是带 app data type 的规范化 Tandem payload；Table1 为 `DATA_MDR (0x0E)`，Table2 为 `DATA_MDR_NO2 (0x0F)`。
- `TandemTransportRouting` 提供 GATT endpoint spec（service/to-acc/from-acc UUID 三元组）、SPP payload 双向映射、通知订阅顺序和 channel 路由逻辑。
- `protocol/` 中的 Sony Tandem codec 只负责命令构造和响应解析，不持有 Android Context 和 UI 状态。`SonyEqEbbPayloadParser` 供 V1/V2 codec 共享 EQ/EBB payload 解析。
- `headphones/` 负责按品牌/型号选择 adapter、声明能力、绑定 feature 到 protocol variant/channel、生成刷新命令和判断写入是否安全。`EqProtocolEngine` 是 EQ 写入/刷新/解析的单一入口，消费 `EqDeviceConfig` 输出 `EqUiCapability`，消除跨 adapter/codec/repository 的 EQ 条件分支。
- Repository 只发起领域操作，不按型号直接构造协议字节。
- `SonyHeadphoneRepository` 是应用状态聚合层，负责把协议响应更新成 `SonyHeadphoneUiState`。
- `OpenBudsApp` 只消费 state 和调用 repository action，不直接构造协议字节。

## 新增控制功能的流程

1. 在 `docs/plan/` 和 `references/SonyConnect/sources/` 中找整理后的命令、枚举和 payload 形状。
2. 在 `references/SonyConnect/sources/` 中确认原始逆向类，优先看：
   - `com/sony/songpal/tandemfamily/message/mdr/p064v2/table1/`
   - `com/sony/songpal/tandemfamily/message/mdr/p064v2/table2/`
   - `com/sony/songpal/mdr/j2objc/tandem/features/`
3. 在 `headphones/` 中确认或新增 profile/capability；不要在 Repository 里按型号写 if。EQ 新增功能需要扩展 `EqDeviceConfig` 和 `EqProtocolEngine`，确保所有路径使用同一 config。
4. 按功能所属协议在 `protocol/` 增加 enum、builder、parser，并在 `TandemCodecRegistry` 中暴露 codec wrapper；adapter 不直接 import protocol object。
5. 在 `SonyTandemV2Table1ProtocolTest.kt`、Table2 test、routing test 和 adapter test 加编码、解析、命令计划单元测试。编码测试必须写死期望字节。
6. 在 `SonyHeadphoneRepository.kt` 增加 state 聚合和 action，刷新命令应来自 adapter。
7. 在 `OpenBudsApp.kt` 增加 UI。未确认写入安全前，只显示状态和日志，不提供开关。
8. 真机运行，打开 debug log，用 logcat 对照 TX/RX。

### 只读状态（Read-Only Status）添加模板

大部分 Tandem V2 的状态查询功能（LEA、Quick Access、佩戴检测等）遵循相同的实现配方。以下为按步骤的模板：

1. **枚举** — 在 `protocol/SonyTandemEnums.kt` 中添加 inquired type、状态码/结果码等 enum。命令常量字节定义放在 `protocol/SonyTandemConstants.kt`（如果跨版本共享）或对应 protocol object 内。参考 `LeaInquiredType`、`WearingDetectionStatus`、`QuickAccessFunction`。

2. **命令常量** — 在对应 `SonyTandemV*Table*Protocol` object 中添加 `GET/RET/NTFY` 命令字节。GET_STATUS 命令 byte 通常在 `0xX2` 位置，RET 在 `0xX3`，NTFY 在 `0xX5`；GET_PARAM 在 `0xX6`，RET_PARAM 在 `0xX7`。

3. **Builder** — 每个 GET 命令提供一个 `buildGetXxx(type)` 函数，使用 `SonyTandemFrame.message(command, byteArrayOf(type.code))`。

4. **Parser** — 在 `when (command)` 分支中路由新命令到私有 parse 函数。parse 函数从 `payload` 中按偏移量提取字段，使用 `entries.firstOrNull { it.code == byte }` 查找枚举。dispatch 多个 inquired type 时使用 `parseSystemRetParam` 的分发模式：先读 `payload[0]`，再按 type 委托到具体 parser。

5. **`ParsedTandemResponse` 变体** — 在 `protocol/SonyTandemTypes.kt` 的 sealed interface 中新增 data class，含 `override val raw: ByteArray`。nullable 字段便于安全落回 `Unknown`。

6. **Repository state** — 在 `data/SonyHeadphoneRepository.kt` 中新增 `data class XxxState(...)`，在 `SonyHeadphoneUiState` 中添加默认字段。apply 函数 **统一使用 `current.copy(state = current.state.copy(...))` 的合并模式**，避免多响应互相覆盖。

7. **`XxxStatusCard`** — 在 `ui/screen/DeviceScreen.kt` 中添加 `@Composable internal fun XxxStatusCard(...)`，渲染字段为 `InfoLine`。所有字段为 null 时整个卡片返回（不渲染）。

8. **Adapter** — 在 `headphones/` 中：`HeadphoneFeature` enum 添加特性；`SonyTandemHeadphoneAdapter` 的 `commonFeatures` 中添加；`buildRefreshCommands` 中加入刷新命令调用；`buildRefreshXxxCommands` 私有方法遍历 inquired type 生成 `HeadphoneCommand`。

9. **`featureStatusesFor`** — 在 Repository 底部添加 `FeatureStatus` 行，特性名描述功能，`profile.supports(...)` 关联实现状态。

10. **测试** — 每个新功能至少覆盖：builder 字节形状 (`assertArrayEquals`)、parser 正常 payload 提取、parser 未知 payload 安全落回（all fields null）。在 `SonyTandemV2Table1ProtocolTest.kt` 中添加。

**关键原则：**
- 所有状态合并使用 `copy(...)`，不要全量替换 `XxxState(...)`。
- per-inquired-type dispatch 必须在 parser 层面完成：先读 `payload[0]` 确定 type，再按 type 选择字段偏移量。
- `OUT_OF_RANGE(0xFF.toByte())` 作为每个 enum 的兜底值，对齐 Reverse 源码惯例。

推荐搜索命令：

```powershell
rg -n "EQEBB|NCASM|LEA_|CONNECTION_MODE|FunctionType" references/SonyConnect/sources docs/plan
rg --files references/SonyConnect/sources | rg "table1|table2|j2objc"
```

## 协议和设备 profile 注意事项

- 功能级协议选择放在 `ConnectedHeadphoneProfile.featureProtocolMap`，通过 `ProfileTemplate` 构造时自动生成 `featureBindings`。`protocolFor(feature)` 只是兼容 helper，新增逻辑应优先读 binding 的 variant 和 channel。
- EQ 功能必须通过 `EqProtocolEngine` 进入，不要在 adapter/codec/repository 里手写条件分支。`EqDeviceConfig` 是单设备 EQ 能力的事实来源，在所有写入、刷新、解析路径中一致使用。
- WH-1000XM4 使用 HEADSET form factor，电量 UI 只显示单 headset battery。
- WH-1000XM4 按完整 Sony Tandem V1 TableSet1 静态 profile 处理，device info、电量、NC/ASM、EQ、Clear Bass、播放控制均绑定 `GATT_V1_MC`；NC/ASM 已按抓包确认 `0E 66/68/69 02 ...`。
- WH-1000XM4 EQ 按 V1 `PRESET_EQ=0x01` 处理：刷新 `0E 52/56/5A 01`，自定义/手动 band 写入 `0E 58 01 FF ...`，Clear Bass 是 raw band 0，通过 `PRESET_EQ_BANDS` 模式随全 band 数组写入。
- LinkBuds S 和 WF-1000XM5 走 Sony Tandem V2 TableSet1，全部 feature 绑定 `GATT_V2_HPC`。
- V1 TableSet2 和 V2 TableSet2 已有 codec、parser、registry、MC/SPP data type 路径，默认通道分别为 `GATT_V1_MC` 和 `GATT_V2_MC`；当前只用于只读解析和诊断，没有真实响应前不要加写入 UI。
- 当前 GATT 握手仍以 V2 HPC service 为入口。V2 MC/V1 MC 会在 HPC 握手成功后注册并订阅通知；V1 MC-only GATT 连接/控制路径尚未实现。
- `featureStatusesFor()` 在 Repository 底部声明每个设备的特性实现状态，供 UI 查询。

## 连接和扫描注意事项

Sony 耳机在 Android 上可能同时出现普通设备名和 `LE_` 前缀设备名。当前策略是：

- 普通端点优先作为 Tandem 控制入口。
- `LE_` 端点主要用于 LE Audio 诊断，不作为主控制入口。
- 扫描之外还会枚举 bonded/connected/profile 设备，避免只靠 BLE scan 漏掉已连接耳机。
- 如果某端点没有 `TANDEM_V2_HPC_SERVICE`，App 会做 unsupported endpoint probe，读取可用 Sony 特征并显示诊断。

不要把 `LE_LinkBuds S` 直接当作 Sony 控制通道。LE Audio 音频 profile 是 Android 系统栈管理的，Sony Tandem 只能查询/切换耳机的连接策略和限制状态。

## UI 约定

- 使用 Compose + Material 3，MIUIX-like 作为可选主题样式。
- Appearance 里再提供独立的颜色模式选择：Light、Dark、System。颜色模式驱动 Material 色板、MIUIX theme controller 和系统栏图标对比度，不和 Theme style 绑定。
- Activity 启动阶段必须在 `setContent` 前启用 edge-to-edge 并把 status/navigation bar 设为透明；XML theme 也要保持透明系统栏和关闭 contrast enforcement，避免冷启动首帧玻璃/透明系统栏延迟生效。
- 页面保持四层：Home、Device、Settings、About。
- Home 显示应用/连接摘要；Device 负责扫描、历史设备和已连接耳机控制；Settings 放全局设置；About 放设备信息、协议来源和免责声明。
- 复杂功能使用二级展开区域，避免主页堆满滑块。
- 对真实耳机有副作用的设置必须显示当前状态，并且只在 `protocolReady=true` 后可操作。
- UI 设置使用 DataStore 持久化；当前 UI 设置库名为 `openbuds_ui_settings_v2`，默认底栏为 `Floating`，默认关闭视觉特效。
- DataStore 设置加载前不要先渲染默认 UI 配置；保留空背景或等价轻量占位，避免用户已保存的 `FloatingGlass + Miuix + UI effects` 冷启动时先闪成默认 Material / effects off。
- Appearance 中保留底栏模式、`UI effects`、颜色模式和 Theme style。底栏模式为 `Normal`、`Semi transparent`、`Floating`、`Liquid glass`。
- `UiEffectsPolicy.kt` 是 UI 渲染能力的唯一入口。页面代码只读取 `UiRenderCapabilities`，不要再直接做厂商特判或分散判断 blur/backdrop/glass 是否可用。
- root kyant backdrop 只服务于 `FloatingGlass` 路径；`Semi transparent` 只创建底栏所需的 `textureBlur` 背景，不向页面卡片或 About 局部玻璃暴露 backdrop。
- Miuix `textureBlur` 的 backdrop 必须保持单向拓扑：source-only 背景层只绘制背景并挂 `layerBackdrop`，前景 `GlassCard`、`BlurredBar`、About 局部玻璃只消费该 backdrop。不要把 Miuix `textureLayerBackdrop` 挂在包含 `AnimatedContent`、卡片、底栏或其他 blur consumer 的全局父节点上。
- 卡片、About 顶栏、quick action 弹层等局部效果必须通过同一能力对象判断；局部玻璃只随 `FloatingGlass` 开启，关闭特效或非液态模式时退回普通 surface、边框和阴影。
- About 的 acrylic / haze 只跟随 `UI effects` 和 render-effect 支持；卡片玻璃仍然只在 `FloatingGlass` 下开启。所有这些局部效果的 tint 必须读取当前 `MaterialTheme.colorScheme`，不要直接读取 `MiuixTheme.colorScheme`，避免 Material 模式下颜色脱节。
- Settings / About 根卡片不要添加纯装饰尾部文字 badge；只保留有真实状态含义的尾部控件。
- Settings 子路由状态必须保存在 theme style 条件包装外层；切换 Material / MIUIX 只能重组视觉主题，不能重置 `Appearance` 等二级页面，也不能把底栏永久留在隐藏态。
- 液态玻璃拖动应以手指触点为中心：drag start 要计算触点相对 selected overlay 中心的偏移，拖动时用 snap 跟随，release 时再吸附到最终 tab。
- 设置 overlay 隐藏底栏时，底栏 item、quick action 长按和液态拖拽入口必须同步禁用，避免透明底栏继续拦截内容区输入。
- Appearance 子页中的 color mode 切换不应依赖底栏模式或卡片玻璃状态，切换后必须保留当前 Settings 子路由。
- 分段选择控件必须支持窄屏换行；Appearance 中的底栏模式、颜色模式和主题样式选择不要写成不可换行的固定横排。
- 非液态路径不得创建 duplicate layer 或 `rememberCombinedBackdrop`，避免无关卡顿。

MIUI / Xiaomi 类设备上如果再次出现 `RenderThread`、`MiBackgroundBlurBlend` 或 native SIGSEGV，优先检查是否有新的代码让 Miuix blur source 捕获自己的 consumer，例如把 `textureLayerBackdrop` 放到包含 `GlassCard` / `BlurredBar` / `AnimatedContent` 的父节点上。其次再检查是否有代码绕过 `UiRenderCapabilities` 直接创建 backdrop，或者把 `textureBlur` / `hazeEffect` 绕开了运行时支持判断。验证时清空 crash buffer 后启动应用：

```powershell
$adb="C:\Software\platform-tools\adb.exe"
& $adb -s <serial> logcat -c
& $adb -s <serial> shell am force-stop dev.ignotus.openbuds
& $adb -s <serial> shell am start -n dev.ignotus.openbuds/dev.ignotus.openbuds.MainActivity
& $adb -s <serial> logcat -b crash -d
```

UI 性能调试建议：

```powershell
$adb="C:\Software\platform-tools\adb.exe"
& $adb shell dumpsys gfxinfo dev.ignotus.openbuds reset
# 手动或用 input 执行一次导航切换/拖动
& $adb shell dumpsys gfxinfo dev.ignotus.openbuds framestats
```

只用稳定交互段的 `framestats` 判断卡顿；安装、冷启动和首次权限弹窗会污染统计。

## 日志约定

统一使用 tag `OpenBuds`。Repository 会把关键日志也写入 UI debug log。

应记录：

- 扫描来源、设备名、地址、广播服务、Sony AD 摘要。
- 连接路径：SPP/GATT、普通端点/LE 端点。
- GATT 服务发现、MTU、通知订阅、unsupported probe 结果。
- 每条协议 TX/RX 的 label 和 hex。
- parser 未识别响应的 command/payload。

不要记录：

- 用户账号、token、云端密钥。
- 无关系统日志。

## 测试要求

每次协议改动至少运行：

```powershell
.\gradlew.bat testDebugUnitTest
```

每次 UI 或 Android 权限/连接改动至少运行：

```powershell
.\gradlew.bat assembleDebug
```

UI 设置、导航和主题改动建议运行完整命令：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug :app:compileDebugAndroidTestKotlin
```

真机验证建议覆盖：

- 未连接状态。
- 扫描中状态。
- 普通端点连接成功。
- `LE_` 端点诊断状态。
- 命令失败或 unsupported 状态。
- 断开后重新连接。
- UI 特效已开启状态，尤其是 `FloatingGlass + Miuix + UI effects` 的冷启动。
- 在 Settings > Appearance 内切换 color mode 和 theme style，确认仍停留在 Appearance 页面，底栏显隐状态不会丢失。

推荐真机启动验证命令：

```powershell
$adb="C:\Software\platform-tools\adb.exe"
& $adb devices
& $adb -s <serial> install -r "app\build\outputs\apk\debug\app-debug.apk"
& $adb -s <serial> logcat -c
& $adb -s <serial> shell am force-stop dev.ignotus.openbuds
& $adb -s <serial> shell am start -n dev.ignotus.openbuds/dev.ignotus.openbuds.MainActivity
Start-Sleep -Seconds 5
& $adb -s <serial> logcat -b crash -d -v time
& $adb -s <serial> logcat -d -v time OpenBuds:I AndroidRuntime:E '*:S'
```

## 资源和机型图片

`scripts/fetch_sony_model_images.py` 用于生成 Sony 云端机型图片 manifest。当前 App 使用：

```text
app/src/main/assets/sony_model_images.json
```

匹配逻辑在 `SonyModelImageCatalog.kt`：

1. 型号 + 颜色精确匹配。
2. 型号 + Default 颜色。
3. 型号任意颜色。

如果设备信息中的颜色解析不准，优先修正 `SonyTandemV2Table1Protocol` 的 series/color 解析和 model color 映射。

## 常见风险

- `OnOffSettingValue` 在 Sony 协议里常见 `0x00=ON`、`0x01=OFF`，不要按布尔直觉写。
- EQ 的 raw band index 0 是 Clear Bass，后面才是 400/1k/2.5k/6.3k/16k。
- 播放控制既有耳机 Tandem 命令，也有 Android media key fallback。UI 状态不要在点击时乐观切两次。
- LE Audio 切换会触发蓝牙重连，不能按普通开关实现。
- 反编译类名不可靠，优先相信 enum 名、byte code、payload 校验逻辑和 j2objc feature 调用链。
