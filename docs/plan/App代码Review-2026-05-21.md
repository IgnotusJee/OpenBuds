# App Code Review — 2026-05-21

对照 `docs/plan/`、`docs/` 下的设计文档和 `app/` 下的实现代码进行审查。

## 一、架构对齐

### 1.1 三层架构 (通过)

代码严格遵循 CLAUDE.md 中描述的三层架构：

| 层次 | 文档位置 | 实现文件 | 对齐 |
|------|---------|---------|------|
| BLE GATT 传输层 | `SonyBleClient.kt` + `SonySppTransport.kt` | `ble/` | ✅ |
| Tandem 协议消息层 | `SonyTandemV2Table1Protocol.kt` / `V1` / `V2Table2` | `protocol/` | ✅ |
| 应用层 | `HeadphoneRepository.kt` + `headphones/` adapter | `data/` + `headphones/` | ✅ |

DEVELOPMENT.md 规定的职责边界全部遵守。Repository 不直接构造协议字节，UI 不持有 BLE 状态。

### 1.2 Adapter 模式 (通过)

`HeadphoneAdapter` 接口 + `HeadphoneAdapterRegistry` 的抽象层设计合理。按型号区分 profile/capability 的逻辑放在 adapter 而非 Repository。当前注册了 `SonyTandemHeadphoneAdapter` 一个 adapter，通过内部 `ProfileTemplate` 匹配 LinkBuds S 和 WH-1000XM4。

---

## 二、协议实现精确度

### 2.1 命令字节码 (全部通过)

V2 Table1 命令字节码与 `docs/plan/Sony耳机BLE协议完整分析.md` 完全一致：

```
CONNECT_GET_PROTOCOL_INFO  0x00 ✅
CONNECT_RET_PROTOCOL_INFO  0x01 ✅
CONNECT_GET_DEVICE_INFO    0x04 ✅
CONNECT_RET_DEVICE_INFO    0x05 ✅
POWER_GET_STATUS           0x22 ✅
POWER_RET_STATUS           0x23 ✅
POWER_NTFY_STATUS          0x25 ✅
EQEBB_*                    0x50-0x5B ✅
NCASM_*                    0x60-0x69 ✅
PLAY_*                     0xA0-0xA9 ✅
```

### 2.2 V1/V2 协议分层

WH-1000XM4 的 feature 级协议选择正确：

```kotlin
// SonyTandemHeadphoneAdapter.kt ProfileTemplate.featureProtocolMap:
"WH-1000XM4" -> Battery/NoiseControl/Ambient → V1_TABLE1, EQ/Playback → V2_TABLE1
```

`SonyTandemV1Table1Protocol` 正确使用了 `NCASM_GET_PARAM (0x66)` 和 `NCASM_SET_PARAM (0x68)` 而非 V2 的 `NCASM_SET_STATUS (0x64)`。电池查询使用 V1 的 `COMMON_GET_BATTERY_LEVEL (0x10)`。这些与协议文档和 btsnoop 抓包一致。

### 2.3 OnOffSettingValue 反转 (通过)

代码中 `ENABLE=0x00`, `DISABLE=0x01`，与协议文档 "OnOffSettingValue: 0x00=ON, 0x01=OFF" 一致。所有 NC/ASM builder 正确使用这个约定。

### 2.4 EQ band index 映射 (通过)

```kotlin
EQ_CLEAR_BASS_RAW_INDEX = 0   // Clear Bass
EQ_FIRST_FREQUENCY_RAW_INDEX = 1  // 400Hz 起点
```

与 PROTOCOL_GUIDE.md "raw band 0 = Clear Bass, raw band 1 = 400 Hz" 一致。`displayEqBands` 正确 drop 了 index 0（Clear Bass）后展示 UI bands。`displayEqStep` 使用 `rawStep - 10` 做 raw↔display 转换。

### 2.5 SPP 帧格式 (通过)

`SonySppTransport` 的帧格式实现与 PROTOCOL_GUIDE.md 完全一致：

```
FRAME_START(0x3E) + escaped(body) + FRAME_END(0x3C)
body: dataType(1) + sequence(1) + length(4,BE) + payload(N) + checksum(1)
```

转义表：`0x3C→0x3D+0x2C`, `0x3D→0x3D+0x2D`, `0x3E→0x3D+0x2E`。unescape 使用 `or 0x10` 正确反转。

SPP 接收后规范化为 `[0x0E, command, payload...]`，使 GATT 和 SPP 路径共享同一个 parser。

---

## 三、问题与建议

### 3.1 播放控制路径不完整 ⚠️

**严重程度：中**

Repository 的 `playbackPrevious/playbackPlayPause/playbackNext` 只发送 Android `AudioManager` media key 事件，从未调用 adapter 的 `buildPlaybackCommands()`。

```kotlin
// HeadphoneRepository.kt
fun playbackPlayPause() {
    if (!canWrite(HeadphoneFeature.PLAYBACK_CONTROL)) return
    appendLog("MEDIA play/pause via AudioManager")
    mediaController.playPause()  // 只走 Android media key
}
```

`SonyTandemHeadphoneAdapter.buildPlaybackCommands()` 存在但完全未被调用。对 LinkBuds S 这类设备，Tandem PLAY_SET_STATUS 命令可以让耳机端主动发送播放控制到手机，效果与 AudioManager 路径不同。

**建议**：在 Repository 的 playback 方法中增加 Tandem 路径，或者在 UI 层区分"耳机控制手机播放"和"手机控制耳机播放"两种语义。

### 3.2 SPP ACK 超时线程泄漏 ⚠️

**严重程度：中**

```kotlin
// SonySppTransport.kt scheduleAckTimeout()
Thread({
    try { Thread.sleep(ACK_TIMEOUT_MS) } catch (_: InterruptedException) { return@Thread }
    // ...
}, "OpenBuds-SppAckTimeout").start()
```

每次等待 ACK 都创建一个新线程。在高丢包场景下（蓝牙干扰、距离过远），短时间内可能累积大量超时线程。ACK_TIMEOUT_MS 为 1200ms，MAX_ACK_RETRIES=1，但每次重试又会创建新线程。

**建议**：改用 `ScheduledExecutorService`（单线程）或 Kotlin coroutine 的 `withTimeout`。

### 3.3 乐观 UI 更新的状态回滚窗口 ⚠️

**严重程度：低**

```kotlin
// setNoiseControlMode() — 先更新 UI state，再发命令
_state.update {
    it.copy(noiseControlState = it.noiseControlState.forMode(mode)...)
}
// 然后发送 SET 命令
adapter.buildSetNoiseControlModeCommands(...).forEach { sendCommand(it.label, it.bytes) }
refreshNoiseControlStateAfterWrite(profile)
```

在命令发送和 refresh 响应到达之间，UI 显示乐观值。如果命令失败或耳机拒绝，会出现短暂的不一致。DEVELOPMENT.md 提到这是已知风险，建议在后续迭代中增加写入失败回滚。

### 3.4 buildSetClearBass 对非 XM4 设备的路径选择可读性差 ⚠️

**严重程度：低**

```kotlin
fun setClearBass(level: Int) {
    // ...
    if (!ensureConnectedProfile().usesXm4EqWriteStrategy() && eq.rawBandSteps.size > EQ_CLEAR_BASS_RAW_INDEX) {
        // 路径 A: 当作 EQ band[0] 发送
        updateEqBands(rawSteps, targetPreset)
        sendEqBandSteps(...)
    } else {
        // 路径 B: 使用独立的 EBB 命令
        adapter.buildSetClearBassCommands(...)
    }
}
```

两个路径的触发条件隐式依赖 `rawBandSteps` 是否已从耳机刷新回来。首次连接后、EQ 未刷新时走路径 B；刷新后切换为路径 A。两条路径发送的字节完全不同。

**建议**：将路径选择逻辑移到 adapter 中（`buildSetClearBassCommands` 应自行决定用 EBB 还是 Custom EQ band 方式），保持 Repository 只管 dispatch。

### 3.5 SonyTandemV1Table1Protocol EQ 方法委托 ⚠️

**严重程度：低**

```kotlin
fun buildGetEqEbbStatus(type: EqEbbInquiredType): ByteArray =
    SonyTandemV2Table1Protocol.buildGetEqEbbStatus(type)
fun buildGetEqEbbParam(type: EqEbbInquiredType): ByteArray =
    SonyTandemV2Table1Protocol.buildGetEqEbbParam(type)
```

V1 协议类将 EQ 方法直接委托给 V2。这是因为 WH-1000XM4 的 EQ feature 走 V2 协议（在 featureProtocolMap 中配置）。但把 V2 方法放在 V1 类里作为公开 API 容易让人误以为所有 V1 设备都支持这些 EQ 命令。

**建议**：移除 V1 协议类中的 EQ 委托方法，让 adapter 根据 featureProtocolMap 直接路由到正确的协议类。

### 3.6 parseNoiseControl 复杂度 ⚠️

**严重程度：低**

`parseNoiseControl()` 函数长达 100+ 行，包含 8 种 `NcAsmInquiredType` 的 switch-case。每种类型有不同的 payload 偏移量和语义。虽然目前全部通过单元测试，但新增 NC/ASM 子类型时容易引入偏移错误。

**建议**：考虑将每种 inquired type 的 parser 提取为独立方法，或使用策略模式按 type 分发。

### 3.7 DeviceInfo.state 中的冗余字段 ℹ️

`DeviceInfoState` 包含 `modelColor`, `modelImageUrl`, `modelImageSourceColor` 和 `protocolReady`。其中 `protocolReady` 语义上不属于设备信息（它表示传输层就绪状态），`modelColor/modelImageUrl/modelImageSourceColor` 是派生/缓存值。

**建议**：`protocolReady` 可以提升到 `HeadphoneUiState` 顶层，图片相关字段可以合并到一个 `DeviceImageState`。

### 3.8 缺少 SonyTandemFrame.kt ℹ️

DEVELOPMENT.md 的代码结构图中列出了 `protocol/SonyTandemFrame.kt`，但实际文件系统中不存在。`SonyTandemFrame` 对象和 `TandemMessage` 数据类定义在 `SonyTandemV2Table1Protocol.kt` 文件的底部。这不影响功能但文档与实际不一致。

**建议**：更新 DEVELOPMENT.md 的代码结构图。

---

## 四、测试覆盖

### 4.1 已有测试 (通过)

| 测试类 | 覆盖范围 | 状态 |
|--------|---------|------|
| `SonyTandemV2Table1ProtocolTest` | 命令编码、响应解析、边界 payload、SPP 归一化 | ✅ 27 个用例 |
| `SonyTandemHeadphoneAdapterTest` | Profile 匹配、refresh 命令计划、NC/ASM 命令路由 | ✅ 5 个用例 |
| `UiEffectsPolicyTest` | 四种底栏模式 × 特效开关矩阵 | ✅ 3 个用例 |
| `AppColorModeTest` | 颜色模式主题重组 | ✅ |
| `OpenBudsAppSmokeTest` | FloatingGlass + Miuix + UI effects 冷启动 | ✅ |

### 4.2 缺失的测试

| 缺失项 | 优先级 |
|--------|--------|
| `SonySppTransport` 帧编码/解码、转义、校验和、ACK 重试 | 高 |
| `SonyBleClient` Sony Audio AD 解析（V1/V2 组合 payload） | 高 |
| `HeadphoneRepository` 状态管理（onMessage → state 更新链） | 中 |
| XM4 Clear Bass 路径的端到端测试 | 中 |
| NC/ASM 全部 8 种子类型的 parser 测试 | 中 |
| V2 Table2 扩展入口测试（即使当前只返回 Unknown） | 低 |

---

## 五、UI 和特效实现

### 5.1 Backdrop 拓扑 (通过)

`UiEffectsPolicy.kt` 的实现与 `REAREye_UI实现分析.md` 第 8 节记录的落地状态一致：

- `FloatingGlass` 启用 kyant backdrop + Miuix textureBlur 双链路
- `SemiTransparent` 只启用底栏 textureBlur，不暴露给卡片
- `Normal`/`Floating` 不创建 backdrop
- 卡片玻璃只在 `glassCardsEnabled=true` 时启用

### 5.2 已清理的安全模式 (通过)

代码中不存在设备黑名单（Xiaomi/Redmi/Poco 特判）、`safe surfaces active` 等强制回退逻辑。只有 `isRenderEffectSupported()` 作为运行时能力 guard。与 REAREye_UI实现分析.md 第 8.1 节描述一致。

### 5.3 冷启动系统栏 (通过)

`MainActivity.onCreate` 在 `setContent` 前调用 `enableEdgeToEdge` 和 `configureInitialSystemBars()`，XML theme 也声明透明系统栏。启动首帧即透明，与 DEV.md 要求一致。

---

## 六、总结

### 优势

1. **三层架构清晰**：BLE→Tandem→App 的分层严格，职责边界明确
2. **协议字节码准确**：所有 V2 Table1 命令码、枚举值与逆向文档完全对齐
3. **V1/V2 共存设计合理**：通过 featureProtocolMap 按功能粒度为 XM4 选择 V1 路径
4. **SPP/GATT 双传输层**：SPP 帧封装/解包正确，规范化后共享 parser
5. **Adapter 模式可扩展**：新增设备型号只需增加 ProfileTemplate
6. **单元测试扎实**：协议编码/解码有 27 个测试用例，覆盖正常、边界、未知场景
7. **UI 特效拓扑正确**：backdrop source/consumer 分离，无递归渲染风险

### 待改进

1. **播放控制**：只实现了 Android media key 路径，未使用 Tandem PLAY_SET_STATUS
2. **SPP ACK 超时**：使用裸 Thread 而非线程池/coroutine
3. **测试缺口**：SPP 传输层和 Repository 状态管理无单元测试
4. **V1 协议类**：EQ 方法不当委托给 V2
5. **parseNoiseControl**：函数过长，建议按 inquired type 拆分
6. **文档不一致**：DEVELOPMENT.md 引用了不存在的 `SonyTandemFrame.kt` 文件

### 与文档总体对齐度：高

代码实现与 `docs/plan/Sony耳机BLE协议完整分析.md`、`docs/PROTOCOL_GUIDE.md`、`docs/DEVELOPMENT.md`、`docs/FEATURE_STATUS.md`、`docs/analysis/REAREye_UI实现分析.md` 的描述基本一致。发现的偏差主要是播放控制路径不完整和文档中一处过时的文件引用，不影响核心协议的准确性。
