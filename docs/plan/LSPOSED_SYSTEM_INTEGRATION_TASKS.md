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
- [x] 更新 `AndroidManifest.xml` — QuickPopupActivity 声明 + `SHOW_QUICK_POPUP` intent-filter。
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
- [x] 新增 `lsposed/XiaomiBluetoothHook.kt` — 探测 `MiuiBluetoothNotification` 构造函数，仅日志。
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

---

## 阶段 P5：HyperOS 通知集成（条件性）

风险：中到高
前提：`com.android.bluetooth.ble.app.MiuiBluetoothNotification` 在 ProbeResultCache 中标记为存在。

待实现：

- [ ] 修改 `XiaomiBluetoothHook.kt`：使用 libxposed API hook `MiuiBluetoothNotification` 构造函数，在 `com.xiaomi.bluetooth` 进程中注册 `BroadcastReceiver`。
- [ ] 新增 `lsposed/HyperOsBatteryNotification.kt`：构建 HyperOS 风格电量通知（独立逻辑，不复制 GPL 代码）。
- [ ] App Service 广播电量变化 → hook 接收 → 创建/更新通知。
- [ ] 新增开关："HyperOS-style notification"（默认关），集中在 Settings > Modules。
- [ ] 类不存在时自动回退标准通知（Phase 2 已实现）。

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
- [ ] HyperOS 通知增强开关（默认关）
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
