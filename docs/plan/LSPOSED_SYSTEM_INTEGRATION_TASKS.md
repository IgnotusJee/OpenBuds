# SonyRebuild LSPosed 系统集成任务清单

更新日期：2026-05-24

本文把“参考 OppoPods / HyperPods，为 Sony 耳机实现 HyperOS 弹窗、状态栏、控制中心入口，以及最终接近小米原生耳机设置体验”的目标拆成仓库级任务。执行顺序按风险从低到高排列；前一阶段没有通过验收时，不进入后一阶段。

## 0. 目标和边界

目标：

- 主 App 保持独立可用，不依赖 root、LSPosed 或 HyperOS。
- LSPosed 模块作为可选系统集成层，负责系统入口、系统提示和系统 UI 注入。
- Sony 协议、状态缓存和写入命令只保留一份实现，避免 App 和模块各自维护协议逻辑。
- 第一可交付目标是“连接弹窗 + 常驻通知 + 快捷控制面板”。
- 第二可交付目标是“控制中心/设备卡片点击进入 SonyRebuild 快捷面板”。
- 第三可交付目标才是“系统蓝牙设置页中的 Sony 耳机入口”。

明确不做：

- 不复制 Sony 官方 App 反编译业务代码。
- 不直接复制 OppoPods / HyperPods 的 GPL 源码到本项目，除非项目许可策略明确接受 GPL 传染风险；可以参考其架构和 hook 目标。
- 不把未知 HyperOS / MIUI 版本当作默认支持对象。
- 不在系统进程中执行复杂协议状态机；系统进程只做入口、展示和轻量转发。

## 1. 现状基准

当前仓库状态：

- `app/` 是单模块 Android Compose App。
- `app/src/main/AndroidManifest.xml` 当前只有主 Activity、蓝牙权限和网络权限，没有 Service、BroadcastReceiver、ContentProvider、LSPosed 元数据或 hook 入口。
- 协议和状态实现主要位于：
  - `app/src/main/java/dev/ignotus/sonyrebuild/ble/`
  - `app/src/main/java/dev/ignotus/sonyrebuild/protocol/`
  - `app/src/main/java/dev/ignotus/sonyrebuild/data/`
  - `app/src/main/java/dev/ignotus/sonyrebuild/headphones/`
- App 内设置入口已经存在：
  - `SettingsRoute.Modules`
  - `SettingsModulesScreen`
- 已实现的可复用状态包括电量、NC/ASM、EQ、播放控制、LE Audio 状态读取、Quick Access 读取、佩戴检测读取。

参考项目：

- OppoPods：`https://github.com/Leaf-lsgtky/OppoPods`
- HyperPods：`https://github.com/Art-Chen/HyperPods`

可借鉴的系统作用域：

- `com.android.bluetooth`：蓝牙连接事件、设备识别、可能的后台通信入口。
- `com.xiaomi.bluetooth`：小米蓝牙通知、Focus Island / 强提示、耳机通知样式。
- `com.android.systemui`：控制中心设备卡片、状态栏/系统 UI 入口。
- `com.android.settings` 或小米设置相关包：系统蓝牙设备详情页入口。该项最高风险，最后处理。

## 2. 阶段 P0：调研和兼容矩阵

风险：低  
目标：先知道要支持哪些系统、哪些类存在、哪些入口可 hook。

任务：

- [ ] 记录目标测试设备和系统版本。
  - 输出：`docs/LSPOSED_COMPATIBILITY_MATRIX.md`
  - 字段：设备型号、Android 版本、HyperOS/MIUI 版本、LSPosed 版本、作用域包版本。
- [ ] 在测试设备上导出目标包信息。
  - `com.android.bluetooth`
  - `com.xiaomi.bluetooth`
  - `com.android.systemui`
  - `com.android.settings`
- [ ] 确认小米系统包名和类名是否与 OppoPods 当前 hook 点一致。
  - Focus Island / 蓝牙通知类。
  - SystemUI 插件加载类。
  - 控制中心设备卡片 wrapper 类。
  - 蓝牙设置设备详情页类。
- [ ] 建立“可 hook 能力表”。
  - 只读类存在。
  - 构造函数可 hook。
  - 方法可 hook。
  - hook 后是否触发。
  - hook 失败是否影响系统稳定。

验收：

- [ ] 兼容矩阵文档存在。
- [ ] 至少一台目标 HyperOS 设备完成类名探测。
- [ ] 每个后续 hook 目标都有类名、方法名、失败回退策略。

不进入下一阶段的阻断条件：

- 目标设备未 root 或 LSPosed 不可用。
- 无法稳定启用 `com.android.bluetooth`、`com.xiaomi.bluetooth`、`com.android.systemui` 作用域。

## 3. 阶段 P1：Core 分层和状态接口

风险：低  
目标：把协议能力从 UI 生命周期中解耦，给 App、Service 和模块共用。

任务：

- [ ] 评估是否新增 Gradle module。
  - 推荐结构：
    - `:sony-core`：协议、profile、状态模型、命令接口。
    - `:app`：Compose UI、Activity、App 设置。
    - `:lsposed-module`：Xposed/YukiHook 入口和系统注入。
  - 如果短期不拆 module，至少先按 package 边界拆清楚。
- [ ] 定义核心控制接口。
  - 建议位置：`app/src/main/java/dev/ignotus/sonyrebuild/core/`
  - 示例职责：
    - 连接目标设备。
    - 断开连接。
    - 读取最新 `SonyHeadphoneUiState` 或精简状态。
    - 设置 NC/ASM。
    - 设置 EQ。
    - 发送播放控制。
    - 刷新设备状态。
- [ ] 把可被后台复用的状态裁剪成轻量 DTO。
  - 不让系统进程依赖 Compose 类型。
  - 不把完整 debug log 广播给系统进程。
- [ ] 保持现有 `SonyHeadphoneRepository` 行为不变。
  - UI 仍从现有 Repository 读取状态。
  - 后台接口先适配 Repository，而不是替换 Repository。
- [ ] 增加 core 单元测试。
  - 状态 DTO 映射。
  - 命令入口不会绕过 profile capability。
  - 不支持的写入返回明确失败。

验收：

- [ ] `.\gradlew.bat testDebugUnitTest assembleDebug` 通过。
- [ ] 主 App 不装模块仍能扫描、连接和控制耳机。
- [ ] 新增 core 接口不直接依赖 Activity、Compose、SystemUI 或 LSPosed。

## 4. 阶段 P2：App 级后台服务和快速入口

风险：低到中  
目标：不开 LSPosed 也能提供弹窗、通知和快捷控制基础体验。

任务：

- [ ] 新增前台服务或绑定服务。
  - 建议文件：
    - `app/src/main/java/dev/ignotus/sonyrebuild/service/SonyHeadphoneService.kt`
    - `app/src/main/java/dev/ignotus/sonyrebuild/service/SonyHeadphoneServiceBinder.kt`
  - 职责：
    - 维护连接。
    - 持有最新精简状态。
    - 暴露轻量命令。
    - 管理标准 Android 通知。
- [ ] 在 Manifest 添加服务声明和所需权限。
  - Android 14+ 注意 foreground service type。
  - 通知权限按 Android 13+ 处理。
- [ ] 新增紧凑弹窗 Activity。
  - 建议文件：
    - `app/src/main/java/dev/ignotus/sonyrebuild/QuickPopupActivity.kt`
    - `app/src/main/java/dev/ignotus/sonyrebuild/ui/screen/QuickPopupScreen.kt`
  - 内容：
    - 设备名。
    - 左/右/盒或单电量。
    - NC/环境声/关闭三态。
    - 播放/暂停、上一曲、下一曲。
    - “更多”进入主 App 设备页。
- [ ] 新增通知 action。
  - 打开快捷弹窗。
  - 断开或停止服务。
  - 刷新状态。
- [ ] 新增普通 App 设置项。
  - 是否启用后台服务。
  - 是否启用连接弹窗。
  - 是否启用常驻通知。
  - 是否允许锁屏显示。

验收：

- [ ] 不启用 LSPosed 时，连接耳机后能显示标准通知。
- [ ] 点击通知能打开紧凑弹窗。
- [ ] 紧凑弹窗能读取当前状态并执行至少 NC/ASM 切换。
- [ ] 耳机断开后通知消失或进入明确 disconnected 状态。
- [ ] 设备重连后状态能恢复。

## 5. 阶段 P3：模块骨架，只读探测

风险：中  
目标：加入 LSPosed 模块，但不改变系统行为。

任务：

- [ ] 决定模块承载形式。
  - 方案 A：在 `app` 内增加 LSPosed 元数据和 hook 入口。
  - 方案 B：新增 `lsposed-module` 独立模块。
  - 推荐方案 B，便于隔离依赖和签名策略。
- [ ] 接入 hook 框架。
  - 可选：YukiHookAPI。
  - 添加 `xposed_init` 或框架要求的 init 资源。
  - 添加 `xposedmodule`、`xposeddescription`、`xposedminversion`、`xposedscope`。
- [ ] 建立 hook 入口。
  - `HookEntry`
  - `BluetoothProcessHook`
  - `XiaomiBluetoothHook`
  - `SystemUiHook`
  - `SettingsHook` 先占位，不启用行为。
- [ ] scope 首版只启用：
  - `com.android.bluetooth`
  - `com.xiaomi.bluetooth`
  - `com.android.systemui`
- [ ] 每个 hook 只记录：
  - 当前进程。
  - class loader。
  - 目标类是否存在。
  - 目标方法是否存在。
  - hook 是否触发。
- [ ] 增加模块状态页。
  - 使用现有 `SettingsRoute.Modules`。
  - 展示：
    - 模块是否安装。
    - 模块是否启用。
    - 作用域是否可能完整。
    - 最近 hook 心跳时间。
    - 当前 ROM 兼容等级。

验收：

- [ ] 安装模块、启用作用域、重启后系统无崩溃。
- [ ] App 的 Modules 页能看到 hook 探测状态。
- [ ] 禁用模块后主 App 仍完全可用。
- [ ] hook 探测失败时只记录日志，不改变系统行为。

## 6. 阶段 P4：模块和 App 的安全通信桥

风险：中  
目标：系统进程能安全打开 App、读取少量状态、触发少量命令。

任务：

- [ ] 设计跨进程通信方式。
  - 优先级：
    1. 显式 `Intent` 打开 Activity。
    2. 受权限保护的 `BroadcastReceiver`。
    3. 绑定 Service / AIDL。
    4. ContentProvider 只用于只读状态。
- [ ] 定义 action 命名空间。
  - 示例：
    - `dev.ignotus.sonyrebuild.action.SHOW_QUICK_POPUP`
    - `dev.ignotus.sonyrebuild.action.REQUEST_STATE`
    - `dev.ignotus.sonyrebuild.action.SET_NOISE_CONTROL`
- [ ] 所有外部入口使用显式包名和组件名。
- [ ] 对写入命令增加来源校验。
  - 只接受本应用签名权限或明确白名单。
  - 不接受任意第三方广播直接改耳机状态。
- [ ] 增加失败回退。
  - Service 未运行时先启动服务或只打开 Activity。
  - 状态不可用时显示 loading / disconnected。

验收：

- [ ] SystemUI 进程通过显式 Intent 能打开 `QuickPopupActivity`。
- [ ] 模块无法直接崩溃 App 进程。
- [ ] 外部伪造广播不能直接执行写入命令。
- [ ] App 未启动时也能打开快捷弹窗并进入连接/等待状态。

## 7. 阶段 P5：连接弹窗和通知的 HyperOS 集成

风险：中到高  
目标：把 P2 的 App 级通知升级为 HyperOS 风格，但保持可降级。

任务：

- [ ] 在 `com.xiaomi.bluetooth` 中 hook 蓝牙通知相关类。
  - 先只注册 receiver。
  - 不替换系统原有通知。
- [ ] 从 app Service 广播精简状态给 `com.xiaomi.bluetooth` hook。
  - 设备名。
  - MAC hash 或显式 MAC，注意隐私。
  - 电量。
  - 是否充电。
  - ANC 当前模式。
- [ ] 尝试创建 HyperOS 风格耳机通知。
  - 不可用时回退标准通知。
- [ ] 尝试触发 Focus Island / 强提示。
  - 只在连接事件触发。
  - 避免状态刷新反复弹出。
- [ ] 增加用户开关。
  - HyperOS 连接弹窗。
  - HyperOS 耳机通知。
  - 标准通知 fallback。

验收：

- [ ] 连接耳机时最多弹出一次系统风格提示。
- [ ] 电量变化能更新通知内容。
- [ ] 断开后通知正确取消。
- [ ] 目标类不存在时自动回退标准通知。
- [ ] 系统蓝牙原生功能不被破坏。

## 8. 阶段 P6：SystemUI 控制中心 / 设备卡片入口

风险：高  
目标：点击控制中心或设备中心的 Sony 耳机卡片时，打开 SonyRebuild 快捷弹窗。

任务：

- [ ] 在 `com.android.systemui` 中 hook 插件 class loader。
  - 识别小米 SystemUI plugin class loader。
  - 只在目标插件加载后安装二级 hook。
- [ ] hook 控制中心设备卡片点击方法。
  - 读取设备 id / MAC / device type。
  - 只拦截 Sony 耳机。
  - 非 Sony 设备立即放行。
- [ ] 设备身份匹配。
  - App 服务维护当前 Sony 设备 MAC。
  - SystemUI hook 点击时向 App 查询或接收最近状态。
  - 避免通过阻塞等待造成 SystemUI 卡顿。
- [ ] 点击后打开 `QuickPopupActivity`。
  - 使用 `FLAG_ACTIVITY_NEW_TASK`。
  - 可选：隐藏控制中心面板。
- [ ] 增加开关。
  - 控制中心卡片接管。
  - 点击后隐藏控制中心。

验收：

- [ ] 点击当前 Sony 耳机卡片打开快捷弹窗。
- [ ] 点击其他蓝牙设备仍保持系统默认行为。
- [ ] App 不在线时不阻塞 SystemUI。
- [ ] SystemUI hook 失败时没有系统 UI 崩溃或卡死。

## 9. 阶段 P7：状态栏耳机图标和系统状态展示

风险：高  
目标：在支持的 HyperOS 版本上显示更接近原生耳机状态的系统 UI。

任务：

- [ ] 调研状态栏耳机图标来源。
  - 是系统蓝牙状态图标。
  - 还是小米蓝牙通知 extras。
  - 还是 SystemUI plugin 内部状态。
- [ ] 首版只影响 SonyRebuild 自己创建的通知 extras。
- [ ] 若需要 hook SystemUI 状态控制器，先只做只读日志。
- [ ] 添加版本白名单。
  - 未知版本默认关闭。
- [ ] 添加开关。
  - 状态栏图标增强。
  - 实验性 SystemUI 状态 hook。

验收：

- [ ] 支持版本上连接 Sony 耳机后能显示耳机图标或等效系统提示。
- [ ] 断开后图标消失。
- [ ] 未知版本默认不启用实验 hook。
- [ ] 无法显示时保留常驻通知作为 fallback。

## 10. 阶段 P8：系统蓝牙设置页入口

风险：很高  
目标：先做到“系统设备详情页出现 SonyRebuild 入口”，不要一开始做完整嵌入式设置页。

任务：

- [ ] 把 `com.android.settings` 或小米设置包加入可选 scope。
  - 默认不启用。
  - Modules 页提示用户这是实验功能。
- [ ] 调研系统蓝牙设备详情页类。
  - Fragment / Activity 名。
  - Preference screen 构建时机。
  - 当前设备对象读取方式。
- [ ] 只对 Sony 设备插入入口。
  - 标题：Sony Sound settings / SonyRebuild。
  - summary：电量、ANC 当前状态或“打开 SonyRebuild”。
  - 点击：打开主 App 设备页或快捷弹窗。
- [ ] 不在第一版直接嵌入复杂 Compose UI。
- [ ] 不覆盖系统原有蓝牙设置项。

验收：

- [ ] Sony 耳机设备详情页出现 SonyRebuild 入口。
- [ ] 非 Sony 设备不出现入口。
- [ ] 点击入口能进入 App 对应设备页。
- [ ] Settings hook 失败时系统设置不崩溃。

## 11. 阶段 P9：系统设置页内嵌控制

风险：最高  
目标：在系统蓝牙详情页中逐步加入原生风格控制项。

任务：

- [ ] 按功能逐项加入，不做一次性大页面。
  - 第一批：电量只读、ANC 三态。
  - 第二批：环境声等级、关注语音。
  - 第三批：EQ preset、Clear Bass。
  - 第四批：Quick Access、佩戴检测。
  - 第五批：LE Audio 策略、连接质量、多点连接。
- [ ] 每个设置项都必须有 capability gating。
- [ ] 每个写入项都必须有失败反馈。
- [ ] 长耗时操作不在 Settings/SystemUI 主线程执行。
- [ ] 设置页只展示稳定功能；实验功能仍跳转 App 内页。

验收：

- [ ] 系统设置页内 ANC 写入成功并能反映状态回读。
- [ ] 状态不可用时控件禁用而不是误写。
- [ ] 系统设置页退出/重进后状态一致。
- [ ] 系统更新导致 hook 失效时自动隐藏内嵌控制项。

## 12. 测试计划

基础测试：

- [ ] `.\gradlew.bat testDebugUnitTest`
- [ ] `.\gradlew.bat assembleDebug`
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

## 13. 发布和开关策略

建议所有系统集成功能都挂在 Settings > Modules：

- [ ] 模块总开关。
- [ ] 后台服务开关。
- [ ] 标准通知开关。
- [ ] 连接弹窗开关。
- [ ] HyperOS 通知增强开关。
- [ ] Control Center 卡片接管开关。
- [ ] 状态栏增强开关。
- [ ] 系统设置入口开关。
- [ ] 实验性系统设置内嵌控制开关。

默认值：

- 标准通知：开。
- 连接弹窗：关，首次引导用户开启。
- HyperOS 通知增强：关，检测到兼容后建议开启。
- Control Center 接管：关。
- 状态栏增强：关。
- 系统设置入口：关。
- 系统设置内嵌控制：关。

## 14. 风险和回退

主要风险：

- HyperOS 类名和方法名随版本变化。
- SystemUI hook 失败可能影响控制中心稳定性。
- Settings hook 失败可能影响系统设置稳定性。
- 蓝牙系统进程 hook 失败可能影响连接稳定性。
- GPL 参考项目代码不能直接混入当前许可未知的 clean-room 实现。

回退要求：

- 每个 hook 必须包在 try/catch 和 class-exists guard 中。
- 每个系统集成功能必须有独立开关。
- 未知 ROM 版本默认关闭高风险功能。
- App 标准通知和主 UI 永远作为 fallback。
- 模块禁用后不能留下系统侧脏状态。

## 15. 建议提交顺序

1. `docs: add LSPosed system integration task plan`
2. `refactor: introduce Sony control core interfaces`
3. `feat: add headphone foreground service`
4. `feat: add quick popup activity`
5. `feat: add module diagnostics settings page`
6. `feat: scaffold LSPosed module`
7. `feat: add read-only HyperOS hook probes`
8. `feat: bridge module actions to SonyRebuild service`
9. `feat: add HyperOS notification integration`
10. `feat: intercept compatible control center device card`
11. `feat: add experimental status bar integration`
12. `feat: add experimental Bluetooth settings entry`
13. `feat: add experimental embedded settings controls`

