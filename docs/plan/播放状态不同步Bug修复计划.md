# 播放状态不同步Bug修复计划

## Bug摘要

使用播放控制功能后，不进行任何操作约80秒，耳机音乐消失，App显示暂停，但Android系统显示正在播放。根因：App完全依赖耳机Tandem NTFY_STATUS获取播放状态，无交叉验证和周期性心跳。

## 涉及文件

| 文件 | 角色 |
|------|------|
| `protocol/SonyTandemTypes.kt` | `PlaybackAck` 增加 `isUnsolicited` 字段 |
| `protocol/SonyTandemV2Table1Protocol.kt` | parser 为 `PlaybackAck` 填充新字段 |
| `data/SonyHeadphoneRepository.kt` | 主要修改：交叉验证、心跳、reconcile回退 |
| `protocol/SonyTandemTypes.kt` / 新建测试 | PlaybackAck 新字段测试 |
| `SonyHeadphoneRepository` 测试文件（新建）| 播放状态管理集成测试 |

---

## Phase 1 — 区分 RET_STATUS 和 NTFY_STATUS

### 1.1 `SonyTandemTypes.kt` — `PlaybackAck` 增加字段

```kotlin
data class PlaybackAck(
    val values: List<Int>,
    val status: PlaybackStatus = PlaybackStatus.UNKNOWN,
    val isUnsolicited: Boolean = false,  // NEW: true=NFTY_STATUS, false=RET_STATUS
    override val raw: ByteArray,
) : ParsedTandemResponse
```

### 1.2 `SonyTandemV2Table1Protocol.kt` — parser 区分两种消息

修改第252行附近的 `parseResponse` 分支，将 `PLAY_RET_STATUS` 和 `PLAY_NTFY_STATUS` 分开处理：

```kotlin
PLAY_RET_STATUS -> ParsedTandemResponse.PlaybackAck(
    values = payload.unsignedList(),
    status = parsePlaybackStatus(payload),
    isUnsolicited = false,  // 响应我们的GET请求
    raw = raw,
)
PLAY_NTFY_STATUS -> ParsedTandemResponse.PlaybackAck(
    values = payload.unsignedList(),
    status = parsePlaybackStatus(payload),
    isUnsolicited = true,   // 耳机主动推送
    raw = raw,
)
```

**测试**: 在 `SonyTandemV2Table1ProtocolTest` 中增加两个用例，分别解析 `A3` 和 `A5` payload，断言 `isUnsolicited` 字段正确。

---

## Phase 2 — 收到非请求NTFY时交叉验证AudioManager

### 2.1 `SonyHeadphoneRepository.kt` — `applyPlayback` 改造

修改 `applyPlayback` 方法（第893行），当收到的消息满足以下条件时触发交叉验证：

- `response.isUnsolicited == true`（耳机主动推送，非我们请求的响应）
- `response.status == PlaybackStatus.PAUSED`
- 当前 App 记录的 `playbackStatus == PlaybackStatus.PLAYING`
- `pendingPlaybackStatus == null`（非用户主动操作转换期）
- `shouldUseTandemPlaybackStatus() == true`

交叉验证逻辑：

```kotlin
private fun applyPlayback(response: ParsedTandemResponse.PlaybackAck) {
    appendLog("Playback notification ${response.values} status=${response.status}" +
        if (response.isUnsolicited) " [NTFY]" else " [RET]")
    if (response.status != PlaybackStatus.UNKNOWN) {
        applyPlaybackStatus(response.status, source = "Tandem", isUnsolicited = response.isUnsolicited)
    } else {
        updatePlaybackStatusFromAudioManager()
    }
}
```

### 2.2 `applyPlaybackStatus` 增加交叉验证参数

```kotlin
private fun applyPlaybackStatus(
    status: PlaybackStatus,
    source: String,
    isUnsolicited: Boolean = false,
) {
    val pending = pendingPlaybackStatus
    if (pending != null) {
        // 现有stale检测逻辑不变
        val now = SystemClock.elapsedRealtime()
        if (now <= pending.ignoreOppositeUntilMs) {
            if (status != pending.expected) {
                appendLog("Ignored stale playback status $status from $source " +
                    "while waiting for ${pending.expected}")
                return
            }
            _state.update { it.copy(playbackStatus = status) }
            return
        }
        pendingPlaybackStatus = null
    }

    // NEW: 交叉验证
    if (isUnsolicited && source == "Tandem" &&
        status == PlaybackStatus.PAUSED &&
        _state.value.playbackStatus == PlaybackStatus.PLAYING
    ) {
        val audioActive = mediaController.currentFallbackStatus() == PlaybackStatus.PLAYING
        if (audioActive) {
            appendLog("NTFY PAUSED from headphones but AudioManager says PLAYING — " +
                "re-querying Tandem before accepting")
            refreshPlaybackState()  // 发送GET playback status重新确认
            // 不立即更新状态，等GET的RET响应来更新
            return
        }
    }

    _state.update { it.copy(playbackStatus = status) }
}
```

**关键设计决策**: 当 Tandem 和 AudioManager 矛盾时，优先相信 AudioManager（因为它反映的是手机端实际音频状态），但通过重新查询 Tandem 来确认。如果重新查询也返回 PAUSED，则接受；如果返回 PLAYING，则保持 PLAYING。

**测试**: 
- 正常 RET_STATUS 依旧按原逻辑处理
- NTFY_STATUS PAUSED + AudioManager PLAYING → 不更新状态，触发重新查询
- NTFY_STATUS PAUSED + AudioManager PAUSED → 正常接受
- NTFY_STATUS PLAYING → 正常接受（无需交叉验证，因为PLAYING不会导致"音乐消失"的问题）

---

## Phase 3 — 播放中周期性心跳

### 3.1 新增常量和字段

```kotlin
private const val PLAYBACK_HEARTBEAT_INTERVAL_MS = 30_000L  // 30秒

// 新增字段
private val playbackHeartbeatRunnable = Runnable { sendPlaybackHeartbeat() }
private var playbackHeartbeatActive = false
```

### 3.2 心跳发送和调度

```kotlin
private fun sendPlaybackHeartbeat() {
    if (!playbackHeartbeatActive) return
    if (_state.value.playbackStatus != PlaybackStatus.PLAYING) {
        stopPlaybackHeartbeat()
        return
    }
    val profile = _state.value.connectedProfile ?: return
    if (!_state.value.deviceInfo.protocolReady) return
    appendLog("Playback heartbeat: GET playback status")
    refreshPlaybackState()
    mainHandler.postDelayed(playbackHeartbeatRunnable, PLAYBACK_HEARTBEAT_INTERVAL_MS)
}

private fun startPlaybackHeartbeat() {
    if (playbackHeartbeatActive) return
    if (!shouldUseTandemPlaybackStatus()) return
    playbackHeartbeatActive = true
    appendLog("Playback heartbeat started (interval=${PLAYBACK_HEARTBEAT_INTERVAL_MS}ms)")
    mainHandler.postDelayed(playbackHeartbeatRunnable, PLAYBACK_HEARTBEAT_INTERVAL_MS)
}

private fun stopPlaybackHeartbeat() {
    if (!playbackHeartbeatActive) return
    playbackHeartbeatActive = false
    mainHandler.removeCallbacks(playbackHeartbeatRunnable)
    appendLog("Playback heartbeat stopped")
}
```

### 3.3 心跳生命周期管理

在 `applyPlaybackStatus` 中（status 更新后），根据新状态启停心跳：

```kotlin
// 在 _state.update { it.copy(playbackStatus = status) } 之后
when (status) {
    PlaybackStatus.PLAYING -> startPlaybackHeartbeat()
    else -> stopPlaybackHeartbeat()
}
```

在 `onConnectionStateChanged` 断开连接时停止心跳（第553行附近已有 `clearPendingPlaybackTransition`）：

```kotlin
if (!connected) {
    clearPendingPlaybackTransition()
    mainHandler.removeCallbacks(playbackRefreshRunnable)
    stopPlaybackHeartbeat()  // NEW
}
```

### 3.4 心跳与用户命令的时间协调

在 `schedulePlaybackStateRefresh` 中，用户命令触发刷新后重置心跳定时器：

```kotlin
private fun schedulePlaybackStateRefresh() {
    mainHandler.removeCallbacks(playbackRefreshRunnable)
    mainHandler.postDelayed(playbackRefreshRunnable, PLAYBACK_REFRESH_AFTER_COMMAND_MS)
    // NEW: 重置心跳，避免刚查询完马上又心跳
    if (playbackHeartbeatActive) {
        mainHandler.removeCallbacks(playbackHeartbeatRunnable)
        mainHandler.postDelayed(playbackHeartbeatRunnable, PLAYBACK_HEARTBEAT_INTERVAL_MS)
    }
}
```

**测试**:
- 状态变为 PLAYING → 心跳启动
- 30秒后发送 GET playback status
- 状态变为 PAUSED/STOP/UNKNOWN → 心跳停止
- 断开连接 → 心跳停止
- 用户命令后 → 心跳定时器重置
- 心跳发送失败的场景（协议未就绪）不崩溃

---

## Phase 4 — Reconcile 中增加 AudioManager 回退

### 4.1 修改 `refreshPlaybackStatusAfterCommand`

在 reconcile 超时（2.8秒后）的回调中，如果 Tandem 查询无法确认状态，回退到 AudioManager：

```kotlin
private fun refreshPlaybackStatusAfterCommand() {
    if (_state.value.connectedDevice == null) return
    if (shouldUseTandemPlaybackStatus()) {
        // 记录查询前的状态，用于事后对比
        val statusBefore = _state.value.playbackStatus
        refreshPlaybackState()
        // Schedule a follow-up check: if Tandem response doesn't arrive 
        // or doesn't match expected, fall back to AudioManager
        mainHandler.postDelayed({
            val statusAfter = _state.value.playbackStatus
            val audioStatus = mediaController.currentFallbackStatus()
            if (statusAfter == statusBefore && statusAfter != audioStatus) {
                appendLog("Reconcile: Tandem status unchanged ($statusAfter) but " +
                    "AudioManager says $audioStatus — falling back")
                _state.update { it.copy(playbackStatus = audioStatus) }
                if (audioStatus == PlaybackStatus.PLAYING) {
                    startPlaybackHeartbeat()
                }
            }
        }, 800L)  // 给Tandem 800ms响应时间
    } else {
        updatePlaybackStatusFromAudioManager(force = true)
    }
}
```

**测试**:
- reconcile 后 Tandem 正常返回 → 不触发回退
- reconcile 后 Tandem 不应答（状态未变化）且与 AudioManager 矛盾 → 回退到 AudioManager
- 非 Tandem 设备 → 原逻辑不变（直接用 AudioManager）

---

## Phase 5 — 防止修复引入回归

### 5.1 需要保护的现有行为

| 行为 | 如何保护 |
|------|---------|
| 用户主动 pause → UI 立即显示 PAUSED（乐观更新） | `pendingPlaybackStatus` 机制不变，`beginPlaybackStatusTransition` 不变 |
| 快速 play/pause 切换 → stale 状态被过滤 | `ignoreOppositeUntilMs` 窗口不变 |
| 非 LinkBuds S 设备 → 不受影响 | `shouldUseTandemPlaybackStatus()` 检查在心跳启动和交叉验证入口 |
| Tandem 连接断开后状态重置 | `onConnectionStateChanged(connected=false)` 中已有重置逻辑，增加心跳停止 |
| RET_STATUS PAUSED（响应GET查询）→ 正常接受 | 交叉验证仅针对 `isUnsolicited=true` 的消息 |

### 5.2 边缘场景

| 场景 | 预期行为 |
|------|---------|
| 耳机真的暂停了（AudioManager也PAUSED） | 交叉验证通过，正常接受PAUSED |
| 耳机NTFY PAUSED但AudioManager PLAYING（本次bug） | 拒绝直接更新，重新查询Tandem确认 |
| 重新查询后耳机确认PAUSED | 接受PAUSED，audio可能确实停了但系统通知延迟 |
| 重新查询后耳机返回PLAYING | 保持PLAYING，记录异常日志 |
| 心跳发送时协议未就绪 | `sendCommandIfReady` 静默跳过 |
| 心跳期间用户切换NC/ASM等功能 | 互不影响，各自走各自的命令通道 |
| 心跳与用户命令并发 | `schedulePlaybackStateRefresh` 中重置心跳定时器避让 |

---

## 实现顺序

1. **Phase 1** — `PlaybackAck.isUnsolicited` 字段（最小改动，为后续打基础）
2. **Phase 2** — 交叉验证逻辑（核心修复）
3. **Phase 3** — 周期性心跳（预防性措施，减少耳机超时可能性）
4. **Phase 4** — Reconcile 回退（增强鲁棒性）
5. 每阶段完成后运行 `.\gradlew.bat testDebugUnitTest` 验证不引入回归

## 验证方法

1. **单元测试**: 新增测试覆盖 `applyPlaybackStatus` 的所有分支（正常、stale过滤、交叉验证触发、交叉验证通过、交叉验证拒绝）
2. **真机测试**: 连接 LinkBuds S，播放音乐后静置 5 分钟，观察：
   - 心跳日志是否每 30 秒出现
   - App 播放状态是否保持 PLAYING
   - 耳机音乐是否持续播放
3. **真机压力测试**: 快速连续按 play/pause 10 次，验证 UI 状态最终与耳机实际状态一致
