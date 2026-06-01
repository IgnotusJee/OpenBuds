# OpenBuds 源代码完整文档

> 生成日期: 2026-05-31 | 共 44 个 Kotlin 源文件
> 分支: `feat/milink-sony-card` | 最新提交: `9f97ed1` (2026-05-31) | 提交数: 72

---

## 目录

1. [根级文件](#1-根级文件)
2. [BLE 传输层 (`ble/`)](#2-ble-传输层-ble)
3. [协议层 (`protocol/`)](#3-协议层-protocol)
4. [数据层 (`data/`)](#4-数据层-data)
5. [耳机适配层 (`headphones/`)](#5-耳机适配层-headphones)
6. [设备 Profile (`headphones/sony/devices/`, `headphones/qcy/devices/`)](#6-设备-profile)
7. [媒体控制 (`media/`)](#7-媒体控制-media)
8. [后台服务 (`service/`)](#8-后台服务-service)
9. [LSPosed 模块 (`lsposed/`)](#9-lsposed-模块-lsposed)
10. [广播接收器 (`receiver/`)](#10-广播接收器-receiver)

---

## 1. 根级文件

### `OpenBudsApplication.kt`

**包**: `dev.ignotus.openbuds`

Application 子类，作为全局异常处理器。

| 类/函数 | 描述 |
|---------|------|
| `OpenBudsApplication` | 继承 `Application`，实现 `Thread.UncaughtExceptionHandler` |
| `onCreate()` | Application 初始化入口 |
| `uncaughtException(t, e)` | 捕获未处理异常：记录日志、持久化崩溃计数和最后崩溃信息到 SharedPreferences，然后 `Process.killProcess()` 退出 |

---

### `MainActivity.kt`

**包**: `dev.ignotus.openbuds`

调试/开发用 Activity，使用程序化 View（非 Compose）展示 BLE 扫描和设备连接状态。

| 类/函数 | 描述 |
|---------|------|
| `MainActivity` | `ComponentActivity`，持有 `HeadphoneRepository` 引用 |
| `onCreate(savedInstanceState)` | 构建 LinearLayout UI：状态文本、扫描按钮、断开按钮、已发现设备列表；请求蓝牙权限；绑定 `SonyControlService`；订阅 `repository.state` Flow 更新 UI |
| `toggleScan()` | 切换 BLE 扫描启停 |
| `updateUi(state)` | 将 `HeadphoneUiState` 渲染到文本 UI：连接状态、MAC、RSSI、协议就绪、电量、NC 模式、EQ 预设 |
| `requestPermissions()` | 按 API 级别请求 `BLUETOOTH_SCAN`/`BLUETOOTH_CONNECT`/`ACCESS_FINE_LOCATION`/`POST_NOTIFICATIONS` |
| `bindSonyService()` | 绑定 `SonyControlService`，获取 `LocalBinder` 引用 |
| `handleDebugIntent(intent)` | 仅 debug 构建：解析 `debug_connect_address`/`debug_action`/`debug_raw_hex` 额外参数，执行连接或调试操作 |
| `onDestroy()` | 解绑服务 |

---

## 2. BLE 传输层 (`ble/`)

### `HeadphoneTransportClient.kt`

**包**: `dev.ignotus.openbuds.ble`

传输层抽象接口，使 `HeadphoneRepository` 可以统一操作 Sony 和 QCY 两种品牌的 BLE 客户端。

| 类/接口 | 描述 |
|---------|------|
| `HeadphoneTransportClient` | 接口，每个品牌实现一个 |
| `id` | 稳定标识符：`"sony-tandem"` 或 `"qcy-gatt"` |
| `matches(device, reportedModelName)` | 判断此客户端是否应处理该设备 |
| `startScan(strictFilter)` | 启动 BLE 扫描 |
| `stopScan()` | 停止 BLE 扫描 |
| `connect(device)` | 连接到设备 |
| `disconnect()` | 断开并释放 GATT 资源 |
| `sendToChannel(channel, bytes)` | 在指定通道上发送字节 |
| `availableChannels()` | 返回当前可用的 Tandem 通道集合 |
| `refreshUnsupportedEndpointProbe()` | 重新运行不支持的端点探测 |
| `HeadphoneTransportListener` | `SonyBleClientListener` 的类型别名，仓库实现此接口以接收回调 |

---

### `HeadphoneTransportSelector.kt`

**包**: `dev.ignotus.openbuds.ble`

多客户端路由器：按设备品牌将 connect/disconnect/send 调用路由到正确的 `HeadphoneTransportClient`，并聚合去重扫描结果。

| 类/函数 | 描述 |
|---------|------|
| `HeadphoneTransportSelector(clients, listener)` | 构造函数，接受客户端列表和共享的监听器 |
| `activeClient` | 最近一次 `connect()` 使用的客户端 |
| `pickFor(device, reportedModelName)` | 返回第一个 `matches()` 返回 true 的客户端 |
| `startScan(strictFilter)` | 启动所有客户端的扫描（QCY 为 no-op，Sony 驱动实际扫描） |
| `stopScan()` | 停止所有客户端的扫描 |
| `isDuplicateScanResult(device)` | MAC 去重：5 秒窗口内同一 MAC 只放行一次 |
| `connect(device)` | 选择正确客户端并连接，设置 `activeClient` |
| `connect(address, name)` | 通过原始 MAC/名称连接 |
| `disconnect()` | 断开所有客户端 |
| `sendToChannel(channel, bytes)` | 委托给 `activeClient` |
| `availableChannels()` | 返回当前活动客户端的可用通道 |
| `refreshUnsupportedEndpointProbe()` | 委托给 `activeClient` |

---

### `ble/sony/SonyBleClient.kt`

**包**: `dev.ignotus.openbuds.ble.sony`

Sony Tandem 的 BLE 客户端实现。包含设备发现（BLE 扫描 + Sony Audio AD 解析）、GATT 连接握手（MTU 协商、通知启用）、SPP 传输路径、以及不支持的端点诊断探测。

**数据类**:

| 类 | 描述 |
|----|------|
| `DiscoveredSonyDevice` | 发现的设备：名称、地址、RSSI、来源、蓝牙类型、广告服务、是否为控制端点、Sony AD 信息 |
| `SonyAudioAdvertisement` | Sony Audio AD V2 解析结果：版本、Android 传输线路、GATT 能力、音频流类型、LE GATT 控制标志、型号 ID、经典蓝牙哈希 |
| `SonyBleConnectionInfo` | 连接信息：MTU、可写值长度、最优 MTU、传输类型 |
| `GattTandemEndpoint` | GATT Tandem 端点：通道类型、toAcc 特征、fromAcc 特征 |
| `UnsupportedEndpointDiagnostics` | 不支持的端点诊断：原因、服务标签、LE Audio 兼容性、友好名称、公网地址、原始读取数据 |

**核心类**:

| 类/函数 | 描述 |
|---------|------|
| `SonyBleClientListener` | 回调接口：蓝牙不可用、不支持的端点、设备发现、扫描状态变化、连接状态变化、就绪、收到消息、日志 |
| `SonyBleClient(context, listener)` | 构造函数，接收 Android Context 和监听器 |
| `startScan(strictFilter)` | 启动 BLE 扫描（SCAN_MODE_LOW_LATENCY），同时枚举已知设备（已配对、已连接 GATT/A2DP/HEADSET） |
| `stopScan()` | 停止 BLE 扫描 |
| `connect(device)` | 连接到设备：判断应使用 SPP 还是 GATT 路径，执行相应的连接流程 |
| `connect(address)` | 通过纯 MAC 地址连接 |
| `disconnect()` | 关闭 GATT 连接并通知 |
| `send(bytes)` | 发送字节到默认写入通道 |
| `sendToChannel(channel, bytes)` | 发送字节到指定通道 |
| `availableChannels()` | 返回当前可用端点集合（SPP_MDR + 已发现的 GATT 端点） |
| `refreshUnsupportedEndpointProbe()` | 在不支持的端点上重新运行诊断探测 |
| `tandemEndpointSupportState(services)` | 静态函数：检查服务列表中是否包含 Tandem 控制服务 |
| `unsupportedTandemEndpointReason(services)` | 静态函数：解释为何端点不受支持 |

**内部流程**:

| 步骤 | 函数 | 描述 |
|------|------|------|
| 扫描回调 | `scanCallback.onScanResult()` | 解析 BLE 扫描结果：提取 Sony Audio AD、QCY 服务、制造商数据，过滤候选设备 |
| GATT 连接 | `gattCallback.onConnectionStateChange()` | 连接成功 → `discoverServices()`，断开 → 清理状态 |
| 服务发现 | `gattCallback.onServicesDiscovered()` | 定位 Tandem V2 HPC/V2 MC/V1 MC 服务，注册 GATT 端点 |
| 握手 | `beginTandemHandshake()` → `requestLargeMtu()` → `enableDetermineMtuNotifications()` → `readWritableValueLength()` → `enableTandemNotifications()` | 完整 GATT 握手流程：读 OPTIMAL_MTU → 请求 MTU → 启用 DETERMINE_MTU 通知 → 读 WRITABLE_VALUE_LENGTH → 禁用 MTU 通知 → 启用所有 Tandem fromAcc 通知 |
| SPP 连接 | `connectSpp()` | 新建线程：创建 RFCOMM socket → 连接 → 创建 `SonySppTransport` → 启动读循环 |
| 写入队列 | `drainWriteQueue()` | 从 `ConcurrentLinkedQueue` 取出待发送帧，通过 GATT `writeCharacteristic` 发送，串行化写入避免并发冲突 |
| Sony Audio AD 解析 | `parseSonyAudioV2Advertisement()` | 解析 V2 格式的 Sony Audio 制造商数据：分块类型 0x00（基本信息/型号 ID）、0x03（Tandem 传输线路）、0x05（经典蓝牙哈希） |

---

### `ble/sony/SonySppTransport.kt`

**包**: `dev.ignotus.openbuds.ble.sony`

Sony SPP (RFCOMM) 传输层实现。处理帧封装/拆包、转义、校验和、ACK 重试。

| 类/函数 | 描述 |
|---------|------|
| `SonySppTransport(socket, onPayload, onClosed, log)` | 构造函数，接收 BluetoothSocket、payload 回调、关闭回调、日志函数 |
| `start()` | 启动读线程 `readLoop()` |
| `send(tandemBytes)` | 将 Tandem 字节通过 `SonySppPayloadMapper.outboundFromTandemBytes` 转为 SPP payload 并入队 |
| `close()` | 关闭 input/output stream 和 socket，标记已关闭 |
| `readLoop()` | 主读循环：按 `FRAME_START(0x3E)` / `FRAME_END(0x3C)` 分帧，调用 `handleFrame()` |
| `handleFrame(escapedBody)` | 帧处理：unescape → 校验 checksum → 解析 header（type/sequence/length）→ 按帧类型分发：ACK 帧解锁等待、DATA 帧 ack 并回调 payload |
| `drainWrites()` | 从写入队列取帧：编码 → 写入 output stream → 如果需要 ACK，调度超时重试 |
| `scheduleAckTimeout(expectedAck, generation)` | ACK 超时线程：等待 1.2 秒后若未收到 ACK，重发帧（最多 1 次重试），仍失败则关闭传输 |
| `sendAck(sequence)` | 发送 ACK 帧（inverse sequence） |
| `encodeFrame(type, sequence, payload)` | 帧编码：[FRAME_START] + escape(body) + [FRAME_END]；body = type(1) + seq(1) + length(4 BE) + payload(N) + checksum(1) |
| `escape(bytes)` / `unescape(bytes)` | 转义/反转义：`0x3C→0x3D+0x2C, 0x3D→0x3D+0x2D, 0x3E→0x3D+0x2E`；unescape 用 `or 0x10` 反转 |
| `checksum(bytes, length)` | 计算校验和：累加取低 8 位 |

**常量**:

| 常量 | 值 | 描述 |
|------|-----|------|
| `FRAME_START` | `0x3E` | 帧起始标记 |
| `FRAME_END` | `0x3C` | 帧结束标记 |
| `ESCAPE` | `0x3D` | 转义字节 |
| `ACK_TIMEOUT_MS` | `1200` | ACK 超时毫秒数 |
| `MAX_ACK_RETRIES` | `1` | 最大 ACK 重试次数 |
| `HEADER_SIZE` | `6` | 帧头长度（type + seq + length(4)） |

---

### `ble/sony/TandemTransportRouting.kt`

**包**: `dev.ignotus.openbuds.ble.sony`

GATT 端点路由和 SPP payload 映射。

| 类/数据类 | 描述 |
|-----------|------|
| `PendingTandemWrite` | 待发送的 Tandem 写操作：通道 + 字节数组 |
| `TandemGattEndpointSpec` | GATT 端点规格：通道类型、服务 UUID、toAcc UUID、fromAcc UUID |
| `TandemGattRouting` | 单例对象，管理 Sony Tandem GATT 端点路由 |
| `SONY_GATT_CHANNELS` | Sony GATT 端点的集合：GATT_V2_HPC, GATT_V2_MC, GATT_V1_MC |
| `endpointSpecFor(channel)` | 返回指定通道的 GATT 端点规格（service/toAcc/fromAcc UUID 三元组） |
| `notificationOrder(channels)` | 返回按优先级排序的通知启用顺序（HPC 先于 MC） |
| `fromAccChannelFor(serviceUuid, characteristicUuid)` | 根据服务 UUID 和特征 UUID 反向查找通道类型 |
| `fromAccChannel(endpoints, serviceUuid, characteristicUuid)` | 在已注册端点中查找匹配的通道 |
| `SppPayloadMapping` | SPP payload 映射：帧类型 + payload 字节 |
| `SonySppFrameType` | SPP 帧类型枚举：DATA_MDR(0x0C), DATA_MDR_NO2(0x0E), ACK(0x01), SHOT_MDR(0x1C), SHOT_MDR_NO2(0x1E), LARGE_DATA_MDR(0x2C)；含 `ackRequired` 属性 |
| `SonySppPayloadMapper.outboundFromTandemBytes(bytes)` | Tandem 字节 → SPP 出站映射：按第一字节类型剥离 dataType 前缀 |
| `SonySppPayloadMapper.inboundToTandemBytes(type, payload)` | SPP 入站 → Tandem 字节映射：恢复 dataType 前缀（0x0E 或 0x0F） |

---

### `ble/qcy/QcyBleClient.kt`

**包**: `dev.ignotus.openbuds.ble.qcy`

QCY 耳机的 BLE GATT 客户端。使用串行化 GATT 操作队列，支持 CCCD 写入、特征读取、MTU 协商、写入操作。

| 类/函数 | 描述 |
|---------|------|
| `QcyBleClient(context, listener)` | 构造函数 |
| `matches(device, reportedModelName)` | 匹配 QCY 设备：名称含 "qcy" 或广告含 QCY 服务 UUID |
| `startScan(strictFilter)` | no-op：扫描由 Sony 客户端代理 |
| `connect(device)` / `connect(mac, deviceName)` | 连接：`connectGatt(mac, TRANSPORT_LE)` |
| `disconnect()` | 断开 GATT 连接，清理操作队列 |
| `sendToChannel(channel, bytes)` | 发送字节到 QCY 命令特征 (0x1001)；所有 QCY 通道的写入都路由到此特征 |
| `availableChannels()` | 返回 6 个 QCY 通道 |
| `getEffectiveMtu()` | 返回协商后 MTU（减 3 字节 ATT 开销） |
| `isConnected()` | 返回 GATT 连接和命令特征是否就绪 |
| `gattCallback` | GATT 回调：连接/断开、服务发现（定位 0xA001 服务、入队 CCCD + 读操作 + MTU 请求）、特征读/写/通知处理 |
| `GattOp` (sealed) | 串行化 GATT 操作：`EnableNotify`, `ReadChar`, `WriteChar` |
| `pumpQueue()` | 从 FIFO 队列取操作执行，一次一个，前一个回调触发下一个 |
| `maybeFireReady()` | 所有 CCCD 完成后 + MTU 协商完成（或超时回退）时触发 `onReady` |

---

## 3. 协议层 (`protocol/`)

### `HeadphoneResponse.kt`

**包**: `dev.ignotus.openbuds.protocol`

所有品牌解析响应的顶层 sealed interface。包含 Sony Tandem 和 QCY 的嵌套 sealed 子树，以及跨品牌包装器。

| 类 | 描述 |
|----|------|
| `ParsedHeadphoneResponse` | 顶层 sealed interface，所有品牌响应的父类型 |
| `ParsedHeadphoneResponse.SonyTandem` | 索尼 Tandem 响应的 sealed interface 子树 |
| `SonyTandem.DeviceInfo(type, text, raw)` | 设备信息响应 |
| `SonyTandem.CommonStatus(type, text, values, raw)` | 通用状态响应 |
| `SonyTandem.Battery(kind, values, raw)` | 电池响应 |
| `SonyTandem.EqEbb(type, enabled, preset, clearBass, bandSteps, values, raw)` | EQ/EBB 响应 |
| `SonyTandem.EqBandInfo(type, value)` | EQ 频段信息 |
| `SonyTandem.EqEbbExtendedInfo(type, bands, values, raw)` | EQ/EBB 扩展信息响应 |
| `SonyTandem.NoiseControl(type, values, enabled, ambientSoundEnabled, ambientLevel, ambientMode, controlMode, raw)` | 降噪控制响应 |
| `SonyTandem.PlaybackAck(values, status, isUnsolicited, raw)` | 播放确认响应 |
| `SonyTandem.LeaStatus(type, values, enabled, streamingStatusL, streamingStatusR, raw)` | LE Audio 状态 |
| `SonyTandem.LeaPairedHistoryStatus(type, values, pairedHistory, raw)` | LE Audio 配对历史 |
| `SonyTandem.QuickAccess(key, function, values, raw)` | 快速访问响应 |
| `SonyTandem.WearingStatus(status, result, values, raw)` | 佩戴状态响应 |
| `SonyTandem.Unknown(dataType, command, payload, raw)` | 未知命令兜底 |
| `SonyTandem.Table2Common(family, command, values, raw)` | Table2 通用响应（已知 family） |
| `SonyTandem.Table2Generic(family, inquiredType, values, raw)` | Table2 泛型响应（含 inquired-type） |
| `ParsedHeadphoneResponse.Qcy` | QCY 响应的 sealed interface 子树 |
| `Qcy.Battery(leftLevel, rightLevel, caseLevel, ...)` | QCY 电池响应 |
| `Qcy.NoiseControl(mode, noiseValue, raw)` | QCY ANC 响应 |
| `Qcy.EqData(eqType, masterGain, bands, raw)` | QCY EQ 数据 |
| `Qcy.DeviceInfo(leftFirmware, rightFirmware, raw)` | QCY 设备信息 |
| `Qcy.Volume(leftVolume, rightVolume, raw)` | QCY 音量 |
| `Qcy.FunctionStatus(inEarDetectionOn, transparencyOn, raw)` | QCY 功能状态 |
| `ParsedHeadphoneResponse.Batch(items, raw)` | 批量响应包装器（如 QCY 多 TLV 帧） |
| `QcyEqBand(frequency, gain, q, bandType)` | QCY EQ 频段数据 |
| `Byte.unsigned` | 扩展属性：Byte → 无符号 Int |
| `ByteArray.hexString()` | 扩展函数：字节数组 → 十六进制字符串 |
| `ByteArray.unsignedList()` | 扩展函数：字节数组 → 无符号 Int 列表 |
| `Byte.percentageOrNull()` | 扩展函数：字节值在 0..100 内则返回，否则 null |

---

### `HeadphoneEnums.kt`

**包**: `dev.ignotus.openbuds.protocol`

跨品牌的共享枚举定义。

| 枚举 | 描述 |
|------|------|
| `EqPresetId` | EQ 预设（含显示名称）：OFF, ROCK, POP, JAZZ, DANCE, EDM, ACOUSTIC, BRIGHT, EXCITED, MELLOW, RELAXED, VOCAL, TREBLE, BASS, SPEECH, HEAVY, CLEAR, HARD, SOFT, CUSTOM(0xA0), USER_SETTING1(0xA1), USER_SETTING2(0xA2), UNSPECIFIED(0xFF) |
| `AmbientSoundMode` | 环境声模式：NORMAL(0x00), VOICE(0x01) |
| `NoiseControlMode` | 降噪控制模式：OFF, NOISE_CANCELLING, AMBIENT_SOUND |
| `PlaybackStatus` | 播放状态：UNKNOWN, PLAYING, PAUSED, STOPPED |

---

### `protocol/sony/SonyTandemConstants.kt`

**包**: `dev.ignotus.openbuds.protocol.sony`

| 常量 | 值 | 描述 |
|------|-----|------|
| `DATA_MDR` | `0x0E` | Table1 数据类型（HPC 通道） |
| `DATA_MDR_NO2` | `0x0F` | Table2 数据类型（MC 通道） |

---

### `protocol/sony/SonyTandemEnums.kt`

**包**: `dev.ignotus.openbuds.protocol.sony`

Sony Tandem 协议的枚举定义（仅 Sony 专用枚举，共享枚举见 `HeadphoneEnums.kt`）。

| 枚举 | 描述 |
|------|------|
| `DeviceInfoType` | 设备信息类型：MODEL_NAME(0x01), FW_VERSION(0x02), SERIES_AND_COLOR_INFO(0x03), INSTRUCTION_GUIDE(0x04) |
| `CommonInquiredType` | 通用查询类型：CONCIERGE, CONNECTION_STATUS, AUDIO_CODEC, UPSCALING_EFFECT, BLE_SETUP, CONNECTION_ESTABLISHED_TIME, DEVICE_SPECIAL_MODE, SMART_PHONE_AND_CONNECTED_DEVICE_INFORMATION_FOR_CLASSIC, TANDEM_RECONNECTION_REQUEST, DISPLAY_FW_VERSION |
| `PowerInquiredType` | 电源查询类型：BATTERY(0x00), LEFT_RIGHT_BATTERY(0x01), CRADLE_BATTERY(0x02), AUTO_POWER_OFF(0x04), POWER_SAVE_MODE(0x06), STAMINA(0x0E) |
| `EqEbbInquiredType` | EQ/EBB 查询类型：PRESET_EQ(0x00), EBB(0x01), PRESET_EQ_NONCUSTOMIZABLE(0x02), PRESET_EQ_AND_ULT_MODE(0x03), PRESET_EQ_AND_ERRORCODE(0x04), SOUND_EFFECT(0x30), CUSTOM_EQ(0x31), TURN_KEY_EQ(0x32) |
| `EqBandInformationType` | 频段信息类型：NO_INFORMATION(0x00), HZ(0x01), KHZ(0x02), SPECIFIC_INFORMATION(0x10) |
| `EqPresetId` | EQ 预设（含显示名称）：OFF, ROCK, POP, JAZZ, DANCE, EDM, R_AND_B_HIP_HOP, ACOUSTIC, BRIGHT, EXCITED, MELLOW, RELAXED, VOCAL, TREBLE, BASS, SPEECH, HEAVY, CLEAR, HARD, SOFT, CUSTOM(0xA0), USER_SETTING1(0xA1), USER_SETTING2(0xA2), UNSPECIFIED(0xFF) |
| `NcAsmInquiredType` | NC/ASM 查询类型（含 V1/V2 子类型）：V1_TABLE_SET1_NC_ASM(0x02), NC_ON_OFF(0x01), NC_ON_OFF_AND_ASM_ON_OFF(0x11), NC_MODE_SWITCH_AND_ASM_ON_OFF(0x12)... 等 12 种 |
| `PlaybackControl` | 播放控制：PAUSE(0x01), TRACK_UP(0x02), TRACK_DOWN(0x03), STOP(0x06), PLAY(0x07) |
| `PlayInquiredType` | 播放查询类型：PLAYBACK_CONTROL_WITH_CALL_VOLUME_ADJUSTMENT(0x01)... PLAY_MODE(0x40) |
| `LeaInquiredType` | LE Audio 查询类型：TWS/HBS 各版本 |
| `LeaEnableDisable` | LE Audio 启停：ENABLE(0x00), DISABLE(0x01), OUT_OF_RANGE(0xFF) |
| `LeaStreamingStatus` | 流媒体状态：POWER_OFF, NONE, VIA_A2DP, VIA_LE_AUDIO_UNICAST |
| `LeaPairedHistory` | 配对历史：BOTH_CLASSIC_BT_BLE, ONLY_CLASSIC_BT, ONLY_BLE |
| `SystemInquiredType` | 系统查询类型：WEARING_STATUS_DETECTOR(0x06), QUICK_ACCESS(0x0D) |
| `QuickAccessKey` | 快速访问键：L_R_KEY(0x00), NC_AMB_KEY(0x01), FIXED_QUICK_ACCESS_KEY(0x02) |
| `QuickAccessFunction` | 快速访问功能：NO_FUNCTION, NC_ASM_OFF, NC_ASM, PLAY_PAUSE, NEXT_TRACK, PREV_TRACK, VOLUME_UP/DOWN, VOICE_RECOGNITION 等 |
| `WearingDetectionStatus` | 佩戴检测状态：NOT_STARTED, STARTED, COMPLETED_SUCCESSFULLY, COMPLETED_UNSUCCESSFULLY |
| `WearingDetectionResult` | 佩戴检测结果：GOOD, POOR |
| `AmbientSoundMode` | 环境声模式：NORMAL(0x00), VOICE(0x01) |
| `NoiseControlMode` | 降噪控制模式：OFF, NOISE_CANCELLING, AMBIENT_SOUND |
| `PlaybackStatus` | 播放状态：UNKNOWN, PLAYING, PAUSED, STOPPED |

---

### `protocol/sony/SonyTandemTypes.kt`

**包**: `dev.ignotus.openbuds.protocol.sony`

Sony 专有的协议消息类型：TandemMessage 和 SonyTandemFrame。（品牌无关的 ParsedHeadphoneResponse 已移至 `HeadphoneResponse.kt`）

---

### `protocol/sony/SonyGatt.kt`

**包**: `dev.ignotus.openbuds.protocol.sony`

Sony BLE GATT 服务和特征 UUID 定义。所有索尼服务 UUID 格式为 `5b833eXX-6bc7-4802-8e9a-723ceca4bd8f`，特征 UUID 格式为 `5b833cXX-...`。

| 服务 UUID | 描述 |
|-----------|------|
| `BLUETOOTH_IAP_CONNECTION_SERVICE (0x06)` | 蓝牙 IAP 连接服务 |
| `TANDEM_V2_HPC_SERVICE (0x20)` | Tandem V2 高优先级通道服务 |
| `TANDEM_V2_MC_SERVICE (0x21)` | Tandem V2 多通道服务 |
| `TANDEM_V1_MC_SERVICE (0x23)` | Tandem V1 多通道服务 |
| `BLE_PAIRING_TWS_HPC_SERVICE (0x0D)` | BLE 配对 TWS HPC 服务 |

| 特征 UUID | 描述 |
|-----------|------|
| `TANDEM_HPC_TO_ACC (0x60)` | HPC 通道：App → 配件 |
| `TANDEM_HPC_FROM_ACC (0x61)` | HPC 通道：配件 → App |
| `TANDEM_MC_TO_ACC (0x62)` | MC 通道：App → 配件 |
| `TANDEM_MC_FROM_ACC (0x63)` | MC 通道：配件 → App |
| `OPTIMAL_MTU (0x94)` | 最优 MTU 值 |
| `WRITABLE_VALUE_LENGTH (0x91)` | 可写值长度 |
| `DETERMINE_MTU (0x93)` | MTU 确定通知 |
| `READY_TO_START_INITIAL_COMMUNICATION (0x90)` | 初始通信就绪 |

| 函数 | 描述 |
|------|------|
| `service(suffix)` | 构造服务 UUID |
| `characteristic(suffix)` | 构造特征 UUID |
| `serviceLabel(uuid)` | 服务 UUID → 可读标签 |
| `characteristicLabel(uuid)` | 特征 UUID → 可读标签 |

---

### `protocol/sony/SonyEqEbbPayloadParser.kt`

**包**: `dev.ignotus.openbuds.protocol.sony`

V1/V2 共享的 EQ/EBB payload 解析器。统一处理 `RET_STATUS`/`NTFY_STATUS`/`RET_PARAM`/`NTFY_PARAM` 四种命令中的 EQ/EBB 数据。

| 函数 | 描述 |
|------|------|
| `parse(version, command, payload, raw)` | 根据协议版本和命令字节解析 EQ/EBB payload：提取 inquired type、enabled 状态、preset、Clear Bass 水平、band steps |
| `parseExtendedInfo(version, payload, raw)` | 解析 EQ 扩展信息：频段数量、每个频段的类型和值 |
| `typeFor(version, code)` | 根据版本将字节码映射到 `EqEbbInquiredType` |
| `parsePreset(version, type, payload)` | 解析 EQ 预设：V1 支持 PRESET_EQ/PRESET_EQ_NONCUSTOMIZABLE；V2 额外支持 EBB/ULT_MODE/ERRORCODE |
| `bandCountOffset(version, type, payload)` | 计算频段数量字段在 payload 中的偏移 |
| `parseClearBass(version, type, payload, bandSteps)` | 解析 Clear Bass 值：V1 从 EBB payload 直接取；V2 可能需要从 band steps[0] 推断 |
| `v2EbbHasPresetField(payload)` | 启发式判断 V2 EBB payload 是否包含 preset 字段 |

---

### `protocol/sony/SonyTandemV2Table1Protocol.kt`

**包**: `dev.ignotus.openbuds.protocol.sony`

Sony Tandem V2 Table1 协议实现（HPC 通道，DataType 0x0E）。提供命令构造和响应解析，覆盖 CONNECT、COMMON、POWER、EQEBB、NCASM、PLAY、LEA、SYSTEM 八个命令族。

**命令族范围**:

| 功能 | 命令范围 | 示例 |
|------|---------|------|
| CONNECT | 0x00-0x07 | `buildGetProtocolInfo()`, `buildGetDeviceInfo(type)` |
| COMMON | 0x10-0x19 | `buildGetDisplayFirmwareVersion()` |
| POWER | 0x20-0x29 | `buildGetBatteryStatus(type)` |
| EQEBB | 0x50-0x5B | `buildGetEqEbbStatus(type)`, `buildSetEqPreset(preset, type, bandSteps)`, `buildSetClearBass(level)`, `buildGetEqEbbParam(type)`, `buildGetEqEbbExtendedInfo(type)` |
| NCASM | 0x60-0x69 | `buildGetNcAsmStatus(type)`, `buildGetNcAsmParam(type)`, `buildSetNoiseControlMode(mode, ambientLevel, ambientMode)`, `buildSetNcModeSwitchAndAmbientLevel(...)`, `buildSetNcOnOff(enabled)`, `buildSetAmbientSound(enabled, mode)`, `buildSetAmbientLevel(level, enabled, mode)` |
| PLAY | 0xA0-0xA9 | `buildGetPlaybackStatus(type)`, `buildPlayback(control, type)` |
| LEA | 0x40-0x49 | `buildGetLeaStatus(type)`, `buildGetLeaPairedHistory(type)` |
| SYSTEM | 0xF0-0xFD | `buildGetQuickAccess()`, `buildGetWearingStatus()` |

**核心函数**:

| 函数 | 描述 |
|------|------|
| `parse(raw)` | 主解析入口：标准化 dataType → 按 command 字节分发到各子解析器 |
| `parseDeviceInfo(payload, raw)` | 解析设备信息：型号名（长度前缀字符串）、固件版本、系列/颜色 |
| `parseCommonStatus(payload, raw)` | 解析通用状态：主要处理 DISPLAY_FW_VERSION |
| `parseBattery(payload, raw)` | 解析电池：单电池/左右电池/充电仓 |
| `parseNoiseControl(command, payload, raw)` | 解析降噪控制：12 种 NC/ASM inquired type 的 payload 解码，提取 controlMode/enabled/ambientLevel/ambientMode |
| `parsePlaybackStatus(payload)` | 解析播放状态：byte[2] → PLAYING/PAUSED/STOPPED |
| `parseLeaStatus(payload, raw)` | 解析 LE Audio 状态：启停、左右耳流媒体状态 |
| `parseLeaParam(payload, raw)` | 解析 LE Audio 参数：配对历史 |
| `parseQuickAccess(payload, raw)` | 解析快速访问：按键和功能映射 |
| `parseWearingStatus(payload, raw)` | 解析佩戴状态：检测状态和结果 |

---

### `protocol/sony/SonyTandemV2Table2Protocol.kt`

**包**: `dev.ignotus.openbuds.protocol.sony`

Sony Tandem V2 Table2 协议实现（MC 通道，DataType 0x0F）。覆盖 CONNECT、POWER、PERIPHERAL、VOICE_GUIDANCE、SAFE_LISTENING、LEA、PARTY、SYSTEM 八个命令族。

| 命令族 | 字节范围 | 枚举 |
|--------|---------|------|
| CONNECT | 0x06-0x07 | 支持功能查询 |
| POWER | 0x20-0x29 | `PowerInquiredTypeTable2`: AUTO_STANDBY, CARING_CHARGE_WITH_THRESHOLD, USB_SUBMERSION |
| PERIPHERAL | 0x30-0x3D | `PeripheralInquiredTypeTable2`: 多点配对管理、音源切换、音乐切换 |
| VOICE_GUIDANCE | 0x40-0x4F | `VoiceGuidanceInquiredTypeTable2`: 语言切换、音量、电池语音、开关机声音 |
| SAFE_LISTENING | 0x50-0x5B | `SafeListeningInquiredTypeTable2`: 安全听音模式、最大音量限制 |
| LEA | 0x60-0x69 | `LeaInquiredTypeTable2`: LE Audio 连接状态通知、切换兼容性 |
| PARTY | 0x70-0x7C | `PartyInquiredTypeTable2`: DJ 控制、灯光、Karaoke |
| SYSTEM | 0xF0-0xFD | `SystemInquiredTypeTable2`: 佩戴检测、手势训练、快速访问、SVA、USB、灯光 |

**核心函数**:

| 函数 | 描述 |
|------|------|
| `classifyFamily(command)` | 将命令字节归类到 `Table2Family` 枚举 |
| 各 `buildGet*Status`/`buildGet*Param` | 构造 GET 命令，接受对应的 inquired type 枚举 |
| `parse(raw)` | 主解析入口：按命令字节分发到各 family 的解析器 |
| 各 `parse*` 函数 | 解析 payload 首字节为 inquired type，返回 `Table2Generic` 响应 |

---

### `protocol/sony/SonyTandemV1Table1Protocol.kt`

**包**: `dev.ignotus.openbuds.protocol.sony`

Sony Tandem V1 Table1 协议实现。V1 与 V2 的关键差异：电池使用 `COMMON_GET_BATTERY_LEVEL(0x10)`（V2 用 `POWER_GET_STATUS(0x22)`）、NC/ASM 使用 `V1_TABLE_SET1_NC_ASM(0x02)` payload 格式、EQ/EBB 使用不同的 inquired type 字节码。

| 函数 | 描述 |
|------|------|
| `buildGetDeviceInfo(type)` | 等同 V2 |
| `buildGetBatteryStatus(type)` | 使用 COMMON_GET_BATTERY_LEVEL(0x10) |
| `buildGetNcAsmParam()` | V1 NC/ASM 参数查询 |
| `buildSetNoiseControlMode(mode, ambientLevel, ambientMode)` | V1 NC/ASM 设置：7 字节 payload，含 effect/setting/ncValue/asmSetting/ambientMode/asmLevel |
| `v1TypeCode(v2)` | V2 `EqEbbInquiredType` → V1 字节码转换 |
| `buildGetEqEbbStatus(type)` / `buildGetEqEbbParam(type)` / `buildSetEqPreset(...)` / `buildSetClearBass(level)` | V1 EQ/EBB 命令构造，使用 V1 字节码 |
| `buildGetPlaybackStatus()` / `buildPlayback(control)` | V1 播放控制 |
| `parse(raw)` | 主解析：0x05→设备信息、0x11→电池（V1 特有）、0x13→电池 NTFY（含启发式 payload 形状验证 `looksLikeV1BatteryPayload`）、0x67/0x69→NC 参数、0x53/0x55/0x57/0x59→EQ/EBB、0x5B→EQ 扩展信息、0xA3/0xA5→播放 |

---

### `protocol/sony/SonyTandemV1Table2Protocol.kt`

**包**: `dev.ignotus.openbuds.protocol.sony`

Sony Tandem V1 Table2 协议实现（MC 通道）。覆盖 PERIPHERAL（多点配对）和 VOICE_GUIDANCE（语音提示）两个命令族。

| 枚举 | 描述 |
|------|------|
| `PeripheralInquiredTypeV1Table2` | NO_USE(0x00), PAIRING_DEVICE_MANAGEMENT_CLASSIC_BT(0x01) |
| `VoiceGuidanceInquiredTypeV1Table2` | NO_USE(0x00), VOICE_GUIDANCE_SETTING(0x01) |

| 函数 | 描述 |
|------|------|
| `classifyFamily(command)` | 命令字节 → PERIPHERAL/VOICE_GUIDANCE/UNKNOWN |
| `buildGetPeripheralStatus(type)` / `buildGetPeripheralParam(type)` | 构造 Peripheral GET 命令 |
| `buildGetVoiceGuidanceStatus(type)` / `buildGetVoiceGuidanceParam(type)` | 构造 Voice Guidance GET 命令 |
| `parse(raw)` | 主解析入口：按命令分发到 Peripheral/Voice Guidance 解析器 |

---

### `protocol/qcy/QcyGatt.kt`

**包**: `dev.ignotus.openbuds.protocol.qcy`

QCY BLE GATT 服务和特征 UUID 定义。所有 QCY UUID 格式为 `XXXXXXXX-0000-1000-8000-00805F9B34FB`。

| 特征 UUID | 短 ID | 描述 |
|-----------|-------|------|
| `CHARACTER_SETTING_UUID` | `00001001` | Write — 主命令写入通道 |
| `CHARACTER_READSET_UUID` | `00001002` | Notify — TLV 命令响应通道 |
| `CHARACTER_EQ_UUID` | `0000000B` | Read/Write/Notify — EQ 数据 |
| `CHARACTER_BUTTON_UUID` | `0000000D` | Read/Write — 按键自定义 |
| `CHARACTER_BATTERY_UUID` | `00000008` | Notify — 电池信息 |
| `CHARACTER_VERSION_UUID` | `00000007` | Read — 固件版本 |
| `CHARACTER_SENDTIME_UUID` | `0000000C` | Write — 时间同步 |
| `CHARACTER_FUNCTION_UUID` | `0000000F` | Read/Notify — 功能开关状态 |
| `CHARACTER_LANGUAGE_UUID` | `00000009` | Read — 语音语言 |

---

### `protocol/qcy/QcyProtocol.kt`

**包**: `dev.ignotus.openbuds.protocol.qcy`

QCY TLV 帧序列化/反序列化和命令 ID 常量。

**帧格式**: `[0xFF] [PayloadLength] [CMD_ID:1] [DATA_LEN:1] [DATA:N]...`

| 数据类/函数 | 描述 |
|-------------|------|
| `QcyTlvEntry(cmdId, data)` | TLV 条目：命令 ID + 数据字节 |
| `QcyProtocol.buildFrame(entries)` | 构建多 TLV 帧 |
| `QcyProtocol.buildReadRequest(targetCmdId)` | 构建读请求帧：`[0xFF, 0x03, 0xFE, 0x01, targetCmdId]` |
| `QcyProtocol.buildSimpleCommand(cmdId, data)` | 构建单命令帧 |
| `QcyProtocol.buildSingleValueCommand(cmdId, value)` | 构建单字节值命令帧 |
| `QcyProtocol.parseFrame(raw)` | 解析 QCY 帧 → `List<QcyTlvEntry>`；严格验证 SOF 和长度 |
| `QcyProtocol.parseEqResponseNew(data)` | 解析新格式 EQ（CMD 34, 7 字节/band）→ `QcyEqResponse` |
| `QcyProtocol.parseEqResponseOld(data)` | 解析旧格式 EQ（CMD 32, 6 字节/band）→ `QcyEqResponse` |
| `QcyProtocol.buildEqCommandNew(eqType, masterGain, bands)` | 构建新格式 EQ 设置命令 |
| `QcyProtocol.buildEqCommandOld(eqType, masterGain, bands)` | 构建旧格式 EQ 设置命令 |
| `QcyEqResponse(eqType, masterGain, bands)` | 解析后的 EQ 响应数据类 |

**关键命令 ID**:

| 常量 | 值 | 描述 |
|------|-----|------|
| `CMDID_REQUESTDATA` | `0xFE` | 读请求包装器 |
| `CMDID_BATTERY` | `47 (0x2F)` | 电池查询 |
| `CMDID_VERSION` | `48 (0x30)` | 固件版本查询 |
| `CMDID_NOISE_MODE` | `12` | ANC 模式 |
| `CMDID_NOISE_VALUE` | `7` | ANC 噪声值 |
| `CMDID_MUSIC_ACTION` | `4` | 音乐控制 |
| `CMDID_MULTIEQ` | `32` | 旧格式 EQ |
| `CMDID_MULTIEQ2` | `34` | 新格式 EQ |
| `CMDID_VOLUME` | `8` | 音量 |
| `CMDID_RUER` | `6` | 入耳检测 |
| `CMDID_JIANTING` | `10` | 通透/监听模式 |

---

## 4. 数据层 (`data/`)

### `HeadphoneRepository.kt`

**包**: `dev.ignotus.openbuds.data`

应用的核心状态管理单例。持有所有 BLE 客户端和传输选择器，将原始协议响应聚合成 `HeadphoneUiState`，对外暴露操作动作（扫描、连接、断开、NC 控制、EQ 控制、播放控制）。

**UI 状态数据类**:

| 数据类 | 字段 |
|--------|------|
| `DeviceInfoState` | modelName, firmwareVersion, seriesAndColor, modelColor, modelImageUrl, modelImageSourceColor, protocolReady |
| `BatteryState` | single, left, right, cradle, raw |
| `NoiseControlState` | controlMode, noiseCancellingEnabled, ambientSoundEnabled, ambientLevel, ambientVoiceMode, raw |
| `EqState` | enabled, preset, presetType, clearBass, bandSteps, rawBandSteps, bandStepCenter, usesCustomEqPayload, raw |
| `LeaState` | enabled, streamingStatusL, streamingStatusR, pairedHistory, raw |
| `QuickAccessState` | lrKeyFunction, ncAmbKeyFunction, raw |
| `WearingState` | status, result, raw |
| `EndpointDiagnosticState` | reason, serviceLabels, leAudioSwitchCompatibility, friendlyName, publicAddress, rawReads |
| `Table2DiagnosticState` | channel, family, command, inquiredType, values, rawHex |
| `FeatureStatus` | title, description, implemented |
| `HeadphoneUiState` | 以上所有状态 + scanState, isScanning, permissionIssue, discoveredDevices, knownDevices, connectedDevice, connectionInfo, connectedProfile, eqUiCapability, playbackStatus, endpointDiagnostic, table2Diagnostic, supportedFeatures, debugLogs, debugLogging, autoReconnect, strictSonyScanFilter, preferredProtocol |

**公共动作方法**:

| 方法 | 描述 |
|------|------|
| `startScan()` | 启动 BLE 扫描，清空之前的诊断结果 |
| `stopScan()` | 停止 BLE 扫描 |
| `connect(device)` | 连接设备：QCY 设备需解析 BLE 地址（双模 MAC 不同）；其他设备直接通过选择器连接 |
| `connect(address, name)` | 调试用：通过 MAC/名称连接 |
| `disconnect()` | 断开当前连接 |
| `refreshBasics()` | 刷新所有基础状态：设备信息、电池、NC、EQ、播放状态 |
| `setNoiseControlMode(mode)` | 设置降噪模式，乐观更新 UI，发送命令后刷新 NC 状态 |
| `setAmbientLevel(level)` | 设置环境声级别 (1-20) |
| `setAmbientVoiceMode(enabled)` | 切换人声模式 |
| `setEqPreset(preset)` | 设置 EQ 预设 |
| `setClearBass(level)` | 设置 Clear Bass (-10 到 +10) |
| `setCustomEqBand(index, level)` | 设置自定义 EQ 频段 |
| `playbackPrevious()` / `playbackPlayPause()` / `playbackNext()` | 播放控制：优先 Tandem，fallback 到 Android Media Key |
| `runDebugAction(action, rawHex)` | 调试操作：nc/ambient/off/eq_bass/eq_bright/clear_bass/eq_band/battery_tandem/raw |
| `setDebugLogging(enabled)` / `setAutoReconnect(enabled)` / `setStrictSonyScanFilter(enabled)` | 设置开关 |

**内部响应处理**:

| 方法 | 描述 |
|------|------|
| `dispatchParsed(channel, parsed)` | 将解析后的响应分发到各 `apply*` 方法 |
| `applyDeviceInfo(response)` | 更新设备型号名、固件版本、系列/颜色 |
| `applyBattery(response)` | 按 PowerInquiredType 更新 single/left/right/cradle 电量 |
| `applyEqEbb(response)` | 更新 EQ 预设、Clear Bass、频段步骤；EQ 频段 raw→display 转换 |
| `applyNoise(response)` | 更新 NC 模式、环境声级别、人声模式 |
| `applyPlayback(response)` | 更新播放状态；含 stale 响应过滤和 AudioManager 交叉验证 |
| `applyLeaStatus(response)` | 更新 LE Audio 启停和流媒体状态 |
| `applyPlaybackStatus(status, source)` | 播放状态更新核心：处理乐观转换窗口 `PendingPlaybackStatus`、NTFY PAUSED 交叉验证 |

**播放心跳机制**:

| 常量 | 值 | 描述 |
|------|-----|------|
| `PLAYBACK_STALE_RESPONSE_WINDOW_MS` | `2500` | 命令发出后忽略相反状态响应的窗口期 |
| `PLAYBACK_REFRESH_AFTER_COMMAND_MS` | `1200` | 命令后延迟刷新播放状态 |
| `PLAYBACK_RECONCILE_AFTER_COMMAND_MS` | `2800` | 命令后延迟验证播放状态（交叉验证 AudioManager） |
| `PLAYBACK_HEARTBEAT_INTERVAL_MS` | `30000` | 播放时每 30 秒发送心跳 GET 防止耳机 watchdog 超时 |

---

### `data/sony/SonyModelImageCatalog.kt`

**包**: `dev.ignotus.openbuds.data.sony`

Sony 耳机型号图片目录。从 `assets/sony_model_images.json` 加载型号/颜色/图片 URL 映射。

| 数据类/函数 | 描述 |
|-------------|------|
| `SonyModelImageMatch(modelName, modelColor, imageUrl, sourceColor)` | 图片匹配结果 |
| `SonyModelImageCatalog(context)` | 构造函数：从 assets JSON 加载条目列表 |
| `resolve(modelName, modelColor)` | 按型号名和颜色匹配图片：优先精确颜色匹配 → 默认颜色 → 第一个条目 |
| `normalizeModelName(value)` | 型号名规范化：去前缀、去多余空格、小写 |
| `normalizeColor(value)` | 颜色规范化：提取 "/" 后颜色名、小写 |

---

### `data/qcy/QcyResponseMapper.kt`

**包**: `dev.ignotus.openbuds.data.qcy`

QCY 解析响应 → `HeadphoneUiState` 的状态映射器。独立对象，避免污染 `HeadphoneRepository`。

| 函数 | 描述 |
|------|------|
| `apply(state, response)` | 按 QCY 响应类型分发到各 `apply*` 子方法 |
| `applyBattery(state, response)` | 更新 left/right/cradle 电量 |
| `applyNoise(state, response)` | 将 QCY ANC mode (0/1/2/3) 映射到 `NoiseControlMode`；噪声值 / 12 映射到 1-20 环境声级别 |
| `applyEq(state, response)` | 将 QCY EQ band gain + masterGain 反向映射为 display steps (center=10, range 0-20) |
| `applyDeviceInfo(state, response)` | 更新固件版本 "L:x.y.z R:x.y.z" |
| `applyVolume(state, response)` | P3 占位（尚未实现） |
| `applyFunctionStatus(state, response)` | P3 占位（尚未实现） |

---

### `data/settings/AppSettingsStore.kt`

**包**: `dev.ignotus.openbuds.data.settings`

使用 Jetpack DataStore 持久化的应用设置。

| 设置项 | 键 | 默认值 |
|--------|-----|--------|
| `serviceBackgroundRun` | `service_background_run` | `false` |
| `notificationPersistent` | `notification_persistent` | `true` |
| `notificationLockscreen` | `notification_lockscreen` | `true` |
| `connectionPopup` | `connection_popup` | `false` |

| 函数 | 描述 |
|------|------|
| `AppSettingsStore(context)` | 构造函数 |
| `settings` | 返回 `Flow<AppSettings>` |
| `setServiceBackgroundRun(enabled)` | 设置后台运行 |
| `setNotificationPersistent(enabled)` | 设置通知持久化 |
| `setNotificationLockscreen(enabled)` | 设置锁屏通知 |
| `setConnectionPopup(enabled)` | 设置连接弹窗 |

---

## 5. 耳机适配层 (`headphones/`)

### `HeadphoneAdapter.kt`

**包**: `dev.ignotus.openbuds.headphones`

核心类型定义和适配器注册表。定义了协议变体、功能枚举、通道类型、设备 profile、命令类型和适配器接口。

**枚举**:

| 枚举 | 值 |
|------|-----|
| `HeadphoneProtocolVariant` | SONY_TANDEM_V1_TABLE1, SONY_TANDEM_V1_TABLE2, SONY_TANDEM_V2_TABLE1, SONY_TANDEM_V2_TABLE2, QCY, UNKNOWN |
| `HeadphoneFormFactor` | HEADSET, TRUE_WIRELESS, UNKNOWN |
| `HeadphoneFeature` | DEVICE_INFO, BATTERY, NOISE_CONTROL, AMBIENT_LEVEL, AMBIENT_VOICE_MODE, PLAYBACK_CONTROL, EQ, CLEAR_BASS, LEA_STATUS, QUICK_ACCESS, WEARING_STATUS, VOLUME |
| `HeadphoneTransport` | UNKNOWN, SPP, GATT_HPC, GATT_MC, UNSUPPORTED_LE_ENDPOINT |
| `TandemChannel` | SPP_MDR, GATT_V2_HPC, GATT_V2_MC, GATT_V1_MC, QCY_SETTING_WRITE, QCY_READSET, QCY_BATTERY, QCY_VERSION, QCY_EQ_RAW, QCY_FUNCTION |
| `PlaybackDispatchStrategy` | TANDEM_FIRST, ANDROID_MEDIA_FALLBACK, TANDEM_ONLY |
| `InfoLayoutHint` | SONY_SERIES, BRAND_MODEL |

**数据类**:

| 类 | 描述 |
|----|------|
| `FeatureProtocolBinding` | 功能到协议变体和通道的绑定：feature, variant, channel, queryTypes, writableTypes |
| `HeadphoneCommand(label, bytes, channel)` | 可执行的协议命令：标签、字节、目标通道 |
| `EqWriteContext(rawBandSteps, preset)` | EQ 写入上下文：当前 raw band steps 和 preset |
| `HeadphoneCapabilities` | 设备能力：功能集、形态因子、电池查询列表、NC 查询类型列表、EQ 配置、播放控制类型等 |
| `EqDeviceConfig` | EQ 设备配置：可用预设、写入 inquired type、查询类型列表、频段数、Clear Bass 模式 |
| `ConnectedHeadphoneProfile` | 运行时设备 profile：含 adapterId、品牌、型号、协议、设备能力、功能协议映射、播放策略 |
| `ProfileTemplate` | 静态设备模板：型号名、系列、能力、功能协议映射；提供 `toProfile()` 转换为 `ConnectedHeadphoneProfile` |

**核心接口和注册表**:

| 接口/对象 | 描述 |
|-----------|------|
| `HeadphoneAdapter` | 适配器接口：match、fallbackProfile、各种 build*Commands、parse、canWrite |
| `HeadphoneAdapterRegistry` | 单例注册表，聚合 Sony 和 QCY 两个适配器；提供统一的 resolve/build/parse/canWrite 入口 |

---

### `headphones/sony/EqProtocolEngine.kt`

**包**: `dev.ignotus.openbuds.headphones.sony`

设备无关的 EQ 引擎。消费 `EqDeviceConfig` 输出 `EqUiCapability`，统一 EQ 写入/刷新/解析路径。

| 类/函数 | 描述 |
|---------|------|
| `EqDeviceConfig` | EQ 设备配置 |
| `ClearBassWriteMode` | Clear Bass 写入模式：EBB_PARAM（独立命令）、PRESET_EQ_BANDS（合并到 EQ band 数组 [0]） |
| `EqUiCapability` | EQ UI 能力：可用预设、可见频段数、频段标签、显示范围、是否有 Clear Bass |
| `EqProtocolEngine(config, codec)` | 构造函数，绑定设备配置和协议 codec |
| `buildRefreshCommands(buildCommand)` | 构建刷新命令列表：状态查询 + 参数查询 + 扩展信息查询 |
| `buildSetPreset(preset)` | 构建设置预设命令 |
| `buildSetBands(bands, preset)` | 构建设置频段命令 |
| `buildSetClearBass(level)` | 构建设置 Clear Bass 命令 |
| `parseResponse(raw)` | 解析 EQ 响应 |
| `uiCapability(config)` | 静态：从 `EqDeviceConfig` 构建 `EqUiCapability` |

**常量**:

| 常量 | 值 | 描述 |
|------|-----|------|
| `BAND_STEP_CENTER` | `10` | 频段步进中心值 |
| `DEFAULT_BAND_LABELS` | `["400 Hz", "1 kHz", "2.5 kHz", "6.3 kHz", "16 kHz"]` | 默认频段标签 |

---

### `headphones/sony/TandemCodecRegistry.kt`

**包**: `dev.ignotus.openbuds.headphones.sony`

TandemCodec 接口和 4 个协议 variant 的实现对象。将原始的 Sony Tandem V1/V2 Table1/Table2 协议对象包装为统一接口。

| 接口/对象 | 描述 |
|-----------|------|
| `TandemCodec` | 统一协议 codec 接口：variant, defaultChannel, parse, buildGet*, buildSet* |
| `TandemCodecRegistry.codecFor(variant)` | 按协议变体返回对应 codec |
| `UnknownTandemCodec` | 未知协议变体的兜底 codec |
| `SonyTandemV1Table1Codec` | V1 Table1 codec：委托到 `SonyTandemV1Table1Protocol` |
| `SonyTandemV1Table2Codec` | V1 Table2 codec：委托到 `SonyTandemV1Table2Protocol` |
| `SonyTandemV2Table1Codec` | V2 Table1 codec：委托到 `SonyTandemV2Table1Protocol`；额外暴露 `buildGetEqEbbStatus(typeCode: Byte)`, `buildSetEqPreset(preset, typeCode, bandSteps)`, `buildSetClearBass(level, ebbTypeCode)` 等类型码级别的 API |
| `SonyTandemV2Table2Codec` | V2 Table2 codec：委托到 `SonyTandemV2Table2Protocol` |

---

### `headphones/sony/SonyTandemHeadphoneAdapter.kt`

**包**: `dev.ignotus.openbuds.headphones.sony`

Sony Tandem 的 `HeadphoneAdapter` 实现。负责设备匹配、命令构造、响应解析路由。

| 函数 | 描述 |
|------|------|
| `match(device, reportedModelName)` | 遍历设备模板（WH-1000XM4, LinkBuds S, WF-1000XM5），匹配设备名称 |
| `fallbackProfile(device)` | 未知 Sony 设备的回退 profile：仅 DEVICE_INFO + BATTERY，V2 TABLE1 |
| `buildRefreshCommands(profile)` | 构建完整刷新命令集：协议信息 → 设备信息 → 固件版本 → 电池 → NC → EQ → 播放 → LEA → Quick Access → 佩戴状态 |
| `canWrite(profile, feature)` | 判断是否可写入：NC/ASM 需要 `writableNoiseControlTypes` 非空；EQ/ClearBass/播放 需要静态 profile；其余直接检查 supports |
| `buildSetNoiseControlModeCommands(profile, mode, ambientLevel, ambientMode)` | 构建 NC 设置命令：优先使用 writableNoiseControlTypes 中对应类型的 SET_PARAM，fallback 到独立 NC/ASM on/off 命令 |
| `buildSetEqPresetCommands(profile, preset, context)` | 通过 `EqProtocolEngine` 构建设置预设命令 |
| `buildSetEqBandCommands(profile, rawSteps, preset, context)` | 通过 `EqProtocolEngine` 构建设置频段命令 |
| `buildSetClearBassCommands(profile, level, context)` | 按 `ClearBassWriteMode`：EBB_PARAM 模式用独立 command，PRESET_EQ_BANDS 模式合并到 EQ band 数组 |
| `buildRefreshNoiseControlCommands(profile)` | 按 profile 配置决定 query NC params 还是 status |
| `buildRefreshEqCommands(profile)` | 委托 `EqProtocolEngine.buildRefreshCommands` |
| `buildRefreshBatteryCommands(profile)` | 按 `batteryQueries` 列表构建电池查询命令 |
| `buildRefreshPlaybackCommands(profile)` | 构建播放状态查询 |
| `buildPlaybackCommands(profile, control)` | 构建播放控制命令 |
| `parse(profile, channel, raw)` | 响应解析路由：`0x0F` 首字节 → Table2 codec；其他 → 按命令字节分类 → 绑定对应 feature 的 codec 解析；0x13 命令三重分歧（V2 COMMON_RET_STATUS vs V1 COMMON_NTFY_BATTERY_LEVEL） |
| `classifyCommand(command, payload)` | 命令字节 → HeadphoneFeature 分类 |
| `classify0x13(payload)` | 0x13 过载命令分类：检查 payload 是 CommonInquiredType 还是 PowerInquiredType；重叠时探测是否像 V1 电池 payload |

---

### `headphones/qcy/QcyHeadphoneAdapter.kt`

**包**: `dev.ignotus.openbuds.headphones.qcy`

QCY 的 `HeadphoneAdapter` 实现。所有命令通过 `TandemChannel.QCY_SETTING_WRITE` 发送；各特征通知分发到对应的 QCY 通道。

| 函数 | 描述 |
|------|------|
| `match(device, reportedModelName)` | 先模板匹配（C30S），再名称匹配（含 "qcy"） |
| `fallbackProfile(device)` | 未知 QCY 设备回退 profile：DEVICE_INFO + BATTERY |
| `buildRefreshCommands(profile)` | 构建 QCY 刷新命令：电池 + 版本 + NC 模式 + NC 值 + EQ（新旧格式） |
| `buildRefreshBatteryCommands(profile)` | CMDID_BATTERY 读请求 |
| `buildRefreshNoiseControlCommands(profile)` | CMDID_NOISE_MODE + CMDID_NOISE_VALUE 读请求 |
| `buildRefreshEqCommands(profile)` | CMDID_MULTIEQ + CMDID_MULTIEQ2 读请求 |
| `buildSetNoiseControlModeCommands(profile, mode, ambientLevel, ambientMode)` | QCY ANC 模式映射：NC→ANC(1), AMBIENT→TRANSPARENCY(3), OFF→OFF(0)；噪声值 = ambientLevel * 12 |
| `buildSetEqBandCommands(profile, rawSteps, preset, context)` | 构建新格式 EQ 设置命令（CMD 34, 7 字节/band, eqType=0x7E） |
| `buildPlaybackCommands(profile, control)` | 播放控制映射到 CMDID_MUSIC_ACTION (1-4) |
| `parse(profile, channel, raw)` | 按通道类型分发解析：QCY_BATTERY→电池字节解析、QCY_VERSION→固件三元组、QCY_FUNCTION→功能开关、QCY_EQ_RAW→EQ heuristic 解析、QCY_READSET→TLV 帧解析 |
| `parseQcyBattery(raw)` | 解析 3 字节电池数据（每字节 bit7=充电标志, low 7 bits=电量） |
| `parseQcyVersionRaw(raw)` | 解析 6 字节固件版本（左 3 + 右 3） |
| `parseQcyEqRaw(raw)` | 启发式判断新旧 EQ 格式（7B/band vs 6B/band） |
| `mapTlvEntry(entry, raw)` | TLV 条目 → `ParsedHeadphoneResponse` 映射 |

---

## 6. 设备 Profile

### `headphones/sony/devices/Wh1000Xm4Profile.kt`

WH-1000XM4 头戴式降噪耳机 profile。

| 属性 | 值 |
|------|-----|
| 协议变体 | V1 TABLE1（全部功能） |
| 形态因子 | HEADSET |
| 电池查询 | BATTERY（单电池） |
| NC 类型 | V1_TABLE_SET1_NC_ASM |
| EQ 预设 | OFF, BRIGHT, EXCITED, MELLOW, RELAXED, VOCAL, TREBLE, BASS, SPEECH, CUSTOM, USER_SETTING1, USER_SETTING2 |
| EQ 频段数 | 6（含 Clear Bass） |
| Clear Bass 模式 | PRESET_EQ_BANDS |

### `headphones/sony/devices/LinkBudsSProfile.kt`

LinkBuds S 真无线降噪耳机 profile。

| 属性 | 值 |
|------|-----|
| 协议变体 | V2 TABLE1（全部功能） |
| 形态因子 | TRUE_WIRELESS |
| 电池查询 | LEFT_RIGHT_BATTERY, CRADLE_BATTERY |
| NC 类型 | MODE_NC_ASM_DUAL_NC_MODE_SWITCH_AND_ASM_SEAMLESS |
| EQ 同 WH-1000XM4，额外支持 LEA_STATUS, QUICK_ACCESS, WEARING_STATUS |

### `headphones/sony/devices/Wf1000Xm5Profile.kt`

WF-1000XM5 真无线降噪耳机 profile。配置与 LinkBuds S 基本一致（V2 TABLE1, 相同功能集），同为 PREMIUM 系列。

### `headphones/qcy/devices/QcyC30SProfile.kt`

QCY C30S 真无线耳机 profile。

| 属性 | 值 |
|------|-----|
| 协议变体 | QCY |
| 形态因子 | TRUE_WIRELESS |
| 功能 | DEVICE_INFO, BATTERY, NOISE_CONTROL, AMBIENT_LEVEL, EQ, PLAYBACK_CONTROL |
| EQ 预设 | OFF, BASS, BRIGHT, POP, JAZZ, VOCAL, CUSTOM |
| EQ 频段数 | 10 |

---

## 7. 媒体控制 (`media/`)

### `MediaPlaybackController.kt`

**包**: `dev.ignotus.openbuds.media`

Android AudioManager 媒体键控制。作为 Tandem 播放控制不可用时的 fallback。

| 函数 | 描述 |
|------|------|
| `MediaPlaybackController(context)` | 构造函数，获取 `AudioManager` |
| `previous()` | 发送 `KEYCODE_MEDIA_PREVIOUS` |
| `playPause()` | 发送 `KEYCODE_MEDIA_PLAY_PAUSE` |
| `next()` | 发送 `KEYCODE_MEDIA_NEXT` |
| `currentFallbackStatus()` | 通过 `AudioManager.isMusicActive()` 判断播放状态：活跃 → PLAYING，否则 → PAUSED |
| `dispatch(keyCode)` | 发送 KeyEvent DOWN + UP |

---

## 8. 后台服务 (`service/`)

### `SonyControlService.kt`

**包**: `dev.ignotus.openbuds.service`

Android 前台 Service。持有 `HeadphoneRepository` 单例，提供 `LocalBinder` 供 Activity/外部进程绑定，维护前台通知。

| 类/函数 | 描述 |
|---------|------|
| `SonyControlService` | `Service`，前台运行 |
| `LocalBinder` | Binder：暴露 `state: LiveData<DeviceStateSnapshot>` 和 `execute(command: ControlCommand)` |
| `onCreate()` | 初始化 repository、启动前台服务、订阅 repository.state 更新 notification |
| `onBind(intent)` | 返回 `LocalBinder` |
| `onStartCommand(intent, flags, startId)` | 处理 `ACTION_DISCONNECT` 操作 |
| `onDestroy()` | 断开设备、取消协程作用域 |
| `createNotification(snapshot)` | 构建前台通知：设备名、电量、NC 模式；含断开操作按钮 |
| `createChannelIfNeeded()` | 创建通知渠道（API 26+） |

### `ControlCommand.kt`

**包**: `dev.ignotus.openbuds.service`

跨进程控制命令 sealed class。

| 命令 | 参数 | 描述 |
|------|------|------|
| `SetNoiseControl` | `mode: NoiseControlMode` | 设置降噪模式 |
| `SetAmbientLevel` | `level: Int` | 设置环境声级别 |
| `SetAmbientVoiceMode` | `enabled: Boolean` | 设置人声模式 |
| `Playback` | `action: PlaybackAction` | 播放控制（PREVIOUS/PLAY_PAUSE/NEXT） |
| `Refresh` | （无） | 刷新设备状态 |

### `DeviceStateSnapshot.kt`

**包**: `dev.ignotus.openbuds.service`

精简的设备状态 DTO，支持 `Bundle` 序列化/反序列化，用于跨进程传递。

| 函数 | 描述 |
|------|------|
| `toBundle()` | 将快照序列化到 `Bundle` |
| `fromBundle(bundle)` | 从 `Bundle` 反序列化 |
| `fromUiState(state)` | 从 `HeadphoneUiState` 构造 |

---

## 9. LSPosed 模块 (`lsposed/`)

### `ModuleMain.kt`

**包**: `dev.ignotus.openbuds.lsposed`

LSPosed 模块入口。在 `com.milink.service` 进程中加载 MiLink 第一方适配主线。M0 已移除旧 MiTWS 身份伪装、卡片重定向和名称修复 hook；M1 接入 AirPods 识别链路验证 hook；M2 接入固定 AirPods 状态字段。

| 函数 | 描述 |
|------|------|
| `ModuleMain` (init) | 记录加载进程名，写入启动标记文件 `/sdcard/openbuds_lsposed_startup.txt` |
| `onPackageLoaded(param)` | `com.milink.service` → `MilinkAirpodsAdapterEntry.install()`；不加载旧 `isMiHeadset` / 卡片重定向 hook |
| `log(msg)` | 日志输出 |

### `milink/MilinkAirpodsAdapterEntry.kt`

**包**: `dev.ignotus.openbuds.lsposed.milink`

MiLink AirPods adapter 主线入口。M1/M2 阶段安装 AirPods 识别链路 hook、固定 `getAirPodsState` 状态数组和 `/airpodsstate` Bundle 代理。

| 函数 | 描述 |
|------|------|
| `install()` | 创建 `MilinkAirpodsM1Hook` 并安装 AirPods 识别链路与固定状态 hook |

### `milink/MilinkAirpodsM1Hook.kt`

**包**: `dev.ignotus.openbuds.lsposed.milink`

M1/M2 可行性验证 hook。只影响 `com.milink.service` 进程内米链客户端方法，不启动 OpenBuds 协议栈，不 hook `isMiHeadset` / `checkIsMiTWS`。

| 函数 | 描述 |
|------|------|
| `install()` | 安装 `MxBluetoothManager` 主 hook、`BluetoothServiceClient` 兜底/trace hook 和 ContentResolver 状态 hook |
| `hookMxBluetoothManager()` | hook `checkIsAirPods(String)` 和 `getAirPodsState(String)`：真实 AirPods 原结果透传；allowlist MAC 在 intercept 模式下返回固定 9 元素状态 |
| `hookBluetoothServiceIsAirPods()` | hook `BluetoothServiceClient.isAirPods(BluetoothDevice)`，在 AirPods manager 不可用时按同一 allowlist 兜底 |
| `hookAirpodsDeviceIdTrace()` | 仅记录 `getAirpodsDeviceId` / `getDeviceIdForAirpods` 调用，不伪造 state |
| `hookAirpodsHeadsetTypeTrace()` | 仅记录 `getAirpodsHeadsetType(String)` 调用和返回值 |

### `milink/AirpodsStateMapper.kt`

**包**: `dev.ignotus.openbuds.lsposed.milink`

M2 固定状态映射器。M3 接入 bridge 后继续复用该类，将真实 snapshot 转换为 MiLink 需要的两种 AirPods 数据形状。

| 函数/类型 | 描述 |
|-----------|------|
| `placeholder(mac)` | 构造 M2 固定状态：左 75、右 80、盒 90、佩戴 true、充电 false、deviceId=`01010101` |
| `toStateArray(snapshot)` | 输出 `MxBluetoothManager.getAirPodsState(String)` 需要的 9 元素 `String[]` |
| `toBundleFields(snapshot)` | 输出 `/airpodsstate` Bundle 的 11 个 string 字段 |

### `milink/DeviceIdRegistry.kt`

**包**: `dev.ignotus.openbuds.lsposed.milink`

M2 deviceId 模板注册表。默认使用 `01010101`，在米链 `AbstractC14649a.m51162b()` 中解析为 type=0 普通耳塞，避免真实 AirPods type=5/6 分支。

| 函数/常量 | 描述 |
|-----------|------|
| `GENERIC_EARBUD_DEVICE_ID` | `01010101`，M2 默认普通耳塞模板 |
| `OPEN_WEAR_DEVICE_ID` | `01013400`，预留开放式耳机模板，预期 type=4 |
| `deviceIdForMac(mac)` | 当前返回普通耳塞模板；M7 多品牌时可按品牌/MAC 选择 |

### `milink/MilinkAirpodsTargetMatcher.kt`

**包**: `dev.ignotus.openbuds.lsposed.milink`

M1 临时目标匹配器。用 MAC allowlist 模拟未来 M3 bridge 的授权设备集合。

| 函数/常量 | 描述 |
|-----------|------|
| `DEBUG_PROPERTY` | `debug.openbuds.milink_m1_macs`，可用逗号/分号/空白分隔多个 MAC |
| `normalizeMac(value)` | 只接受完整 `XX:XX:XX:XX:XX:XX` 格式并转大写 |
| `configuredTargets(value)` | 从 debug property 解析 allowlist；无有效值时使用 M1 默认测试 MAC |
| `airpodsDecision(originalResult, mac, value)` | 原结果 true 时透传；原结果 false 且 MAC 命中 allowlist 时返回 true |

---

## 10. 广播接收器 (`receiver/`)

### `SystemIntegrationReceiver.kt`

**包**: `dev.ignotus.openbuds.receiver`

跨进程广播接收器。Phase 1 预留，当前为空实现，未来处理 LSPosed 模块的白名单刷新触发器等跨进程通信。

| 函数 | 描述 |
|------|------|
| `onReceive(context, intent)` | 接收广播，记录 action 日志（Phase 1: 无实际操作） |

---

## 架构总结

```
┌─────────────────────────────────────────────────────────┐
│                  Application Layer                       │
│  HeadphoneRepository  ←→  HeadphoneUiState              │
│  MediaPlaybackController    data/sony/SonyModelImageCatalog  │
├─────────────────────────────────────────────────────────┤
│              Headphone Adapter Layer                     │
│  HeadphoneAdapterRegistry                               │
│  headphones/sony/SonyTandemHeadphoneAdapter              │
│  headphones/sony/EqProtocolEngine                       │
│  headphones/sony/TandemCodecRegistry                    │
│  headphones/qcy/QcyHeadphoneAdapter                     │
│  ProfileTemplates (sony/devices/* / qcy/devices/*)      │
├─────────────────────────────────────────────────────────┤
│              Protocol / Codec Layer                      │
│  protocol/HeadphoneResponse (sealed hierarchy)           │
│  protocol/HeadphoneEnums (shared enums)                  │
│  protocol/sony/SonyTandemV1/2Table1/2Protocol           │
│  protocol/sony/SonyEqEbbPayloadParser                   │
│  protocol/sony/SonyGatt / SonyTandemConstants            │
│  protocol/qcy/QcyProtocol / QcyGatt                     │
├─────────────────────────────────────────────────────────┤
│              Transport / BLE Layer                       │
│  ble/HeadphoneTransportSelector                         │
│  ble/HeadphoneTransportClient (interface)                │
│  ble/sony/SonyBleClient (GATT+SPP)                      │
│  ble/sony/SonySppTransport                              │
│  ble/sony/TandemTransportRouting                        │
│  ble/qcy/QcyBleClient (GATT only)                       │
├─────────────────────────────────────────────────────────┤
│              LSPosed (Optional)                          │
│  ModuleMain -> milink/MilinkAirpodsAdapterEntry          │
│  M1/M2: AirPods classification + fixed state hooks       │
└─────────────────────────────────────────────────────────┘
```
