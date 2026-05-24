# 功能状态

本文档记录 SonyRebuild 当前功能覆盖范围和后续开发优先级。

## 已实现

### 连接和诊断

- BLE 扫描。
- bonded/connected/profile 设备枚举。
- Sony audio advertisement 解析。
- 普通端点优先连接。
- SPP Tandem 控制链路，包含 Table1 `DATA_MDR` 和 Table2 `DATA_MDR_NO2` 双向映射。
- GATT Tandem V2 HPC 握手路径。
- HPC 握手成功后的 V2 MC / V1 MC endpoint 发现、channel-aware 写入路由、串行 FROM_ACC 通知订阅和 RX channel 标注。
- `LE_` 端点 unsupported probe 和诊断显示。
- UI debug log 和 logcat 输出。
- 当前连接 profile、adapter、transport 状态显示。

### 设备适配

- 通用 Headphone adapter/profile/capability 抽象。
- Sony Tandem adapter。
- 功能级 `FeatureProtocolBinding`，声明 protocol variant、Tandem channel、查询类型和可写类型。
- Tandem codec registry，统一 V1/V2 TableSet1/TableSet2 build/parse 入口。
- Sony Tandem V1 TableSet1 codec。
- Sony Tandem V1 TableSet2 codec。
- Sony Tandem V2 TableSet1 codec。
- Sony Tandem V2 TableSet2 codec。
- LinkBuds S profile。
- WF-1000XM5 profile（V2 Table1，功能等同 LinkBuds S）。
- WH-1000XM4 profile。
- 按 profile 生成刷新命令。
- 按 capability 启用或禁用 UI 控制和写入操作。

### 设备信息

- 协议 ready 状态。
- 型号。
- 固件版本。
- 系列/颜色。
- 型号图片 URL 匹配。

### 电量

- 左耳电量。
- 右耳电量。
- 充电盒电量。
- 单电量字段，用于 WH-1000XM4 等头戴式设备。

### 降噪/环境声

- 降噪。
- 环境声。
- 关闭。
- 环境声等级 1-20。
- 关注语音。

### 播放控制

- 上一曲。
- 播放/暂停。
- 下一曲。
- Tandem-first dispatch：静态 Sony profile 优先通过 adapter `buildPlaybackCommands()` 发送 Tandem 播放命令；`PlayInquiredType` 按型号配置（V1 设备 `PLAYBACK_CONTROL_WITH_CALL_VOLUME_ADJUSTMENT`，V2 设备 `PLAYBACK_CONTROL_WITH_FUNCTION_CHANGE`）。
- 播放状态跨验证：`isUnsolicited` 字段区分 `RET_STATUS(0xA3)` 和 `NTFY_STATUS(0xA5)`，非主动通知时用 `AudioManager.isMusicActive` 交叉验证防止 stale PAUSED。
- 播放 session 心跳：30s 间隔 GET playback status，防止耳机 watchdog 超时误报 PAUSED。
- Tandem 不可用时 fallback 到 Android media key。
- 基础播放状态显示。

### EQ

- 官方预设。
- 手动。
- 自定义 1。
- 自定义 2。
- Clear Bass。
- 400 Hz。
- 1 kHz。
- 2.5 kHz。
- 6.3 kHz。
- 16 kHz。
- 设备无关引擎 `EqProtocolEngine`：`EqDeviceConfig` 声明于各 device profile，统一写入/刷新/解析路径；`ClearBassWriteMode` 区分 `PRESET_EQ_BANDS`（Clear Bass 随全 band 数组写入）和 `EBB_PARAM`（独立 Clear Bass 命令）。

WH-1000XM4：EQ 使用 V1 `PRESET_EQ` type code `0x01` 读取 active preset 与 6 个 raw band；raw band 0 是 Clear Bass。刷新会追加 `0E 5A 01` 扩展 band 信息读取，具体 band/Clear Bass 写入使用 `0E 58 01 FF ...`，Clear Bass 模式为 `PRESET_EQ_BANDS`。

### LE Audio / 系统状态

- LE Audio 状态读取：enabled、per-ear streaming（L/R 独立）。
- LE Audio 配对历史读取。
- Quick Access L/R Key 和 NC/AMB Key 功能状态读取。
- 佩戴检测状态：佩戴状态和贴合结果。

### UI 和设置

- Home / Device / Settings / About 四页结构。
- 连接前 Device 页显示已知设备和扫描设备。
- 连接后 Device 页显示当前耳机控制项。
- 浅色、深色、跟随系统三种颜色模式，独立于 Material / MIUIX-like 主题样式。
- 深色 MIUIX 背景色修复。
- 统一 UI 能力层 `UiRenderCapabilities` 收敛 blur、backdrop、glass 和背景渐变开关。
- 底栏四种模式：Normal、Semi transparent、Floating、Liquid glass。
- 单一 `UI effects` 开关控制视觉特效，不再按设备厂商封锁特效。
- Liquid glass 底栏使用 kyant backdrop / blur / lens / vibrancy；Semi transparent 底栏只使用 REAREye 同类 `textureBlur` 背景，不启用卡片玻璃。
- 卡片和 About 顶部 logo 在 Liquid glass + UI effects 开启时使用 `textureBlur` 链路；About 顶栏 acrylic / haze 跟随全局 `UI effects` 和 render-effect 支持，不依赖卡片玻璃是否启用。颜色跟随当前 `MaterialTheme.colorScheme`，关闭特效时回到普通 Compose surface。
- Miuix `textureBlur` source 与 consumer 已按 REAREye 拓扑拆分：source-only 背景层只绘制背景，前景 `GlassCard` / `BlurredBar` 只消费 backdrop，避免全局 `textureLayerBackdrop` 捕获整棵前景内容导致 native blur 递归崩溃。
- DataStore 持久化 UI 设置。
- 冷启动阶段先应用透明系统栏，再进入 Compose UI，减少状态栏玻璃效果首帧延迟。
- Settings > Appearance 切换 Material / MIUIX 时保留二级路由栈，避免主题重组把页面弹回 Settings 根页，同时避免底栏显隐状态丢失。
- REAREye 风格悬浮底栏和液态玻璃选中层。
- 液态玻璃拖动按触点偏移修正，选中层跟随手指中心。
- 设置 overlay 子页面隐藏底栏时，底栏点击、长按 quick action 和液态拖拽交互同步禁用，避免透明底栏拦截内容区输入。
- Appearance / 颜色模式 / Theme 分段选择控件改为可换行布局，窄屏和大字体下不再硬挤单行。
- About 顶部渐隐标题和背景渐变。
- Settings / About 根卡片尾部装饰 badge 已清理。
- `UiEffectsPolicyTest` 已覆盖半透明模式只影响底栏；UI smoke test 已覆盖 `FloatingGlass + Miuix + UI effects` 启动渲染，并通过 androidTest Kotlin 编译。
- 新增 smoke test 目录下的 color mode 切换回归测试，覆盖 Appearance 子页在主题重组后不应返回根页的行为约束。

## 部分实现

- 设备图片：当前使用云端 manifest 匹配远程 URL，并已增加进程内内存缓存；仍没有离线 fallback 图片包。
- 播放状态：已有 Tandem-first dispatch、`isUnsolicited` 交叉验证、30s 心跳维持、`PendingPlaybackStatus` optimistic UI 过渡和 stale response filtering（2.5s 窗口）；但没有完整 media session 订阅。
- GATT 控制：V2 HPC 可握手；HPC 握手后可使用 V2 MC/V1 MC endpoint 路由。V1 MC-only GATT 端点会进入 unsupported 诊断并标记为待验证，不会误报为可控。
- WH-1000XM4：已有完整 V1 TableSet1 静态 profile，device info、电量、NC/ASM、EQ、Clear Bass、播放控制均走 `GATT_V1_MC`；仍需要按新 profile 做真机逐项 TX/RX 回归。
- WF-1000XM5：已有完整 V2 TableSet1 静态 profile（功能配置等同 LinkBuds S）；需要真机逐项 TX/RX 验证。
- V1 TableSet2 / V2 TableSet2：已有 codec、parser、registry、MC/SPP data type 路径和最近响应的结构化 diagnostics state；当前只做只读解析/诊断，尚未开放具体 UI 写入功能。
- 液态玻璃 UI：已实现可选效果和拖动修正；视觉细节仍以真机截图/帧统计继续迭代。
- LE Audio：状态读取已实现（enabled、per-ear streaming L/R、配对历史），模式切换和多点 LE Audio 连接策略尚未开放。

## 未实现

### 连接和系统

- 多点连接管理。
- Classic Audio / LE Audio 连接方式切换。
- LE Audio 高可靠/低延迟模式。
- LE Audio 连接限制列表。
- 连接质量/音质优先级。
- 自动重连真实逻辑。
- 设备配对管理。
- V1 MC-only GATT 控制入口。

### 设备信息和电源

- 自动关机。
- 省电模式。
- STAMINA。
- Caring Charge。
- 安全聆听。
- 最大音量限制。
- BT standby。

### 声音和听感

- ULT。
- Sound Effect 其他类型。
- Sound Field Optimization。
- TV Sound Booster。
- VPT / Upmix。
- 背景音乐效果。
- 声音泄露降低。

### 降噪和自适应

- Sense 自适应声音控制。
- Scene-based listening。
- Auto NC/ASM。
- NC optimizer。
- 风噪/气压/个人优化相关功能。

### 操作和系统设置

- 佩戴检测设置（状态读取已实现，写入未开放）。
- 摘戴控制播放。
- Quick Access 分配设置（状态读取已实现，写入未开放）。
- 触控/按键分配。
- 语音助手。
- 语音唤醒词。
- 头部手势。
- 头部追踪。
- Auto Volume。
- Smart Talking。
- 语音提示语言和音量。
- 重复点击训练。

### 高级功能

- AutoPlay。
- SAR / Sound AR。
- Link Auto Switch。
- FOTA / 固件更新。
- 日志/遥测相关功能。
- USB browser。
- 灯光/DJ/Karaoke 等非耳机功能。

## 建议优先级

1. WH-1000XM4 真机 TX/RX 回归：电量、NC/ASM、EQ、播放控制。
2. WF-1000XM5 真机 TX/RX 回归：全 profile（V2 Table1，等同 LinkBuds S 配置）。
3. LinkBuds S 真机回归，确认 V1/V2 分层后无回退。
4. LE Audio 连接方式和低延迟模式切换，带确认和重连恢复（状态读取已完成）。
5. 佩戴检测写入和摘戴播放控制（状态读取已完成）。
6. 多点连接状态读取。
7. Quick Access 写入和分配（状态读取已完成）。
8. 语音助手设置。
9. 安全聆听和最大音量限制。
10. FOTA 只做版本和可用状态展示，不实现固件下载/刷写。
