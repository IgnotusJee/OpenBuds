# Sony耳机蓝牙通信协议完整分析

> 基于 Sony Sound Connect v13.0.5 APK 逆向工程结果  
> 分析日期: 2026-05-16

---

## 一、协议架构总览

Sony耳机的蓝牙通信协议分为三个层次：

```
┌──────────────────────────────────────────────┐
│  应用层:  AutoPlay / Sound Connect APP       │
│  (jp.co.sony.hes.autoplay / com.sony.songpal.mdr) │
├──────────────────────────────────────────────┤
│  消息层:  Tandem Family Protocol             │
│  V1(p063v1) / V2(p064v2)  Table1 / Table2    │
│  (com.sony.songpal.tandemfamily.message.mdr)  │
├──────────────────────────────────────────────┤
│  传输层:  BLE GATT 服务和特征                 │
│  (com.sony.songpal.ble.client)               │
│  26个 GATT Service + 88个 GATT Characteristic │
└──────────────────────────────────────────────┘
```

### 1.1 BLE GATT 层

所有 Sony 自定义服务使用统一 UUID 格式：
- **服务 UUID**: `5b833eXX-6bc7-4802-8e9a-723ceca4bd8f`
- **特征 UUID**: `5b833cXX-6bc7-4802-8e9a-723ceca4bd8f`

**关键耳机服务：**

| 服务名称 | UUID后缀 | 用途 |
|---------|---------|------|
| TANDEM_V2_HPC_SERVICE | 5b833e20 | V2协议主控制通道（耳机控制） |
| TANDEM_V2_MC_SERVICE | 5b833e21 | V2协议媒体控制通道 |
| TANDEM_V1_MC_SERVICE | 5b833e23 | V1协议媒体控制通道 |
| TANDEM_V1_FIESTABLE_SERVICE | 5b833e24 | V1协议Fiestable服务 |
| BLANC_FOTA_SERVICE | 5b833e30 | 固件OTA升级 |

**关键耳机特征：**

| 特征名称 | UUID后缀 | 方向 | 用途 |
|---------|---------|------|------|
| TANDEM_HPC_TO_ACC | 5b833c60 | 手机→耳机 | V2命令发送 |
| TANDEM_HPC_FROM_ACC | 5b833c61 | 耳机→手机 | V2响应/通知 |
| TANDEM_MC_TO_ACC | 5b833c62 | 手机→耳机 | V1/V2媒体命令发送 |
| TANDEM_MC_FROM_ACC | 5b833c63 | 耳机→手机 | V1/V2媒体响应 |

### 1.2 Tandem Family 协议层

协议有两个版本（V1和V2），每种版本有Table1和Table2两个子协议：

| 协议版本 | Table | 数据通道 | 用途 |
|---------|-------|---------|------|
| V2 (p064v2) | Table1 | HPC (5b833e20) | 主功能：NC/ASM、EQ、电源、播放、Sense、SAR、系统设置、固件升级 |
| V2 (p064v2) | Table2 | MC (5b833e21) | 扩展功能：外设管理、语音提示、安全聆听、LE Audio、派对模式 |
| V1 (p063v1) | Table1 | MC (5b833e23) | 旧版主功能 |
| V1 (p063v1) | Table2 | MC (5b833e23) | 旧版扩展功能 |

**消息基本格式：**

```
[DataType(1字节)] [Command(1字节)] [Payload(N字节)]
```

- DataType: `0x0E` = DATA_MDR (Table1), `0x0F` = DATA_MDR_NO2 (Table2)
- Command: 功能命令字节码
- Payload: 变长参数数据

**通信模式：**

```
GET_CAPABILITY → RET_CAPABILITY  (查询支持的功能)
GET_STATUS → RET_STATUS / NTFY_STATUS  (读取当前状态)
SET_PARAM / SET_STATUS (设置参数)
NTFY_PARAM / NTFY_STATUS (耳机主动通知)
```

### 1.3 连接握手流程

```
1. BLE扫描 → 发现设备
2. BLE连接 (GATT connect)
3. 服务发现 (Service Discovery)
4. 读取 OPTIMAL_MTU (5b833c94)
5. 请求更大 MTU (通过 GATT MTU Request)
6. 启用 DETERMINE_MTU 通知 (5b833c93)
7. 确定 MTU 大小
8. 禁用 DETERMINE_MTU 通知
9. 读取 WRITABLE_VALUE_LENGTH (5b833c91)
10. 启用 TANDEM_*_FROM_ACC 通知 (5b833c61)
11. 开始协议通信
```

---

## 二、各功能对应的蓝牙协议详解

### 2.1 降噪/环境声控制 (NC/ASM)

**使用的GATT服务**: TANDEM_V2_HPC_SERVICE (5b833e20)  
**使用的GATT特征**: TANDEM_HPC_TO_ACC (send) / TANDEM_HPC_FROM_ACC (receive)  
**协议版本**: V2 Table1 或 V1 Table1  
**命令范围**: 0x60-0x69

| 命令 | V2字节码 | V1字节码 | 方向 |
|------|---------|---------|------|
| GET_CAPABILITY | 0x60 | 0x60 | 手机→耳机 |
| RET_CAPABILITY | 0x61 | 0x61 | 耳机→手机 |
| GET_STATUS | 0x62 | 0x62 | 手机→耳机 |
| RET_STATUS | 0x63 | 0x63 | 耳机→手机 |
| SET_STATUS | 0x64 | — | 手机→耳机 (仅V2) |
| NTFY_STATUS | 0x65 | 0x65 | 耳机→手机 |
| GET_PARAM | 0x66 | 0x66 | 手机→耳机 |
| RET_PARAM | 0x67 | 0x67 | 耳机→手机 |
| SET_PARAM | 0x68 | 0x68 | 手机→耳机 |
| NTFY_PARAM | 0x69 | 0x69 | 耳机→手机 |

**InquiredType (第一个参数字节):**

| 值 | 含义 | 说明 |
|---|------|------|
| 0x01 | NC_ON_OFF | 降噪开关(基础) |
| 0x11 | NC_ON_OFF_AND_ASM_ON_OFF | 降噪+环境音开关 |
| 0x12 | NC_MODE_SWITCH_AND_ASM_ON_OFF | 降噪模式切换+环境音开关 |
| 0x21 | ASM_ON_OFF | 环境音开关 |
| 0x22 | ASM_SEAMLESS | 无缝环境音 |
| 0x30 | NC_AMB_TOGGLE | 降噪/环境音切换 |
| 0x40 | NC_TEST_MODE | 降噪测试模式 |

**关键枚举值:**

NcAsmMode: `0x00=NC(降噪)`, `0x01=ASM(环境音)`  
AmbientSoundMode: `0x00=NORMAL`, `0x01=VOICE(语音模式)`  
OnOffSettingValue: `0x00=ON`, `0x01=OFF` (注意：与常规布尔相反)  
NcValue: `0=OFF`, `1=ON_SINGLE`, `2=ON_DUAL`, `3=AUTO`  
ValueChangeStatus: `0=CHANGED`, `1=UNDER_CHANGING`

**消息示例:**

```
// 打开降噪
[0x64] [0x01] [0x00]

// 切换到环境音模式
[0x64] [0x12] [0x01]
```

### 2.2 自适应声音控制 (Adaptive Sound Control / Sense Engine)

**使用的GATT服务**: TANDEM_V2_HPC_SERVICE (5b833e20)  
**协议版本**: V2 Table1  
**命令范围**: 0x70-0x7B

| 命令 | 字节码 | 方向 |
|------|--------|------|
| SENSE_GET_CAPABILITY | 0x70 | 手机→耳机 |
| SENSE_RET_CAPABILITY | 0x71 | 耳机→手机 |
| SENSE_SET_STATUS | 0x74 | 手机→耳机 |
| SENSE_NTFY_STATUS | 0x75 | 耳机→手机 |
| SENSE_SET_PARAM | 0x78 | 手机→耳机 |
| SENSE_NTFY_PARAM | 0x79 | 耳机→手机 |
| SENSE_GET_EXT_INFO | 0x7A | 手机→耳机 |
| SENSE_RET_EXT_INFO | 0x7B | 耳机→手机 |

**SenseInquiredType:**

| 值 | 含义 |
|---|------|
| 0x00 | ADAPTIVE_CONTROL |
| 0x01 | ADAPTIVE_CONTROL_WITH_PARAMETER_NOTIFICATION |

**SenseSettingControl:** `0=START_SETTING`, `1=END_SETTING`

**Sense可控制的功能:** NC模式切换、ASM模式、EQ预设、智能通话模式

### 2.3 EQ均衡器 / 音效控制

**使用的GATT服务**: TANDEM_V2_HPC_SERVICE (5b833e20)  
**协议版本**: V2 Table1  
**命令范围**: 0x50-0x5B

| 命令 | 字节码 | 方向 |
|------|--------|------|
| EQEBB_GET_CAPABILITY | 0x50 | 手机→耳机 |
| EQEBB_RET_CAPABILITY | 0x51 | 耳机→手机 |
| EQEBB_GET_STATUS | 0x52 | 手机→耳机 |
| EQEBB_RET_STATUS | 0x53 | 耳机→手机 |
| EQEBB_NTFY_STATUS | 0x55 | 耳机→手机 |
| EQEBB_GET_PARAM | 0x56 | 手机→耳机 |
| EQEBB_RET_PARAM | 0x57 | 耳机→手机 |
| EQEBB_SET_PARAM | 0x58 | 手机→耳机 |
| EQEBB_NTFY_PARAM | 0x59 | 耳机→手机 |
| EQEBB_GET_EXTENDED_INFO | 0x5A | 手机→耳机 |
| EQEBB_RET_EXTENDED_INFO | 0x5B | 耳机→手机 |

**EqEbbInquiredType:**

| 值 | 含义 |
|---|------|
| 0x00 | PRESET_EQ (预设均衡器) |
| 0x01 | EBB (额外低音/Clear Bass) |
| 0x02 | PRESET_EQ_NONCUSTOMIZABLE |
| 0x03 | PRESET_EQ_AND_ULT_MODE |
| 0x30 | SOUND_EFFECT (音效) |
| 0x31 | CUSTOM_EQ (自定义均衡器) |
| 0x32 | TURN_KEY_EQ (一键式均衡器) |

**EqPresetId (均衡器预设):**

| 值 | 预设 | 值 | 预设 |
|---|------|---|------|
| 0x00 | OFF | 0x07 | ACOUSTIC |
| 0x01 | ROCK | 0xA0 | CUSTOM |
| 0x02 | POP | 0xA1-0xA5 | USER_SETTING1-5 |
| 0x03 | JAZZ | 0xB0-0xBB | ARTIST_COLLAB1-12 |
| 0x04 | DANCE | ... | Bright/Excited/Mellow/Relaxed/Vocal/Treble/Bass/Speech等 |
| 0x05 | EDM | ... | Gaming预设 |
| 0x06 | R&B/HIP_HOP | | |

**SoundEffectType (音效类型):**

| 值 | 含义 |
|---|------|
| 0 | SOUND_EFFECT_OFF |
| 1 | ULT |
| 2 | ULT1 |
| 3 | ULT2 |
| 4 | CUSTOM |
| 5 | FLAT |
| 6 | LIVE |

### 2.4 电量与电源管理

**使用的GATT服务**: TANDEM_V2_HPC_SERVICE (5b833e20)  
**协议版本**: V2 Table1 (V1使用COMMON命令)  
**命令范围**: 0x20-0x29 (V2), 0x13 (V1通知)

| 命令 | V2字节码 | 方向 |
|------|---------|------|
| POWER_GET_CAPABILITY | 0x20 | 手机→耳机 |
| POWER_RET_CAPABILITY | 0x21 | 耳机→手机 |
| POWER_GET_STATUS | 0x22 | 手机→耳机 |
| POWER_RET_STATUS | 0x23 | 耳机→手机 |
| POWER_SET_STATUS | 0x24 | 手机→耳机 |
| POWER_NTFY_STATUS | 0x25 | 耳机→手机 |
| POWER_GET_PARAM | 0x26 | 手机→耳机 |
| POWER_RET_PARAM | 0x27 | 耳机→手机 |
| POWER_SET_PARAM | 0x28 | 手机→耳机 |
| POWER_NTFY_PARAM | 0x29 | 耳机→手机 |

**PowerInquiredType:**

| 值 | 含义 |
|---|------|
| 0x00 | BATTERY (单电量) |
| 0x01 | LEFT_RIGHT_BATTERY (左右耳电量) |
| 0x02 | CRADLE_BATTERY (充电盒电量) |
| 0x03 | POWER_OFF (关机) |
| 0x04 | AUTO_POWER_OFF (自动关机) |
| 0x05 | AUTO_POWER_OFF_WEARING_DETECTION |
| 0x06 | POWER_SAVE_MODE (省电模式) |
| 0x07 | LINK_CONTROL |
| 0x0E | STAMINA (耐力模式) |

**AutoPowerOffElements (自动关机时间):**

| 值 | 时间 |
|---|------|
| 0 | 5分钟后 |
| 1 | 30分钟后 |
| 2 | 60分钟后 |
| 3 | 180分钟后 |
| 4 | 15分钟后 |
| 0x11 | 禁用自动关机 |

### 2.5 播放控制

**使用的GATT服务**: TANDEM_V2_HPC_SERVICE (5b833e20)  
**协议版本**: V2 Table1  
**命令范围**: 0xA0-0xA9

| 命令 | 字节码 | 方向 |
|------|--------|------|
| PLAY_GET_CAPABILITY | 0xA0 | 手机→耳机 |
| PLAY_RET_CAPABILITY | 0xA1 | 耳机→手机 |
| PLAY_GET_STATUS | 0xA2 | 手机→耳机 |
| PLAY_RET_STATUS | 0xA3 | 耳机→手机 |
| PLAY_SET_STATUS | 0xA4 | 手机→耳机 |
| PLAY_NTFY_STATUS | 0xA5 | 耳机→手机 |
| PLAY_GET_PARAM | 0xA6 | 手机→耳机 |
| PLAY_RET_PARAM | 0xA7 | 耳机→手机 |
| PLAY_SET_PARAM | 0xA8 | 手机→耳机 |
| PLAY_NTFY_PARAM | 0xA9 | 耳机→手机 |

**PlaybackControl (操作码):**

| 值 | 操作 | 值 | 操作 |
|---|------|---|------|
| 0x00 | KEY_OFF | 0x05 | GROUP_DOWN |
| 0x01 | PAUSE | 0x06 | STOP |
| 0x02 | TRACK_UP | 0x07 | PLAY |
| 0x03 | TRACK_DOWN | 0x08 | FAST_FORWARD |
| 0x04 | GROUP_UP | 0x09 | FAST_REWIND |

**PlayInquiredType:**

| 值 | 含义 |
|---|------|
| 0x01 | PLAYBACK_CONTROL_WITH_CALL_VOLUME_ADJUSTMENT |
| 0x20 | MUSIC_VOLUME (音乐音量) |
| 0x21 | CALL_VOLUME (通话音量) |
| 0x30 | MUSIC_VOLUME_WITH_MUTE |
| 0x31 | CALL_VOLUME_WITH_MUTE |
| 0x40 | PLAY_MODE |

### 2.6 佩戴检测与头部手势

**使用的GATT服务**: TANDEM_V2_HPC_SERVICE (5b833e20)  
**协议版本**: V2 Table1 (SYSTEM命令族)  
**命令范围**: 0xF0-0xFD

| 命令 | 字节码 | 方向 |
|------|--------|------|
| SYSTEM_GET_CAPABILITY | 0xF0 | 手机→耳机 |
| SYSTEM_RET_CAPABILITY | 0xF1 | 耳机→手机 |
| SYSTEM_GET_STATUS | 0xF2 | 手机→耳机 |
| SYSTEM_RET_STATUS | 0xF3 | 耳机→手机 |
| SYSTEM_SET_STATUS | 0xF4 | 手机→耳机 |
| SYSTEM_NTFY_STATUS | 0xF5 | 耳机→手机 |
| SYSTEM_GET_PARAM | 0xF6 | 手机→耳机 |
| SYSTEM_RET_PARAM | 0xF7 | 耳机→手机 |
| SYSTEM_SET_PARAM | 0xF8 | 手机→耳机 |
| SYSTEM_NTFY_PARAM | 0xF9 | 耳机→手机 |

**SystemInquiredType (与佩戴相关):**

| 值 | 含义 |
|---|------|
| 0x01 | PLAYBACK_CONTROL_BY_WEARING (佩戴播放控制) |
| 0x02 | SMART_TALKING_MODE_TYPE1 (智能通话模式) |
| 0x06 | WEARING_STATUS_DETECTOR (佩戴检测) |
| 0x07 | EARPIECE_SELECTION (耳塞选择) |
| 0x0A | AUTO_VOLUME (自动音量) |
| 0x0C | SMART_TALKING_MODE_TYPE2 |
| 0x0F | HEAD_GESTURE_ON_OFF (头部手势开关) |
| 0x10 | HEAD_GESTURE_TRAINING (头部手势训练) |

**耳塞适配检测相关枚举:**

- EarpieceSeries: 耳塞系列
- EarpieceSize: SS / S / M / L / LL
- EarpieceFittingDetectionOperationStatus: 操作状态
- EarpieceFittingDetectionOperationErrorCode: 错误码
- EarpieceFittingDetectionResult: 检测结果
- DetectSensitivity: 检测灵敏度

### 2.7 Auto Play / SAR (感官活动识别)

**使用的GATT服务**: TANDEM_V2_HPC_SERVICE (5b833e20)  
**协议版本**: V2 Table1  
**命令范围**: 0xB0-0xB9

| 命令 | 字节码 | 方向 |
|------|--------|------|
| SAR_AUTO_PLAY_GET_CAPABILITY | 0xB0 | 手机→耳机 |
| SAR_AUTO_PLAY_RET_CAPABILITY | 0xB1 | 耳机→手机 |
| SAR_AUTO_PLAY_GET_STATUS | 0xB2 | 手机→耳机 |
| SAR_AUTO_PLAY_RET_STATUS | 0xB3 | 耳机→手机 |
| SAR_AUTO_PLAY_NTFY_STATUS | 0xB5 | 耳机→手机 |
| SAR_AUTO_PLAY_GET_PARAM | 0xB6 | 手机→耳机 |
| SAR_AUTO_PLAY_RET_PARAM | 0xB7 | 耳机→手机 |
| SAR_AUTO_PLAY_SET_PARAM | 0xB8 | 手机→耳机 |
| SAR_AUTO_PLAY_NTFY_PARAM | 0xB9 | 耳机→手机 |

**SARAutoPlayInquiredType:**

| 值 | 含义 |
|---|------|
| 0x00 | SAR (感官活动和识别) |
| 0x01 | AUTO_PLAY (自动播放) |
| 0x02 | INTEGRATED_AUTO_PLAY (集成自动播放) |
| 0x0F | GATT_CONNECTABLE (GATT可连接) |
| 0x20 | SAR_OPTIMIZATION_COMPASS_ACCEL_TYPE |
| 0x21 | SAR_OPTIMIZATION_ACCEL_TYPE |

**AutoPlay 应用层命令:**

**PlaybackCommand:**
| 值 | 命令 |
|---|------|
| 0x01 | PLAY_ONESHOT |
| 0x02 | PLAY |
| 0x03 | PREPARE |
| 0x05 | FINISH |
| 0x06 | STOP |

**MediationCommand:**
| 值 | 命令 |
|---|------|
| 0x01 | REQUEST_PLAY |
| 0x02 | REQUEST_PLAY_RESPONSE |
| 0x03 | REQUEST_STATUS |
| 0x04 | STATUS_UPDATE |
| 0x05 | FINISH_PLAY |
| 0x06 | STOP |
| 0x07 | SESSION_TIMEOUT |
| 0x08 | BATCH_RESET_EVENT |

**AppLinkageType:**
| 值 | 类型 |
|---|------|
| 0x01 | AUTO_TRIGGER (头部手势自动触发) |
| 0x02 | MEDIATION (应用调解模式) |

**设备状态枚举:**

| 枚举 | 值 |
|------|-----|
| HpActiveStatus | INACTIVE / ACTIVE |
| HpWearStatus | ON / OFF |
| HpBusyStatus | NOT_BUSY / BUSY |
| HpInteractionMode | NORMAL / INTERACTION / INTERACTION_YES_ONLY / INTERACTION_NO_ONLY |
| HpInteractionType | POSITIVE / NEGATIVE |
| HpMultipointStatus | DISABLED / ENABLED_IF_SUPPORTED |

### 2.8 快速访问 (Quick Access)

**使用的GATT服务**: TANDEM_V2_HPC_SERVICE (5b833e20)  
**协议版本**: V2 Table1 (通过SYSTEM命令族)  
**SystemInquiredType**: 0x0D = QUICK_ACCESS

**QuickAccessKey (按键):**

| 值 | 按键 |
|---|------|
| 0x00 | L_R_KEY (左/右键) |
| 0x01 | NC_AMB_KEY (降噪/环境音键) |
| 0x02 | FIXED_QUICK_ACCESS_KEY (固定快速访问键) |

**可分配功能 (Function):**

| 值 | 功能 | 值 | 功能 |
|---|------|---|------|
| 0x00 | NO_FUNCTION | 0x20 | PLAY_PAUSE |
| 0x01 | NC_ASM_OFF | 0x21 | NEXT_TRACK |
| 0x02 | NC_ASM | 0x22 | PREV_TRACK |
| 0x03 | NC_OFF | 0x23 | VOLUME_UP |
| 0x04 | ASM_OFF | 0x24 | VOLUME_DOWN |
| 0x05 | QUICK_ATTENTION | 0x30 | VOICE_RECOGNITION |
| 0x06 | NC_OPTIMIZER | 0x40 | LAUNCH_MLP |
| | | 0x42 | SPTF_ONE_TOUCH (Spotify) |
| | | 0x70 | MIC_MUTE |

### 2.9 固件升级 (FOTA)

**使用的GATT服务**: TANDEM_V2_HPC_SERVICE (5b833e20) + BLANC_FOTA_SERVICE (5b833e30)  
**协议版本**: V2 Table1  
**命令范围**: 0x30-0x3F

| 命令 | 字节码 | 方向 |
|------|--------|------|
| UPDT_GET_CAPABILITY | 0x30 | 手机→耳机 |
| UPDT_RET_CAPABILITY | 0x31 | 耳机→手机 |
| UPDT_GET_STATUS | 0x32 | 手机→耳机 |
| UPDT_RET_STATUS | 0x33 | 耳机→手机 |
| UPDT_SET_STATUS | 0x34 | 手机→耳机 |
| UPDT_NTFY_STATUS | 0x35 | 耳机→手机 |
| UPDT_GET_PARAM | 0x36 | 手机→耳机 |
| UPDT_RET_PARAM | 0x37 | 耳机→手机 |
| UPDT_SET_PARAM | 0x38 | 手机→耳机 |
| UPDT_NTFY_PARAM | 0x39 | 耳机→手机 |
| UPDT_TRANSFER_DATA | 0x3E | 手机→耳机 (数据块) |
| UPDT_NTFY_MESSAGE | 0x3F | 耳机→手机 (升级消息) |

**TandemFotaCommand (升级控制):**

| 值 | 命令 |
|---|------|
| 0x01 | ENTER_FW_UPDATE_MODE |
| 0x02 | EXIT_FW_UPDATE_MODE |
| 0x03 | START_TRANSFER |
| 0x04 | FINISH_TRANSFER |
| 0x05 | CANCEL_TRANSFER |
| 0x06 | EXECUTE_FW_UPDATE |

**TandemFotaStatus (升级状态):**

| 值 | 状态 |
|---|------|
| 0 | INVALID |
| 1 | IDLE |
| 2 | NOT_READY |
| 3 | DATA_RECEIVING |
| 4 | UPDATING |

**固件升级能力类型 (UpdateCapability):**

| 类型 | 说明 |
|------|------|
| CSR | CSR芯片升级 |
| MTK_RHO_W_DISCONNECTION | 联发科断线升级 |
| MTK_TRANSFER_WO_DISCONNECTION | 联发科不断线传输 |
| TANDEM | Tandem协议升级 (左右耳同步) |
| USING_MC_APP | 使用MC应用升级 |
| NOT_SUPPORTED | 不支持 |

### 2.10 多点连接 (Multipoint)

**使用的GATT服务**: TANDEM_V2_MC_SERVICE (5b833e21)  
**协议版本**: V2 Table2 或 V1 Table2  
**命令范围**: 0x30-0x3D (PERIPHERAL命令族)

| 命令 | V2字节码 | V1字节码 | 方向 |
|------|---------|---------|------|
| PERI_GET_CAPABILITY | 0x30 | 0x30 | 手机→耳机 |
| PERI_RET_CAPABILITY | 0x31 | 0x31 | 耳机→手机 |
| PERI_GET_STATUS | 0x32 | 0x32 | 手机→耳机 |
| PERI_RET_STATUS | 0x33 | 0x33 | 耳机→手机 |
| PERI_SET_STATUS | 0x34 | 0x34 | 手机→耳机 |
| PERI_NTFY_STATUS | 0x35 | 0x35 | 耳机→手机 |
| PERI_GET_PARAM | 0x36 | 0x36 | 手机→耳机 |
| PERI_RET_PARAM | 0x37 | 0x37 | 耳机→手机 |
| PERI_SET_PARAM | 0x38 | — | 手机→耳机 (仅V2) |
| PERI_NTFY_PARAM | 0x39 | 0x39 | 耳机→手机 |
| PERI_SET_EXTENDED_PARAM | 0x3C | 0x3C | 手机→耳机 |
| PERI_NTFY_EXTENDED_PARAM | 0x3D | 0x3D | 耳机→手机 |

### 2.11 连接/设备信息

**使用的GATT服务**: TANDEM_V2_HPC_SERVICE (5b833e20)  
**协议版本**: V2 Table1 (CONNECT命令族)  
**命令范围**: 0x00-0x07

| 命令 | 字节码 | 方向 |
|------|--------|------|
| CONNECT_GET_PROTOCOL_INFO | 0x00 | 手机→耳机 |
| CONNECT_RET_PROTOCOL_INFO | 0x01 | 耳机→手机 |
| CONNECT_GET_CAPABILITY | 0x02 | 手机→耳机 |
| CONNECT_RET_CAPABILITY | 0x03 | 耳机→手机 |
| CONNECT_GET_DEVICE_INFO | 0x04 | 手机→耳机 |
| CONNECT_RET_DEVICE_INFO | 0x05 | 耳机→手机 |
| CONNECT_GET_SUPPORT_FUNCTION | 0x06 | 手机→耳机 |
| CONNECT_RET_SUPPORT_FUNCTION | 0x07 | 耳机→手机 |

**DeviceInfoType:**

| 值 | 查询内容 |
|---|---------|
| 1 | MODEL_NAME (型号名称) |
| 2 | FW_VERSION (固件版本) |
| 3 | SERIES_AND_COLOR_INFO (系列和颜色) |
| 4 | INSTRUCTION_GUIDE (使用指南) |

### 2.12 音频编解码器

**使用的GATT服务**: TANDEM_V2_HPC_SERVICE (5b833e20)  
**协议版本**: V2 Table1 (AUDIO/COMMON命令族)

**AudioCodec:**

| 值 | 编解码器 |
|---|---------|
| 0 | UNSETTLED |
| 1 | SBC |
| 2 | AAC |
| 3 | LDAC |
| 32 | APT_X |
| 33 | APT_X_HD |
| 48 | LC3 (LE Audio) |

**UpscalingType (音频增强):**

| 值 | 类型 |
|---|------|
| 0 | DSEE_HX |
| 1 | DSEE |
| 2 | DSEE_HX_AI |
| 3 | DSEE_ULTIMATE |

### 2.13 V2 Table2 其他功能

| 功能 | 命令范围 | 说明 |
|------|---------|------|
| VOICE_GUIDANCE | 0x40-0x49 | 语音提示设置 |
| SAFE_LISTENING | 0x50-0x5B | 安全聆听功能 |
| LEA (LE Audio) | 0x60-0x69 | LE Audio管理 |
| PARTY | 0x70-0x79 | 派对连接模式 |
| SYSTEM (Table2) | 0xF0-0xFD | 系统扩展参数 |

---

## 三、设备型号与协议版本对应

Sony耳机按系列区分，不同系列支持不同的协议版本：

| 系列 | 持久化ID | V1支持 | V2支持 | 代表型号 |
|------|---------|--------|--------|---------|
| EXTRA_BASS | 16 | ✓ | ✓ | WH-XB系列 |
| ULT_POWER_SOUND | 17 | ✗ | ✓ | WH-ULT系列 |
| HEAR | 32 | ✓ | ✓ | WH-H系列 |
| PREMIUM | 48 | ✓ | ✓ | WH-1000X系列 |
| SPORTS | 64 | ✓ | ✓ | WF-SP系列 |
| CASUAL | 80 | ✓ | ✓ | WH-CH系列 |
| LINK_BUDS | 96 | ✗ | ✓ | LinkBuds系列 |
| NECKBAND | 112 | ✗ | ✓ | WI系列 |

**ModelId (耳机子型号):**

| 范围 | 型号组 | 说明 |
|------|--------|------|
| 0x30 | MDR_TEST | 测试 |
| 0x31 | MDR_WH | 头戴式WH系列 |
| 0x32 | MDR_WF | 真无线WF系列 |
| 0x33 | MDR_WI | 颈挂式WI系列 |
| 0x34 | MDR_WF_G | WF Gaming |
| 0x35 | MDR_WH_G | WH Gaming |

---

## 四、V1与V2协议差异

| 特性 | V1 (p063v1) | V2 (p064v2) |
|------|------------|------------|
| NC/ASM模式 | 简单 (ON_OFF, LEVEL_ADJUSTMENT, DUAL_SINGLE_OFF) | 扩展 (DUAL, SINGLE, AUTO, NCSS, Noise Adaptation, Test Mode) |
| 电源管理 | 通过COMMON命令, 功能有限 | 独立POWER命令族, 支持省电/待机/保养充电/STAMINA |
| LE Audio | 不支持 | 完整支持 |
| 安全聆听 | 不支持 | 支持 (Table2) |
| 派对连接 | 不支持 | 支持 (Table2) |
| SAR自动播放 | 不支持(使用SPORTS) | 支持 |
| 命令-类映射 | 在Command枚举中直接映射 | 通过工厂switch语句间接映射 |
| 字符串编码 | 无专门类型 | C12413b长度前缀编码 |
| FunctionType | ~46个条目 | ~180+条目 |
| SET_STATUS (NCASM) | 不支持 | 支持(0x64) |

---

## 五、协议可提取性评估

### 5.1 可提取程度

逆向工程结果中的协议代码**非常完整**，包含：

1. **完整的GATT层定义**: 26个服务和88个特征的UUID、读写方向、关联关系全部明确
2. **完整的消息格式**: 每个命令的字节码、参数枚举值、序列化/反序列化逻辑都可从反编译代码中恢复
3. **完整的枚举值**: 所有参数类型（InquiredType、预设ID、状态码等）的枚举值都已提取
4. **完整的状态机**: 连接握手、协议启动、错误处理流程清晰
5. **两个协议版本**: V1和V2的差异、兼容关系明确

### 5.2 可提取的代码模块

以下模块可以提取并重写为独立代码：

| 模块 | 提取难度 | 说明 |
|------|---------|------|
| GATT服务/特征定义 | ★☆☆ (容易) | 纯UUID常量定义 |
| 协议枚举值 | ★☆☆ (容易) | 纯常量枚举 |
| 消息序列化/反序列化 | ★★☆ (中等) | 字节级编解码逻辑 |
| BLE连接管理 | ★★★ (较难) | 涉及Android BLE API + Sony握手协议 |
| 协议状态机 | ★★★ (较难) | 涉及多个状态和错误处理 |
| AutoPlay传感器管理 | ★★★★ (困难) | 涉及头部姿态传感器数据融合 |
| 固件升级 | ★★★★ (困难) | 涉及多芯片类型(CSR/MTK/Tandem) |

### 5.3 重写可行性结论

**完全可以提取协议代码并重写一个功能相同的APP。** 理由如下：

1. **协议是字节级通信协议**：不依赖Sony的服务器或云服务，所有命令都是手机与耳机之间的BLE通信，协议完全本地化
2. **逆向结果极完整**：所有88个GATT特征、所有命令字节码、所有参数枚举值都已提取
3. **两个协议版本可覆盖所有设备**：V1覆盖旧型号，V2覆盖新型号
4. **Android BLE API是标准API**：不需要Sony的专有SDK
5. **从零重写无版权风险**：使用逆向工程理解协议规则后，用新代码实现通信是合法的（不复制原代码）

### 5.4 建议的实现路线

1. **Phase 1 - 协议核心库**：提取GATT UUID定义、协议枚举、消息编解码器
2. **Phase 2 - BLE连接管理**：实现扫描、连接、服务发现、MTU协商
3. **Phase 3 - 功能模块**：逐个实现NC/ASM、EQ、电量、播放控制等
4. **Phase 4 - 高级功能**：Sense自适应控制、AutoPlay行为识别
5. **Phase 5 - UI层**：实现用户界面

---

## 六、协议安全性说明

Sony耳机BLE协议**未发现加密层的迹象**。所有的Tandem Family协议消息都以明文字节形式通过GATT特征传输。这意味着：

- 协议消息可以被BLE嗅探器捕获和解析
- 没有应用层加密或认证机制
- 任何能够建立BLE连接并写入GATT特征的设备都可以控制耳机功能

这与Sony的SSH (Sony Surround Hub) 服务不同，后者有完整的设备注册、RSA密钥交换和SEEDS签名机制（用于电视/音箱系统）。

---

## 附录: 关键源文件索引

### BLE GATT层

| 文件 | 内容 |
|------|------|
| `com/sony/songpal/ble/client/ServiceUuid.java` | 26个GATT服务UUID定义 |
| `com/sony/songpal/ble/client/CharacteristicUuid.java` | 88个GATT特征UUID定义 |
| `com/sony/songpal/ble/client/GattConnectionTransport.java` | BR_EDR/LE传输类型 |
| `com/sony/songpal/ble/client/GattError.java` | GATT错误码 |
| `mg/AbstractC20713e.java` | 特征序列化基类 |

### 协议消息层

| 目录 | 内容 |
|------|------|
| `tandemfamily/message/mdr/p063v1/table1/` | V1 Table1协议定义 |
| `tandemfamily/message/mdr/p063v1/table2/` | V1 Table2协议定义 |
| `tandemfamily/message/mdr/p064v2/table1/` | V2 Table1协议定义及所有功能参数 |
| `tandemfamily/message/mdr/p064v2/table2/` | V2 Table2协议定义 |
| `tandemfamily/message/mdr/p064v2/table1/ncasm/param/` | NC/ASM参数枚举 |
| `tandemfamily/message/mdr/p064v2/table1/eqebb/param/` | EQ/EBB参数枚举 |
| `tandemfamily/message/mdr/p064v2/table1/power/param/` | 电量参数枚举 |
| `tandemfamily/message/mdr/p064v2/table1/playback/param/` | 播放控制参数 |
| `tandemfamily/message/mdr/p064v2/table1/sense/param/` | Sense参数 |
| `tandemfamily/message/mdr/p064v2/table1/sarautoplay/param/` | SAR自动播放参数 |
| `tandemfamily/message/mdr/p064v2/table1/system/param/` | 系统功能参数 |
| `tandemfamily/message/mdr/p064v2/table1/updt/param/` | 固件升级参数 |

### BLE Central参数

| 目录 | 内容 |
|------|------|
| `ble/central/param/audio/` | 音频参数(流类型、型号、传输线) |
| `ble/central/param/device/` | 设备参数(固件更新) |
| `ble/central/param/lighting/` | 灯光控制参数 |
| `ble/central/param/option/` | BLE选项(角色、链路状态) |

### AutoPlay应用层

| 文件 | 内容 |
|------|------|
| `jp/co/sony/hes/autoplay/core/ble/BleConnectionManagerImpl.java` | 主BLE连接管理器 |
| `jp/co/sony/hes/autoplay/core/ble/AutoPlayConversionsKt.java` | 协议层转换 |
| `jp/co/sony/hes/autoplay/core/bleprotocol/command/` | AutoPlay命令定义 |
| `jp/co/sony/hes/autoplay/core/bleprotocol/devicestatus/` | 设备状态枚举 |
| `jp/co/sony/hes/autoplay/core/bleprotocol/quickaccess/` | 快速访问管理 |

### j2objc桥接层

| 目录 | 内容 |
|------|------|
| `mdr/j2objc/tandem/features/ncasm/` | NC/ASM V1/V2桥接 |
| `mdr/j2objc/tandem/features/battery/` | 电量V1/V2桥接 |
| `mdr/j2objc/tandem/features/quickaccess/` | 快速访问桥接 |
| `mdr/j2objc/tandem/features/multipoint/` | 多点连接桥接 |
| `mdr/j2objc/tandem/features/sense/` | Sense桥接 |
| `mdr/j2objc/tandem/features/fwupdate/` | 固件升级桥接 |
| `mdr/j2objc/tandem/MdlSeries.java` | 型号系列V1/V2映射 |
