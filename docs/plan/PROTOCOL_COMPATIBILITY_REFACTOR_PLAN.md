# Sony Tandem 协议兼容层剩余修复计划

更新日期：2026-05-23

2026-05-22 P1 回查结论：

- ✅ V1 MC-only GATT 端点现在会被明确识别为 unsupported endpoint，不会进入 ready 或注册为可用控制通道；按 Sony 官方 App 逆向逻辑，GATT Tandem 入口要求 `TANDEM_V2_HPC_SERVICE`，当前不实现独立 V1 MC-only 控制握手。
- ✅ Table2 响应现在会进入 `Table2DiagnosticState`，Settings > Diagnostics 可显示最近的 channel、family、command、inquired type、values 和 raw hex；Table2 SET / UI 写入路径仍保持关闭。

2026-05-22 P2/P3 回查结论：

- ✅ `Table2Common` / `Table2Generic` 已改为 raw 内容相等，避免 `ByteArray` 引用相等导致状态/测试误判。
- ✅ V1 NC/ASM unknown notification 会保留真实 command，例如 `0x69` 不再被记录为 `0x67`。
- ✅ V1 Table2 unknown inquired type 明确选择 mirror Sony V1 enum：折叠为 `NO_USE(0x00)`，并由注释和测试覆盖。
- ✅ Source guard 单测可从 `App/` module cwd 或仓库根目录定位源码；BLE channel 测试命名已与断言一致。
- ✅ 回查确认 P2/P3 已处理干净；当前无开放 P2/P3 协议兼容层问题。

2026-05-23 真机日志验收结论：

- ✅ LinkBuds S：`captures/sonyrebuild-logcat-linkbudss.txt` 显示 SPP connected / Tandem ready，battery、NC/ASM、EQ、playback status 均经 SPP DATA_MDR 收发闭环，NC/ASM、EQ preset、playback 控制均有 ACK/RX 证据。
- ✅ WH-1000XM4：`captures/sonyrebuild-logcat-wh1000xm4.txt` 显示 SPP connected / Tandem ready；battery refresh 使用 `GET battery BATTERY [GATT_V1_MC] -> 0E 10 00`，NC/ASM refresh 使用 `GET NC/ASM param V1 [GATT_V1_MC] -> 0E 66 02`，ambient/NC/off 写入均使用 `SET NC/ASM V1 table1 ... [GATT_V1_MC] -> 0E 68 02 ...` 并收到 `0x69` notification 与 `0x67` readback；该日志中的 EQ/playback 仍来自旧混合 profile，当前实现已按逆向证据改为完整 V1 profile，待下一轮真机回归。
- ⚠️ WH-1000XM4 日志中有一次 `SPP ACK timeout expected=1; resending frame retry=1`，随后收到 ACK 并继续完成后续收发，不影响本次协议路由验收。

2026-05-23 XM4 TableSet1 修正：

- ✅ WH-1000XM4 的静态 profile 已改为独立 feature set，device info、battery、NC/ASM、ambient、EQ、Clear Bass、playback 均绑定 `SONY_TANDEM_V1_TABLE1` / `GATT_V1_MC`。
- ✅ WH-1000XM4 不再继承 LinkBuds S 的 V2-only LEA、Quick Access、Wearing status feature。

本文记录协议兼容层剩余问题的处理状态。此前已经完成的阶段性工作不再展开，包括：协议常量拆分、`TandemCodecRegistry`、`FeatureProtocolBinding`、WH-1000XM4 feature 协议映射修正、V1 Table1 GATT 默认通道修正、GATT MC endpoint 注册、V1/V2 Table2 codec、SPP `DATA_MDR_NO2` 映射、Repository 去协议策略化、P1 V1 MC-only unsupported 识别、P1 Table2 diagnostics state，以及文档同步。

## 1. 当前基准

主要参考：

- `docs/plan/Sony耳机BLE协议完整分析.md`
- `docs/PROTOCOL_GUIDE.md`
- `docs/DEVELOPMENT.md`
- `docs/FEATURE_STATUS.md`
- `references/SonyConnect/sources/com/sony/songpal/tandemfamily/message/mdr/p063v1/`
- `references/SonyConnect/sources/com/sony/songpal/tandemfamily/message/mdr/p064v2/`
- `references/SonyConnect/sources/com/sony/songpal/mdr/j2objc/tandem/MdlSeries.java`
- `references/SonyConnect/sources/com/sony/songpal/mdr/j2objc/tandem/features/`

当前实现状态：

- App 侧已有 `SonyTandemHeadphoneAdapter`、`ConnectedHeadphoneProfile`、`FeatureProtocolBinding`、`HeadphoneCommand.channel` 和 `TandemCodecRegistry`。
- LinkBuds S 主要走 SPP/Tandem 或 V2 Table1；WH-1000XM4 按完整 V1 TableSet1 静态 profile 处理，device info、battery、NC/ASM、ambient、EQ、Clear Bass、playback 均默认走 `GATT_V1_MC`。
- GATT discovery 仍以 `TANDEM_V2_HPC_SERVICE` 为入口；HPC 握手成功后会注册 V2 MC 和 V1 MC endpoint，并按 channel 写对应 `TO_ACC`。
- V1 MC-only GATT 控制握手不实现。Sony 官方 App 的 GATT Tandem 连接路径在缺少 `TANDEM_V2_HPC_SERVICE` 时直接走 target service missing failure；`TANDEM_V1_MC_SERVICE` 只出现在 UUID/characteristic 支持表中，没有发现作为独立 handshake/control 入口的代码路径或设备白名单。
- Table2 目前是只读 parse/diagnostic 路径，Repository 会保存最近响应的结构化 diagnostics state，不开放 UI 写入。

逆向约束：

- V2 Table1 主功能走 HPC。
- V2 Table2 扩展功能走 MC。
- V1 主要走 MC。
- SPP 封装 Tandem payload，不等同于 GATT 的 HPC/MC channel 选择。
- 新增功能必须先声明 profile binding，再由 codec registry 构建/解析，Repository 不直接选择 V1/V2/Table1/Table2。

## 2. 最新审计结论

审计范围：`App` 仓库最近提交 `837dd65 refactor: complete protocol compatibility routing`，对比 `HEAD~1..HEAD`，并按本文基准手工复核。

自动审计：

- `coderabbit review --agent --base-commit HEAD~1 -c ../AGENTS.md`
- 返回 6 个 issues；P0/P1/P2/P3 均已处理并回查完成。V1 Table2 fallback 已明确选择 mirror Sony V1 enum 的 `NO_USE(0x00)` 语义。
- P0 回查已完成：`defaultChannelFor(SONY_TANDEM_V1_TABLE1)` 和 `SonyTandemV1Table1Codec.defaultChannel` 均为 `GATT_V1_MC`；WH-1000XM4 的 device info / battery / NC / EQ / Clear Bass / playback command 均带 `GATT_V1_MC`。

测试验证：

- WSL shell 当前有 Java：`/usr/bin/java`, OpenJDK `21.0.10`。
- WSL 默认 Gradle user home 会重新下载 Gradle 9.3.1，速度很慢。
- WSL 复用 Windows Gradle cache 时触发 Gradle cache I/O error，不作为代码失败处理。
- Windows `cmd.exe` 在 `App/` module cwd 执行 `gradlew.bat testDebugUnitTest` 通过。
- Windows `cmd.exe` 在仓库根目录执行 `App\gradlew.bat -p App testDebugUnitTest` 通过。
- XML 汇总：`196 tests, 0 failures, 0 errors, 0 skipped`。

## 3. 已处理问题

### ✅ P2：Table2 response data class 的 `ByteArray` equality 不稳定

涉及文件：

- `app/src/main/java/dev/ignotus/sonyrebuild/protocol/SonyTandemTypes.kt`

现状：

- `Table2Common` 和 `Table2Generic` 是 data class，字段包含 `ByteArray raw`。
- Kotlin data class 对 `ByteArray` 使用引用相等，不是内容相等。
- `TandemMessage` 已手写 `equals()` / `hashCode()`，但 Table2 response 没有。

风险：

- 后续测试、状态比较、去重或缓存可能把相同 raw 内容判为不相等。

修复方向：

1. 给 `Table2Common` 和 `Table2Generic` 手写 `equals()` / `hashCode()`，使用 `raw.contentEquals()` 和 `raw.contentHashCode()`。
2. 顺手检查其他 `ParsedTandemResponse` data class 是否也需要结构化 equality。若暂不统一处理，至少为 Table2 增加回归测试。

处理状态：

- 已为 `Table2Common` 和 `Table2Generic` 增加结构化 equality / hashCode，并补充 raw 内容相等回归测试。

验收：

- 两个 raw 内容相同但实例不同的 `Table2Common` 相等，hashCode 相同。
- 两个 raw 内容相同但实例不同的 `Table2Generic` 相等，hashCode 相同。

### ✅ P2：V1 NC/ASM Unknown 响应丢失真实 command

涉及文件：

- `app/src/main/java/dev/ignotus/sonyrebuild/protocol/SonyTandemV1Table1Protocol.kt`

现状：

- `parseNoiseControl()` 在 unsupported type 时固定返回 `command = NCASM_RET_PARAM (0x67)`。
- 调用方同时把 `NCASM_RET_PARAM (0x67)` 和 `NCASM_NTFY_PARAM (0x69)` 路由到该函数。

风险：

- 未知 V1 NC/ASM notification 会在诊断日志里被记录成 `0x67`，丢失 `0x69` 上下文。

修复方向：

1. `parseNoiseControl(command, payload, raw)` 接收真实 command。
2. Unknown 返回使用真实 `command.unsigned`。
3. 增加 `0x69` unsupported payload 的测试。

处理状态：

- 已按真实 command 传入 parser，并覆盖 `0x69` unknown notification。

验收：

- V1 `0x69` unknown NC/ASM payload 解析为 `Unknown(command = 0x69)`。

### ✅ P2：V1 Table2 unknown inquired type fallback 语义需要明确

涉及文件：

- `app/src/main/java/dev/ignotus/sonyrebuild/protocol/SonyTandemV1Table2Protocol.kt`
- `references/SonyConnect/sources/com/sony/songpal/tandemfamily/message/mdr/p063v1/table2/peripheral/param/PeripheralInquiredType.java`
- `references/SonyConnect/sources/com/sony/songpal/tandemfamily/message/mdr/p063v1/table2/voiceguidance/param/VoiceGuidanceInquiredType.java`

现状：

- V1 Table2 的 `fromCode()` 对未知值返回 `NO_USE(0x00)`。
- 这和部分 Sony V1 逆向 enum 行为一致，但 clean-room diagnostic 层会因此把未知 byte 记录成 `0x00`。

风险：

- 诊断数据中无法区分真实 `NO_USE` 和未知 inquired type。

修复方向：

1. 做一次显式设计选择：
   - 若目标是忠实 mirror Sony enum，保留 `NO_USE` fallback，并在代码注释和测试中说明。
   - 若目标是诊断准确性，增加 `OUT_OF_RANGE(0xFF)` 或直接在 `Table2Generic.inquiredType` 保留原始 payload byte。
2. 不要在未说明的情况下把未知值静默折叠为 `0x00`。

处理状态：

- 已明确选择忠实 mirror Sony V1 enum：未知 inquired type 折叠为 `NO_USE(0x00)`。该选择会牺牲 raw unknown byte 的诊断区分度，但与逆向 enum fallback 一致，并已由 peripheral / voice guidance 测试覆盖。

验收：

- 测试覆盖 V1 Table2 unknown type，并明确断言所选语义。

### ✅ P3：测试可靠性和命名清理

涉及文件：

- `app/src/test/java/dev/ignotus/sonyrebuild/headphones/ProtocolCompatibilityArchitectureTest.kt`
- `app/src/test/java/dev/ignotus/sonyrebuild/ble/SonyBleClientChannelTest.kt`

问题：

- `ProtocolCompatibilityArchitectureTest.mainSource()` 使用相对路径 `src/main/...`，依赖 JVM working directory。
- `channelFromService_sppHasNoGattService` 实际传入 `TANDEM_V2_HPC_SERVICE`，只断言不是 SPP，测试名和变量不准确。

修复方向：

1. `mainSource()` 使用稳定 project dir 解析，兼容 module cwd 和 repo root cwd。
2. 将测试重命名为 `channelFromService_v2HpcIsNotSpp`，或删除冗余断言。

处理状态：

- 已加固 `mainSource()` 的路径解析，并将测试重命名为 `channelFromService_v2HpcIsNotSpp`。

验收：

- 从 `App/` module cwd 和仓库根目录触发测试时，source guard 测试都能定位源码。
- BLE channel 测试名与实际断言一致。

## 4. 结论：不实现 V1 MC-only GATT handshake

结论：当前关闭 `feat: design V1 MC-only GATT handshake`。Sony 官方 App 的 MDR/Tandem GATT 入口不是“按型号或 table set 在 V2 HPC 与 V1 MC 之间择一握手”，而是固定以 `TANDEM_V2_HPC_SERVICE` 建立 GATT Tandem 会话；缺少 V2 HPC 时直接失败，不会 fallback 到 `TANDEM_V1_MC_SERVICE`。

官方逆向证据：

- `references/SonyConnect/sources/com/sony/songpal/mdr/platform/connection/connection/C11640i0.java` 的 `connectDeviceWithGatt()` 只创建 `new C29205b(..., CommandTableSet.TABLE_SET_2, ...)`，GATT 连接入口固定是 Table Set 2。
- `references/SonyConnect/sources/za0/C29205b.java` 的 `startInternal()` 只用 `ServiceUuid.TANDEM_V2_HPC_SERVICE`、`CharacteristicUuid.TANDEM_HPC_FROM_ACC`、`CharacteristicUuid.TANDEM_HPC_TO_ACC` 构造 `C27327b`。
- 同一个类的 `onEstablishedGattConnection()` 在 `!bleGattDevice.hasService(ServiceUuid.TANDEM_V2_HPC_SERVICE)` 时直接调用 target service missing failure 并返回；这里没有检查 `TANDEM_V1_MC_SERVICE` 的 fallback 分支。
- `references/SonyConnect/sources/wa0/C27327b.java` 只保存构造函数传入的 service/from/to characteristic，并在 holding-device case 复用同一组参数；搜索未发现第二个以 `TANDEM_V1_MC_SERVICE` 构造会话的入口。
- `references/SonyConnect/sources/wa0/C27326a.java` 的写入路径只处理 `TANDEM_V2_HPC_SERVICE` 和 `TANDEM_V2_MC_SERVICE`；如果 session service 既不是 V2 HPC 也不是 V2 MC，会直接 return，因此 V1 MC service 即使被传入也不会写出 Tandem payload。
- `TANDEM_V1_MC_SERVICE` 只在 `ServiceUuid.java` 定义，以及在 `CharacteristicUuid.java` 中作为 `TANDEM_MC_TO_ACC` / `TANDEM_MC_FROM_ACC` 的可挂载 service 出现；未发现设备型号白名单、连接策略或 handshake 入口使用它作为独立 MDR/Tandem GATT 控制端点。

本项目处理策略：

- 保持当前行为：缺少 V2 HPC、但发现 V1 MC service 时，只记录 unsupported/probe diagnostics，不进入 ready，不注册为可用控制通道。
- 不新增 `GATT_V1_MC_ONLY` handshake profile，不把 V1 MC service 当作 V2 HPC 的替代入口。
- 若未来出现官方 App 新版本代码或 HCI 抓包能证明官方确实向 `TANDEM_V1_MC_SERVICE / TANDEM_MC_TO_ACC` 写入 Tandem payload，再重新开独立 investigation；在当前 Sony Sound Connect v13.0.5 逆向依据下不实现。

## 5. 已完成修复顺序

1. ✅ `test: harden protocol architecture source guards`
2. ✅ `fix: preserve V1 NC notification command in unknown responses`
3. ✅ `fix: add structural equality for Table2 parsed responses`
4. ✅ `docs/test: clarify V1 Table2 unknown inquired type fallback`
5. ✅ `feat: design V1 MC-only GATT handshake` 关闭：按官方 App 逆向逻辑不实现独立 V1 MC-only GATT 入口。

## 6. 当前验收清单

- ✅ `gradlew.bat testDebugUnitTest` 通过。
- ✅ `.\gradlew.bat assembleDebug` 通过。
- ✅ WH-1000XM4 的 V1 Table1 command 在 GATT transport 下走 `GATT_V1_MC`，已有 battery / NC refresh / NC write 单测覆盖。
- ✅ LinkBuds S 真机：SPP 主路径可刷新 battery、NC/ASM、EQ、playback status。
- ✅ WH-1000XM4 真机：battery、NC/ASM、ambient level 写入和刷新均按 V1 Table1 profile route，并确认 GATT/SPP 路径行为。
- ✅ `HeadphoneCommand.channel` 从 adapter 传到 transport，Repository 不丢弃 channel。
- ✅ `sendToChannel(GATT_V2_MC/GATT_V1_MC)` 已按 endpoint 写 MC `TO_ACC`。
- ✅ SPP Table2 outbound/inbound 保留 app `DATA_MDR_NO2 (0x0F)`。
- ✅ Table2 response 进入结构化 diagnostics state，而不是只写日志。
- ✅ V1 MC-only GATT endpoint 不会被误判为已支持；按官方 App 逆向逻辑保持 unsupported，不实现独立控制握手。
- ✅ V1 MC-only GATT 控制握手已关闭为非目标功能，除非后续出现官方代码或 HCI 抓包反证。
