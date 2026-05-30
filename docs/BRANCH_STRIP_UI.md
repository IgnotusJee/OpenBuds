# refactor/strip-ui-keep-protocol-lsposed — 分支文档

## 目标

抛弃 Compose UI 界面，保留蓝牙协议 + LSPosed 模块，实现小米融合设备中心（MiLink Fusion Device Center）的索尼耳机适配，提供与第一方耳机一致的富控件体验。

## 分支提交历史

| Commit | 内容 |
|--------|------|
| `3ebf1bb` | 删除 `ui/`、`theme/`、QuickPopupActivity、CrashHandleActivity；MainActivity 改为 View 版；strip 全部 Compose/MIUIX/Haze 依赖 |
| `21b2056` | 清除旧 LSPosed 钩子，Phase 1：MiLinkIdentityHook + DeviceWhitelist |
| `25c9a50` | MainActivity 加入扫描/连接/断开功能；LSPosed 诊断日志 |
| `f1cba24` | 挂钩进程从 com.milink.service 移到 com.android.bluetooth（参考 HyperPods/OppoPods） |
| `711aa81` | 重写为 MiLinkIdentityHook：基于名称的 Sony 检测，无需白名单文件 |
| `0d08235` | Phase 2：CardContentHook — 卡片弹窗内通过 SonyHeadphoneRepository 注入电池/降噪/EQ 数据 |
| `67bd63f` | Phase 3 探索：HeadsetClientTraceHook + TextViewNameFixHook |
| `7c6a998` | Phase 2 重写：MiLinkHeadsetCardHook — 协议层 hooks 实现原生第一方富控件 |
| `f6a4ad8` | 稳定性修复：名称匹配回退、移除 hostUpdateSent、跨进程 MAC 白名单 |
| `e390e69` | 修复加载延迟：重试 hostListener 获取、移除死循环 |
| `c9b869c` | 清理废弃的叠加层代码（CardContentHook, MLCardNativeControlsHook） |

## 当前架构

```
LSPosed 模块 → com.milink.service
  ├── MiLinkIdentityHook        Phase 1: isMiHeadset/getHeadsetType/isCirculateDevice
  │                              ├─ Sony 耳机识别为第一方卫星贴纸 ✅
  │                              ├─ isCirculateDevice 仅在连接时返回 true
  │                              ├─ BLE 预连接触发
  │                              └─ 跨进程 MAC 白名单写入
  ├── MiLinkHeadsetCardHook     Phase 2: 协议层 — 原生第一方富控件
  │                              ├─ MLCardViewHostService.v() → third_headset → AUDIOGLASSES
  │                              ├─ b0 控制器 hooks: getter(A/B/C/D/F/G) + future(L/X/e0/M/O) + set(Z/b0/Y)
  │                              ├─ c 工厂 hooks: 代理 headset client (k/m/n 接口)
  │                              ├─ SyntheticHeadsetState → HeadsetDeviceInfo/HeadsetHost 合成数据
  │                              ├─ 重试机制: 等 hostListener 就绪后推送 HeadsetHost 更新
  │                              └─ 全部使用 ProGuard 运行时类名 (b0/c/k/m/n/l/i/j)
  ├── HeadsetClientTraceHook    Phase 3: HeadsetServiceClient.b(Message) 名称修复
  ├── TextViewNameFixHook       Phase 3 备选: TextView.setText 框架级拦截
  └── DeviceWhitelist           跨进程 MAC 白名单（/sdcard/headset_whitelist.txt）
```

## Phase 1 — 卫星贴纸 ✅

挂钩 `BluetoothServiceClient` 三个方法，基于设备名称匹配 Sony 模式：

| 方法 | 返回值 | 效果 |
|------|:---:|------|
| `isMiHeadset(BluetoothDevice)` | `true` | 识别为第一方，写入 MAC 白名单，触发 BLE 预连接 |
| `getHeadsetType(BluetoothDevice)` | `2` (SINGER_BATTERY) | TWS 双耳 |
| `isCirculateDevice(BluetoothDevice)` | `isConnected()` | 断开后贴纸自动消失 |

Sony 识别规则：`wf-` / `wh-` / `wi-` / `mdr-` / `xba-` 前缀 + `linkbuds` + `sony` 子串。

## Phase 2 — 协议层原生富控件 ✅

通过 hook MiLink 的协议数据管道，让卡片自然渲染第一方耳机控件（电池弧、ANC 模式、设备名、音量等）。

### 数据管线

```
MLCardViewHostService.v(DeviceInfo, cardId)
  └→ 检测 third_headset → 重定向到 AUDIOGLASSES 策略 (plugin.n(service))
       └→ 策略安装 → card rendering
            ├→ b0.m19869B(serviceInfo) → HeadsetDeviceInfo { name, power, mode, ... }
            ├→ b0.m19868A(serviceInfo) → power list [L%, R%, case%, ...]
            ├→ b0.m19870C(serviceInfo) → int ANC mode
            ├→ b0.m19871D(serviceInfo) → device name
            ├→ b0.m19873G(serviceInfo) → volume
            └→ k.initialize() → HeadsetHost 更新 → 过渡到富控件
```

### 类名映射 (JADX → ProGuard 运行时)

| JADX 名 | 运行时名 | 用途 |
|---------|---------|------|
| `C4737b0` | `b0` | 头戴控制器 (com.miui.circulate.api.protocol.headset) |
| `C6540c` | `c` | 工厂类 (com.miui.headset.api) |
| `InterfaceC6548k` | `k` | 头戴客户端接口 |
| `InterfaceC6550m` | `m` | Profile 接口 |
| `InterfaceC6551n` | `n` | Query 接口 |
| `C6549l` | `l` | Multipoint 数据类 |
| `InterfaceC6546i` | `i` | Host 监听器 |
| `InterfaceC6547j` | `j` | Service 监听器 |

方法名同理：`m19869B` → `B`，`m25629a` → `a`，`m19868A` → `A`，etc.

### 合成数据

`SyntheticHeadsetState` 提供静态占位数据（`power=[85,85,85]`, `mode=2`, `volume=60`），卡片渲染后由 `SonyHeadphoneRepository` BLE 连接提供实时数据。未来可将 BLE 数据接入替换静态值。

### 加载延迟处理

`MiLinkHeadsetCardHook.hookMlCardStrategy()` 中，策略重定向成功后调用 `scheduleHostUpdateWhenReady(300)`，每 500ms 检查 `cachedHostListener` 是否就绪（即工厂代理 `c.a()` 是否已创建），就绪后立即推送 `HeadsetHost` 更新触发富控件渲染。最多等待 5 秒。

## Phase 3 — 名称修复

- `HeadsetClientTraceHook`: hook `HeadsetServiceClient.b(Message)`，将错误名称 "XXX的Xiaomi 型号" 替换为正确设备名
- `TextViewNameFixHook`: 框架级 `TextView.setText` 拦截作为备选

## 已知问题

| 问题 | 状态 | 说明 |
|------|:---:|------|
| 加载延迟 (~1-2s) | ⚠️ | 需等工厂代理创建后才能推送 HeadsetHost 更新；已有 5s 重试机制兜底 |
| 合成数据为静态值 | ⚠️ | `power=[85,85,85]`, `mode=2` 等为占位值，尚未接入 BLE 实时数据 |
| 音量控件不可用 | ⚠️ | 合成数据 pipeline 未处理音量 set 操作的双向同步 |
| 控制中心卡片标题偶尔显示错误名称 | ⚠️ | Message 层修复已生效但卡片可能从缓存渲染 |
| `ProbeResultCache` 在系统进程中写入 `/data/local/tmp/` 权限被拒 | ⚠️ | 不影响功能，仅诊断缓存缺失 |

## 文件清单

### LSPosed 模块文件 (`lsposed/`)

| 文件 | 作用 |
|------|------|
| `ModuleMain.kt` | 入口，分发到各 Phase 钩子 |
| `MiLinkIdentityHook.kt` | Phase 1 身份欺骗 + BLE 预连接 |
| `MiLinkHeadsetCardHook.kt` | Phase 2 协议层 hooks (策略重定向 + 控制器数据 + 工厂代理) |
| `HeadsetClientTraceHook.kt` | Phase 3 Message 级别名称修复 + 追踪 |
| `TextViewNameFixHook.kt` | Phase 3 备选框架级修复 |
| `HeadsetApiProbeHook.kt` | Phase 3 探测（参考） |
| `DeviceWhitelist.kt` | 跨进程 MAC 白名单 |
| `ProbeResultCache.kt` | 跨进程探测结果缓存 |

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

### 参考逆向文件

- `references/mi/com.milink.service/sources/` — MiLink 服务反编译源码
- 关键类：`MLCardViewHostService.java`, `C4737b0.java` (b0 控制器), `C6540c.java` (c 工厂), `CameraGlassesCard.java`, `AudioGlassesCard.java`
