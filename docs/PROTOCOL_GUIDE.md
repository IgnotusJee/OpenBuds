# 协议实现说明

SonyRebuild 当前实现的是 Sony Tandem V1/V2 的本地控制子集，主要覆盖 LinkBuds S 的 SPP/Tandem 链路和 WH-1000XM4 的首版兼容路径。

## 资料来源

优先级：

1. `docs/plan/Sony耳机BLE协议完整分析.md`
2. `references/SonyConnect/sources/` 中的原始逆向类

`references/SonyConnect/` 只作为协议互操作参考。新代码应重新实现，不复制官方业务逻辑。

## 传输层

### GATT

GATT UUID 定义在 `SonyGatt.kt`。

关键服务和特征：

```text
TANDEM_V2_HPC_SERVICE              5b833e20-6bc7-4802-8e9a-723ceca4bd8f
TANDEM_V2_MC_SERVICE               5b833e21-6bc7-4802-8e9a-723ceca4bd8f
TANDEM_V1_MC_SERVICE               5b833e23-6bc7-4802-8e9a-723ceca4bd8f
TANDEM_HPC_TO_ACC                 5b833c60-6bc7-4802-8e9a-723ceca4bd8f
TANDEM_HPC_FROM_ACC               5b833c61-6bc7-4802-8e9a-723ceca4bd8f
TANDEM_MC_TO_ACC                  5b833c62-6bc7-4802-8e9a-723ceca4bd8f
TANDEM_MC_FROM_ACC                5b833c63-6bc7-4802-8e9a-723ceca4bd8f
WRITABLE_VALUE_LENGTH             5b833c91-6bc7-4802-8e9a-723ceca4bd8f
DETERMINE_MTU                     5b833c93-6bc7-4802-8e9a-723ceca4bd8f
OPTIMAL_MTU                       5b833c94-6bc7-4802-8e9a-723ceca4bd8f
LE_AUDIO_CAPABILITY_FOR_HPC       5b833e27-6bc7-4802-8e9a-723ceca4bd8f
LE_AUDIO_SWITCH_SUPPORTED_COMPAT  5b833c68-6bc7-4802-8e9a-723ceca4bd8f
```

GATT 握手目标流程：

```text
connect
discover services
find TANDEM_V2_HPC_SERVICE
register TANDEM_V2_MC_SERVICE / TANDEM_V1_MC_SERVICE if present
read OPTIMAL_MTU
request MTU
enable DETERMINE_MTU notification
read WRITABLE_VALUE_LENGTH
enable registered FROM_ACC notifications serially
send Tandem commands
```

`HeadphoneCommand.channel` 会一直传到 `SonyBleClient.sendToChannel()`：V2 Table1 默认写 `TANDEM_HPC_TO_ACC`，V2 Table2 写 V2 MC service 的 `TANDEM_MC_TO_ACC`，V1 Table2 写 V1 MC service 的 `TANDEM_MC_TO_ACC`。收到通知时使用 service UUID + characteristic UUID 恢复通道，避免 V1/V2 MC 共用 `TANDEM_MC_FROM_ACC` 时混淆。

如果缺少 Tandem V2 HPC 服务，`SonyBleClient` 会进入 unsupported endpoint probe，读取 LE Audio capability、friendly name、public address 等可诊断特征。V1 MC-only GATT 连接/控制路径尚未实现，暂不把只有 V1 MC service 的设备当成可控制 endpoint。

### SPP

LinkBuds S 当前主要依赖 `SonySppTransport`。SPP 帧不是裸 Tandem payload，格式为：

```text
0x3E
escaped body
0x3C

body:
dataType(1)
sequence(1)
length(4, big endian)
payload(N)
checksum(1)
```

当前 SPP data type：

```text
0x0C DATA_MDR
0x0E DATA_MDR_NO2
0x01 ACK
0x1C SHOT_MDR
0x1E SHOT_MDR_NO2
0x2C LARGE_DATA_MDR
```

SPP 收到 MDR payload 后，上层会按 SPP frame type 恢复 app data type：

```text
DATA_MDR / SHOT_MDR / LARGE_DATA_MDR  -> [0x0E, command, payload...]
DATA_MDR_NO2 / SHOT_MDR_NO2           -> [0x0F, command, payload...]
```

发送时也做反向映射：app `[0x0E, ...]` 封装为 SPP `DATA_MDR`，app `[0x0F, ...]` 封装为 SPP `DATA_MDR_NO2`。这样 Table2 over SPP 不会丢失 `DATA_MDR_NO2` 上下文。

## Tandem 消息格式

App 内部统一使用：

```text
DataType(1) Command(1) Payload(N)
```

共享 frame data type 只有两种：`DATA_MDR = 0x0E` 用于 Table1，`DATA_MDR_NO2 = 0x0F` 用于 Table2。协议命令常量保留在各自的 V1/V2/Table1/Table2 object 内，避免 `0x13` 等跨版本命令碰撞被错误共用。共享常量（`DATA_MDR`、`DATA_MDR_NO2` 等）定义在 `SonyTandemConstants.kt`。

命令构造集中在 `protocol/` 的协议 object，并通过 `headphones/TandemCodecRegistry.kt` 和 `headphones/EqProtocolEngine.kt` 暴露给 adapter。不要在 UI 或 Repository 里手写字节数组，除非是在新增 builder 的过程中临时验证。

## 当前已实现命令族

### Connect / Device Info

```text
CONNECT_GET_PROTOCOL_INFO  0x00
CONNECT_RET_PROTOCOL_INFO  0x01
CONNECT_GET_DEVICE_INFO    0x04
CONNECT_RET_DEVICE_INFO    0x05
```

已解析：

- `MODEL_NAME`
- `FW_VERSION`
- `SERIES_AND_COLOR_INFO`

设备信息 payload 不是普通字符串拼接。型号/固件是 `type + length + utf8`；系列/颜色是 `type + seriesByte + colorByte`。

### Power / Battery

```text
POWER_GET_STATUS   0x22
POWER_RET_STATUS   0x23
POWER_NTFY_STATUS  0x25
```

已使用：

- `LEFT_RIGHT_BATTERY`
- `CRADLE_BATTERY`

### NC / Ambient Sound

```text
NCASM_GET_STATUS  0x62
NCASM_RET_STATUS  0x63
NCASM_SET_STATUS  0x64
NCASM_NTFY_STATUS 0x65
NCASM_GET_PARAM   0x66
NCASM_RET_PARAM   0x67
NCASM_SET_PARAM   0x68
NCASM_NTFY_PARAM  0x69
```

当前 UI 使用三态模型：

```text
OFF
NOISE_CANCELLING
AMBIENT_SOUND
```

环境声支持：

- level: 1-20
- mode: normal / voice focus

新增 NC/ASM 子功能时必须先确认 `NcAsmInquiredType` 和 payload 长度。这个命令族存在多个相似能力组合，不能只按名称猜 payload。

### Playback

```text
PLAY_GET_STATUS   0xA2
PLAY_RET_STATUS   0xA3
PLAY_SET_STATUS   0xA4
PLAY_NTFY_STATUS  0xA5
```

当前控制：

```text
TRACK_DOWN 0x03
PLAY       0x07
PAUSE      0x01
TRACK_UP   0x02
```

播放状态由 Tandem 响应和 `AudioManager.isMusicActive` 辅助更新。`PlayInquiredType` 按型号配置（`playbackControlType`），支持 `PLAYBACK_CONTROL_WITH_CALL_VOLUME_ADJUSTMENT`（WH-1000XM4 V1）和 `PLAYBACK_CONTROL_WITH_FUNCTION_CHANGE`（LinkBuds S V2）。`isUnsolicited` 字段区分 `RET_STATUS(0xA3)` 和 `NTFY_STATUS(0xA5)`，非主动通知时用 AudioManager 交叉验证防止 stale PAUSED。播放 session 有心跳维持（30s 间隔 GET playback status），避免耳机 watchdog 超时误报 PAUSED。

### EQ / EBB

```text
EQEBB_GET_STATUS   0x52
EQEBB_RET_STATUS   0x53
EQEBB_NTFY_STATUS  0x55
EQEBB_GET_PARAM    0x56
EQEBB_RET_PARAM    0x57
EQEBB_SET_PARAM    0x58
EQEBB_NTFY_PARAM   0x59
EQEBB_GET_EXTENDED_INFO 0x5A
EQEBB_RET_EXTENDED_INFO 0x5B
```

EQ 功能通过 `EqProtocolEngine` 进入，消费 `EqDeviceConfig`（声明于各 device profile）输出 `EqUiCapability`。`EqDeviceConfig` 是单设备 EQ 能力的事实来源：
- `writeInquiredType`：写入使用的 inquired type
- `statusQueryTypes` / `paramQueryTypes` / `extendedInfoQueryTypes`：刷新查询列表
- `bandCount`：总 band 数（含 Clear Bass）
- `hasClearBass`：是否支持 Clear Bass
- `clearBassWriteMode`：`PRESET_EQ_BANDS`（合并到 EQ band 数组）或 `EBB_PARAM`（独立 Clear Bass 命令）

**EQ/EBB payload 解析** 由 `SonyEqEbbPayloadParser` 统一处理，供 V1/V2 codec 共享。支持：
- V1 type codes: `PRESET_EQ(0x01)`, `EBB(0x02)`, `PRESET_EQ_NONCUSTOMIZABLE(0x03)`
- V2 type codes: 所有 `EqEbbInquiredType` entries
- EBB 自动检测 preset 字段（`v2EbbHasPresetField`）：payload 长度匹配时从 band count 偏移推断结构
- Clear Bass 提取：V1 EBB 直接读 payload[1]；V2 EBB 无 preset 时读 payload[1]，有 preset 时读 band[0]

当前支持：

- 官方预设。
- 手动。
- 自定义 1。
- 自定义 2。
- Clear Bass。
- 自定义 EQ bands。

重要映射：

```text
raw band 0 = Clear Bass
raw band 1 = 400 Hz
raw band 2 = 1 kHz
raw band 3 = 2.5 kHz
raw band 4 = 6.3 kHz
raw band 5 = 16 kHz
```

WH-1000XM4 使用 V1 TableSet1 EQ/EBB type code：`PRESET_EQ=0x01`、`EBB=0x02`、`PRESET_EQ_NONCUSTOMIZABLE=0x03`。XM4 抓包中的自定义/手动 EQ 读取为 `0E 56 01` → `0E 57 01 <preset> <count> <raw bands...>`，随后读取扩展 band 信息 `0E 5A 01` → `0E 5B 01 ...`。手动调节具体 band 时发送 `0E 58 01 FF <count> <raw bands...>`；Clear Bass 是 raw band 0，也随这组 `PRESET_EQ` bands 一起写入。

UI 展示值以 `10` 为中心换算成 `-10..+10`。发送时要转回 raw step。

预设行为：

- 调节八个官方预设时，当前 preset 切换到 `手动`。
- 调节 `手动`、`自定义1`、`自定义2` 时不自动切换 preset。

## LE Audio 当前边界

LE Audio 分三层：

- Android 系统 LE Audio profile：系统管理，App 只能观察/跳转设置，不能直接实现音频链路。
- `LE_` 设备端点：常暴露 LE Audio capability，不等同于 Tandem 主控制端点。
- Sony Tandem LEA 命令：用于查询/切换连接策略、连接质量和功能限制。

当前 App 已实现 LEA 状态读取和配对历史查询：

```text
LEA_GET_STATUS   0x42
LEA_RET_STATUS   0x43
LEA_NTFY_STATUS  0x45
LEA_GET_PARAM    0x46
LEA_RET_PARAM    0x47
LEA_NTFY_PARAM   0x49
```

已解析：
- `LeaEnableDisable`：enable/disable 状态。
- `LeaStreamingStatus`：per-ear（L/R 独立），区分 A2DP / LE Audio Unicast / None。
- `LeaPairedHistory`：配对历史（纯经典蓝牙 / 混合）。

LEA 模式切换和多点 LE Audio 连接策略尚未开放写入。

后续实现 LE Audio 切换时应覆盖：

- 手机 `BluetoothAdapter.isLeAudioSupported()`。
- Android `LE_AUDIO_CONNECTION_STATE_CHANGED` 广播。
- Table2 LEA `0x60-0x69`（连接策略/质量/限制，与已实现的 Table1 LEA 状态查询 `0x42-0x49` 互补）。
- Audio table `CONNECTION_MODE_CLASSIC_AUDIO_LE_AUDIO`。

切换 LE Audio 会导致蓝牙重连，必须做确认流程和连接恢复。

## Parser 设计

各 `SonyTandemV*Table*Protocol.parse(raw)` 必须满足：

- 对未知 command 不崩溃。
- 返回 `ParsedTandemResponse.Unknown` 并保留 raw payload。
- SPP 和 GATT 输入都能规范化处理，并保留 Table1/Table2 app data type。
- 已支持响应只更新自己负责的 state，不清空其他功能状态。

新增 parser 后必须补单元测试，包括：

- 正常 payload。
- 边界长度。
- 未知/不完整 payload 不崩溃。

## 单元测试原则

编码测试写死字节，例如：

```kotlin
assertArrayEquals(
    byteArrayOf(0x0E, 0x04, 0x01),
    SonyTandemV2Table1Protocol.buildGetDeviceInfo(DeviceInfoType.MODEL_NAME),
)
```

解析测试应覆盖真实日志中出现过的 payload。发现新设备行为时，先把 raw bytes 加入测试，再改 parser。

## 新功能落地检查表

- 已在 `references/SonyConnect/` 找到原始 message class 或 enum。
- 已在 `docs/plan/` 或文档中记录命令和 payload。
- 已新增 builder。
- 已新增 parser 或 Unknown 日志处理。
- 已新增 unit test。
- Repository 刷新流程会读取状态。
- UI 禁用态和 unsupported 文案明确。
- 真机 log 中能看到 TX/RX 和状态更新。
                           