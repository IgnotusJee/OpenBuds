# 传输层与协议层分离计划

## 目标

将当前项目中耦合在一起的蓝牙传输层（SPP / BLE GATT）与品牌协议层（Sony Tandem / QCY TLV）彻底分离，实现：

1. **底层传输完全品牌无关** — SPP 和 BLE GATT 作为两个独立的传输通道，只负责"字节进、字节出"
2. **上层品牌 Adapter 决定使用哪个通道** — Sony Tandem 可以选择 SPP 或 GATT，QCY 选择 GATT
3. **新增品牌不走传输层代码** — 新增品牌只需写 Adapter，复用现有 Transport

## 当前问题

| 问题 | 位置 | 影响 |
|------|------|------|
| `SonyBleClient` (已移除) 曾包含扫描、GATT、SPP、Tandem握手、Sony AD解析 | 已拆分到 `ble/sony/SonyTandemTransportClient.kt`、`SonyTandemGattSession.kt`、`SonyTandemSppSession.kt`、`SonyBleScanner.kt`、`SonyAudioAdParser.kt` | Phase 3 已解决 |
| `TandemChannel` enum 混合 Sony GATT 通道、SPP、QCY 通道 | `headphones/HeadphoneAdapter.kt` | 品牌耦合 |
| `SonyBleClientListener` typealias 给 QCY 用 | `ble/HeadphoneTransportClient.kt` | Phase 3 已替换为 `HeadphoneTransportListener` |
| `SonySppTransport` 生命周期嵌入 `SonyBleClient` | 已拆分到 `ble/sony/SonyTandemSppSession.kt` + `ble/transport/SppTransport.kt` | Phase 3 已解决 |
| `QcyBleClient.startScan()` no-op，依赖 Sony 扫描 | `ble/qcy/QcyBleClient.kt` | 扫描耦合 |
| 传输通道选择 (`shouldUseSpp`) 硬编码在 `SonyBleClient` | 已迁移到 `ble/sony/SonyTandemTransportClient.kt` | Phase 3 已解决 |

## 目标架构

```
┌─────────────────────────────────────────────────────────────┐
│  HeadphoneRepository              数据层                    │
│  - 管理 UI State                                           │
│  - 持有 Adapter + Transport                                 │
│  - onMessage(bytes) → adapter.parse(bytes) → State          │
└──────────────┬──────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────┐
│  HeadphoneAdapter (per brand)      协议层                   │
│                                                             │
│  SonyTandemAdapter              QcyAdapter                  │
│  - build Tandem 指令            - build TLV 指令            │
│  - parse Tandem 响应            - parse TLV 响应            │
│  - 决定 transport:              - 决定 transport:           │
│    [SPP, GATT_HPC, 两者]          [GATT_Qcy]                │
└──────────────┬──────────────────────────────────────────────┘
               │ 纯字节接口
┌──────────────▼──────────────────────────────────────────────┐
│  BluetoothTransport (接口)       传输层 - 品牌无关           │
│                                                             │
│  ┌─────────────────┐    ┌──────────────────────────────┐    │
│  │ SppTransport     │    │ GattTransport                │    │
│  │ - RFCOMM socket  │    │ - BLE GATT                   │    │
│  │ - framing/escape │    │ - service discovery          │    │
│  │ - checksum/ACK   │    │ - char read/write/notify     │    │
│  │ - 纯字节 in/out   │    │ - 纯字节 in/out              │    │
│  └─────────────────┘    └──────────────────────────────┘    │
│                                                             │
│  相同接口:                                                   │
│    send(bytes)                                               │
│    close()                                                   │
│    val info: TransportInfo                                   │
│  连接入口按实现提供：SppTransport 包装已连接 socket，         │
│  GattTransport.connect(device) 建立 Android GATT。            │
└─────────────────────────────────────────────────────────────┘
```

## 新接口定义

```kotlin
// ble/transport/BluetoothTransport.kt
interface BluetoothTransport {
    val name: String              // "SPP", "GATT"
    val info: TransportInfo
    fun send(bytes: ByteArray)
    fun close()
}

data class TransportInfo(
    val mtu: Int,
    val kind: String,
    val writableValueLength: Int? = null,
)

interface TransportListener {
    fun onReady(info: TransportInfo)
    fun onMessage(bytes: ByteArray)
    fun onDisconnected(reason: String?)
    fun onLog(message: String)
}

interface GattTransportListener : TransportListener {
    fun onConnected()
    fun onMessage(characteristicUuid: UUID, bytes: ByteArray)
    fun onSetupFailed(message: String)
}
```

## 分阶段实施计划

### Phase 1 — SPP 传输独立化（已完成）

**目标**：`SppTransport` 成为独立可复用的传输类，不依附于 `SonyBleClient`。

**变更：**
1. 新建 `ble/transport/` 包
2. 定义 `BluetoothTransport` 接口 + `TransportInfo` + `TransportListener`
3. 从 `ble/sony/SonySppTransport.kt` 提取核心逻辑到 `ble/transport/SppTransport.kt`
   - 移除 `Sony` 前缀（`SonySppFrameType` → `SppFrameType`）
   - 帧类型映射（`SonySppPayloadMapper`）作为可注入策略
   - 实现 `BluetoothTransport` 接口
4. Sony Tandem path 改用新的 `SppTransport`

**风险**：低。SPP 已经是独立类，主要是包移动+接口化。

**完成状态：**
- `ble/transport/SppTransport.kt` 负责 SPP 帧、ACK、转义和校验。
- `ble/sony/SonySppPayloadMapper.kt` 作为可注入策略处理 Sony Tandem app data type (`0x0E/0x0F`) 与 SPP frame type (`0x0C/0x0E`) 的双向映射。
- Sony SPP path 注入 `SonySppPayloadMapper`，并保持对外连接状态回调不重复。

### Phase 2 — GATT 传输通用化（本次）

**目标**：从 Sony Tandem 和 `QcyBleClient` 提取公共 GATT 逻辑。

**变更：**
1. 实现 `GattTransport`，参数化 service UUID、write characteristic UUID、notify/read characteristic UUIDs
2. 通用 GATT 连接流程：connect → discoverServices → enableNotify/read → requestMtu → ready
3. 通用写入队列、CCCD 串行化、MTU 协商和 ready deadline
4. QCY `QcyBleClient` 改用 `GattTransport`，只保留品牌匹配、channel 分发和 repository 回调适配
5. Sony GATT path 暂不强迁：OPTIMAL_MTU、DETERMINE_MTU、WRITABLE_VALUE_LENGTH、V2 HPC/V2 MC/V1 MC endpoint 注册仍在 Sony 状态机中，迁移点留到 Phase 3/4

**风险**：中。Sony 和 QCY 的 GATT 流程差异大（握手步骤、CCCD 并行度）。本阶段先迁移 QCY 标准 GATT-TLV path，Sony 维持现有已验证路径。

### Phase 3 — SonyBleClient 解体（已完成）

**目标**：`SonyBleClient` 不再存在，职责分散。

**变更：**
1. Sony 扫描逻辑 → 独立的 `SonyBleScanner` 组件
2. Sony transport 创建入口 → `SonyTandemHeadphoneAdapter.createTransportClient(context, listener)`
3. Sony transport 编排 → `SonyTandemTransportClient`
4. Sony GATT 握手与 endpoint probe → `SonyTandemGattSession`
5. Sony SPP socket 与 `SppTransport` 生命周期 → `SonyTandemSppSession`
6. Sony AD 解析 → `SonyAudioAdParser`
7. Sony endpoint support 判断 → `SonyTandemEndpointSupport`
8. `SonyBleClientListener` typealias → `HeadphoneTransportListener`

**完成状态：**
- `SonyBleClient.kt`、`SonyBleClientListener.kt`、`SonyBleConnectionInfo.kt` 已移除。
- Sony GATT 特殊握手仍保持专用 session，未强行复用通用 `GattTransport`，避免破坏多 endpoint 和 MTU handshake。
- `HeadphoneTransportSelector` 仍保留到 Phase 5/6；它仍负责多品牌 transport client 路由。
- `HeadphoneTransportClient.kt` 现在定义品牌无关的 `HeadphoneTransportListener` 与 `HeadphoneConnectionInfo`，QCY 与 Sony 共用同一回调类型。
- `HeadphoneRepository` 通过 `SonyTandemHeadphoneAdapter.createTransportClient(appContext, this)` 创建 Sony transport，不再直接依赖 Sony BLE 客户端类。
- `SonyDeviceMatcher` 统一 Sony/QCY 名称排除、Sony AD 和 Tandem service 匹配逻辑，`SonyBleScanner` 与 `SonyTandemTransportClient` 共用该判断。
- 新增测试覆盖：
  - `SonyAudioAdParserTest`：direct manufacturer payload、split raw scan record、非法/截断 payload。
  - `SonyDeviceMatcherTest`：QCY 排除、Sony 名称、Sony AD、Tandem UUID/label 匹配。
  - `SonyBleClientChannelTest`：改为使用 `SonyTandemEndpointSupport`，覆盖 V2 HPC、V1 MC、LE Audio endpoint、pairing/name endpoint。
  - `ProtocolCompatibilityArchitectureTest`：确认主源码不再包含 `class SonyBleClient` / `SonyBleClientListener`，并确认 Repository 走 adapter factory。
- Phase 3 验证命令：`.\gradlew.bat testDebugUnitTest assembleDebug`。

### Phase 4 — TandemChannel 品牌解耦

**目标**：`TandemChannel` enum 不再是跨品牌的混装类型。

**变更：**
1. Sony GATT 通道内化到 Sony adapter
2. QCY 通道内化到 QCY adapter
3. Transport 层不再有 "channel" 概念，只有 send/receive bytes
4. 如果需要多通道（如 HPC + MC 同时），由 adapter 维护多个 Transport 实例

**风险**：中。Channel 概念在整个项目中广泛使用。

### Phase 5 — 扫描独立化

**目标**：BLE 扫描不依附于任何一个 Transport。

**变更：**
1. 统一 `BleScanner` 组件，所有品牌共用
2. 扫描结果通过 `DiscoveredDevice`（去掉 Sony 前缀）发布
3. 各 Adapter 的 `match()` 接收扫描结果决定是否处理

**风险**：低。

### Phase 6 — 清理收尾

**目标**：移除向后兼容代码，统一命名。

**变更：**
1. 移除 `HeadphoneTransportSelector`（如果不再需要）
2. `DiscoveredSonyDevice` → `DiscoveredDevice`
3. 文档更新

**风险**：低。

## 不变原则

- **不改变协议解析逻辑**（Tandem V1/V2 codec、QCY TLV parser 不变）
- **不改变 UI 层**（`DevicePage.kt` 等不变）
- **不改变 `HeadphoneRepository` 的对外 API**（StateFlow 接口不变）
- **每次 Phase 结束后确保编译通过且基础功能可用**
