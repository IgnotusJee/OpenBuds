# OpenBuds 米链第一方耳机适配器集成方案（方案 A）

更新日期：2026-06-01

本文细化"让 OpenBuds 支持的耳机（Sony / QCY / …）被 HyperOS 米链当作第一方设备渲染"的可行方案。

> **实现方式**：本方案的推荐路径是 **LSPosed 模块**，注入到 `com.milink.service` 进程，劫持米链在调用蓝牙栈 binder 之前的客户端方法（`MxBluetoothManager`、`BluetoothServiceClient`）。不会修改米链一行代码，也不跨越 `com.android.bluetooth` 进程边界。

前置阅读：

- `references/mi/com.milink.service/` 反编译源（米链 13.x）
- `docs/plan/LSPOSED_SYSTEM_INTEGRATION_TASKS.md` — 系统集成的 UI 注入路径（互补方案）
- `docs/PROTOCOL_GUIDE.md` — OpenBuds 现有协议层结构

## 0. 目标与边界

> **本方案是 OpenBuds 系统集成的目标主方案**，替代两条旧路径：
> 1. `LSPOSED_SYSTEM_INTEGRATION_TASKS.md` 中基于 UI 控件注入的 P5-P9 阶段；
> 2. 当前分支里直接 hook 米链卡片/策略/`b0` 控制器的数据管线方案（例如 `MiLinkHeadsetCardHook`）。
>
> 现有直接 hook 卡片方案可以作为调研样本和回退实现保留到 M2 通过；之后应从主线移除，避免两套 hook 同时影响米链渲染。

### 目标

把 OpenBuds 已经握住的协议状态（电量、ANC、佩戴、播放、EQ 等）"伪装"成 Xiaomi 蓝牙栈认识的第一方耳机协议输出，使米链 (`com.milink.service`) 在以下两个表面上**不修改任何米链代码**就把 OpenBuds 支持的耳机当作 Mi Buds 同级渲染：

1. 系统下拉控制中心的米链组件（设备贴纸 + 点击展开的 MLCard）
2. 融合设备中心 (`CirculateWorldActivity`) 的设备列表 + `HeadsetDetailFragment` 全屏控制面板

成功标准分阶段定义：
- **M1-M3（只读主线）**：控制中心和融合设备中心把 OpenBuds 设备识别为 `HEADSET`，展示设备名、图标、左右耳/盒电量、佩戴/充电状态；真 AirPods 不受影响。
- **M4+（可写增强）**：在不 hook `com.android.bluetooth` 的前提下，选择性接入查找耳机、音量、ANC 等反向控制。只有确认 AirPods 路径下相关控件可见且会进入米链控制层后，才把对应能力纳入验收。
- **降级标准**：OpenBuds App、LSPosed 模块或自有闸门关闭后，米链应自然回退到 `third_headset` / 通用蓝牙路径，不崩溃、不污染真实 AirPods。

### 明确不做

- 不修改 `com.milink.service` 本身 — 所有 Hook 点都在米链进程内的 `MxBluetoothManager` / `BluetoothServiceClient` 等**客户端方法**上，在它调 binder 之前拦截。不跨越进程边界到 `com.android.bluetooth`
- 不跨越进程边界，不需要逆向 `com.android.bluetooth` 的 APK — 所有 Hook 目标都在 `references/mi/com.milink.service/` 里已有源码
- 不分发任何被 Xiaomi 著作权保护的 jar 二进制（`AirpodsAdapter.jar`、`MxBluetoothService` 内部实现）
- 不承诺非 HyperOS 设备上的可用性（AOSP / 三方 ROM 不会有 `com.android.bluetooth.ble.app.headsetdata.provider`）
- 不继续扩大当前直接 hook `MLCardViewHostService` / `HeadsetServiceController` / `com.miui.headset.api.*` 的卡片重定向方案。该方案维护面太宽，只作为旧代码资产和对照样本。

### 与已废弃的 UI 注入方案的关系

旧方案 (`LSPOSED_SYSTEM_INTEGRATION_TASKS.md`) 的 P5-P9 阶段试图 hook `systemui` / `com.xiaomi.bluetooth` / `com.android.settings` 的 UI 控件来完成弹窗、控制中心入口、设置页注入。该路径因以下原因被放弃：

1. 每项 UI 注入都依赖具体 HyperOS 版本的混淆类名和方法签名，维护成本不可持续
2. 每个表面（弹窗、卡片、设置页）需要独立适配，总工作量与版本数相乘
3. P6（控制中心卡片）勉强可用但 P7（设置页注入）直接遇到打断的调研缺口（需要小米第一方耳机做对照逆）

本方案替换旧方案的全部 UI 注入目标：
- **控制中心组件** → 协议层注入后**米链原生渲染**（本方案的直接效果）
- **融合设备中心设备列表** → **米链原生渲染**（同上）
- **弹窗 / Focus Island** → 不再需要，米链自身会为第一方耳机出弹窗
- **设置页注入** → 不再需要，HeadsetDetailFragment 自带全部控件
- **状态栏图标** → 米链第一方耳机自带状态栏增强

已有协议层、仓库层和服务层代码可作为数据源；跨进程 IPC 通道需要补齐，不能假定现有 `SonyControlService.LocalBinder` 可被米链进程直接绑定。


## 1. 关键架构（来自反编译的事实）

### 1.1 米链对耳机的判别只通过两道 RPC

`com/miui/circulate/api/protocol/bluetooth/BluetoothServiceClient.java` 行 303-319：

```java
private String getDeviceType(BluetoothDevice dev) {
    if (isCameraGlasses(dev))    return CAMERAGLASSES;
    if (isCirculateAudioDevice(dev)) return AUDIOGLASSES;
    if (isMiHeadset(dev) || isAirPods(dev))
        return CirculateConstants.DeviceType.HEADSET;   // <- 第一方耳机的入口
    if (isMiSound(dev))          return SOUND;
    if (isMiScreenSound(dev))    return SCREEN_SOUND;
    return getBluetoothTypeNative(dev);                 // <- 第三方退化路径
}
```

只要 `isMiHeadset` 或 `isAirPods` 任一返回 true，米链就把该设备的 `devicesType` 标为 `HEADSET`。之后的所有逻辑（DeviceStrategy 选 `C5269e` 而非 `C5277m`、UI 进 `HeadsetDetailFragment` 而非降级贴纸、loading 标题用 `deviceInfo.title` 而非通用"耳机"字符串）全部由这一个字符串值驱动。

底层 binder：

| 米链 java 调用 | binder 接口 | transaction id |
|---|---|---|
| `isMiHeadset(dev)` → `MxBluetoothManager.checkIsMiTWS` | `com.android.bluetooth.ble.app.IMiuiHeadsetService` | 19 |
| `isAirPods(dev)` → `MxBluetoothManager.checkIsAirPods` | 同上 | 21 |

两者共享同一个 `IMiuiHeadsetService` 远端实现，宿主进程是 `com.android.bluetooth`（Xiaomi 修改版的蓝牙系统服务）。

### 1.2 状态读取走 ContentProvider

`content://com.android.bluetooth.ble.app.headsetdata.provider/` 是米链读耳机状态的唯一通道：

| Path / Bundle key | 提供者 | 米链消费方 |
|---|---|---|
| `/airpodsstate` + `call("getAirpodsState", mac)` → 11 字段 Bundle | AirpodsAdapter | `AncBatteryController$registerAirpodsStateCallback$1.onChange` (行 1295-1340) |
| `/deviceinfo` (cursor query) | Xiaomi 蓝牙栈 | `MxBluetoothService` 内部 |
| `/device_classify/bluetoothaddress_soundbox` | Xiaomi 蓝牙栈 | `C4704m`（音箱分类） |

AirPods 11 字段（来自反编译的 Bundle key 列表，行 1306-1316）：

```
device | connectState | isLeftWearing | leftBattery | isRightWearing
rightBattery | boxBattery | isLeftCharging | isRightCharging | isBoxCharging | modelName
```

米链解析这 11 字段后（`airpodsBatteryParse` 行 919-963）整形成 6 元组 `[box, left, right, isBoxCharging, isLeftCharging, isRightCharging]`，从这一步起整个堆栈再分不出这是 AirPods 还是小米耳机。

### 1.3 米链对 AirPods 的特殊处理只有两处

完整搜过仓库后，米链对 AirPods 的非通用代码只有：

1. `HeadsetServiceClient.java:636` — 远端主动设备变更时，如果 `headsetDeviceInfo.type` 是 5 或 6（AirPods / AirPods Max），跳过音频切换处理。这是为了避免双向投送时把 AirPods 抢走。
2. 图标查找映射 `4 → openwear`, `5 → airpods_headset`, `6 → airpods_headphones`（散落在 `BluetoothDeviceObserver.java:329`、`HeadsetInfoView.java:60`、`AbstractC6169a0.java:21` 等）。

**这意味着只要适配器在 `headsetType` 字段返回 `0` 或 `4`（普通耳塞 / 开放式耳机），就能完全避开米链的 AirPods 专属分支，直接走"通用第一方耳机"路径**。


## 2. 适配器架构

### 2.1 部署形态

适配器本质上是"在米链进程内伪造 AirPods 查询链路的返回值 + 给 `headsetdata.provider` 观察者提供虚拟数据"。不要让米链进入 MiTWS/MMA 路线，也不要直接替换卡片渲染策略。

本方案只采用 **Xposed/LSPosed 模块** 这一种部署形态：

- **运行宿主**：注入到 `com.milink.service` 进程（米链就是在这个进程里调用蓝牙栈的）
- **Hook 目标**：
  - 优先：`com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager.checkIsAirPods(String)`
  - 优先：`com.xiaomi.mxbluetoothsdk.manager.MxBluetoothManager.getAirPodsState(String)`
  - 必要时：`com.miui.circulate.api.protocol.bluetooth.BluetoothServiceClient.isAirPods(BluetoothDevice)`，作为 `MxBluetoothManager` 签名变化时的兜底
  - 必要时：`ContentResolver.call(airpodsstate, "getAirpodsState", mac, null)`，为 `AncBatteryController` 的主动刷新提供 11 字段 Bundle
  - 可选：`MxBluetoothManager.getRingFindState(String)` / `ringFindForAirPods(String, boolean)` / `getDeviceId(BluetoothDevice)`
- **路由策略**：在 Hook 的 after-callback 里检查参数 MAC 是不是 OpenBuds 管理的设备，是就 `param.setResult(...)` 返回伪造值，不是就让原方法继续
- **优点**：可逆、易调试、不需要改系统镜像、跟现有 `LSPOSED_SYSTEM_INTEGRATION_TASKS.md` 共用模块壳
- **缺点**：需要 LSPosed/Magisk 环境

明确不 hook：
- `checkIsMiTWS(BluetoothDevice)` 默认透传，避免米链走 MMA 控制通道。
- `MLCardViewHostService`、`C4737b0/b0` getter、`com.miui.headset.api.*` 工厂/代理默认不再 hook。只有 M4 证明 AirPods 路径无法完成某个反向控制时，才以独立实验分支短期验证，不进入 M1-M3 主线。

### 2.2 模块与 OpenBuds 主 App 的关系

```
+---------------------------+    App bridge IPC    +-----------------------------+
| OpenBuds App (普通进程)   |<-------------->| LSPosed 模块                 |
|  HeadphoneRepository      |                | (注入 com.milink.service)    |
|  MilinkBridgeService      |                | MilinkAirpodsAdapterHook     |
+---------------------------+                +--------------+--------------+
                                                            |
                                                            | Hook 在米链进程内
                                                            | 拦截 binder 调用方
                                                            v
                                            +---------------+---------------+
                                            | com.milink.service (米链)      |
                                            |                                |
| BluetoothServiceClient         |
|  .isAirPods()    <- 被劫持    |
| MxBluetoothManager             |
|  .checkIsAirPods() <- 被劫持   |
|  .getAirPodsState() <- 被劫持  |
                                            |                                |
                                            | AncBatteryController           |
                                            |  ContentResolver.call(URI, …)  |
                                            |   ← 被劫持，不进入蓝牙进程     |
                                            +-------------------------------+
                                                            │ 不跨越进程边界
                                                            ▼
                                            +-------------------------------+
                                            | com.android.bluetooth          |
                                            | (不需要 Hook，不需要逆向)      |
                                            +-------------------------------+
```

> 关键设计：所有 Hook 都打在 `com.milink.service` 进程内，在米链调用 binder 的**客户端方法**上拦截。`MxBluetoothManager` 和 `MxBluetoothService` 在这个进程中充当通往蓝牙栈的门面——我们劫持门面的返回值，蓝牙栈进程根本不会被调到，因此不需要逆向 `com.android.bluetooth` APK 的任何内容。

模块 ⇄ App 的 IPC 选型：
- 新增 `MilinkBridgeService`（或 AIDL 等价实现），由 OpenBuds App 进程持有 `HeadphoneRepository`，对米链进程内的模块暴露只读快照和少量命令接口。
- 现有 `SonyControlService.LocalBinder` 不是跨进程 AIDL，且 `android:exported="false"`，不能直接作为模块 IPC 使用。
- Bridge 必须校验调用方：优先校验 UID/package 为 `com.milink.service` 或 LSPosed 模块宿主上下文，叠加自定义 signature/normal 权限和随机 session token。不要把控制接口开放给任意三方 App。
- 模块只做状态消费、格式转换和 Hook 代理，**不重复实现协议解析**。临时 POC 可以在米链进程内直接创建 `HeadphoneRepository`，但 M3 前必须迁回 App 进程，避免系统进程持有 BLE 连接导致生命周期和权限不可控。


## 3. 协议适配器：方法级实现规格

本节给出适配器要 Hook 的每个方法的输入/输出契约，以及 OpenBuds 端数据如何映射进去。

### 3.1 `checkIsMiTWS(BluetoothDevice) : int`

- 原语义：返回 1 表示是小米 TWS（MMA 协议握手成功）
- 适配器策略：**保持默认**。让原方法继续返回原值。OpenBuds 设备走另一个 hook（`checkIsAirPods`）以避开"米链对真正的 MiTWS 设备会主动发起 MMA 控制请求"的副作用
- 备选：若需要伪装 MiTWS（更深度集成），需要同时实现 MMA 协议响应—成本极高，不推荐

### 3.2 `checkIsAirPods(String mac) : boolean`

- 原语义：调用 AirpodsAdapter 看蓝牙地址是不是 Apple Continuity 广播过的 AirPods
- 适配器策略：
  - 先调用 `param.invokeOriginalMethod()` 拿到原结果
  - 如果原结果是 true → 透传（真的是 AirPods）
  - 否则，查 OpenBuds bridge 返回的 "已知设备 MAC 集合"，命中且所有闸门打开时返回 true
- **Hook 层级**：优先 hook `MxBluetoothManager.checkIsAirPods(String)`，因为 `mAirPodsAdapterEnable <= 0` 时原方法会直接返回 false。Hook 应在原方法之后覆盖结果，或在方法入口处对 OpenBuds MAC 直接返回 true，不能依赖系统 AirPods 闸门已经打开。
- **闸门检查**：
  - `OPENBUDS_MILINK_ADAPTER_ENABLE` 是主闸门，默认关闭，由 OpenBuds 设置页控制。
  - `AIRPODS_ADAPTER_JAR_ENABLE` 只作为诊断项记录。不要把它作为强制前置；很多机型没有 AirPods adapter 或默认不开，强依赖它会导致方案不可用。
  - 如果用户明确选择"严格模拟系统 AirPods adapter"，可增加兼容模式，要求 `AIRPODS_ADAPTER_JAR_ENABLE > 0` 后才响应。


### 3.3 `getAirPodsState(String mac) : String[]`

- 原语义：`MxBluetoothManager.getAirPodsState(mac)` 返回 `String[]`；`AncBatteryController.getAirpodsStatus()` 会直接消费这个数组并调用 `airpodsBatteryParse(states)`。
- 适配器返回值规约：必须至少包含 8 个元素。`BluetoothServiceClient.getDeviceIdForAirpods()` 还会读取 index 8，所以为了兼容设备类型反查，实际返回长度应为 9。每个字段都是数字字符串或 `"true"`/`"false"`/`"-"`。
- OpenBuds → 米链字段映射：

| 数组 index | 字段（米链命名） | OpenBuds 来源 | 缺省值 |
|---|---|---|---|
| 0 | `isLeftWearing` | `DeviceStateSnapshot` 佩戴状态；没有就 "-" | "-" |
| 1 | `leftBattery` (0-100) | `DeviceStateSnapshot.leftBattery` | "-" |
| 2 | `isRightWearing` ("true"/"false") | `wearingState.right` | "-" |
| 3 | `rightBattery` | `DeviceStateSnapshot.rightBattery` | "-" |
| 4 | `boxBattery` | `DeviceStateSnapshot.caseBattery` | "-" |
| 5 | `isLeftCharging` | `chargingState.left` | "false" |
| 6 | `isRightCharging` | `chargingState.right` | "false" |
| 7 | `isBoxCharging` | `chargingState.case` | "false" |
| 8 | `deviceId`（米链用它选图标） | 见 3.4 节"deviceId 设计" | 必填 |

依据当前反编译结果，`airpodsBatteryParse` 读取 `states[4]` 为盒电量、`states[1]` 为左耳电量、`states[3]` 为右耳电量、`states[5..7]` 为充电状态；`registerAirpodsStateCallback` 从 Bundle 构造数组时也是 `{isLeftWearing, leftBattery, isRightWearing, rightBattery, boxBattery, isLeftCharging, isRightCharging, isBoxCharging}`。M2 必须用真机日志确认不同 HyperOS 版本是否一致。

### 3.3.1 `ContentResolver.call(..., "getAirpodsState", mac, null) : Bundle`

这是 ContentObserver 刷新路径，不等同于 `getAirPodsState(String): String[]`。Hook 返回 Bundle 时必须填以下 key：

| Bundle key | OpenBuds 来源 | 缺省值 |
|---|---|---|
| `device` | MAC 或设备名 | MAC |
| `connectState` | 固定 `"2"`（connected） | `"0"` |
| `isLeftWearing` | 左耳佩戴 | `"-"` |
| `leftBattery` | 左耳电量 | `"-"` |
| `isRightWearing` | 右耳佩戴 | `"-"` |
| `rightBattery` | 右耳电量 | `"-"` |
| `boxBattery` | 盒电量 | `"-"` |
| `isLeftCharging` | 左耳充电 | `"false"` |
| `isRightCharging` | 右耳充电 | `"false"` |
| `isBoxCharging` | 盒充电 | `"false"` |
| `modelName` | 伪 deviceId 或型号名，需和 3.4 对齐 | 必填 |

如果 M2 发现 `MxBluetoothService.isAirPods(BluetoothDevice)` 只用 `modelName` 判断类型，而 UI 电量只走 `String[]`，Bundle hook 可以降级为只服务 `notifyChange` 后的刷新。

### 3.4 deviceId 设计

`deviceId`/`modelName` 字段会被 `BluetoothServiceClient.getAirpodsHeadsetType(deviceId)` 反查成 int type，再用于：
- `AbstractC6169a0.m24477a()` 选耳机图标
- `HeadsetServiceClient.onActiveHeadsetChanged` 行 636 判断要不要跳过音频切换

要让米链拿到合理图标 + 不触发 AirPods 跳过逻辑，`deviceId` 应该让 `AbstractC14649a.m51162b()` 返回 `0` 或 `4`（普通耳塞 / 开放式耳机）。

调研步骤：
1. 读 `references/mi/com.milink.service/sources/...AbstractC14649a.java`（混淆类名待定）查 `m51162b` 的 deviceId → headsetType 映射表。
2. 优先选映射到 type=0 的普通耳塞 deviceId；若需要开放式图标，再选 type=4。
3. 每个品牌可以共用一个稳定模板，但同一设备的 deviceId 必须跨重启稳定，避免米链缓存产生重复设备。
4. 若米链版本升级换了映射表，适配器需要随版本调整；找不到安全模板时返回普通 type=0。

### 3.5 `getRingFindState(String mac) : boolean` / `ringFindForAirPods(mac, boolean)`

- 原语义：耳机查找的开/关查询和命令
- 适配器策略：透传到 OpenBuds 的对应能力（Sony 协议里是 `RING_NOTIFY` family，QCY 是 CMD 待查）。OpenBuds 没实现就返回 false / 忽略命令

### 3.6 `getDeviceInfo(String mac) : String`

- 原语义：返回 JSON 设备元数据（型号、固件、能力 bitmap 等）
- 适配器策略：M1-M3 不依赖该接口。只有真机确认某个米链版本在 AirPods 路径仍查询它时，才构造最小 JSON：

```json
{"model":"<vendor>_<model>","firmware":"<fwVersion>","capability":7}
```

`capability` 的 bit 含义需要从调用方反推；未知时不要伪造高能力位，避免 UI 显示 OpenBuds 实际不能响应的控件。

### 3.7 ContentProvider：`/airpodsstate` 推送

米链通过 `ContentObserver` 监听 `URI_AIRPODS_STATE = content://com.android.bluetooth.ble.app.headsetdata.provider/airpodsstate`，每次 `notifyChange` 会触发它重新 `call("getAirpodsState", mac)`。

适配器要做的事：
1. Hook `ContentResolver.call` 在 `(URI_AIRPODS_STATE, "getAirpodsState", openbudsMac, null)` 这一组参数下劫持，返回我们构造的 Bundle。
2. 每次 OpenBuds 状态变化时，从模块进程触发 `context.getContentResolver().notifyChange(URI_AIRPODS_STATE, null)`。
3. 若 `notifyChange` 在第三方 provider URI 上被权限或系统限制拦截，则 fallback 为 hook 米链内部的 `registerAirpodsStateCallback` / `getAirpodsStatus` 查询点，或在下次 `getAirPodsState` 被动查询时更新。

后一步是 push 通知米链"状态变了，来取"。OpenBuds 端应由新 bridge 提供最新快照；不要依赖不可跨进程访问的 `SonyControlService.LocalBinder`。


## 4. 设备发现与配对（让米链"看见"OpenBuds 设备）

仅 Hook `IMiuiHeadsetService` 还不够 —— 米链得先**在蓝牙连接列表里看见这个设备**才会去问"它是不是第一方"。需要再处理三处：

### 4.1 已配对设备扫描

米链通过 `BluetoothDevice` 集合扫描，对每个已配对设备调 `getDeviceType`。OpenBuds 管理的耳机本来就是用户系统配对过的标准 BT 设备，**默认就在这个集合里**。无需额外 hook。

### 4.2 BT class 与 device class

`BluetoothServiceClient.getBluetoothTypeNative` 行 709 用 BT class 区分：
- `1028` 或 `1048` → BT_HEADSET
- `1796` → BT_WATCH
- 其它 → SOUND

但这条路径只在 `isMiHeadset` 和 `isAirPods` 都返回 false 时才会被走到。我们 Hook 让 `isAirPods` 直接返回 true，**根本到不了这里**。所以 OpenBuds 设备的 BT class 是什么都不重要。

### 4.3 设备名称展示

米链的设备贴纸标题用 `BluetoothDevice.getAlias() ?? getName()`，这是 Android Bluetooth 的标准 API。OpenBuds 不需要伪造，系统配对后用户能直接在 HyperOS 蓝牙设置改名。

## 5. 闸门、权限与卸载安全

### 5.1 多重闸门

适配器是高权限组件（注入到系统进程），必须有充分的"关掉"路径：

1. **App 设置闸门**：OpenBuds DataStore 中的 `milink_adapter_enabled`，默认 false。模块每次命中 hook 时通过 bridge 读取或使用带 TTL 的缓存。
2. **模块运行闸门**：LSPosed scope 必须只包含 `com.milink.service`；未来若扩展 scope，必须单独评审。
3. **MAC 白名单**：只对当前被 OpenBuds App 实际连接或用户显式授权的设备生效。任意其它 BT 设备的查询透传原方法。
4. **真 AirPods 透传**：原方法返回 true 的设备必须直接返回 true，不改状态、不改 deviceId、不注入 Bundle。
5. **Bridge 存活检查**：如果 OpenBuds App/bridge 不可达，模块停止响应注入。米链会自动 fallback 到 `third_headset` 路径。
6. **可选系统诊断闸门**：记录 `AIRPODS_ADAPTER_JAR_ENABLE` 当前值，作为兼容性诊断，不作为默认强制条件。

### 5.1.1 模块部署提示

> **给模块开发者的提示**：请在 Android Studio 上禁用部署优化，或使用 `gradlew installDebug` 命令进行安装，否则无法更新模块。

```powershell
.\gradlew.bat installDebug
```

### 5.2 权限要求

- LSPosed 模块：标准 Xposed 安装即可。
- App 设置闸门：使用 OpenBuds 自有 DataStore，不需要 `WRITE_SECURE_SETTINGS`。
- 若需要兼容模式写入 `Settings.Secure.OPENBUDS_MILINK_ADAPTER_ENABLE` 或测试 `AIRPODS_ADAPTER_JAR_ENABLE`，才需要 `WRITE_SECURE_SETTINGS`，可通过 ADB 一次性授权：

```
adb shell pm grant dev.ignotus.openbuds android.permission.WRITE_SECURE_SETTINGS
```

不要假设注入到 `com.milink.service` 后自动拥有写 secure settings 的权限；不同 ROM 的 UID/SELinux 行为可能不同。默认设计应不依赖写 `Settings.Secure`。

### 5.3 卸载与禁用

- 模块禁用 → Hook 失效 → 米链下一次查询 `checkIsAirPods` 返回 false → 设备被分类为 `third_headset` → 自动降级为退化卡片
- 这条降级路径**米链原生支持**，不需要任何额外清理代码
- 用户卸载 OpenBuds App 时，bridge 不可达，模块自动停止注入；如曾启用兼容模式写入 Settings，设置页或卸载前清理流程应清理自定义键


## 6. OpenBuds 仓库内的集成点

以下代码资产可复用，但需要按 AirPods adapter 主线重新组织：

| 已存在的部件 | 本方案的用途 |
|---|---|
| `data/HeadphoneRepository.kt` | App 进程内唯一协议状态源和控制命令执行点 |
| `service/DeviceStateSnapshot.kt` | 可作为 bridge 输出 DTO 的基础；需要补齐佩戴/充电等 AirPods 字段 |
| `service/ControlCommand.kt` | M4+ 反向控制命令可复用，但不是 M1-M3 必需项 |
| `lsposed/ModuleMain.kt` | 继续作为 libxposed 入口，scope 保持 `com.milink.service` |
| `lsposed/ModuleMain.kt` | M0 后仅分发到 `lsposed/milink/` AirPods adapter 主线；不再加载旧 MiTWS/卡片重定向 hook |
| `lsposed/milink/MilinkAirpodsAdapterEntry.kt` | M0 占位入口；M1 起在该包内实现 AirPods 查询链 hook |
| 已删除旧 hook | `MiLinkIdentityHook`、`MiLinkHeadsetCardHook`、`HeadsetClientTraceHook`、`TextViewNameFixHook`、`HeadsetApiProbeHook`、`DeviceWhitelist`、`ProbeResultCache` 已从源码移除 |

新增文件清单（建议命名）：

```
app/src/main/java/dev/ignotus/openbuds/lsposed/milink/
    MilinkAirpodsHook.kt                // Hook MxBluetoothManager/BluetoothServiceClient AirPods 查询链
    AirpodsStateContentProviderProxy.kt // 拦截 /airpodsstate 的 call()
    DeviceIdRegistry.kt                 // OpenBuds 设备 MAC ↔ 假 deviceId/headsetType 映射
    AirpodsStateMapper.kt               // Snapshot DTO → String[] / Bundle 的纯函数转换器
    MilinkBridgeClient.kt               // 米链进程内绑定 App bridge，带缓存和超时
    NotifyChangePump.kt                 // 订阅 bridge 状态变化，触发 notifyChange 或 fallback 刷新
```

`app/` 侧新增：

```
app/src/main/java/dev/ignotus/openbuds/integration/milink/
    MilinkBridgeService.kt              // exported=true，受权限/调用方校验保护的 bridge
    MilinkBridgeAidl.kt / .aidl         // 若采用 AIDL，定义 snapshot 和 command 接口
    MilinkDeviceSnapshot.kt             // 跨进程稳定 DTO，避免直接暴露 UI state
    MilinkAdapterStatus.kt              // 适配器是否启用、米链版本、命中设备列表的 UI 状态
    MilinkAdapterSettingsScreen.kt      // Settings 里的开关页面
```

测试新增：

```
app/src/test/java/dev/ignotus/openbuds/integration/milink/
    AirpodsStateMapperTest.kt
    DeviceIdRegistryTest.kt
    MilinkBridgePermissionTest.kt
```

`docs/` 侧补充：

```
docs/plan/MILINK_FIRST_PARTY_ADAPTER_PLAN.md   // 本文档
docs/MILINK_BUNDLE_FORMAT.md                   // 11-字段 Bundle 的实际抓包结果归档（按米链版本）
```

## 7. 实施阶段

### 阶段 M0：路线切换与旧 hook 隔离（0.5 周）

- [x] 从 `ModuleMain` 主入口移除 `MiLinkHeadsetCardHook`、`HeadsetClientTraceHook`、`TextViewNameFixHook` 等直接卡片方案。
- [x] 按 M0 清理策略直接删除旧代码，不再保留默认禁用的旧 MiTWS/卡片重定向实现；同一构建中只保留 AirPods 主线入口。
- [x] 新建 `lsposed/milink/` 包，AirPods adapter 主线全部放入该包。
- [x] 从 `SonyControlService` 移除旧 `/sdcard/headset_whitelist.txt` 白名单写入；M3 改由 bridge 返回授权设备集合。
- [x] 验收：禁用旧 hook 后，OpenBuds 设备在米链中恢复通用蓝牙/third_headset 表现，无崩溃。旧 hook 已从源码删除。

### 阶段 M1：可行性验证（1 周）

> **状态：已完成代码实现，真机验证通过，HyperOS 3.0 类名/签名完全匹配。**

#### 代码实现 (x)

- [x] Hook `MxBluetoothManager.checkIsAirPods(String)`，对 M1 allowlist MAC 返回 true；原方法 true 的真实 AirPods 透传。
- [x] 如 `MxBluetoothManager` 签名不稳定，增加 `BluetoothServiceClient.isAirPods(BluetoothDevice)` 兜底 hook。
- [x] Trace-only hook `BluetoothServiceClient.getAirpodsDeviceId()` / `getAirpodsHeadsetType()`，记录调用但不修改返回值。
- [x] Trace-only hook `ContentResolver.query()`，监控米链对 headsetdata provider 的所有访问（发现它**不会**在控制中心卡片阶段查询 ContentProvider）。
- [x] ContentResolver.call() 拦截钩子已实现 — 但默认 trace-only，仅在 `debug.openbuds.milink_m1_intercept=true` 时返回伪造 Bundle。
- [x] 新增 M1 临时 MAC allowlist matcher，支持 `debug.openbuds.milink_m1_macs` 逗号分隔覆盖。
- [x] 所有 KDoc 注释已完成（4 个文件）。

#### 真机验证（HyperOS 3.0，小米13 Pro）(x)

- [x] 控制中心下拉栏日志确认：
  - `MxBluetoothManager.checkIsAirPods` 对所有已配对设备被调用
  - `LinkBuds S` (F8:4E:17:D1:32:27)、`WH-1000XM4` (88:C9:E8:92:4B:1D) → `target=true`
  - `Redmi Buds 6` (24:B2:31:C9:DC:FF) → `target=false`（未被冒领）
  - 所有类名/签名与 jadx 反编译完全一致，**零 missing**
- [x] `BluetoothServiceClient.isAirPods(BluetoothDevice)` 兜底 hook 调用链正常
- [x] 真 AirPods 透传逻辑验证（代码路径）：`original || isTargetMac`
- [x] `SystemProperties` 反射读取正常（`setprop` + `getprop` 均可工作）
- [ ] 实测 HyperOS 1.x、不同小米机型上的类名/方法名，形成 hook 签名清单。

#### M1 关键发现

**直接返回 `checkIsAirPods=true` 会导致控制中心设备卡片消失。** 原因是米链将设备分类为 `HEADSET` 后进入第一方耳机渲染策略（`C5269e`），该策略依赖设备元数据/ContentProvider 数据初始化。在缺乏这些数据的情况下（M2 才实现），整个卡片不渲染 — 连降级为 third_headset 的路径都被阻断。

**ContentProvider 仅在详细面板（`HeadsetDetailFragment`）阶段被查询，不在控制中心贴纸阶段。** ContentResolver `call()` 和 `query()` 的 trace hook 均未观察到控制中心下拉时对 `headsetdata` authority 的任何访问。

为此增设了 **TRACE / INTERCEPT 双模式机制**：

| 系统属性 | 默认值 | 效果 |
|---------|--------|------|
| `debug.openbuds.milink_m1_macs` | `00:11:22:33:44:55`（占位） | MAC 白名单，逗号分隔 |
| `debug.openbuds.milink_m1_intercept` | `"false"` | `"false"`=日志模式，不改变返回值 / `"true"`=注入模式 |

Trace 模式日志格式：
```
[TRACE] MxBluetoothManager.checkIsAirPods mac=XX:XX:XX:XX:XX:XX
    original=false  target=true  overridden=true  result=false
```
其中 `result=false` 表示**保留了原始返回值**，设备不受影响。

切换到 intercept 模式（需配合 M2 数据钩子）：
```powershell
adb shell setprop debug.openbuds.milink_m1_intercept true
```

### 阶段 M2：状态字段对齐（1-2 周）

- [x] Hook `MxBluetoothManager.getAirPodsState(String)`，返回固定 9 元素假数据。
- [x] Hook `ContentResolver.call(airpodsstate, "getAirpodsState", mac, null)`，返回固定 11 字段 Bundle。
- [x] 验收：米链下拉栏贴纸显示占位电量（75/80/90），`HeadsetDetailFragment` 分耳/盒电量稳定显示。HyperOS 3.0 小米13 Pro 验证通过。
- [x] 反编译确认 `String[]` index、Bundle key、`modelName/deviceId` 对 `headsetType` 的影响，更新 3.3 和 `docs/MILINK_BUNDLE_FORMAT.md`。真机调用链已通过 logcat 验证。
- [x] 抓取调用频率：`getAirPodsState` 每 2-3 秒一组（每次 5-6 burst），`ContentResolver.call("getAirpodsState")` **0 次/会话**，仅 String[] 路径有效。`ContentResolver.query` 仅访问 `/device_classify/...soundbox`，不访问 `/airpodsstate`。`notifyChange` 节流上限建议 ≤1 Hz。

#### M2 验收记录

- HyperOS 3.0（小米13 Pro）真机通过：控制中心富控件卡片渲染、占位电量显示、`HeadsetDetailFragment` 分耳电量、Redmi Buds 6 无冒领、关闭 intercept 降级为 `third_headset`、真 AirPods 透传保护（代码路径）。
- `ContentResolver.call` 路径在该版本未被触发，电池数据完全通过 `MxBluetoothManager.getAirPodsState(String[])` 获取。
- 占位数据：左耳 75、右耳 80、盒 90、左右佩戴 true、全部 charging false、connectState=2、deviceId=01010101（type=0）。

#### M2 实现记录

- 新增 `AirpodsStateMapper`，固定输出 `getAirPodsState` 的 9 元素数组和 `/airpodsstate` 的 11 字段 Bundle。当前占位值：左耳 75、右耳 80、盒 90、左右佩戴 true、全部 charging false、connectState=2。
- 新增 `DeviceIdRegistry`，默认使用 `01010101` 作为普通耳塞模板。该值存在于 `AbstractC14650b.m51179g()`，不命中 `m51162b()` 的 type=1/2/3/4/5/6 特殊分支，因此返回 type=0，避免 AirPods type=5/6 分支。
- `MxBluetoothManager.getAirPodsState(String)` hook 会先调用原方法；若原返回值已是长度至少 9 的数组则透传，以保护真实 AirPods。只有 `debug.openbuds.milink_m1_intercept=true` 且 MAC allowlist 命中且原状态缺失时才返回固定假数据。
- `ContentResolver.call(..., "getAirpodsState", mac, null)` 同样仅在 intercept + allowlist 命中时接管；若系统 provider 已返回 Bundle 则透传，否则返回固定 Bundle。

### 阶段 M3：OpenBuds 数据接入（2 周）

> **状态：已完成。**

- [x] 新增 `MilinkBridgeService` / AIDL，输出按 MAC 查询的 `MilinkDeviceSnapshot`，并提供授权设备集合。
- [x] Bridge 做调用方校验、超时和失败返回；模块端做 TTL 缓存，避免 hook 阻塞米链主线程。
- [x] 实现 `AirpodsStateMapper` 纯函数：`MilinkDeviceSnapshot` → `String[]` 和 Bundle。
- [x] 接入 `NotifyChangePump`：状态变化时节流触发 `notifyChange`，失败时只更新缓存等待被动查询。
- [x] 验收：真机 Sony LinkBuds S，控制中心展示真实电量、佩戴、充电状态

#### M3 实现记录

桥梁 IPC (`MilinkBridgeService` → `MilinkBridgeClient` → `MilinkBridgeCache`) 全部打通。关键额外工作：

- **`Application.attach` → `Application.onCreate` hook**：原 `attach` hook 在 LSPosed `onPackageLoaded` 之前已执行，导致 bridge client 从未启动。改为 `onCreate` 后正常。
- **`milinkAdapterEnabled` 默认 `true`**：模块默认启用，不需要用户每次打开 App 切换开关。bridge 仅在有已连接设备时推送快照，安全无害。
- **`debug.openbuds.milink_m1_macs` fallback**：当 bridge 无已授权设备时（App 未启动、BLE 协议未建立），`isAuthorized()` 降级检查系统属性白名单，确保 `checkIsAirPods=true` 最快返回。
- **`SonyControlService.autoConnect()` + `BluetoothConnectReceiver`**：耳机通过系统蓝牙连接时，自动拉起 `SonyControlService`，通过 4 秒 BLE 扫描发现设备后自动 BLE 连接。解决"不打开 App 也能获取真实电量"的核心问题。扫描路径提供完整 SonyAd 广告数据，确保 SPP 通道选择正确——直接 `connect(address,name)` 无广告数据会选错 SPP UUID 导致读失败。
- **`MilinkAdapterStatus.kt`** + `MainActivity` 中 adapter 状态指示器：开启 `milinkAdapterCheck` 后显示当前设备 MAC、电量、佩戴、协议状态。
- 测试新增：`AirpodsStateMapperTest.kt`（13 用例）、`DeviceIdRegistryTest.kt`（11 用例）、`MilinkBridgePermissionTest.kt`（14 用例）。

### 阶段 M4：反向控制可行性评估（1-2 周）

> 详见 §11.2

- [ ] 优先实现 `ringFindForAirPods(mac, boolean)` → bridge command（如果 OpenBuds 对应品牌支持查找）。
- [ ] 观察 AirPods 路径下 `HeadsetDetailFragment` 是否显示 ANC/音效/音量控件；如果控件不可见，不要为它们写生产 hook。
- [ ] 若控件可见且会进入 `C4737b0/b0` set 方法，再在独立实验闸门下 hook `setNoiseCancelling` / `setAudioEffect` / `setVolume`，转发到 bridge command。
- [ ] 验证完整闭环：米链 UI 操作 → bridge command → OpenBuds 协议执行 → 快照变化 → `notifyChange` → 米链 UI 更新。
- [ ] 通过后再决定是否把对应 hook 纳入主线；未通过则 M4 只交付查找耳机和只读状态。

### 阶段 M5：能力裁剪与降级（1 周）

- [ ] 对 OpenBuds 确实不支持的能力（空间音频、按键自定义、耳机独立音量等）不伪造 capability，不 hook 控制命令。
- [ ] 明确每个品牌的只读字段和可写命令矩阵。
- [ ] 验收：HeadsetDetailFragment 上不会出现明显不可用的控件；出现但无法关闭的控件必须在风险清单中标为阻塞。

### 阶段 M6：闸门与设置 UI（1 周）

- [ ] 5.1 节的多重闸门全部接入
- [ ] OpenBuds 设置页加"米链集成"开关
- [ ] 设置页展示 hook 命中状态、bridge 连接状态、最近一次米链版本/签名探测结果。
- [ ] 验收：关闭后米链下一次扫描或刷新时回到 `third_headset` 路径；不要求 1 秒内强制刷新，除非 M2 已证明 `notifyChange` 可稳定触发。

### 阶段 M7：QCY 等其它品牌（持续）

- [ ] 接入 QCY 后验证 5.1 节"MAC 白名单"机制能正确区分 Sony / QCY
- [ ] 给不同品牌选择不同的假 `deviceId` 以便米链显示对应图标
- [ ] 状态缓存从单设备全局变量升级为 `Map<Mac, Snapshot>`；禁止使用 `lastSonyMac` 这类单设备模型作为主线状态。

### 阶段 M8：评估蓝牙进程 hook 全能力适配（M7 后评估）

> 详见 §11.1。此阶段仅在 M0-M7 已稳定交付且团队有额外预算时考虑。

- [ ] 解包真机 `com.android.bluetooth` APK，确定 `MxBluetoothService` 的混淆后类名和方法签名
- [ ] 逆向 Mi-Headset MMA 协议握手包的最小必须回应字段
- [ ] 可行性评估：LSPosed 作用域扩展后蓝牙连接稳定性是否可接受
- [ ] 决策：是否从 AirPods 路径迁移到 MiTWS 路径


## 8. 风险与开放问题

### 8.1 已知风险

| 风险 | 触发条件 | 缓解措施 |
|---|---|---|
| 米链版本升级改了类名/方法签名 | HyperOS OTA | 在 M1 阶段建立"目标方法签名清单"，每个 HyperOS 版本回归一次 |
| 米链主动给设备发 MMA 控制命令导致 OpenBuds 协议混乱 | hook 太彻底，让米链以为是真的 MiTWS | 不 Hook `checkIsMiTWS`；只走 `checkIsAirPods` 路径 |
| Xiaomi 自家蓝牙栈对未知 `deviceId` 报错 | 假 `deviceId` 不在 `AbstractC14649a` 映射表里 | M2 阶段从映射表里选一个真实存在的 deviceId 模板 |
| 用户同时配对真的 AirPods 和 OpenBuds 设备 | 二者都被 Hook 路径覆盖 | 4.x 节的 MAC 白名单 + AirPods 透传原方法 |
| Bridge 暴露控制接口给三方 App | `MilinkBridgeService` exported 但校验不足 | 校验调用 UID/package、权限、session token；默认只提供只读接口，M4 才开放命令 |
| Hook 阻塞米链主线程 | 每次 hook 同步跨进程查询 App | 模块端 TTL 缓存、超时返回原方法、bridge 异步推送快照 |
| 当前旧卡片 hook 与 AirPods hook 同时生效 | 未隔离 `MiLinkHeadsetCardHook` | M0 先禁用旧入口；同一构建只能启用一条主线 |
| 法律/合规风险（Xiaomi 可能视为侵权） | 适配器分发被发现 | 模块以 MIT/Apache 协议开源，不打包任何 Xiaomi 资产；仅做"接口模仿"而非"代码复制" |
| 适配器导致 `com.milink.service` 崩溃 | Hook 异常未捕获 | 所有 Hook 的 callback 整体 try/catch，异常时调用 `param.invokeOriginalMethod()` 透传 |
| 米链记录了被适配设备的稳定 ID 上报到云端 | 米链 OneTrack 埋点 | M1-M3 不 hook 埋点；先记录上报路径和字段，是否脱敏另立隐私评审，避免过度 hook |

### 8.2 开放问题（需要在 M1 阶段确认）

1. `MxBluetoothManager`、`BluetoothServiceClient`、`AncBatteryController` 在真机 dex 里的类名/方法名是否与 jadx 输出一致？→ **已确认 HyperOS 3.0 小米13 Pro 上完全匹配。** 其他机型/版本待验证。
2. `mAirPodsAdapterEnable` 是否只在 `MxBluetoothManager` 层判断？如果某些版本在更上层也拦截 AirPods，需要 hook 更高层 `BluetoothServiceClient.isAirPods`。→ **`BluetoothServiceClient.isAirPods` 兜底 hook 已验证有效，调用链正常。**
3. `AIRPODS_ADAPTER_JAR_ENABLE` 在没有 AirpodsAdapter.jar 的设备上是否影响 provider/observer 初始化？它不作为默认闸门，但 M1-M2 要记录它对调用链的影响。
4. 米链 `HeadsetServiceClient.onActiveHeadsetChanged` 行 636 的 type=5/6 跳过逻辑，如果我们设 type=0/4，会不会触发其它意料外的代码路径。→ **未触发（trace 模式未改变 deviceId/type）。**
5. 控制中心组件刷新频率：`notifyChange` 调多了会不会被系统限流；如果限流，是否能接受被动刷新。
6. AirPods 路径是否会显示 ANC/音量/音效控件？→ **M1 确认：`checkIsAirPods=true` 会使控制中心卡片完全消失，说明 HEADSET 策略初始化失败时没有降级回退。ContentProvider 数据对接（M2）是卡片渲染的前置条件。**


## 9. 验证矩阵

### 9.1 单元测试

- `AirpodsStateMapper` 是纯函数，覆盖 `String[]` 和 Bundle 两种输出。
- `DeviceIdRegistry` 的 MAC ↔ deviceId 映射也是纯逻辑，测试覆盖全部品牌。
- `MilinkBridgeClient` 的缓存/超时/bridge 不可达行为用 JVM 或 Robolectric 测试覆盖。

### 9.2 集成测试（需要真机）

| 测试场景 | 状态 | 备注 |
|---|---|---|
| M0 禁用旧卡片 hook | ✅ | 旧 hook 已从源码删除，构建中不含旧路径 |
| M1 硬编码 Sony LinkBuds S MAC | ✅ | `debug.openbuds.milink_m1_macs=F8:4E:17:D1:32:27`，白名单命中 |
| M1 TRACE 模式设备不被误分类 | ✅ | `[TRACE] result=false`，卡片正常显示 |
| LinkBuds S MAC 在 logcat 可见分类链路 | ✅ | `checkIsAirPods`、`isAirPods`、deviceId trace 全部记录 |
| 关闭模块（在 LSPosed 管理器里） | ⬜ | 待手动验证降级 |
| 用户实际购买的 AirPods | ⬜ | 未测试（无真 AirPods 配对在测试机） |
| 多种品牌（Sony + QCY）同时配对 | ⬜ | Qcy 品牌待接入 |
| M1 INTERCEPT 模式 + ContentProvider 假数据 | ⬜ | 需 `setprop debug.openbuds.milink_m1_intercept true` 后触发 |

| 测试场景 | 期望表现 |
|---|---|
| Sony LinkBuds S，控制中心拉下来 | 出现设备贴纸，显示占位电量（75/80/90） |
| 上述贴纸点击 | 弹出 HeadsetDetailFragment，电量分耳显示 |
| 卸下耳机一只 | 控制中心电量更新（佩戴状态变） |
| 进入融合设备中心 | 设备列表里有该耳机，点击进 HeadsetDetailFragment |
| OpenBuds App 强制停止 | 米链回退到 third_headset，无崩溃 |

### 9.3 性能基线

- 控制中心展开延迟：接近米链原生第一方耳机基线；具体阈值以 M1 真机基线为准
- Hook 回调单次耗时：< 10ms（命中缓存）；bridge 同步查询超时上限 < 50ms
- `getAirPodsState` 单次调用耗时：< 50ms（避免阻塞米链主线程）
- `notifyChange` 频率上限：1 Hz（人眼可察觉的状态变化最小间隔）

## 10. 参考资料

- 反编译路径：
  - `references/mi/com.milink.service/sources/com/miui/circulate/api/protocol/bluetooth/BluetoothServiceClient.java`
  - `references/mi/com.milink.service/sources/com/xiaomi/mxbluetoothsdk/manager/MxBluetoothManager.java`
  - `references/mi/com.milink.service/sources/com/xiaomi/mxbluetoothsdk/service/MxBluetoothService.java`
  - `references/mi/com.milink.service/sources/com/android/bluetooth/ble/app/IMiuiHeadsetService.java`
  - `references/mi/com.milink.service/sources/com/miui/headset/runtime/AncBatteryController.java`
  - `references/mi/com.milink.service/sources/com/miui/circulate/api/protocol/headset/HeadsetServiceClient.java`
  - `references/mi/com.milink.service/sources/com/miui/circulate/world/MLCardViewHostService.java`
  - `references/mi/com.milink.service/sources/com/miui/circulateplus/world/headset/HeadsetDetailFragment.java`
  - `references/mi/com.milink.service/sources/com/miui/circulateplus/world/headset/HeadSetsDetail.java`
- 平行参考实现：
  - HyperPods (`references/HyperPods/`) — Apple 设备的 L2CAP 协议参考
  - OppoPods (`references/OppoPods/`) — HyperOS UI 注入参考
- OpenBuds 内部文档：
  - `docs/plan/LSPOSED_SYSTEM_INTEGRATION_TASKS.md` — 已废弃的 UI 注入方案（P5-P9 已取消，P1-P4 代码资产被本方案复用）
  - `docs/PROTOCOL_GUIDE.md` — Sony / QCY 协议层
  - `docs/BRAND_INTEGRATION_GUIDE.md` — 新增品牌的接入流程

## 附录 A：AirPods 案例对照

| 项目 | AirPods | OpenBuds 适配 |
|---|---|---|
| 进入门槛 | 系统出厂带 AirpodsAdapter.jar + Settings 开关 | LSPosed 模块 + OpenBuds 设置闸门 |
| 协议判别 | `IMiuiHeadsetService.checkIsAirPods` → Apple MMA 探针 | `checkIsAirPods` hook 返回 true |
| 状态来源 | AirpodsAdapter 解析 BLE Advertisement + L2CAP | OpenBuds 已握住的厂商协议 |
| 数据格式 | `String[]` + 11 字段 Bundle | 同上，由 `AirpodsStateMapper` 合成 |
| 控制能力 | 仅电量 + 查找 + 主动设备检测 | M1-M3 只读电量/佩戴/充电；M4 评估查找和少量控制 |
| 米链 UI | HeadsetDetailFragment 自动渲染 | 同上（共用代码） |
| 米链额外分支 | type=5/6 跳过音频切换 | 用 type=4 规避 |

**核心启示**：AirPods 案例证明米链的"第一方"是协议判别问题而非品牌判别问题。任何能回应 `MxBluetoothManager` 查询的实体（不限于系统蓝牙进程内的 Adapter——Hook 客户端方法一样有效），都会被米链一视同仁。OpenBuds 的适配器就是这种"回答者"的扮演者。


## 11. 未来方向：从只读到可写——双向控制

当前方案走 AirPods 路径，米链对设备的能力模型是"只读第一方"：电量、佩戴、型号、查找响铃。`HeadSetsDetail` 上的降噪切换、音效按钮、播放控制**当前阶段不会生效**——因为我们走的是 `checkIsAirPods` 路径，米链知道 AirPods 不支持 ANC 写命令，所以 UI 里的这些控件被自动隐藏或置灰。

下面两条路径各自能把控制能力向前推一步。

### 11.1 路径一：hook 蓝牙进程，走 MiTWS 路线

**目标**：让米链认为 OpenBuds 设备是小米 TWS（而非 AirPods），从而解锁全部双向控制——降噪模式切换、ANC 等级调节、音效切换、播放控制、按键自定义。

**做法**：
1. LSPosed 模块扩展到 `com.android.bluetooth` 进程作用域
2. Hook `MxBluetoothService`（注意：蓝牙进程里的**服务端实现**，类名和 milink 里的同名类不同）— 劫持 `IMiuiHeadsetService` 上的以下方法：
   - `changeAncMode(int mode, BluetoothDevice dev)` — 降噪模式切换
   - `changeAncLevel(String level, BluetoothDevice dev)` — ANC 等级
   - `changePlayStatus(int status, BluetoothDevice dev)` — 播放/暂停
   - `setFunKey(int key, int action, BluetoothDevice dev)` — 按键自定义
3. 同时实现 **Mi-Headset MMA 协议响应层**——因为米链在识别设备为 MiTWS 后会通过 SPP/L2CAP 尝试连接 MMA channel。如果不回应，蓝牙连接会出现握手超时或音频中断

**前提条件**：
- 逆向 `com.android.bluetooth` 的 APK，确定 `MxBluetoothService` 的混淆后类名和方法签名
- 实现一套能回应 Mi-Headset MMA 握手包的最小协议栈（或者 hook 更底层让米链的 MMA 连接请求不发给耳机）

**收益**：
- 全部控制能力可用（ANC 模式、ANC 等级、音效、播放、按键映射）
- 米链 UI 里的所有控件都启用

**代价**：
- 逆向一个我们没有源码的系统 APK
- 蓝牙进程 hook 出错会影响设备蓝牙连接稳定性
- 需要维护 Mi-Headset 协议兼容性（米链 OTA 可能改协议）

**建议**：此路径留待 M8 阶段评估。前提是 AirPods 路径和多品牌状态桥已稳定交付，且团队有真机上对 `com.android.bluetooth` 做类和协议逆向的预算。

### 11.2 路径二：milink 进程控制命令 hook（实验）

**目标**：不碰蓝牙进程，在 AirPods 只读链路稳定后，评估米链 UI 是否仍会暴露少量可写入口，并把这些入口转发到 `MilinkBridgeService`。

优先级顺序：
1. `ringFindForAirPods(mac, boolean)`：这是 AirPods 路径天然存在的控制面，优先实现。
2. `C4737b0/b0` set 方法：只在真机证明 AirPods 路径下对应 UI 可见且会调用这些方法后再实验。
3. 能力伪造：默认不做。为了显示 ANC/音效控件而伪造 capability 会重新扩大 hook 面，必须单独评审。

可能涉及的方法：

| Hook 方法 | 对应能力 | OpenBuds 端对应命令 | 主线状态 |
|---|---|---|---|
| `ringFindForAirPods(mac, isPlay)` | 查找耳机 | `ControlCommand` 新增查找命令或品牌专用命令 | M4 优先 |
| `m19883Z(svc, mode)` / `b0.Z` | ANC 模式切换 | `ControlCommand.SetNoiseControl(mode)` | 实验 |
| `m19882Y(svc, mode)` / `b0.Y` | 音效切换 | 待确认协议支持度 | 实验 |
| `m19885b0(svc, level)` / `b0.b0` | 音量调节 | 系统 AudioManager 或协议层音量命令 | 实验 |

实现要点：
1. 所有可写 hook 必须有独立实验闸门，默认关闭。
2. 只对 bridge 授权的 OpenBuds MAC 生效；真实 AirPods 和其它蓝牙设备全部透传。
3. 参数枚举必须通过真机日志确认后再映射到 OpenBuds 命令。
4. 用户操作后，状态回推走 bridge 快照 → `AirpodsStateMapper` → `notifyChange` / 被动查询，不再依赖 `SonyControlService.LocalBinder`。

局限：
- AirPods 路径下 ANC/音效/音量控件很可能被隐藏，因此 `C4737b0/b0` hook 不是 M1-M3 交付前提。
- 该路径虽然不影响蓝牙进程，但仍会影响米链控制层，不能称为零风险；异常时必须透传原方法。

建议：M4 只承诺评估和查找耳机。ANC/音效/音量只有在控件可见、调用链明确、命令闭环验证通过后，才考虑进入主线。

### 11.3 两条未来路径对比

| | 蓝牙进程 hook（MiTWS 路线） | milink 进程控制命令 hook |
|---|---|---|
| 需要逆向蓝牙 APK | **要** | 不要 |
| 需要实现 MMA 协议 | **要** | 不要 |
| 作用域扩展 | LSPosed scope 加 `com.android.bluetooth` | 不加（仍在 `com.milink.service`） |
| 稳定性风险 | 中等（蓝牙进程崩溃 = 所有设备断连） | 低到中等（米链进程，异常时透传原方法） |
| 可解锁的控制 | 全部（ANC + 音效 + 播放 + 按键） | 查找耳机优先；ANC/音效/音量取决于 AirPods 路径 UI |
| 实施阶段建议 | M8+（评估型） | M4 评估，不默认纳入主线 |
| 已有源码 | 需新逆 | `C4737b0.java` / `b0` 可参考，但签名仍需真机确认 |

推荐演进路线：AirPods 只读路径（M0-M3）→ AirPods 原生查找耳机（M4）→ 条件性控制命令实验（M4/M5）→ 交付后再评估是否需要进入蓝牙进程做 MiTWS 全能力适配。
