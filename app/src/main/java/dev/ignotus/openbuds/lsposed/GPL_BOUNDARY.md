# GPL 边界规则 — LSPosed 模块

本文档定义 clean-room 实现中引用 HyperPods (GPLv3) 和 OppoPods (GPLv3) 的合法边界。

## 允许引用（关于小米操作系统的事实）

以下内容视为关于小米操作系统的无版权事实，可自由引用：

1. **类名** — 如 `miui.systemui.devicecenter.devices.DeviceInfoWrapper`、`com.android.systemui.shared.plugins.PluginInstance`。这些是小米的公共（尽管未记录）API 表面。通过动态分析发现这些名称以用于互操作目的。

2. **方法签名** — 如 `performClicked(Context)`、`loadPlugin()`、`handleConnectionStateChanged(device, from, to)`。方法签名是关于 API 的事实，不受版权保护。

3. **行为观察** — 如"调用 `exitOrHide()` 会在点击设备卡片后隐藏控制中心面板"、或"`MiuiBluetoothNotification` 构造函数接收 2 个参数"。这些是对系统运行时行为的观察。

4. **进程结构** — `com.xiaomi.bluetooth` 中存在 `MiuiBluetoothNotification` 类、或 `com.android.systemui` 加载 `miui.systemui.plugin` 包的事实。

5. **libxposed API** — `io.github.libxposed:api` 使用 Apache 2.0 许可证，其 API 模式和用法可自由使用。

## 禁止（GPL 衍生作品）

以下行为将使本项目成为 GPL 衍生作品，**严禁**：

1. **逐字复制任何 .kt 或 .java 文件**来自 HyperPods 或 OppoPods 仓库。

2. **复制通知构建器逻辑的结构和流程**。HyperOS 风格通知构建逻辑必须独立编写，仅基于通过动态分析观察到的小米通知结构事实。

3. **复制精确的字符串常量**。例如，HyperPods 使用 `"chen.action.hyperpods..."` 模式的 action 名称。OpenBuds 必须使用原始 action 名称，前缀为 `dev.ignotus.openbuds.*`。

4. **复制视频/图像/动画资产**。HyperPods 包含内嵌为 Base64 字符串的 Lottie 动画文件（如 AirPods 外壳动画）。OpenBuds 不得复制或使用这些资产。

5. **复制精确的电池百分比格式化逻辑**。小米蓝牙通知中的电池文本格式必须独立构造，从 `com.xiaomi.bluetooth` 中读取资源字符串 ID 并在运行时解析，而非从 GPL 代码移植格式化表达式。

## 实操工作流程

1. 阅读 HyperPods/OppoPods 源码以理解其 hook 了哪些小米类和方法。
2. 在记事本（或心智笔记）中记录目标类名和方法签名，**不复制代码**。
3. 关闭参考源代码。
4. 仅根据步骤 2 的笔记，结合 OpenBuds 自身的协议和 BLE 知识，使用 libxposed 现代 API 编写 hook 逻辑。
5. 提交信息应注明"通过动态分析观察到的小米类结构"或"通过运行时探测发现的目标方法"，而非"移植自 HyperPods"或"参考 OppoPods 实现"。

## 审查检查清单

在提交任何 LSPosed hook 代码前，确认：

- [ ] 未从 GPL 参考项目中复制代码行
- [ ] 所有 action 字符串使用 `dev.ignotus.openbuds.*` 命名空间（非 `chen.action.*`）
- [ ] 通知构建逻辑独立编写，基于对小米通知结构的理解
- [ ] 未嵌入来自 HyperPods/OppoPods 的 Base64 资产
- [ ] 提交信息描述了观察到的小米系统行为，而非引用 GPL 源代码
