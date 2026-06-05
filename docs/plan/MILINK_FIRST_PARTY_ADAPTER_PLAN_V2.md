# OpenBuds 米链第一方耳机适配器集成方案 V2

更新日期：2026-06-05

本文是 V2 草案的可行性修订版，基于对 `com.milink.service` 和 `com.xiaomi.bluetooth` 反编译源码的交叉验证。结论先行：**把 OpenBuds 设备伪装成 MiTWS 并让米链用第一方耳机页面渲染是可行的，且 MiTWS 路径是主线。** AirPods 伪装路径（V1）已废弃，不再维护。

主线在 `com.milink.service` 内做 MiTWS facade，复用现有 bridge 状态；`com.xiaomi.bluetooth` 只作为弹窗、状态栏通知、或更深层 MMA/GATT/SPP 的后续实验。

## 0. 可行性结论

| 范围 | 可行性 | 当前判断 | 主线阶段 |
|------|--------|----------|----------|
| `com.milink.service` 中 hook `MxBluetoothManager.checkIsMiTWS(BluetoothDevice)` | 高 | 能让 `BluetoothServiceClient.getDeviceType()` 进入 `HEADSET`，但只是分类入口，不代表状态和控制已可用 | M1 |
| `com.milink.service` 中 hook MiTWS 状态 callback / getter | 高 | `registerCallback`、电量、ANC、wear getter 已可从 bridge 喂数据；下一步不是继续堆只读点位，而是让 `com.miui.headset.runtime` 自己看到 OpenBuds 作为自然的 active headset | M2 |
| `com.milink.service` 中 hook MiTWS 控制方法 | 中 | `openAnc`、`openTransparent`、`closeAnc` 已可转发到 OpenBuds；runtime projection 已进入可用态，但 ANC 首屏时序与 ring / volume / audio effect 等剩余控制面仍待收敛 | M3+ |
| `com.xiaomi.bluetooth` 快连弹窗/通知接入 | 中低 | FastConnect 依赖小米/Apple 广播和 deviceId 云控，Sony/QCY 广播默认进不来，需要伪造 adv/cache/notification 状态 | M4 实验 |
| `com.xiaomi.bluetooth` GATT/SPP/MMA 代理 | 低到中 | 技术上可 hook，但服务/特征 UUID、SPP UUID、MMA 帧、注册门控和 Sony 默认路径都不匹配；不能作为 M2 前置 | M5 实验 |
| 完整 MMA 协议栈或 Xiaomi 蓝牙插件仿真 | 低 | 工作量接近逆向一套 Mi Headset 协议，且蓝牙进程稳定性风险高 | 暂不承诺 |

**推荐路线**：MiTWS 作为主线。M2 已证明第一方页面、状态和降级基本稳定；截至 2026-06-04，经过进程筛选后，卡片消失和高频抖动问题已大幅缓解，主线已收敛到 `com.milink.service:ui` 和 `com.milink.service:core` 两个关键进程。下一步仍然优先补齐 `ProfileContext + DiscoveryImpl` 的 runtime projection，让 `ProfileImpl` / `Query` / `HeadsetDetailFragment` 走更自然的第一方运行时路径，再补 ring / volume / audio effect 和剩余 query 面。`com.xiaomi.bluetooth` 的 FastConnect、GATT 或 SPP/MMA 仍是后续实验，不作为当前主线阻塞项。

## 1. 对原 V2 草案的关键修正

### 1.1 `checkIsMiTWS` 只是分类入口

米链分类链路：

```text
BluetoothServiceClient.getDeviceType(dev)
  -> isMiHeadset(dev) || isAirPods(dev)
  -> HEADSET

isMiHeadset(dev)
  -> MxBluetoothManager.checkIsMiTWS(dev) == 1
```

因此 hook `MxBluetoothManager.checkIsMiTWS(BluetoothDevice)` 返回 `1` 可以让 OpenBuds 设备进入 `HEADSET`。但这只解决"这是不是第一方耳机"的问题，不解决下面三件事：

1. MiTWS 状态是否能被 `HeadsetDetailFragment` 读到。
2. 米链是否会主动调用 `connectMma` 并等待 Xiaomi MMA 连接。
3. 控件操作是否会走 `openAnc` / `openTransparent` / `closeAnc` 等可 hook 方法。

原草案中"只需要将 hook 点从 `checkIsAirPods` 改为 `checkIsMiTWS`"的结论需要降级为：**只对分类成立，不能作为完整可用结论。**

### 1.2 Flora deviceId 的 hook 层级要区分清楚

反编译链路中，`MxBluetoothService.checkIsMiTWS(BluetoothDevice)` 会执行：

```text
if (Constant.isFlora(getDeviceId(device))) return 1
else mBluetoothHeadsetService.checkIsMiTWS(address)
```

这里的 `getDeviceId()` 是 `MxBluetoothService` 的私有方法（源码第737行），会调用 `IMiuiHeadsetService.checkSupport(device)` 这个 AIDL 跨进程调用。如果我们只 hook `MxBluetoothManager.getDeviceId(BluetoothDevice)`，不会影响 `MxBluetoothService.checkIsMiTWS()` 内部的 Flora 快速路径。

**关键理解**：如果选择在 `MxBluetoothManager.checkIsMiTWS()` 层 hook（推荐），Flora 快速路径完全不会被触发，因为 hook 在 Manager 层就返回了，不会执行到 `MxBluetoothService.checkIsMiTWS()` 内部的 `Constant.isFlora(getDeviceId())` 调用。Flora deviceId 的讨论只对以下场景有意义：
- 不 hook Manager 层而是 hook 更底层（如 `MxBluetoothService` 或 `IMiuiHeadsetService`）
- 想通过设置 Flora deviceId 让 service 层自动返回 1 以减少 hook 点

V2 主线策略：

1. 对 OpenBuds allowlist 设备，直接在 `MxBluetoothManager.checkIsMiTWS(BluetoothDevice)` 层返回 `1`，不要依赖 service 内部 Flora fast-path。
2. `MxBluetoothManager.getDeviceId(BluetoothDevice)` 的 hook 用于米链 UI、图标、能力 profile，而不是用于让 service fast-path 生效。
3. 如果后续确实需要 service fast-path，需要另行 hook `MxBluetoothService.getDeviceId()` 或蓝牙侧 `IMiuiHeadsetService.checkSupport()`，这属于更高风险实验。

### 1.3 `MiuiGattPeripheral` 的真实签名不是草案中的形状

参考源中的 `MiuiGattPeripheral` 位于：

```text
references/mi/com.xiaomi.bluetooth/sources/com/xiaomi/bluetooth/peripheral/MiuiGattPeripheral.java
```

关键方法签名是字符串 UUID 门面，而不是 `BluetoothGattCharacteristic` 直接参数：

```java
public boolean writeCharacteristic(BluetoothDevice device, String serviceUuid, String characteristicUuid, byte[] value)
public boolean readCharacteristic(BluetoothDevice device, String serviceUuid, String characteristicUuid)
public boolean writeDescriptor(BluetoothDevice device, String serviceUuid, String characteristicUuid, String descriptorUuid, byte[] value, int type)
public boolean setCharacteristicNotification(BluetoothDevice device, String serviceUuid, String characteristicUuid, boolean enable)
public boolean requestMTU(BluetoothDevice device, int mtu)
public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
```

所以如果进入 GATT 实验，不能只改写 `value`。必须同时处理：

- `serviceUuid` 和 `characteristicUuid` 从 Xiaomi MMA UUID 到 Sony/QCY 实际 UUID 的映射。
- `readCharacteristic`、`writeDescriptor`、`setCharacteristicNotification` 的同一套 UUID 映射。
- `onCharacteristicChanged` 里回调给 `IPCServiceEventCallback.onCharacteristicChanged(device, serviceUuid, characteristicUuid, value)` 的反向 UUID 和数据映射。
- `BluetoothGatt.getService()` 查不到 Xiaomi UUID 时的失败路径。
- `onServicesDiscovered` 回调（第582-594行）→ `onServicesDiscovered(BluetoothDevice, List<BluetoothGattService>, int status)`。如果 Xiaomi 层在 service discovery 后发现没有预期的 Xiaomi 服务，可能会提前断开。

只做 `writeCharacteristic` after-hook 修改字节会太晚，因为原方法已经按 Xiaomi UUID 查找服务和特征，Sony/QCY 设备大概率直接失败。

### 1.4 `com.xiaomi.bluetooth` 快连不是自然入口

FastConnect 侧依赖 `ScanRecord` 的小米 fast-pair service data、manufacturer data、deviceId 云控和 `checkAdvData()`。Sony/QCY 广播默认不会通过这些条件。即使 hook `MiuiFastConnectControllerFactory.getController()`，设备也可能根本到不了 ControllerFactory。

因此快连弹窗不能作为 M2 核心路径。它应拆成后续实验：

1. 先 trace `MiuiFastConnectService` 是否看到目标设备。
2. 再验证是否能通过 cached deviceId、notification state、或手动启动 Activity 触发弹窗。
3. 最后才评估是否伪造 adv data 或 hook `checkAdvData()`。

### 1.5 Sony 默认不适合走 Xiaomi GATT/SPP 注入

OpenBuds 当前对 LinkBuds S 的可靠路径是 App 侧 SPP/Tandem。Sony 官方 App 也同时存在 GATT 和 SPP 两条控制路径：GATT 侧通过 Sony 自己的 service/characteristic UUID 做 Tandem 读写；SPP 侧通过 MDR SPP UUID 建 RFCOMM socket。

`com.xiaomi.bluetooth` 里也存在 `MiuiSppPeripheral`，它可以用调用方传入的 UUID 建 RFCOMM socket，并通过 `sendData(byte[])` 写原始字节。但这个 SPP 入口属于 Xiaomi PC service / MMA 注册体系，受 package allowlist、cloud switch、type=SPP 注册、UUID 和上层协议状态机约束。它不是一个可以直接替换成 Sony Tandem SPP 的自然入口。

`MiuiSppPeripheral.sendData()`（源码第248-273行）没有帧封装、没有 ACK、没有重试——只是原始字节写入。Sony Tandem 的 SPP 帧封装（`SppFrameType`, `SonySppPayloadMapper`）如果通过 Xiaomi SPP 发送，需要在调用 `sendData` 之前自己完成帧封装。

因此，把 Xiaomi GATT 门面直接改造成 Sony GATT 代理，或把 Xiaomi SPP 门面直接改造成 Sony SPP 代理，都可能绕开当前最可靠的 OpenBuds 协议状态来源。

V2 主线应继续以 OpenBuds App 里的 `HeadphoneRepository` 为唯一状态源，通过 bridge 向米链提供状态和命令。`com.xiaomi.bluetooth` 不应在主线里重复持有 BLE/SPP 连接。

## 2. 修订后的架构

```text
+---------------------------+     AIDL bridge      +--------------------------------+
| OpenBuds App              | <------------------> | LSPosed module                 |
| HeadphoneRepository       |                      | injected into com.milink.service|
| Sony/QCY transport        |                      |                                |
| MilinkBridgeService       |                      | MilinkMiTwsFacadeHook          |
+---------------------------+                      | - checkIsMiTWS                 |
                                                   | - getDeviceId                  |
                                                   | - connectMma/disconnectMma    |
                                                   | - getBattery/getAnc/getWear   |
                                                   | - registerCallback dispatch   |
                                                   | - openAnc/openTransparent     |
                                                   +--------------------------------+
                                                              |
                                                              v
                                                   com.milink.service 原生 UI
                                                   Control Center / MLCard
                                                   HeadsetDetailFragment
```

可选实验层：

```text
LSPosed module injected into com.xiaomi.bluetooth
  - trace FastConnect scan / cached deviceId / notification state
  - trace MiuiGattPeripheral UUID/data
  - trace MiuiSppPeripheral and MMA-over-SPP registration
  - gated experiments only; default no mutation
```

关键边界：

- 主线不 hook `com.android.bluetooth`。
- 主线不让 `com.milink.service` 的 MiTWS 调用真正打到 Xiaomi MMA 连接层。
- 主线不在 `com.xiaomi.bluetooth` 内运行完整 OpenBuds 协议栈。
- 真实小米耳机必须透传原方法。

## 3. `com.milink.service` Hook 规格

### 3.1 `checkIsMiTWS(BluetoothDevice): int`

策略：

1. 先调用原方法。
2. 原返回 `1` 时透传，保护真实 MiTWS。
3. 原返回非 `1` 且 MAC 命中 OpenBuds bridge allowlist 时返回 `1`。
4. bridge 不可达、未授权、未连接或闸门关闭时透传原值。

返回值语义按参考源保留：

```text
1 = MiTWS
0 = not MiTWS
-1 = RemoteException
2 = null device
3 = Bluetooth off
4 = headset service null
```

### 3.2 `getDeviceId(BluetoothDevice): String`

deviceId 要拆成两类模板：

| 模板 | 用途 | 默认级别 |
|------|------|----------|
| `01010101` | V1 已验证的普通耳塞模板，避开 AirPods type=5/6 特殊分支 | 默认安全模板 |
| `01013201` / `020104005A` | Flora/O73 模板，可能启用更多 MiTWS 控件和资源 | 实验模板 |

实现要求：

- 按 MAC 持久化 deviceId，避免米链缓存里同一耳机漂移成多个设备。
- 默认先使用 `01010101` 验证 MiTWS 页面是否能渲染。
- 只有在 M2 证明普通模板隐藏了必要 MiTWS 控件时，才切到 Flora 模板。
- deviceId hook 只服务 UI 和能力 profile，不宣称能影响 `MxBluetoothService.checkIsMiTWS()` 内部 fast-path。
- **副作用注意**：`BluetoothServiceClient` 中 `getHeadsetType()`（第349-360行）和 `isCirculateGlasses()`（第433-444行）等多个方法都调 `mxBluetoothManager.getDeviceId()`。如果返回 Flora 模板 deviceId，`getHeadsetType()` 会通过 `AbstractC14649a.m51162b(deviceId)` 返回不同的 headsetType，影响图标和功能展示。同时 `isCirculateGlasses()` 等眼镜检测也会被影响。

### 3.3 `connectMma(BluetoothDevice)` / `disconnectMma(BluetoothDevice)`

这是 MiTWS facade 的关键安全点。对于 OpenBuds allowlist 设备：

- **不调用原始 `connectMma`**，避免 Xiaomi MMA 层尝试连接 Sony/QCY 耳机。原始方法（`MxBluetoothService.java:614-628`）会调用 `ConnectManager.checkAndConnect()` → `BluetoothEngineImpl`，对非小米设备大概率超时失败。
- 返回米链认为成功或可接受的状态码，具体码值必须 M1 trace 真实 MiTWS 或参考调用方确认。
- 通过捕获到的 `MMACallback` 主动派发 `onConnectMmaStateChanged(device, true)`。
- `disconnectMma` 同理返回成功并派发断开状态。

M1 必须先 trace 原始返回码和调用方判断，不要直接猜测 `0` 或 `1`。

### 3.4 `registerCallback(MMACallback)` / `unregisterCallback(MMACallback)`

MiTWS 状态不应只靠 getter。模块要捕获米链注册的 `MMACallback`，并在 bridge 快照变化时派发：

```text
onBatteryLevel(BluetoothDevice, int[])
onAncStateChanged(BluetoothDevice, int)
onConnectMmaStateChanged(BluetoothDevice, boolean)
onDeviceIdUpdate(BluetoothDevice, String)
onRingStateChanged(BluetoothDevice, boolean)
```

**实现策略**：拦截 `MMACallback` 有两种方式——

**策略 A（捕获 callback — 推荐）**：hook `MxBluetoothManager.registerCallback()`，捕获调用方传入的 `MMACallback` 实例，存入自己的分发表。当 bridge snapshot 变化时，直接调用 `callback.onBatteryLevel()` 等。需要同时处理 `unregisterCallback` 和 callback 生命周期。

**策略 B（拦截 proxy）**：hook `MiaoXiangCallbackProxy`（`MxBluetoothManager` 的私有内部类，第48行）的回调方法。这些方法在 `MxBluetoothService` 回调到达时被调用，在分发给所有 `MMACallback` 之前拦截。优点是无需处理 register/unregister 生命周期，缺点是依赖内部类结构，可能随 HyperOS 版本变化。

推荐策略 A，因为更稳定（不依赖内部类实现细节）。

细节要求：

- 回调必须在 hook 进程内用 `Handler` 异步派发，避免阻塞米链 UI 线程。
- bridge 快照有 `revision`，只在 revision 变化时派发。
- 每个 MAC 维护独立 callback state，禁止单设备全局变量。
- bridge 不可达时不派发假状态，透传原始行为或保持最后一次 TTL 内缓存。

### 3.5 状态 getter

优先 hook：

```text
getBatteryLevel(BluetoothDevice): int
getAncState(BluetoothDevice): int
getWearStatus(BluetoothDevice): String
```

映射策略需要 M1 trace 确认返回格式：

- `getBatteryLevel` **只是触发刷新**（`MxBluetoothService.java:720-734` 总是返回 `1`，表示"请求已发送"）。实际电量通过 `MmaInterfaces.getTargetInfoResponse()` 回调 → `onBatteryLevel(localDevice, mulQuantity)` 传递，其中 `mulQuantity` 是 `int[3]` 数组 `[left?, right?, case?]`。若返回值不被 UI 直接消费，应以 `MMACallback.onBatteryLevel(int[])` 为主。
- `getAncState` 从 `mAncStateMap` 读取（`MxBluetoothService.java:695-717`）。可从 OpenBuds `NoiseControlMode` 映射，初始只支持 `OFF`、`NOISE_CANCELLING`、`AMBIENT_SOUND`。
- `getWearStatus` 通过 `setCommonCommand(102, "", device)` 实现（`MxBluetoothService.java:782-798`）。返回字符串格式未知，M1 先 trace 真实返回或调用方解析逻辑。

OpenBuds 现有 `MilinkDeviceSnapshot` 已扩展到电量、佩戴、充电、ANC 和基础 capability flags；主线未完成项转为 volume / audio effect / ring 状态，以及让这些字段进入 `DiscoveryImpl.assembleHeadsetInfo()` 的自然运行时路径。

### 3.6 反向控制

优先 hook：

```text
openAnc(BluetoothDevice): int
openTransparent(BluetoothDevice): int
closeAnc(BluetoothDevice): int
```

ANC 模式值（`MxBluetoothService.java:50-52`）：`0=OFF, 1=ANC, 2=TRANSPARENT`。

**重要**：hook 这三个方法后，**必须不调用原方法**（`chain.proceed()` 应返回 mock 成功值如 `1`），因为原方法会通过 `MmaCommandTools.tryToSetAncMode()` 尝试通过 MMA 协议栈发送命令到设备，而 Sony/QCY 设备不支持 MMA 协议。命令应通过 bridge 的 AIDL 接口发送到 OpenBuds App，由 App 侧的 `HeadphoneRepository` 通过 Sony Tandem/QCY TLV 协议执行。

后续再评估：

```text
changeAncMode(int, BluetoothDevice)
changeAncLevel(String, BluetoothDevice)
changePlayStatus(int, BluetoothDevice)
setCommonCommand(int, String, BluetoothDevice)
MiTWS 查找耳机实际调用链
```

实现要求：

- `MilinkBridgeService.executeCommand()` 已支持 `set_noise_control`；M3+ 继续扩展 `ringFind`、`setVolume`、`setAudioEffect` 等命令。
- 所有控制 hook 独立闸门，默认关闭。
- UI 操作后尽量依赖 bridge 快照回推状态；只有必要时做最小乐观更新，避免米链与 OpenBuds 状态漂移。
- 不支持的品牌能力必须返回失败或透传，不伪造成功。
- **重要方向修正**：主线下一步不是继续为每条控制链增加单点 bypass hook，而是优先修复 `ProfileContext + DiscoveryImpl` 运行时视图，让 `ProfileImpl.getHeadsetProperty(...)`、`updateHeadsetMode(...)`、后续 `updateHeadsetVolume(...)` / `updateHeadsetAudioEffect(...)` 都能自然命中 OpenBuds 的 active headset。设计要点已并入上文 `5.1 主线修订：MiTWS runtime projection`。

## 4. `com.xiaomi.bluetooth` 实验规格

本节不是 M1-M3 主线。

### 4.1 FastConnect trace

先只做 trace，不改结果：

- `MiuiFastConnectService` 是否能扫描到 OpenBuds 设备。
- `MiuiFastConnectStateMachine.getAssembleAdvData(scanResult)` 是否返回非空。
- `checkAdvData(advData)` 失败原因。
- `saveBattery(scanResult)`、`saveDeviceId(...)` 是否被调用。
- `MiuiFastConnectControllerFactory.getController()` 是否收到目标 deviceId。

如果设备根本到不了 `getController()`，不要继续写 Controller wrapper。

### 4.2 GATT trace

先只记录以下方法参数和结果：

```text
MiuiGattPeripheral.connect(boolean)
MiuiGattPeripheral.discoverServices()
MiuiGattPeripheral.writeCharacteristic(device, serviceUuid, charUuid, value)
MiuiGattPeripheral.readCharacteristic(device, serviceUuid, charUuid)
MiuiGattPeripheral.writeDescriptor(device, serviceUuid, charUuid, descriptorUuid, value, type)
MiuiGattPeripheral.setCharacteristicNotification(device, serviceUuid, charUuid, enable)
MiuiGattPeripheral.onCharacteristicChanged(gatt, characteristic)
```

只有满足这些条件才进入 mutation 实验：

1. Xiaomi 层确实在目标 OpenBuds 设备上建立了 GATT。
2. 失败点是可映射的 service/characteristic UUID，而不是更高层 deviceId/云控/配对逻辑。
3. 目标品牌可走 GATT。Sony LinkBuds S 默认仍应走 App 侧 SPP bridge，不应强制改为 Xiaomi GATT。

### 4.3 SPP / MMA-over-SPP trace

`com.xiaomi.bluetooth` 存在 SPP 能力，但不是主线直通层。进入 SPP 实验前只做 trace：

- `MiuiPeripheralConnectionServiceReal.registerPCService(device, type=1, uuid, packageName, callback)` 的调用方、package allowlist 和 cloud switch。
- `MiuiSppPeripheral(device, uuid, callback)` 使用的 UUID、连接状态和 `sendData(byte[])` 字节。
- `MiuiMMARegisterManager.registerMMAService(...)` 是否把目标设备设成 direct SPP，以及上层 MMA 数据帧是否可理解。
- `MiuiMMADataHandler` 发出的 payload 是否是可独立映射的数据，还是依赖 Xiaomi 认证/握手状态。

退出条件：

- 如果目标设备无法通过 package allowlist、SPP cloud switch 或注册配置进入 `MiuiSppPeripheral`，停止 SPP 实验。
- 如果上层 payload 是完整 Xiaomi MMA/RCSP 协议而非可映射命令，停止 SPP mutation，回到 M3 bridge 命令路径。
- Sony LinkBuds S 默认仍使用 OpenBuds App 侧 `SppTransport` + `SonySppPayloadMapper`，不把 Xiaomi SPP 作为控制主线。

### 4.4 若进入 GATT mutation，必须做完整 UUID + 数据双向映射

最小接口不能只叫 `MmaProtocolTranslator`，应包含 transport mapping：

```kotlin
interface MiuiGattProxyStrategy {
    val brand: String
    fun mapOutgoingRequest(request: MiuiGattRequest): GattProxyWrite?
    fun mapIncomingNotification(notification: OpenBudsGattNotification): MiuiGattNotification?
    fun canProxy(device: BluetoothDevice, snapshot: MilinkDeviceSnapshot): Boolean
}

data class MiuiGattRequest(
    val serviceUuid: String,
    val characteristicUuid: String,
    val value: ByteArray?,
    val operation: Operation,
)
```

必须覆盖 write/read/descriptor/notify 四类操作，否则订阅和读状态会先失败。

## 5. Bridge 需要补齐的能力

现有 bridge 已支持：

- session/token 校验。
- 授权 MAC 查询。
- 单设备 snapshot 查询。
- adapter status 和 snapshot callback。

V2 主线剩余 bridge / runtime 能力：

| 能力 | 说明 | 阶段 | 当前状态 |
|------|------|------|---------|
| 多设备 snapshot map | Sony + QCY 同时连接或历史设备时不能只存一个 latestSnapshot | M3+ | ❌ 未实现 |
| ANC 状态字段 | `ancMode` 已接入 bridge snapshot | M2 | ✅ 已实现 |
| volume / audio effect / ring 状态字段 | 详情页剩余第一方控件依赖这些状态；volume 已接入 bridge snapshot，audio effect / ring 尚未 | M3+ | ✅ Volume 已实现 |
| `setNoiseControl` 命令 AIDL | ANC 三态控制 | M3 | ✅ 已实现 |
| `ringFind` / `setVolume` / `setAudioEffect` / `playback` 命令 AIDL | 剩余控制面 | M3+ | ✅ `setVolume` 已实现；ringFind / setAudioEffect 未实现 |
| 命令结果回执缓存 | 当前只有 `accepted/reason/requestId`；缺少按协议执行结果回写的短超时缓存 | M3+ | ⚠️ 部分实现 |
| capability 矩阵 | 当前已有 `supportsBattery` / `supportsNoiseControl` / `supportsWearing` / `supportsRing` 字段，但没有按品牌/型号形成稳定能力矩阵 | M3+ | ⚠️ 部分实现 |
| runtime projection (`ProfileContext + DiscoveryImpl`) | 让 `ProfileImpl.getHeadsetProperty(...)`、`updateHeadsetMode(...)`、`updateHeadsetVolume(...)`、`updateHeadsetAudioEffect(...)` 走自然 first-party runtime 路径，而不是继续增加单点旁路 hook | M3+ | ⚠️ 已实现基础投影，仍待收敛 |

命令接口建议保持领域语义，不暴露 Sony/QCY 原始字节：

```text
setNoiseControl(mac, mode, ambientLevel, ambientMode)
ringFind(mac, enabled)
setVolume(mac, value)
setAudioEffect(mac, state)
sendPlayback(mac, control)      // 仅在 MiLink 确认走 MiTWS 控制面时再做
setEqPreset(mac, preset)        // 非当前主线阻塞项
setEqBand(mac, bandIndex, value)// 非当前主线阻塞项
```

### 5.1 主线修订：MiTWS runtime projection

M3 之后的主线不再默认继续扩大“单点控制 bypass hook”。

当前 `ProfileImpl.updateHeadsetMode(...)` 之所以需要特判，不是因为只有
`CirculateDeviceInfo` 没挂好，而是因为 **`com.miui.headset.runtime` 自己并
没有把 OpenBuds 视为自然的 active headset**。

从反编译链路看，`C4737b0.m19864t(...)` 最终会进入：

```text
Profile.updateHeadsetMode(hostId, address, deviceId, opAncMode)
  -> ProfileImpl.updateHeadsetMode(...)
     1. getDiscovery().getActiveHeadset()
     2. activeHeadset == null -> return 206 (TargetNotMatch)
     3. activeHeadset.address != address -> return 206
     4. only then ProfileContext.setAncState(activeHeadset.bluetoothDevice, opAncMode)
```

`ProfileImpl.getHeadsetProperty(...)`、后续 `updateHeadsetVolume(...)`、
`updateHeadsetAudioEffect(...)` 也有同类前置条件：只有运行时里的
`activeHeadset` 成立，native profile 操作才会把目标视为合法。

与此同时，`DiscoveryImpl` 的 runtime 视图来自：

```text
ProfileContext
  -> DiscoveryImpl.updateHeadsetDevice(...)
  -> DiscoveryImpl.activeHeadset / connectedHeadsets / bondedHeadsets
  -> DiscoveryImpl.assembleHeadsetInfo()
  -> ProfileImpl / Query / HeadsetDetailFragment
```

`DiscoveryImpl.assembleHeadsetInfo()` 会直接读：

- `ProfileContext.getDeviceId(...)`
- `ProfileContext.getBatteryLevel(...)`
- `ProfileContext.getAncState(...)`
- `ProfileContext.getVolume()`
- `ProfileContext.getDeviceType(...)`
- `ProfileContext.getSwitchState(...)`
- `ProfileContext.getAudioSpatialEffectState(...)`

因此，更无缝的主线修订不是继续给每条控制链增加特判，而是：

1. 保留现有 MiTWS facade 输入层：
   - `checkIsMiTWS`
   - `getDeviceId`
   - `MMACallback` 注入
   - bridge snapshot / command
2. 在 runtime 层做 projection，让 OpenBuds 授权设备在
   `com.miui.headset.runtime` 中表现为自然的 first-party headset target
3. 让 `ProfileImpl.getHeadsetProperty(...)`、
   `updateHeadsetMode(...)`、后续 `updateHeadsetVolume(...)` /
   `updateHeadsetAudioEffect(...)` 复用同一运行时 target，而不是每条路径都
   单独定点 bypass

推荐落点：

- `ProfileContext`
  - `getConnectedDevices()`
  - `getActiveDevice()`
  - `isConnected(BluetoothDevice)`
  - `isActive(BluetoothDevice)`
  - `getDeviceId(BluetoothDevice)`
  - `getBatteryLevel(BluetoothDevice)`
  - `getAncState(BluetoothDevice)`
  - `getVolume()`
  - `getAudioSpatialEffectState(BluetoothDevice)`
  - `getSwitchState(String address)`
- `DiscoveryImpl`
  - `updateHeadsetDevice(String lineTag)`
  - `assembleHeadsetInfo()`
  - `getActiveHeadset()`
  - `notifyHeadsetInfoUpdate(...)`
  - 必要时投影 `activeHeadset` / `connectedHeadsets` / `bondedHeadsets`

实现策略建议分两层：

#### 第一层：compute-time override

优先 hook 方法返回 projected 值，不直接写内部字段：

- 风险更小
- 更容易回滚
- 适合先验证 OpenBuds 是否能自然通过 `ProfileImpl` 的 target matching

#### 第二层：field-backed projection

如果 downstream 仍依赖 map membership 或对象 identity，再升级为字段投影：

- 写入 `activeHeadset`
- 写入 `connectedHeadsets`
- 写入 `bondedHeadsets`

这个阶段再做，不要一开始就污染完整 runtime state。

边界要求：

- runtime projection 仍然只在 `com.milink.service` 内进行
- 不进入 `com.xiaomi.bluetooth` 做主线协议代理
- 不进入 `com.android.bluetooth`
- 不默认扩展到完整 `HeadsetHostSupervisor / MultipointProcessor`
  host graph 仿真；只有 `ProfileContext + DiscoveryImpl` 投影后仍有结构性
  差异，才继续下钻

阶段验收：

- OpenBuds 设备不再因 `activeHeadset == null` 或
  `address != activeHeadset.address` 导致 `ProfileImpl.updateHeadsetMode(...)`
  返回 `206 / TargetNotMatch`
- `ProfileImpl.getHeadsetProperty(...)` 也能通过同一 native path
- 后续 `updateHeadsetVolume(...)` / `updateHeadsetAudioEffect(...)` 可以复用同一
  runtime target，而不是再增加新的单点特判
- 现有电量 / ANC / callback facade 不回退

## 6. 实施阶段

### M0：证据冻结和闸门设计（1-2 天）

- [x] 在文档中记录已确认的真实签名：`MxBluetoothManager`、`MxBluetoothService`、`MiuiGattPeripheral`、`MiuiSppPeripheral`。
- [x] 给 V2 增加独立开关：App 设置开关 + `debug.openbuds.milink_mitws_enable` 系统属性。
- [x] 删除 V1 AirPods 路径的所有代码（`MilinkAirpodsM1Hook.kt`、`MilinkAirpodsAdapterEntry.kt`、`AirpodsStateMapper.kt`、`MilinkAirpodsTargetMatcher.kt`、`NotifyChangePump.kt`、`MilinkBridgeCache.kt`）。
- [x] 明确真实 MiTWS 保护策略：M0 trace hook 一律调用原方法并返回原结果；M1+ mutation hook 也必须在原方法返回真值、bridge 不可达、App 设置关闭或系统属性关闭时透传。

验收：

- V1 代码完全移除。
- V2 trace 不改变米链行为。

M0 完成记录（2026-06-02）：

- V1 6 个源文件 + 5 个测试文件全部删除。
- 新增 `mitws/` 目录：`MilinkRouteConfig.kt`、`MilinkMiTwsFacadeEntry.kt`、`MilinkMiTwsTraceEntry.kt`、`MilinkMiTwsFacadeHook.kt`、`MilinkBridgeClient.kt`、`MiTwsBridgeCache.kt`、`MiTwsDeviceIdPolicy.kt`。
- `ModuleMain.kt` 路由逻辑从 AirPods V1 改为 MiTWS V2：`com.milink.service` → `MITWS`/`TRACE_ONLY` 双模式。
- 所有 hook 为 trace-only，调用原方法并返回原结果。
- M0 审计通过：V1 完全清除 ✓，trace 不改变行为 ✓。

M0 签名冻结：

| 类 | 已确认方法 |
|----|------------|
| `MxBluetoothManager` | `checkIsMiTWS(BluetoothDevice): int`、`getDeviceId(BluetoothDevice): String`、`connectMma(BluetoothDevice): int`、`disconnectMma(BluetoothDevice): int`、`registerCallback(MMACallback): boolean`、`unregisterCallback(MMACallback): boolean`、`getBatteryLevel(BluetoothDevice): int`、`getAncState(BluetoothDevice): int`、`getWearStatus(BluetoothDevice): String`、`openAnc(BluetoothDevice): int`、`openTransparent(BluetoothDevice): int`、`closeAnc(BluetoothDevice): int`。来源：`references/mi/com.milink.service/sources/com/xiaomi/mxbluetoothsdk/manager/MxBluetoothManager.java` 第317、349、366、400、438、461、478、516、601、618、635、701行附近。 |
| `MxBluetoothService` | `checkIsMiTWS(BluetoothDevice): int`、`connectMma(BluetoothDevice): int`、`disconnectMma(BluetoothDevice): int`、`getAncState(BluetoothDevice): int`、`getBatteryLevel(BluetoothDevice): int`、`getDeviceId(BluetoothDevice): String`、`getWearStatus(BluetoothDevice): String`、`openAnc(BluetoothDevice): int`、`openTransparent(BluetoothDevice): int`、`closeAnc(BluetoothDevice): int`、`registerCallback(IMiaoXiangCallback): void`、`unregisterCallback(IMiaoXiangCallback): void`。来源：`references/mi/com.milink.service/sources/com/xiaomi/mxbluetoothsdk/service/MxBluetoothService.java` 第565、593、614、657、695、720、737、782、877、898、919、1023行附近。 |
| `MiuiGattPeripheral` | `connect(boolean): boolean`、`disconnect(): void`、`writeCharacteristic(BluetoothDevice, String, String, byte[]): boolean`、`readCharacteristic(BluetoothDevice, String, String): boolean`、`writeDescriptor(BluetoothDevice, String, String, String, byte[], int): boolean`、`requestMTU(BluetoothDevice, int): boolean`、`setCharacteristicNotification(BluetoothDevice, String, String, boolean): boolean`、`onCharacteristicChanged(BluetoothGatt, BluetoothGattCharacteristic): void`。来源：`references/mi/com.xiaomi.bluetooth/sources/com/xiaomi/bluetooth/peripheral/MiuiGattPeripheral.java` 第134、189、216、248、279、346、361、597行附近。 |
| `MiuiSppPeripheral` | `MiuiSppPeripheral(Context, BluetoothDevice, String, IPCServiceEventCallback)`、`connect(boolean): boolean`、`sendData(byte[]): boolean`；内部连接使用 `createRfcommSocketToServiceRecord(UUID.fromString(mUuid))`。来源：`references/mi/com.xiaomi.bluetooth/sources/com/xiaomi/bluetooth/peripheral/MiuiSppPeripheral.java` 第56、153、248、329行附近。 |

### M1：MiTWS 分类和调用链 trace（1 周）

- [x] 新增 `MilinkMiTwsFacadeHook`，只注入 `com.milink.service`。
- [x] trace `checkIsMiTWS`、`getDeviceId`、`connectMma`、`disconnectMma`、`registerCallback`、`getBatteryLevel`、`getAncState`、`getWearStatus`。
- [x] 在实验开关开启时，仅对 OpenBuds allowlist 设备让 `checkIsMiTWS` 返回 `1`。
- [x] `connectMma` trace 分两类：真实 MiTWS 可以观察原始返回码；OpenBuds allowlist 默认 trace + no-op，不调用原始 Xiaomi MMA 连接。只有独立 debug 开关允许对 OpenBuds 临时透传原调用。
- [x] 对比 `01010101` 和 `01013201` deviceId 对 UI、图标、控件的影响。

验收：

- OpenBuds 设备可进入 `HEADSET` 分类。
- 真实小米耳机透传。
- 明确 HeadsetDetailFragment 是否打开、哪些 getter/callback 被调用。
- 明确 `connectMma` 成功码/失败码/调用时机；OpenBuds fake MiTWS 不应在 M1 默认触发真实 Xiaomi MMA 连接。

M1 完成记录（2026-06-02）：

- `MilinkMiTwsFacadeHook` 注册 12 个 hook（10 Manager 方法 + 2 callback 方法），全部在 `com.milink.service` 进程内。
- `checkIsMiTWS` 对授权 OpenBuds 设备返回 `1`，真实 MiTWS 透传原值。
- `getDeviceId` 按 `debug.openbuds.milink_mitws_device_id` 系统属性选择模板（默认 `01010101`），per-MAC 稳定映射。
- `connectMma`/`disconnectMma` 对 OpenBuds 默认 no-op 返回 `0`；`debug.openbuds.milink_mitws_mma_passthrough=true` 可临时透传。
- `openAnc`/`openTransparent`/`closeAnc` 对 OpenBuds 返回 `0`（control no-op，M3 实现）。
- 其余 getter 和 callback 保持 trace-only。
- 双闸门：`debug.openbuds.milink_mitws_enable` 系统属性 + App 设置 `milinkAdapterEnabled`。
- 新增测试：`MiTwsDeviceIdPolicyTest`（6 个）、`MiTwsBridgeCacheTest`（3 个）、`MilinkRouteConfigTest`（9 个）。

M1 真机验证发现的问题及修复（2026-06-02）：

- **问题：第三方贴纸与第一方贴纸并存** — `MiTwsBridgeCache` 的 TTL 仅 10s，SPP 断连重连期间 bridge 未推送 snapshot 更新，缓存过期 → `checkIsMiTWS` 从 1 翻转为 0 → 米链创建设备的第三方贴纸，但第一方贴纸已渲染不消失，二者并存。
  - **修复**：引入 `STALE_TOLERANCE_MS = 300s`（5 分钟），TTL 过期后不立即丢弃 snapshot，在 stale tolerance 范围内继续返回过期数据用于分类判断。
- **问题：首次连接时先出现第三方贴纸，然后才出现第一方贴纸** — bridge client 通过 `Application.onCreate()` hook 异步启动，但 `checkIsMiTWS` 在 `Application.onCreate()` 之前就被调用。此时 bridge 未连接、缓存为空 → 返回 0 → 第三方贴纸先渲染。
  - **修复 1**：`MilinkMiTwsFacadeEntry` 在 `install()` 中通过 `ActivityThread.currentApplication()` 立即启动 bridge client，不等 `Application.onCreate()` hook。
  - **修复 2**：`MiTwsBridgeCache` 引入 `knownAuthorizedMacs` — 一旦 bridge 返回过授权列表，MAC 被永久记住。bridge 未连接或缓存过期时，返回最小 `placeholderSnapshot`（`connected=true`），让 `checkIsMiTWS` 仍能返回 1。`markError` 不擦除 `knownAuthorizedMacs`，bridge 重启后分类不丢失。

### M2：MiTWS 只读 facade（1-2 周）

- [x] 对 allowlist 设备接管 `connectMma` / `disconnectMma`，避免触发真实 Xiaomi MMA。
- [x] hook `registerCallback` 并从 bridge 派发电量、佩戴、连接、deviceId 状态。
- [x] hook 必要状态 getter，补齐 callback 之外的同步读取。
- [x] 扩展 `MilinkDeviceSnapshot`：ANC 状态、ring 状态、capability flags。

验收：

- 控制中心贴纸和 HeadsetDetailFragment 显示真实电量/佩戴。
- OpenBuds App 普通进程死亡或未运行时，bridge 可重新绑定或使用 TTL 缓存；被用户强停时只要求快速降级，等待用户重新打开 App 后恢复。
- Bridge 不可达时自然降级，不崩溃、不阻塞米链。

M2 完成记录（2026-06-02）：

**新增文件：**
- `MiTwsStateMapper.kt` — bridge snapshot → MiTWS 状态映射：`batteryArray`、`ancState`、`connected`、`ringing`、`wearStatus`
- `MiTwsCallbackPump.kt` — 捕获 `MMACallback` 实例，按 revision 去重派发 snapshot 到所有注册 callback。主线程检查（original `MiaoXiangCallbackProxy` 用 `mHandler.post`），`allowNullDevice` 支持
- `MiTwsBridgeCache` — 增加 `knownAuthorizedMacs`、`placeholderSnapshot`、`authorizedSnapshots()`
- `MilinkBridgeClientFacade` — 增加 `authorizedSnapshots()`、`addSnapshotListener()`/`removeSnapshotListener()`
- `MilinkMiTwsFacadeEntry.BridgeClientHolder` — 重写为 `attach()` 模式，delegate 就绪前缓存 listener
- `MilinkBridgeContract` — 增加 `KEY_ANC_MODE`、`KEY_RINGING`、`KEY_SUPPORTS_*`
- `MilinkDeviceSnapshot` — 增加 `ancMode`、`ringing`、`supportsBattery`、`supportsNoiseControl`、`supportsWearing`、`supportsRing`
- `MilinkBridgeSnapshotMapper.fromUiState()` — 映射 ANC 模式（0/1/2）、capability flags
- 测试：`MiTwsStateMapperTest`（wearStatus 5 个 + battery/ANC/capability）、`MiTwsCallbackPumpTest`（5 个测试）

**Hook 变更：**
- `hookBatteryLevel`（M2）— 返回 `1`（触发刷新）+ `dispatchSnapshot(force=true)` 派发到 callback
- `hookAncState`（M2）— 返回映射后的 ANC 值 + `dispatchSnapshot`
- `hookWearStatus`（M2）— 返回 MiTWS wear status 字符串（0/1/2/3/-1）
- `hookRegisterCallback`/`hookUnregisterCallback`（M2）— 捕获 MMACallback 实例
- `hookMmaConnection` — `connectMma` 返回 `1`（之前为 `0`），仅派发 `onConnectMmaStateChanged(true)`；`disconnectMma` 不派发状态

M2 真机验证发现的关键问题及修复（2026-06-02）：

1. **`adapter=false` 导致所有 facade 不生效** — App 设置 `milinkAdapterEnabled` DataStore 默认 `false`。修复：首次启动需手动勾选 OpenBuds App 的 MiLink adapter 开关。
2. **`register()` 初始派发用 placeholder snapshot** — `authorizedSnapshots()` 返回 `placeholderSnapshot`（`supportsBattery=false`），导致电池/ANC callback 被跳过。修复：`register()` 改回派发完整 `dispatchSnapshotLocked`。
3. **`dispatchSnapshot` 在 bridge handler 线程执行，MMACallback 方法需主线程** — 修复：`dispatchSnapshot()` 检查 `Looper.myLooper()`，非主线程时 post 到 `mainHandler`。
4. **`disconnectMma` 每 ~100ms 触发，持续派发 `onConnectMmaStateChanged(false)` 导致 UI 隐藏电池/ANC** — 修复：`hookMmaConnection` 只对 `connectMma` 派发 `onConnectMmaStateChanged(true)`，`disconnectMma` 不派发状态。
5. **`ancBatteryModel` 只在 `onConnectMmaStateChanged(true)` 回调中创建，且要求 `pendingConnectMmaAddress` 匹配** — `register()` 初始派发在 `connectMma` 之前，`pendingConnectMmaAddress` 为空，模型未创建。后续 `connectMma` 虽创建模型但 UI 已渲染完毕。修复：`ensureAncBatteryModel()` 通过反射访问 `AncBatteryController$mmaCallback$1.this$0`，强制创建 `ancBatteryModel` 并设置 `pendingConnectMmaAddress`。

**当前状态（2026-06-04）：** 电量和 ANC 状态已在卡面正确显示，ANC 调节已通过 bridge 闭环可用；关键运行时进程已确认是 `com.milink.service:ui` 和 `com.milink.service:core`，二者都必须持有 facade，其中 `:core` 还必须接入 bridge，否则耳机卡片会消失。进程筛选后，卡片缩小/样式抽搐和 ANC 偶发点不动已从高频问题降为低频余留问题；当前最主要的嫌疑是 `:core` 内 `disconnectMma` 的高频轮询会扰动 headset runtime，但该链路暂只做观测，不再主动做行为性干预。与此同时，当前 ANC 路径仍部分依赖 `ProfileImpl.updateHeadsetMode(...)` 的定点特判，这说明 `com.miui.headset.runtime` 的 active headset 运行时视图尚未自然成立。

**增量进展（2026-06-04 晚些时候）：**

1. `MiTwsBridgeCache` 与 `MilinkBridgeClientFacade` 已拆分“分类资格”和“live runtime snapshot”语义：bridge 断线时仅保留 `checkIsMiTWS/getDeviceId` 的分类粘性，不再让 placeholder/历史授权设备参与 active headset、ProfileContext 或 ProfileImpl 运行时投影。
2. `ProfileContext + DiscoveryImpl` 的基础 runtime projection 已落地：
   - `getActiveHeadset` / `getActiveDevice` / `getConnectedDevices`
   - `isConnected` / `isActive`
   - `getBatteryLevel` / `getAncState` / `getSwitchState` / `getDeviceType`
   - `assembleHeadsetInfo`
   - `ProfileImpl.getHeadsetProperty(...)` / `updateHeadsetMode(...)`
3. `HeadsetInfo.powers` 已改成与米链 first-party runtime 一致的 6 槽位格式：
   `[boxPower, leftPower, rightPower, isBoxCharging, isLeftCharging, isRightCharging]`。
   这修复了 `HeadSetsDetail` 对 `powers[3..5]` 的越界访问。
4. `HeadphoneFormFactor` 已进入 bridge snapshot，并开始驱动 MiLink runtime 的 `deviceType` 投影：
   - `TRUE_WIRELESS -> type 0 (BUDS)`
   - `HEADSET -> type 2`
   不再把 OpenBuds 真无线设备误投到 AirPods 专用 `type == 5` 分支。
5. volume / audio effect 仍未实现，但 runtime 投影已改为“有真实支持值才 override；否则保留 original 值或原链路”，不再伪造默认值。
6. ring 路径仍未实现；当前策略是避免让 OpenBuds 设备误落入 AirPods/ring 专用分支，而不是伪造支持状态。
7. `getSupportAncMode`、`isMmaHeadset`、`getBondStateWithTargetHost` 已从 trace-only 升级为最小 query replacement，仅在 live OpenBuds snapshot 命中时覆写返回值；`switchToHeadsetActivity(...)` 仍保持原状。
8. 为减少首屏纯空状态，OpenBuds App 侧增加了 TWS 设备的系统电量 fallback：当仓库尚未收到真实 left/right/case battery 时，优先使用系统 `BluetoothDevice.getBatteryLevel()` 填充单电量 fallback，而不是立即把 `HeadsetInfo.powers` 全部投影为 `-1`。
9. ANC 支持 query 已改回三态设备分支；当前仍需继续用真机确认 LinkBuds S 等设备在详情页里持续显示“通透 / 降噪 / 关闭”三态，而不是回退为双态。

#### 附录：`:core` 中 `disconnectMma` 高频轮询的当前证据

截至 2026-06-04 的真机日志，`com.milink.service:core` 已被确认是耳机卡片和
headset runtime 的关键进程之一。当前已确认的现象如下：

- `:core` 会持续高频调用：
  - `getDeviceId(...)`
  - `getAncState(...)`
  - `registerCallback(...)`
  - `disconnectMma(...)`
- `:ui` 更接近展示层，主要跟随接收 `snapshotChanged(...)`，而不是承担 runtime
  维护主链。
- `:core` 中的 `disconnectMma(...)` 调用频率约为 100ms 级别反复出现，明显高于
  用户操作频率。

当前工作结论：

1. `:core` 不是噪声进程，而是 `HeadsetLocalService` /
   `HeadsetCirculateSessionService` / `AncBatteryController` 所在的关键运行时进程。
2. 之前将 `:core` 设为 facade-only 会直接导致耳机卡片消失，因此当前已修正为
   `bridge + facade`。
3. 当前低频余留问题（卡片偶发缩小、ANC 偶发点不动）更接近 `:core`
   内部高频 runtime 维护动作的副作用，而不是桥接状态完全丢失或设备真实断开。
4. 由于直接对 `disconnectMma` 做 UI 强推或会话语义改写曾显著放大卡片抽搐，
   当前策略改为：保留诊断日志，只做观察，不对 `disconnectMma` 增加新的行为
   性 UI 干预。

### M3：反向控制闭环（1-2 周）

- [x] 扩展 bridge 命令 AIDL。
- [x] 实现 `openAnc`、`openTransparent`、`closeAnc` 到 OpenBuds `ControlCommand.SetNoiseControl` 的映射。**注意：hook 后必须不调用原方法**，因为原方法会通过 MMA 协议栈发送命令到不支持的设备。
- [ ] trace 并实现 MiTWS 对应查找接口到 OpenBuds 查找命令。（`ringFindForAirPods` 已加 trace-only hook，等待 OpenBuds 侧 ring 数据源就绪后再实现 mutation）
- [x] 如果 UI 调用了 `changeAncMode` / `changeAncLevel`，再加 hook；否则不主动实现。
- [x] 每个命令需要：米链 UI 操作 -> bridge command -> OpenBuds 协议执行 -> snapshot 更新 -> callback 回推。

验收：

- 若 MiTWS UI 调用链验证通过，ANC 三态至少对 Sony LinkBuds S 可用。
- 不支持的设备不显示或不启用相应能力。
- 命令失败不会让米链 UI 长期显示错误状态。

M3 完成记录（2026-06-02 / 更新 2026-06-05）：

- `IMilinkBridgeService` 新增 `executeCommand(token, mac, command)`；命令 envelope 使用 `commandType`、`noiseMode`、`requestId`，结果使用 `success`、`reason`、`requestId`。
- App 侧新增 `MilinkBridgeCommandProcessor`，统一校验 adapter 开关、授权 MAC、连接状态、protocol ready 和 capability；当前只接受 `set_noise_control`，预留 `ring_find`、`playback`、`set_eq_preset` 常量但不执行。
- `MilinkBridgeService.executeCommand()` 对有效 `set_noise_control` 调用 `HeadphoneRepository.setNoiseControlMode()`；返回值仅表示命令被 OpenBuds 接受并下发，最终状态仍由 snapshot/callback 回推。
- 模块侧新增 `MiTwsControlMapper` 和 `MilinkBridgeClient.executeCommand()`；hook `openAnc` / `openTransparent` / `closeAnc` 时对 OpenBuds facade 设备不调用原方法，bridge 接受后返回 `1`，失败返回 `0`。
- `IMiuiHeadsetService$Stub$Proxy` 的 `changeAncMode`、`changeAncLevel`、`changePlayStatus`、`setCommonCommand`、`ringFindForAirPods` 已加 trace-only hook。查找耳机和播放控制仍不做 mutation，等待真机 trace 确认调用链。
- 单元测试新增 `MilinkBridgeCommandProcessorTest` 和 `MiTwsControlMapperTest`；`.\gradlew.bat testDebugUnitTest` 通过。

### M3+：MiTWS runtime projection 和剩余主线能力（1-2 周 / Volume 闭环 2026-06-05）

- [x] 通过真机日志确认当前关键进程为 `com.milink.service:ui` 和 `com.milink.service:core`；前者更贴近卡片/详情页 UI，后者承载 `HeadsetLocalService` / `HeadsetCirculateSessionService` / `AncBatteryController` 等 headset runtime。
- [x] 收紧进程筛选：仅在 `:ui` 和 `:core` 安装 MiTWS facade，排除 `:audio`、`:provider`、`persistent`、`com.milink.runtime`、`com.milink.crossdeviceservice` 等非关键进程。
- [x] 将 bridge client 限制到 `:ui` + `:core` 两个关键进程；验证 `:core` 若缺 bridge 会导致耳机卡片消失。
- [x] 新增 `ProfileContext + DiscoveryImpl` runtime projection，让 OpenBuds 授权设备在 `com.miui.headset.runtime` 中表现为自然的 `activeHeadset`，而不是只在 facade 层看起来像 MiTWS。当前已覆盖 active device、connected devices、battery/ANC/switch/deviceType、`assembleHeadsetInfo` 与基础 `ProfileImpl` 路径。
- [x] 让 `ProfileImpl.getHeadsetProperty(...)`、`updateHeadsetMode(...)` 在不依赖定点旁路 hook 的情况下通过 native target matching。ANC 仍保留 bridge command 闭环与最小 optimistic snapshot。
- [x] 在 runtime projection 后补齐最关键 query 差异：`getSupportAncMode`、`isMmaHeadset`、`getBondStateWithTargetHost` 已有最小替换，仅在 live OpenBuds snapshot 命中时覆写。
- [x] 收敛首屏时序：进一步确认 `HeadSetsDetail` 首次展开时的 ANC 卡片显示路径，减少依赖后续异步刷新才显现的情况。（2026-06-05：`getAncState` / `getWearStatus` / `getSupportAncMode` 三个 hook 新增 `facadeClassificationEligible` 兜底保护，防止首帧 snapshot 未到达时 native 返回值导致 ANC 卡片永久隐藏或阻塞）
- [x] 在 runtime projection 稳定后，评估是否下调或删除 `ProfileImpl.updateHeadsetMode(...)` 特判 hook，避免继续为每条控制链单独加 bypass。（2026-06-05：结论——不可删除。runtime projection 已解决 target matching 问题，但 `setAncState()` 最终调用 Xiaomi MMA 协议栈，与 Sony/QCY 不兼容。已移除冗余的 `projectedTarget` 和 `passthrough chain.proceed()` 调试代码）
- [x] 扩展 bridge snapshot：volume 状态；`currentVolume` 从 `VolumeState.musicVolume` 映射，`supportsVolumeControl` 从 profile 判定。audio effect 状态已实现（2026-06-05）。ring 状态仍为框架桩（无协议支持）。
- [x] 扩展 bridge 命令：`setVolume` 已通过 `MilinkBridgeService.executeAcceptedCommand()` → `repository.setVolume()` 闭环。`setAudioEffect` 已实现（2026-06-05：Sony DSEE UPSCALING AUDIO_PARAM + QCY CMDID_SPACE_AUDIO）。`ringFind` 仍为框架桩（Sony Tandem v13.0.5 和 QCY 均无 BLE ring/find 命令，二者 OEM App 查找功能均为手机端 GPS+扬声器）。
- [x] `ProfileImpl.updateHeadsetVolume(...)` 已从 guard-only 升级为 bridge 转发，通过 `MilinkMiTwsFacadeEntry.installVolumeControlHook()` 实现。
- [x] 扩展 bridge snapshot：audio effect 状态；`currentAudioEffectState` 从 `AudioEffectState.enabled` 映射，`supportsAudioEffect` 从 profile 判定。ring 状态仍为框架桩（见上文）。
- [x] 扩展 bridge 命令：`setAudioEffect` 已实现；`ringFind` 框架桩（无协议支持）；`playback` 仅在 MiLink 真机确认通过 MiTWS 控制面调用时再纳入主线。
- [x] 继续观测 `:core` 内 `disconnectMma` 高频轮询对耳机卡片 runtime 的扰动；若低频卡片缩小/ANC 点不动仍残留，再做更窄的 `disconnectMma` 调用侧治理，而不是直接做 UI 强推或会话语义改写。（2026-06-05：`disconnectMma` 已标记为观测模式，`[OBSERVE]` 日志前缀 + `TODO(M3+)` 注释，不做行为性干预）

验收：

- `ProfileImpl.updateHeadsetMode(...)` 对 OpenBuds 不再因为 `activeHeadset == null` 或 `address != activeHeadset.address` 返回 `206 / TargetNotMatch`。
- `ProfileImpl.getHeadsetProperty(...)` 对同一设备也能通过 native path。
- `HeadsetInfo.powers` 与米链 first-party runtime 兼容，不再触发 `HeadSetsDetail` 越界访问。
- 真无线设备的 `deviceType` 投影不再误落到 AirPods/ring 专用分支。
- `getSupportAncMode`、`isMmaHeadset`、`getBondStateWithTargetHost` 对当前 active OpenBuds 设备不再依赖 native luck path。
- 后续 `updateHeadsetVolume(...)` / `updateHeadsetAudioEffect(...)` 可以复用同一 runtime target，而不是再做新的单点特判。
- OpenBuds 仍不触发真实 Xiaomi MMA 连接，不跨到 `com.xiaomi.bluetooth` 或 `com.android.bluetooth` 做主线协议代理。

设计说明：

- 这里的 runtime projection 不等于完整 `HeadsetHostSupervisor / MultipointProcessor` host graph 仿真；只有当 `ProfileContext + DiscoveryImpl` 投影后仍存在阻断性缺口时，才继续下钻到完整 host graph。
- 当前进程结论已经明确：`com.milink.service:ui` 与 `com.milink.service:core` 是主线关键进程；`com.milink.service:audio`、`com.milink.service:provider`、`com.milink.service.persistent`、`com.milink.runtime`、`com.milink.crossdeviceservice` 目前不是 MiTWS 耳机卡片主链关键宿主。

M3+ Volume 完成记录（2026-06-05）：

**协议层**：
- Sony V2 Table1：新增 `PLAY_GET_PARAM(0xA6)` / `PLAY_RET_PARAM(0xA7)` / `PLAY_SET_PARAM(0xA8)` / `PLAY_NTFY_PARAM(0xA9)` 命令字节，`buildGetMusicVolume()` / `buildSetMusicVolume()`，`parsePlayParam()` 支持 `MUSIC_VOLUME` / `CALL_VOLUME` / 带 MUTE 变体。
- Sony V1 Table1：相同 PLAY_PARAM 命令族，双层 payload `[PLAYBACK_CONTROLLER(0x01), VOLUME(0x20), value]`。
- `PlayInquiredType` 枚举新增 `MUSIC_VOLUME(0x20)`、`CALL_VOLUME(0x21)`、`MUSIC_VOLUME_WITH_MUTE(0x30)`、`CALL_VOLUME_WITH_MUTE(0x31)`。
- 新增 `ParsedHeadphoneResponse.SonyTandem.Volume` 类型，带 `muted` 字段。
- QCY：`CMDID_VOLUME(0x08)` 读请求和 `[08, 3, left, right, 0]` 写命令，`QcyResponseMapper.applyVolume()` 从空桩改为真实映射。

**Adapter / Profile 层**：
- `HeadphoneAdapter` 接口 + `Registry` 新增 `buildRefreshVolumeCommands()` / `buildSetVolumeCommands()` 委托。
- `SonyTandemHeadphoneAdapter`：`classifyPlayParam()` 路由 PLAY_PARAM → `HeadphoneFeature.VOLUME`，volume builder 实现。
- `QcyHeadphoneAdapter`：`buildRefreshVolumeCommands()` / `buildSetVolumeCommands()` 实现。
- 启用 `HeadphoneFeature.VOLUME` 的 profile：LinkBuds S、WF-1000XM5、WH-1000XM4、QCY C30S。

**Repository 层**：
- 新增 `VolumeState(musicVolume, callVolume, isMuted, raw)` 数据类。
- `HeadphoneUiState.volumeState` 字段，`setVolume()` 公开方法，`applySonyVolume()` 响应处理，dispatch 路由。
- `featureStatusesFor()` 增加 Volume Control 条目。

**MiLink 集成层**：
- `MilinkDeviceSnapshot.fromUiState()`：`currentVolume` 从 `VolumeState.musicVolume` 映射，`supportsVolumeControl` 从 profile 判定。
- `MilinkBridgeService.executeAcceptedCommand()`：`SetVolume` 调用 `repository.setVolume()`。
- `MiTwsControlMapper`：新增 `buildSetVolumeCommand()` 和 `volume()` 读取方法。
- `MilinkMiTwsFacadeEntry.installVolumeControlHook()`：hook `ProfileImpl.updateHeadsetVolume` 并转发到 bridge 命令。
- `MilinkMiTwsFacadeHook.installProfileImplHooks()`：移除冗余的 `updateHeadsetVolume` guard（已被 Entry 层 hook 替代）。

**测试**：
- `QcyResponseMapperTest`：applyVolume 从 noop 改为验证真实映射。
- `MilinkBridgeSnapshotMapperTest`：supportsVolumeControl 断言更新为 true（QCY C30S 启用 VOLUME）。
- `SonyTandemProfileRoutingTest`：XM4 feature set 增加 VOLUME。

**构建**：`testDebugUnitTest` 409/409 通过，`assembleDebug` 成功。

### M4：`com.xiaomi.bluetooth` 快连和通知实验（可选，1-2 周）

- [ ] LSPosed scope 增加 `com.xiaomi.bluetooth`，默认 trace-only。
- [ ] trace FastConnect 扫描过滤、cached deviceId、notification state。
- [ ] 评估是否能只通过 cached deviceId/notification API 触发连接 toast 或状态栏增强。
- [ ] 不做 GATT mutation。

验收：

- trace 对蓝牙稳定性无影响。
- 如果无法让 Sony/QCY 广播进入 FastConnect，不继续投入 Controller wrapper。

### M5：GATT / SPP / MMA 代理实验（可选，2-4 周）

- [ ] 只对明确支持 GATT 或 SPP 控制的品牌启用。Sony LinkBuds S 默认不走此路径。
- [ ] 建立 `MiuiGattProxyStrategy` / `MiuiSppProxyStrategy`，同时映射 transport endpoint、UUID 或 SPP UUID、数据帧。
- [ ] 先支持 read-only query，再支持 write。
- [ ] 所有 mutation 必须有 per-MAC allowlist、per-brand strategy、kill switch。

退出条件：

- 如果 Xiaomi MMA/SPP 上层需要大量未知握手或认证，停止实验，回到 M3 bridge 命令路径。
- 如果 `com.xiaomi.bluetooth` 崩溃或影响普通蓝牙连接，停止实验。

### M6：硬化和多品牌（1 周+）

- [ ] 多设备缓存。
- [ ] Sony/QCY capability 矩阵。
- [ ] 真实 MiTWS 回归测试。

## 7. 测试矩阵

| 场景 | M1 | M2 | M3 | 预期 |
|------|----|----|----|------|
| Sony LinkBuds S 已连接 | 必测 | 必测 | 必测 | 进入 HEADSET，状态真实；M3 通过 UI 调用链验证后 ANC 可控 |
| QCY C30S 已连接 | 选测 | 必测 | 选测 | 状态只读先可用，控制按 capability 启用 |
| 真实 Redmi / Xiaomi Buds | 必测 | 必测 | 必测 | 原路径透传，无误注入 |
| Bridge 不可达 | 必测 | 必测 | 必测 | hook 透传或返回安全缺省 |
| OpenBuds App 普通进程死亡 | 必测 | 必测 | 必测 | 可重新绑定服务或使用 TTL 缓存恢复 |
| OpenBuds App 被用户强停 | 必测 | 必测 | 必测 | 不承诺自动拉起；hook 必须快速降级，用户重新打开 OpenBuds 后恢复 |
| LSPosed 模块关闭 | 必测 | 必测 | 必测 | 米链回到 third_headset/系统默认路径 |

性能指标：

- hook 命中缓存耗时小于 10 ms。
- 同步 bridge 调用超时小于 50 ms；主线程路径优先使用 TTL 缓存。
- callback 派发节流不高于 1 Hz，除非用户正在操作控制面板。

## 8. 风险与缓解

| 风险 | 触发条件 | 缓解 |
|------|----------|------|
| 分类成功但页面无状态 | 只 hook `checkIsMiTWS` | M2 接管 callback/getter |
| 米链触发真实 MMA 连接导致超时 | `connectMma` 透传到 Xiaomi 栈 | M1 对 OpenBuds 默认 no-op，M2 接管并返回安全码 |
| 进程筛选过度导致耳机卡片消失 | `:core` 未接入 bridge 或关键 facade 不在实际运行时进程中 | 以真机日志反证进程职责；当前确认 `:ui + :core` 必须保留，其他进程再排除 |
| `:core` 内高频 `disconnectMma` 扰动卡片 runtime | `AncBatteryController` / headset runtime 轮询过密 | 当前先只保留观测日志，不再对 `disconnectMma` 做主动 UI 推送；后续若仍有低频问题，再做更窄的调用侧治理 |
| 控制路径持续依赖单点旁路 hook | `ProfileImpl` / `DiscoveryImpl` 没有把 OpenBuds 视为自然 active headset | M3+ 优先做 `ProfileContext + DiscoveryImpl` runtime projection，再减少单点 bypass |
| deviceId 触发错误能力 UI | 使用过强 Flora 模板 | 默认 `01010101`，Flora 模板只在实验开关下使用 |
| 真实小米耳机被误 hook | allowlist 过宽或原方法结果被覆盖 | 原结果为真永远透传，MAC + bridge snapshot 双重校验 |
| Bridge 命令暴露给第三方 | AIDL 增加可写接口 | 保持 caller verifier、session token、权限校验，命令默认关闭 |
| `com.xiaomi.bluetooth` 崩溃 | GATT/SPP/FastConnect mutation 有 bug | M4/M5 独立 kill switch，默认 trace-only |
| Sony 传输路径倒退 | 强行改走 Xiaomi GATT/SPP | Sony 默认继续 App 侧 SPP/Tandem，Xiaomi GATT/SPP 只做实验 |
| Xiaomi SPP 被误当直通层 | 直接复用 `MiuiSppPeripheral` 发送 Sony 字节 | SPP 仅 M5 trace/mutation 实验，主线仍走 App 侧 `SppTransport` + `SonySppPayloadMapper` |

## 9. 代码资产映射

可复用（需改造）：

| 文件 | 用途 | 改造方向 |
|------|------|---------|
| `integration/milink/MilinkBridgeService.kt` | App 侧 bridge | 已支持 `executeCommand(set_noise_control)`；M3+ 继续扩展 `ringFind` / `setVolume` / `setAudioEffect` 与结果缓存 |
| `integration/milink/MilinkDeviceSnapshot.kt` | 状态快照 | 已支持 ANC 和基础 capability；M3+ 继续扩展 volume / audio effect / ring / 多设备 |
| `lsposed/mitws/MilinkBridgeClient.kt` | 模块侧 bridge client | M0 已迁移为 MiTWS 命名，并移除 AirPods fallback/system-property allowlist |
| `lsposed/mitws/MiTwsBridgeCache.kt` | 模块侧 snapshot TTL 缓存 | M0 已重建为 MiTWS 命名 |
| `lsposed/mitws/MiTwsDeviceIdPolicy.kt` | deviceId 稳定映射 | M0 已加入默认 `01010101` 和实验 Flora 模板 |
| `lsposed/mitws/MilinkMiTwsFacadeHook.kt` | 核心 facade 与控制 hook | M3+ 重点从“继续加单点控制 hook”转向配合 runtime projection 收敛特判 |

废弃（M0 删除）：

| 文件 | 原因 |
|------|------|
| `lsposed/milink/MilinkAirpodsM1Hook.kt` | V1 AirPods 路径已废弃 |
| `lsposed/milink/MilinkAirpodsAdapterEntry.kt` | V1 AirPods 路径已废弃 |
| `lsposed/milink/AirpodsStateMapper.kt` | V1 AirPods 路径已废弃 |
| `lsposed/milink/MilinkAirpodsTargetMatcher.kt` | V1 AirPods 路径已废弃 |
| `lsposed/milink/NotifyChangePump.kt` | V1 AirPods 路径已废弃 |
| `lsposed/milink/MilinkBridgeCache.kt` | V1 命名已废弃；M0 以 `MiTwsBridgeCache` 重建最小缓存 |

新增（注意：类名不带 V1/V2 后缀）：

```text
app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/
    MilinkRouteConfig.kt              // 路由配置（MITWS / TRACE）
    MilinkMiTwsFacadeEntry.kt         // MiTWS 入口
    MilinkMiTwsTraceEntry.kt          // trace-only 入口
    MilinkMiTwsFacadeHook.kt          // 核心 hook 实现
    MilinkBridgeClient.kt             // 模块侧 bridge client
    MiTwsBridgeCache.kt               // bridge snapshot TTL 缓存
    MiTwsDeviceIdPolicy.kt            // deviceId 模板策略
    MiTwsStateMapper.kt               // M2: bridge snapshot → MiTWS 状态映射
    MiTwsCallbackPump.kt              // M2: callback 派发泵
    MiTwsControlMapper.kt             // M3: 反向控制命令映射

app/src/main/java/dev/ignotus/openbuds/lsposed/xiaomi_bluetooth/
    XiaomiBluetoothTraceEntry.kt
    FastConnectTraceHook.kt
    MiuiGattPeripheralTraceHook.kt
    MiuiSppPeripheralTraceHook.kt
    MmaOverSppTraceHook.kt
    MiuiGattProxyStrategy.kt        // M5 only
    MiuiSppProxyStrategy.kt         // M5 only, default disabled
```

`ModuleMain.kt` 路由逻辑（类名不带 V1/V2 后缀）：

```kotlin
when (param.packageName) {
    "com.milink.service" -> when (MilinkRouteConfig.mode()) {
        MilinkRouteMode.MITWS -> MilinkMiTwsFacadeEntry(cl).install()
        MilinkRouteMode.TRACE_ONLY -> MilinkMiTwsTraceEntry(cl).install()
    }
    "com.xiaomi.bluetooth" -> {
        XiaomiBluetoothTraceEntry(cl).installIfEnabled()
    }
}
```

`MilinkRouteMode`（枚举值不带 V1/V2 后缀）：

```kotlin
enum class MilinkRouteMode {
    MITWS,       // 默认：MiTWS 主线
    TRACE_ONLY,  // 仅 trace，不修改返回值
}
```

## 10. 参考源

- `references/mi/com.milink.service/sources/com/miui/circulate/api/protocol/bluetooth/BluetoothServiceClient.java`
- `references/mi/com.milink.service/sources/com/xiaomi/mxbluetoothsdk/manager/MxBluetoothManager.java`
- `references/mi/com.milink.service/sources/com/xiaomi/mxbluetoothsdk/service/MxBluetoothService.java`
- `references/mi/com.milink.service/sources/com/xiaomi/mxbluetoothsdk/control/Constant.java`
- `references/mi/com.milink.service/sources/com/android/bluetooth/ble/app/IMiuiHeadsetService.java`
- `references/mi/com.xiaomi.bluetooth/sources/com/xiaomi/bluetooth/peripheral/MiuiGattPeripheral.java`
- `references/mi/com.xiaomi.bluetooth/sources/com/xiaomi/bluetooth/peripheral/MiuiSppPeripheral.java`
- `references/mi/com.xiaomi.bluetooth/sources/com/xiaomi/bluetooth/peripheral/MiuiPeripheralConnectionServiceReal.java`
- `references/mi/com.xiaomi.bluetooth/sources/com/xiaomi/bluetooth/mma/plugin/MiuiMMARegisterManager.java`
- `references/mi/com.xiaomi.bluetooth/sources/com/android/bluetooth/ble/app/fastconnect/MiuiFastConnectService.java`
- `references/mi/com.xiaomi.bluetooth/sources/com/android/bluetooth/ble/app/fastconnect/MiuiFastConnectStateMachine.java`
- `references/mi/com.xiaomi.bluetooth/sources/com/android/bluetooth/ble/app/headset/plugin/BluetoothHeadsetServicePlugin.java`
- `docs/plan/MILINK_FIRST_PARTY_ADAPTER_PLAN.md`
- `docs/PROTOCOL_GUIDE.md`
- `docs/BRAND_INTEGRATION_GUIDE.md`
