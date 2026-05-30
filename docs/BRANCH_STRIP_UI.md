# refactor/strip-ui-keep-protocol-lsposed — 分支文档

## 目标

抛弃 Compose UI 界面，保留蓝牙协议 + LSPosed 模块，实现小米融合设备中心（MiLink Fusion Device Center）的索尼耳机适配。

## 分支提交历史

| Commit | 内容 |
|--------|------|
| `3ebf1bb` | 删除 `ui/`、`theme/`、QuickPopupActivity、CrashHandleActivity；MainActivity 改为 View 版；strip 全部 Compose/MIUIX/Haze 依赖 |
| `21b2056` | 清除旧 LSPosed 钩子，Phase 1：MiLinkIdentityHook + DeviceWhitelist |
| `25c9a50` | MainActivity 加入扫描/连接/断开功能；LSPosed 诊断日志 |
| `f1cba24` | 挂钩进程从 com.milink.service 移到 com.android.bluetooth（参考 HyperPods/OppoPods） |
| `711aa81` | 重写为 MiLinkIdentityHook：基于名称的 Sony 检测，无需白名单文件 |
| `0d08235` | Phase 2：CardContentHook — 卡片弹窗内通过 SonyHeadphoneRepository 注入电池/降噪/EQ 数据 |
| `80baac5` | 修复卡片去重和名称检测 |
| `659845b` | 过滤控制中心卡片中的 "XXX的Xiaomi" 账号名格式 |
| `3efa9c5` | NameTraceHook 诊断工具 |
| `67bd63f` | Phase 3 探索：HeadsetClientTraceHook（Message 级别修复）+ TextViewNameFixHook（框架级） |

## 当前架构

```
LSPosed 模块 → com.milink.service
  ├── MiLinkIdentityHook     Phase 1: isMiHeadset/getHeadsetType/isCirculateDevice
  │                           → 索尼耳机识别为卫星贴纸 ✅
  ├── CardContentHook        Phase 2: WindowManager.addView 检测 MLCard 窗口
  │                           → SonyHeadphoneRepository 直连 BLE
  │                           → 注入电量/降噪/EQ Views ✅
  ├── HeadsetClientTraceHook Phase 3: HeadsetServiceClient.b(Message)
  │                           → 在 Message 层面修复 HeadsetHost.name ✅
  │                           → 发现 headsetInfo=null（CardFrameService 无数据）
  ├── TextViewNameFixHook    Phase 3 备选: TextView.setText 框架级拦截
  └── DeviceWhitelist        共享 MAC 白名单（现已不用，名称检测替代）
```

## Phase 1 — 卫星贴纸 ✅

挂钩 `BluetoothServiceClient` 三个方法，基于设备名称匹配 Sony 模式：

| 方法 | 返回值 | 效果 |
|------|:---:|------|
| `isMiHeadset(BluetoothDevice)` | `true` | 识别为第一方 |
| `getHeadsetType(BluetoothDevice)` | `2` (SINGER_BATTERY) | TWS 双耳 |
| `isCirculateDevice(BluetoothDevice)` | `true` | 保险钩子 |

Sony 识别规则：`wf-` / `wh-` / `wi-` / `mdr-` / `xba-` 前缀 + `linkbuds` + `sony` 子串。

## Phase 2 — 卡片内容注入 ✅

从融合设备中心和控制中心点击卫星贴纸 → MLCard 窗口创建（窗口标题 `com.milink.card.frame.library.host.MLCard`）→ `WindowManagerGlobal.addView` 钩子检测到 → 从卡片视图提取设备名 → 匹配 Sony → 初始化 `SonyHeadphoneRepository` 建立独立 BLE 连接 → 观察 `StateFlow` → 注入自定义 `LinearLayout` 显示：设备名、电量（L/R/C）、降噪模式、EQ 预设、佩戴状态。

**关键技术决策**：在 `com.milink.service` 进程内直接调用项目的 `ble/` + `protocol/` + `data/` 层，零跨进程通信。

## Phase 3 发现 — HeadsetHost 消息机制

通过挂钩 `HeadsetServiceClient.b(Message)` 发现：

```
Message what=0, arg1=1, arg2=0
  obj = HeadsetHost(
    hostId    = "local_device_id"
    headsetInfo = null        ← CardFrameService 没数据
    name      = "账号的Xiaomi 型号"  ← 错误名称来源
    extra     = Bundle
  )
```

在 Message 层面修复了 `HeadsetHost.name`，但卡片标题在 Message 到达前已从缓存渲染，修复不反映到 UI。

## 已知问题

| 问题 | 状态 | 说明 |
|------|:---:|------|
| 控制中心卡片标题显示 "XXX的Xiaomi" | ⚠️ | Message 层修复已生效，但卡片不会重渲染；TextView.setText 钩子也未拦截到 |
| CardFrameService 内容为空白 | ⚠️ | `headsetInfo=null`；`com.miui.headset.api` 包在本版 HyperOS 已重构 |
| `com.miui.headset.api` 类不存在 | ⚠️ | `C6540c`、`InterfaceC6548k` 等类名在本版 HyperOS 不存在 |

## 本地 View 注入卡片效果

当前卡片本地注入显示：
- 设备名称
- 左右耳 + 充电仓电量
- 降噪模式
- EQ 预设
- 佩戴状态

从融合设备中心和控制中心两个入口均已生效。

## 文件清单

### LSPosed 模块文件 (`lsposed/`)

| 文件 | 作用 |
|------|------|
| `ModuleMain.kt` | 入口，分发到各 Phase 钩子 |
| `MiLinkIdentityHook.kt` | Phase 1 身份欺骗 |
| `CardContentHook.kt` | Phase 2 卡片内容注入 |
| `HeadsetClientTraceHook.kt` | Phase 3 Message 级别名称修复 + 追踪 |
| `HeadsetApiProbeHook.kt` | Phase 3 探测 |
| `TextViewNameFixHook.kt` | Phase 3 备选框架级修复 |
| `DeviceWhitelist.kt` | 共享 MAC 白名单 |
| `ProbeResultCache.kt` | 跨进程探测结果缓存 |
| `GPL_BOUNDARY.md` | GPL 边界声明 |

### 保留的非 UI 文件

| 目录 | 说明 |
|------|------|
| `ble/` | BLE GATT/SPP 传输层 (SonyBleClient, QcyBleClient 等) |
| `protocol/` | Tandem V1/V2 + QCY 协议 |
| `headphones/` | 设备适配器 + profile |
| `data/` | SonyHeadphoneRepository + 图片目录 + settings |
| `service/` | SonyControlService 前台服务 |
| `receiver/` | SystemIntegrationReceiver |
| `media/` | MediaPlaybackController |

### LSPosed 配置

- `scope.list`: `com.milink.service`
- 模块通过 `io.github.libxposed:api` + `io.github.libxposed:service` 构建
- 入口：`dev.ignotus.openbuds.lsposed.ModuleMain`
