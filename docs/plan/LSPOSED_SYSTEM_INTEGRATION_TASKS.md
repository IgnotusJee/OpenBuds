# OpenBuds LSPosed 系统集成任务清单

更新日期：2026-05-25

本文把"参考 OppoPods / HyperPods，为 Sony 耳机实现 HyperOS 弹窗、状态栏、控制中心入口，以及最终接近小米原生耳机设置体验"的目标拆成仓库级任务。使用 **libxposed 现代 API** (`io.github.libxposed:api:101.0.1`)，同一 hook 挂载点，更类型安全。

## 0. 目标和边界

目标：

- 主 App 保持独立可用，不依赖 root、LSPosed 或 HyperOS。
- LSPosed 模块作为可选系统集成层，负责系统入口、系统提示和系统 UI 注入。
- Sony 协议、状态缓存和写入命令只保留一份实现，避免 App 和模块各自维护协议逻辑。
- 第一可交付目标是"连接弹窗 + 常驻通知 + 快捷控制面板"。
- 第二可交付目标是"控制中心/设备卡片点击进入 OpenBuds 快捷面板"。
- 第三可交付目标才是"系统蓝牙设置页中的 Sony 耳机入口"。

明确不做：

- 不复制 Sony 官方 App 反编译业务代码。
- 不直接复制 OppoPods / HyperPods 的 GPL 源码到本项目；可参考其 hook 目标类名和方法签名（作为小米操作系统的事实），所有 hook 逻辑从零编写。
- 不把未知 HyperOS / MIUI 版本当作默认支持对象。
- 不在系统进程中执行复杂协议状态机；系统进程只做入口、展示和轻量转发。

## 现状基准

当前仓库状态：

- `app/` 是单模块 Android Compose App。
- 协议和状态实现主要位于：
  - `app/src/main/java/dev/ignotus/openbuds/ble/`
  - `app/src/main/java/dev/ignotus/openbuds/protocol/`
  - `app/src/main/java/dev/ignotus/openbuds/data/`
  - `app/src/main/java/dev/ignotus/openbuds/headphones/`
- App 内设置入口已经存在：`SettingsRoute.Modules` → `SettingsModulesScreen`。
- 已实现的可复用状态包括电量、NC/ASM、EQ、播放控制、LE Audio 状态读取、Quick Access 读取、佩戴检测读取。

参考项目：

- OppoPods：`https://github.com/Leaf-lsgtky/OppoPods`
- HyperPods：`https://github.com/Art-Chen/HyperPods`

可借鉴的系统作用域：

- `com.android.bluetooth`：蓝牙连接事件、设备识别、可能的后台通信入口。
- `com.xiaomi.bluetooth`：小米蓝牙通知、Focus Island / 强提示、耳机通知样式。
- `com.android.systemui`：控制中心设备卡片、状态栏/系统 UI 入口。
- `com.android.settings` 或小米设置相关包：系统蓝牙设备详情页入口。该项最高风险，最后处理。

## libxposed 现代 API vs 旧 API 对比

| 项目 | 旧方案 (HyperPods/YukiHookAPI) | 新方案 (OpenBuds) |
|------|-------------------------------|-------------------|
| 核心 API | `de.robv.android.xposed:api` + YukiHookAPI | `io.github.libxposed:api:101.0.1` |
| 入口类 | `object : IYukiHookXposedInit` + `@InjectYukiHookWithXposed` | `class : XposedModule()` |
| 入口注册 | `assets/xposed_init` | `META-INF/xposed/java_init.list` |
| 模块配置 | Manifest `<meta-data>` (xposedmodule, xposedscope...) | `META-INF/xposed/module.prop` |
| 作用域 | `res/values/xposed_arrays.xml` | `META-INF/xposed/scope.list` |
| Hook 注册 | `loadApp(pkg, hooker)` 在 `onHook()` 中 | `onPackageLoaded(param)` + 反射/ClassLoader 探测 |
| Hook 目标 | 相同的类名和方法名 | 完全相同 — 仅 hook 机制不同 |
| 许可 | YukiHookAPI Apache 2.0, XposedBridge Apache 2.0 | libxposed Apache 2.0 |

---

## 阶段 P1 ✅ 已完成：基础 —— 前台 Service + 跨进程 DTO

风险：低
目标：让 Repository 能在后台运行，暴露精简状态和命令接口，不依赖 Compose/Activity。

完成内容：

- [x] 新增 `gradle/libs.versions.toml` — libxposed API 101.0.1 + service 101.0.0 依赖声明。
- [x] 更新 `app/build.gradle.kts` — `compileOnly(libs.libxposed.api)` + `implementation(libs.libxposed.service)`。
- [x] 新增 `service/DeviceStateSnapshot.kt` — 精简状态 DTO：设备名、MAC、左右/盒电量、NC 模式、ANC/ASM 开关、播放状态、EQ preset 名。提供 `fromUiState()` 映射和 `toBundle()`/`fromBundle()` 序列化。
- [x] 新增 `service/ControlCommand.kt` — sealed class：`SetNoiseControl(mode)`, `SetAmbientLevel(level)`, `SetAmbientVoiceMode(enabled)`, `Playback(action)`, `Refresh`。
- [x] 新增 `service/SonyControlService.kt` — 前台 Service，持有 `SonyHeadphoneRepository`，通过 `LiveData<DeviceStateSnapshot>` 暴露状态，通过 `LocalBinder.execute()` 委托命令。管理常驻通知（设备名 + 电量 + 断开/弹窗操作）。
- [x] 更新 `AndroidManifest.xml` — `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_CONNECTED_DEVICE` / `POST_NOTIFICATIONS` 权限 + Service 声明。
- [x] 更新 `AppUiSettingsStore.kt` — 新增 `serviceBackgroundRun`、`notificationPersistent`、`notificationLockscreen`、`connectionPopup` 四个偏好。

---

## 阶段 P2 ✅ 已完成：App 级弹窗与通知

风险：低到中
目标：不开 LSPosed 也能提供弹窗、通知和快捷控制基础体验。

完成内容：

- [x] 新增 `QuickPopupActivity.kt` — 对话框主题 Activity（`singleInstance`、`excludeFromRecents`），绑定 `SonyControlService`，承载 `QuickPopupScreen`。
- [x] 新增 `ui/screen/QuickPopupScreen.kt` — Compose 布局：设备名 + 电量行 + 三态 ANC 切换 + 环境声 slider + 播放控制行 + "More settings" 按钮。断开后 5 秒自动关闭。
- [x] 新增 `res/values/themes.xml` `Theme.OpenBuds.Popup` — `windowIsTranslucent`、`windowIsFloating`、`backgroundDimEnabled`。
- [x] 更新 `AndroidManifest.xml` — QuickPopupActivity 声明（P4.5 后改为 `exported=false`，无 intent-filter，由 Receiver 和内部组件显式启动）。
- [x] 更新 `SettingsModulesScreen` — "Background service" / "Persistent notification" / "Connection popup" 开关。

---

## 阶段 P3 ✅ 已完成：LSPosed 模块骨架 —— 只读探测

风险：中
目标：加入 LSPosed 模块，使用 libxposed 现代 API，只做类存在性探测和能力评估，不改变系统行为。

完成内容：

- [x] 新增 `META-INF/xposed/module.prop` — `minApiVersion=100`, `targetApiVersion=101`, `staticScope=true`。
- [x] 新增 `META-INF/xposed/java_init.list` — 入口类 `dev.ignotus.openbuds.lsposed.ModuleMain`。
- [x] 新增 `META-INF/xposed/scope.list` — `com.android.bluetooth`, `com.xiaomi.bluetooth`, `com.android.systemui`。
- [x] 新增 `lsposed/ModuleMain.kt` — `XposedModule()` 子类，`onPackageLoaded()` 按进程分发探测。
- [x] 新增 `lsposed/BluetoothProcessHook.kt` — 探测 `A2dpService.handleConnectionStateChanged`、`MiuiBluetoothNotification`，使用 ClassLoader 反射，仅日志。
- [x] 新增 `lsposed/XiaomiBluetoothHook.kt` — 探测 `MiuiBluetoothNotification` 构造函数，仅日志。（P5 已扩展为完整 hook）
- [x] 新增 `lsposed/SystemUiHook.kt` — 探测 `PluginInstance.loadPlugin`、`MainPanelController.onCreate`、`DeviceInfoWrapper.performClicked`，仅日志。
- [x] 新增 `lsposed/ProbeResultCache.kt` — 类存在性 JSON 持久化，供 Settings 页读取。
- [x] 更新 `SettingsModulesScreen` — 新增 "LSPosed System Integration" 卡片，显示 ROM 兼容等级、最近探测时间、实验性警告。
- [x] 新增 `lsposed/GPL_BOUNDARY.md` — GPL 边界规则文档。

---

## 阶段 P4 ✅ 已完成：模块-App 通信桥

风险：中
目标：系统进程能安全打开 App、查询状态、触发命令。

完成内容：

- [x] 新增 `lsposed/CrossProcessActions.kt` — 集中定义所有跨进程 action 常量和 extra key，使用 `dev.ignotus.openbuds.*` 命名空间。
- [x] 新增 `receiver/SystemIntegrationReceiver.kt` — 在 App 进程中处理 `QUERY_DEVICE_MAC` 和 `SHOW_QUICK_POPUP`。
- [x] 更新 `AndroidManifest.xml` — `signature` 级别自定义权限 `SYSTEM_INTEGRATION` + Receiver 声明。

## 阶段 P4.5：P1-P4 审计修复（进入 P5 前阻断）

风险：中
目标：修复 P1-P4 最近实现中的稳定性、安全性和协议语义问题，保证 App 级弹窗、前台 Service、LSPosed 只读探测和模块-App 通信桥在继续做 HyperOS 通知/SystemUI hook 前足够稳。

审计范围：

- 最近 LSPosed 系统集成基础改动：P1 到 P4。
- 重点文件：`QuickPopupActivity.kt`、`QuickPopupScreen.kt`、`AndroidManifest.xml`、`SonyControlService.kt`、`SonyHeadphoneRepository.kt`、`SonyTandemV2Table1Protocol.kt`、`lsposed/*`。
- 工具结果：CodeRabbit 对 `HEAD~1` 到当前工作区的审计问题。

修复清单：

- [x] **Critical：QuickPopupActivity 永远显示空状态** ✅ 已修复
  - 方案：`QuickPopupActivity` 通过 `LiveData.observe()` 订阅 Service 实时状态，binder 存入 `mutableStateOf` 触发重组。

- [x] **Critical：ModuleMain 进程名反射读取不安全** ✅ 已修复
  - 方案：改用 `Class.forName("android.app.ActivityThread").getDeclaredMethod("currentProcessName").invoke(null)` 反射获取进程名。

- [x] **Critical：ModuleMain.instance 未初始化** ✅ 已修复
  - 方案：在 `init` 中赋值 `instance = this`。

- [x] **Major：QuickPopupActivity exported 入口缺少权限保护** ✅ 已修复
  - 方案：移除 `QuickPopupActivity` 的 intent-filter，改为 `exported=false`；仅 Receiver 和内部组件可显式启动。

- [x] **Major：V2 NC/ASM 写入需要使用 inverted OnOffSettingValue** ✅ 已修复
  - 方案：`NCASM_ON = 0x00`（原 0x01），`NCASM_OFF = 0x01`（原 0x00），符合 Sony 协议 `0x00=ON, 0x01=OFF`。`NCASM_EFFECT_OFF` 保持不变（非 OnOff 值）。Builder/parser/adapter 测试全部更新。

- [ ] **Major：setNoiseControlMode 乐观更新 confirmed state** ⚠️ 已知问题，暂缓
  - 说明：与 CLAUDE.md 记录的已知问题 #2 相同，非本次改动引入。需更大范围重构（pending/confirm 状态流），留待后续专项处理。

- [x] **Major：ProbeResultCache 非线程安全** ✅ 已修复
  - 方案：所有公开 API 使用 `synchronized(lock)` 保护。

- [x] **Minor：QuickPopupActivity 初次 composition 时 binder 为空** ✅ 已修复
  - 方案：binder 通过 `mutableStateOf` 在 `onServiceConnected` 中赋值触发重组。

- [x] **Minor：QuickPopupScreen 在 never-connected 情况下不会自动关闭** ✅ 已修复
  - 方案：条件改为 `!state.isConnected`，移除 `deviceName != null` 要求。

- [x] **Minor：ProbeResultCache 方法列表会重复追加** ✅ 已修复
  - 方案：使用 `.distinct()` 去重。

- [x] **Minor：ProbeResultCache.save() 静默吞异常** ✅ 已修复
  - 方案：`catch (e: Exception) { Log.e(TAG, "Failed to save $FILE_NAME", e) }`。

- [x] **Minor：ProbeResultCache.load() 静默吞异常** ✅ 已修复
  - 方案：`catch (e: Exception) { Log.e(TAG, "Failed to load $FILE_NAME", e) }`。

修复顺序：

1. 先修安全和崩溃阻断项：Activity 权限、`ModuleMain` 初始化、QuickPopup 实时状态。
2. 再修协议和状态一致性：NC/ASM inverted 编码、noise control pending/confirm 流程。
3. 最后修诊断缓存健壮性：线程安全、去重、save/load 日志。

回归要求：

- [x] `.\gradlew.bat testDebugUnitTest`
- [x] `.\gradlew.bat assembleDebug`
- [ ] `.\gradlew.bat :app:compileDebugAndroidTestKotlin`
- [ ] 手动验证：未启用 LSPosed 时，主 App、前台 Service、常驻通知、QuickPopup 正常。
- [ ] 手动验证：第三方未授权 Intent 不能直接打开 `QuickPopupActivity`。
- [ ] 手动验证：ProbeResultCache JSON 损坏时 Settings 模块页不崩溃，并输出错误日志。

### 第二次审计（2026-05-25，commit `f1b488c`）

自审发现并修复了以下问题：

- [x] **Critical：QuickPopupActivity recomposition 失效** — `_connectedBinder` 字段更新不触发 Compose 重组。修复：将 `mutableStateOf` 提升到 Activity 级别，在 `ServiceConnection.onServiceConnected` 中赋值，使用 `DisposableEffect` 桥接 LiveData → Compose state。
- [x] **Major：SonyControlService 隐式 Intent** — `setClassName("dev.ignotus.openbuds.QuickPopupActivity")` 使用字符串字面量。修复：改用 `Intent(this, QuickPopupActivity::class.java)` 显式类引用。
- [x] **Minor：QuickPopupScreen 未使用 imports** — 移除 `Bluetooth`、`MusicNote`、`width`。

已知遗留问题：

- [x] ~~**Phase 5+ IPC 阻止**~~ ✅ 已修复（commit `03731d9`）：`SystemIntegrationReceiver` 权限降级为 `normal` + `onReceive` 中 UID→包名白名单校验（`ALLOWED_CALLER_PACKAGES`：`com.android.bluetooth`、`com.android.systemui`、`com.xiaomi.bluetooth`），系统进程 LSPosed 模块可正常发送广播。
- ⚠️ `notificationLockscreen` 偏好已连接但未被 `SonyControlService` 消费 — 留待后续通知增强时使用。

### 第三次审计（2026-05-25，commit `03731d9`）

P5 前置条件修复：

- [x] **`SystemIntegrationReceiver` IPC 权限降级** — `protectionLevel` 从 `signature` 改为 `normal`，移除 `PERMISSION_SYSTEM_INTEGRATION` 常量，新增 `ALLOWED_CALLER_PACKAGES` 白名单和 `Binder.getCallingUid()` → `getPackagesForUid()` 运行时校验。

P5 入口条件现已全部满足，可进入 HyperOS 通知集成。

---

## 阶段 P5 ✅ 已完成：HyperOS 通知集成（条件性）

风险：中到高
前提：
- `com.android.bluetooth.ble.app.MiuiBluetoothNotification` 在 ProbeResultCache 中标记为存在。
- [x] `SystemIntegrationReceiver` 权限已从 `signature` 改为 `normal` + 调用包名白名单校验（commit `03731d9`），系统进程可正常发送广播。

完成内容：

- [x] 更新 `lsposed/CrossProcessActions.kt` — 新增 `ACTION_UPDATE_HYPEROS_NOTIFICATION`、`ACTION_CANCEL_HYPEROS_NOTIFICATION` 两个 action 常量和 `EXTRA_BATTERY_SINGLE/LEFT/RIGHT/CRADLE`、`EXTRA_IS_CONNECTED` 五个 extra key。
- [x] 新增 `lsposed/HyperOsBatteryNotification.kt` — `BroadcastReceiver` 子类在 `com.xiaomi.bluetooth` 进程中运行：
  - `ACTION_UPDATE_HYPEROS_NOTIFICATION`：提取设备名/MAC/电量/连接状态，连接时构建或更新 HyperOS 风格通知，断开时取消通知。
  - `ACTION_CANCEL_HYPEROS_NOTIFICATION`：按 MAC 取消通知。
  - 通知构建通过 `resources.getIdentifier()` 动态解析 `com.xiaomi.bluetooth` 包内资源 ID（`miheadset_notification_Box/LeftEar/RightEar/Disconnect`、`system_notification_accent_color`、`ic_headset_notification`），零值时有英文 fallback。
  - 通知渠道 ID 格式 `BTHeadset$address`，通知 ID `10003`（观察到的 HyperOS 事实）。
  - Sony 特有电量格式：TWS 显示 Case/L/R 三行，头戴式显示单行 Battery。
  - 使用反射调用 `notifyAsUser(UserHandle.ALL)` 支持多用户，反射失败 fallback 到标准 `notify()`。
  - 所有逻辑独立编写，action 字符串使用 `dev.ignotus.openbuds.*` 命名空间，不复制 GPL 代码。
- [x] 修改 `lsposed/XiaomiBluetoothHook.kt`：
  - 保留原有 `probe()` 方法不变。
  - 新增 `hook()` 方法：通过 libxposed API (`ModuleMain.instance.hook(constructor).intercept(...)`) hook `MiuiBluetoothNotification` 2 参数构造函数。
  - after-hook 回调中通过反射获取 `mContext` 字段，注册 `HyperOsBatteryReceiver`。
  - `@Volatile receiverRegistered` 标志防止重复注册；`RECEIVER_EXPORTED` 用于跨 UID 广播（API < 33 时无 flag）。
- [x] 修改 `lsposed/ModuleMain.kt` — `com.xiaomi.bluetooth` 分发逻辑改为同时调用 `probe()` 和 `hook()`。
- [x] 修改 `service/SonyControlService.kt`：
  - 新增 `AppUiSettingsStore` 依赖，通过协程收集 `hyperOsNotification` 偏好。
  - 在现有 `repository.state.collect` 块中，当 `hyperOsEnabled && isConnected` 时发送 `ACTION_UPDATE_HYPEROS_NOTIFICATION` 显式广播（`setPackage("com.xiaomi.bluetooth")`）。
  - 跟踪 `lastBroadcastMac`，断开时发送 `ACTION_CANCEL_HYPEROS_NOTIFICATION`。
- [x] 更新 `AppUiSettingsStore.kt` — 新增 `hyperOsNotification: Boolean = false` 偏好、`HyperOsNotificationKey` 键、`setHyperOsNotification()` setter。
- [x] 更新 `SettingsModulesScreen` — 在 "Background service" SectionCard 内新增 "HyperOS-style notification" 开关行，仅当 ProbeResultCache 中 `MiuiBluetoothNotification` class 标记为 found 时可见。
- [x] 更新 `OpenBudsApp.kt` — 在 `SettingsScreen` 调用点接入 `hyperOsNotification` 参数。

架构流：
```
SonyControlService (app process)
  ──[setPackage("com.xiaomi.bluetooth") explicit broadcast]──→
HyperOsBatteryReceiver (com.xiaomi.bluetooth process, registered by hook)
  ──[notifyAsUser(UserHandle.ALL) via reflection]──→
HyperOS notification drawer (渠道 BTHeadset$MAC, ID 10003)
```

回退：类不存在时 toggle 不显示，`hyperOsNotification` 默认 `false`，标准 Phase 2 通知始终作为 fallback。

---

## 阶段 P6：控制中心设备卡片入口（条件性）

风险：高
前提：`PluginInstance.loadPlugin`、`DeviceInfoWrapper.performClicked`、`MainPanelController` 均存在。

待实现：

- [ ] 修改 `SystemUiHook.kt`：在 `loadPlugin` after hook 中获取 `miui.systemui.plugin` 的 ClassLoader，加载 `DeviceCardHook`。
- [ ] 新增 `lsposed/DeviceCardHook.kt`：hook `DeviceInfoWrapper.performClicked`，过滤 `deviceType == "third_headset"`，通过广播查询 MAC，匹配后打开弹窗。
- [ ] 新增 `lsposed/MainPanelControllerProxy.kt`：薄反射封装调用 `exitOrHide()` 隐藏控制中心。
- [ ] 新增开关："Control center device card interception"（默认关）。

---

## 阶段 P7：状态栏耳机图标（条件性）

风险：高
前提：`StatusBarManager.setIconVisibility` 有效。

待实现：

- [ ] 调研状态栏耳机图标来源（系统蓝牙状态图标 / 小米蓝牙通知 extras / SystemUI plugin 内部状态）。
- [ ] 首版只影响 OpenBuds 自己创建的通知 extras。
- [ ] 若需要 hook SystemUI 状态控制器，先只做只读日志。
- [ ] 添加版本白名单，未知版本默认关闭。
- [ ] 新增开关："Status bar icon enhancement"（默认关）。

---

## 阶段 P8：系统蓝牙设置页入口（条件性）

风险：很高
前提：`com.android.settings` 目标类存在。

待实现：

- [ ] 把 `com.android.settings` 或小米设置包加入可选 scope（默认不启用）。
- [ ] Modules 页提示用户这是实验功能。
- [ ] 调研系统蓝牙设备详情页类（Fragment/Activity 名、Preference screen 构建时机、当前设备对象读取方式）。
- [ ] 只对 Sony 设备插入入口：标题 "Sony Sound settings / OpenBuds"，summary 显示电量/ANC 当前状态，点击打开主 App 设备页。
- [ ] 不在第一版直接嵌入复杂 Compose UI。
- [ ] 不覆盖系统原有蓝牙设置项。
- [ ] 新增开关："System settings entry"（默认关）。

---

## 阶段 P9：系统设置页内嵌控制（条件性）

风险：最高
前提：P8 已验证，仅经验证的 HyperOS 构建启用。

待实现（按功能逐项加入，不做一次性大页面）：

- [ ] 第一批：电量只读、ANC 三态。
- [ ] 第二批：环境声等级、关注语音。
- [ ] 第三批：EQ preset、Clear Bass。
- [ ] 第四批：Quick Access、佩戴检测。
- [ ] 第五批：LE Audio 策略、连接质量、多点连接。
- [ ] 每个设置项都必须有 capability gating。
- [ ] 每个写入项都必须有失败反馈。
- [ ] 长耗时操作不在 Settings/SystemUI 主线程执行。
- [ ] 设置页只展示稳定功能；实验功能仍跳转 App 内页。
- [ ] 系统更新导致 hook 失效时自动隐藏内嵌控制项。
- [ ] 新增开关："System settings embedded controls"（默认关，实验性）。

---

## 测试计划

基础测试：

- [x] `.\gradlew.bat testDebugUnitTest`
- [x] `.\gradlew.bat assembleDebug`
- [ ] `.\gradlew.bat :app:compileDebugAndroidTestKotlin`

真机回归：

- [ ] LinkBuds S：连接、弹窗、电量、NC/ASM、EQ、播放控制。
- [ ] WH-1000XM4：连接、电量、NC/ASM、EQ、播放控制。
- [ ] 模块未启用：主 App 行为正常。
- [ ] 模块启用但 scope 不完整：App 显示诊断，不崩溃。
- [ ] 模块启用且 scope 完整：弹窗、通知、SystemUI 入口正常。

系统稳定性：

- [ ] 重启手机后服务/通知状态合理。
- [ ] 蓝牙开关反复切换不会卡死。
- [ ] 耳机快速连接/断开不会反复弹窗。
- [ ] SystemUI 重启后 hook 可恢复或自动降级。
- [ ] 设置 App 重启后入口可恢复或自动隐藏。

安全测试：

- [ ] 第三方 App 无法伪造写入广播。
- [ ] 跨用户通知不泄露设备详细信息。
- [ ] 锁屏状态只显示用户允许的信息。
- [ ] 未授权状态下不公开 MAC 明文。

---

## 发布和开关策略

所有系统集成功能挂在 Settings > Modules：

- [x] 后台服务开关（`serviceBackgroundRun`，默认关）
- [x] 常驻通知开关（`notificationPersistent`，默认开）
- [x] 连接弹窗开关（`connectionPopup`，默认关）
- [x] HyperOS 通知增强开关（`hyperOsNotification`，默认关）
- [ ] Control Center 卡片接管开关（默认关）
- [ ] 状态栏增强开关（默认关）
- [ ] 系统设置入口开关（默认关）
- [ ] 实验性系统设置内嵌控制开关（默认关）

---

## 风险和回退

主要风险：

- HyperOS 类名和方法名随版本变化。
- SystemUI hook 失败可能影响控制中心稳定性。
- Settings hook 失败可能影响系统设置稳定性。
- 蓝牙系统进程 hook 失败可能影响连接稳定性。
- GPL 参考项目代码不能直接混入 clean-room 实现（详见 `lsposed/GPL_BOUNDARY.md`）。

回退要求：

- 每个 hook 必须包在 try/catch 和 class-exists guard 中。
- 每个系统集成功能必须有独立开关。
- 未知 ROM 版本默认关闭高风险功能。
- App 标准通知和主 UI 永远作为 fallback。
- 模块禁用后不能留下系统侧脏状态。
