# MiLink MiTWS First-Party Equivalence Checklist

Updated: 2026-06-04

This document evaluates whether the current OpenBuds MiLink hook path is already equivalent to a real first-party MiTWS device from the perspective of `com.milink.service`.

Design, sequencing, and implementation direction now live in:

- [MILINK_FIRST_PARTY_ADAPTER_PLAN_V2.md](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/docs/plan/MILINK_FIRST_PARTY_ADAPTER_PLAN_V2.md)
- [MILINK_MITWS_IMPLEMENTATION_CHECKLIST.md](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/docs/target/MILINK_MITWS_IMPLEMENTATION_CHECKLIST.md)

This document should stay focused on:

- evaluation criteria
- current status
- strict equivalence verdict

Evaluation scale:

- `Satisfied`
- `Mostly satisfied`
- `Partially satisfied`
- `Not satisfied`

The standard here is strict:

> MiLink should not just render the first-party headset UI.  
> It should be unable to distinguish the OpenBuds device from a real first-party MiTWS device across classification, runtime state, query surface, callback surface, and control surface.

## Current scope

Current mainline is the MiTWS facade path inside `com.milink.service`.  
This checklist evaluates that path as currently implemented, without repeating
the design rationale already consolidated in the main V2 plan.

Current validated process roles:

- `com.milink.service:ui` — card/detail UI-facing process, must keep `bridge + facade`
- `com.milink.service:core` — headset runtime/session process, must keep `bridge + facade`
- other `com.milink.service*` subprocesses are currently excluded from the MiTWS
  mainline path unless later evidence proves they are required

## A. Device classification and entry conditions

### A1. `checkIsMiTWS(BluetoothDevice)` returns first-party result

- Status: `Satisfied`
- Current implementation:
  - `MilinkMiTwsFacadeHook.hookCheckIsMiTws()`
  - For bridge-authorized devices, final result is forced to `1`
- Why it matters:
  - `BluetoothServiceClient.isMiHeadset()` depends on `MxBluetoothManager.checkIsMiTWS(device) == 1`
  - This is the gate for `devicesType = HEADSET`
- Conclusion:
  - The primary first-party classification gate is already replaced.

### A2. Device is routed into the `HEADSET` device type path

- Status: `Mostly satisfied`
- Current behavior:
  - Because `checkIsMiTWS()` is forced true, `BluetoothServiceClient.getDeviceType()` resolves `HEADSET`
  - MiLink then treats the Bluetooth device as a headset device instead of a generic third-party device
- Remaining caveat:
  - This still depends on current MiLink internal logic preserving the same classification chain
- Conclusion:
  - Good enough for current builds, but tied to MiLink implementation stability.

### A3. Stable MiTWS `deviceId` is supplied

- Status: `Satisfied`
- Current implementation:
  - `MilinkMiTwsFacadeHook.hookGetDeviceId()`
  - Device ID template comes from `MiTwsDeviceIdPolicy`
- Current templates:
  - `01010101` (default generic earbud)
  - `01013201` (Flora experimental)
  - `020104005A` (O73 Flora experimental)
- Why it matters:
  - MiLink uses `deviceId` for headset subtype, capability profile, and multiple downstream branches
- Conclusion:
  - Stable `deviceId` replacement exists.
  - This does not yet prove template parity with a real target first-party model.

## B. MiTWS runtime session and MMA semantics

### B1. `connectMma()` behaves as a successful first-party connection

- Status: `Satisfied`
- Current implementation:
  - `MilinkMiTwsFacadeHook.hookMmaConnection(..., "connectMma")`
  - Returns facade success and dispatches `onConnectMmaStateChanged(true)`
- Why it matters:
  - Prevents MiLink from falling into the real Xiaomi MMA stack, which would fail for OpenBuds devices
- Conclusion:
  - Sufficient for current first-party runtime entry.

### B2. `disconnectMma()` is semantically equivalent to real first-party behavior

- Status: `Partially satisfied`
- Current implementation:
  - `disconnectMma()` is intercepted
  - The hook intentionally avoids dispatching repeated `false` callbacks because MiLink polls this path aggressively
- Why this matters:
  - Current behavior is engineered for UI stability, not for full session-semantic parity
- Conclusion:
  - Works pragmatically, but not as a fully faithful MiTWS session model.
  - Bridge loss or history-only authorization should not keep OpenBuds projected
    as a live active headset. Only classification stickiness is retained.

### B3. MiTWS callback registration lifecycle is replaced

- Status: `Satisfied`
- Current implementation:
  - `hookRegisterCallback()`
  - `hookUnregisterCallback()`
  - `MiTwsCallbackPump` captures and drives callback instances
- Conclusion:
  - The core callback surface used by MiLink is actively replaced.

### B4. Process scoping is narrowed to the two proven critical processes

- Status: `Satisfied`
- Current implementation:
  - facade installation is limited to `com.milink.service:ui` and
    `com.milink.service:core`
  - bridge startup is also enabled for those two critical processes
- Why it matters:
  - broad multi-process injection caused state competition and card instability
  - excluding non-critical processes reduced high-frequency card disappearance
    and style jitter
- Remaining caveat:
  - `:core` still shows high-frequency `disconnectMma` polling and remains the
    main suspect for low-frequency residual jitter

## C. State surface consumed by MiLink headset UI

### C1. Battery state

- Status: `Mostly satisfied`
- Current implementation:
  - `hookBatteryLevel()`
  - `MiTwsCallbackPump -> onBatteryLevel(int[])`
  - `MiTwsStateMapper.batteryArray()`
  - `MiTwsStateMapper.headsetInfoPowers()` now projects the 6-slot first-party
    power list needed by `HeadsetInfo` / `HeadSetsDetail`
- MiLink dependency:
  - `HeadSetsDetail` repeatedly consumes `C4737b0.m19868A()`
- Conclusion:
  - Battery is covered for current UI paths, including the 6-slot first-party
    `HeadsetInfo.powers` shape.
  - Residual risk remains around device-type-specific card sections rather than
    raw battery transport.

### C2. ANC current state

- Status: `Satisfied`
- Current implementation:
  - `hookAncState()`
  - `MiTwsCallbackPump -> onAncStateChanged()` and `onReportAncState()`
  - `MiTwsStateMapper.ancState()`
- Conclusion:
  - Current ANC state display path is covered.

### C3. Wear status

- Status: `Mostly satisfied`
- Current implementation:
  - `hookWearStatus()`
  - `MiTwsStateMapper.wearStatus()`
- Important caveat:
  - The mapper intentionally collapses some wearing states to `"1"` to satisfy MiLink ANC gating behavior
- Conclusion:
  - Good enough for functional gating.
  - Not a full one-to-one semantic reproduction of first-party wear-state values.

### C4. Device ID update callback

- Status: `Satisfied`
- Current implementation:
  - `MiTwsCallbackPump -> onDeviceIdUpdate()`
- Conclusion:
  - MiLink runtime can keep its local model in sync with the replaced `deviceId`.

### C5. `AncBatteryModel` lifecycle

- Status: `Satisfied`
- Current implementation:
  - `MiTwsCallbackPump.ensureAncBatteryModel()`
  - Forces creation of `ancBatteryModel`
  - Also sets `pendingConnectMmaAddress`
- Why it matters:
  - Without this, MiLink may receive state values but still fail to build the runtime model used by the headset page
- Conclusion:
  - This is one of the most important compensating hooks in the current solution.

### C6. Volume state

- Status: `Satisfied`
- MiLink dependency:
  - `HeadSetsDetail` reads `C4737b0.m19873G()`
  - Controller ultimately reads `HeadsetDeviceInfo.headsetVolume`
- Current implementation:
  - `hookProfileContextVolume()` returns `currentVolume` from active snapshot
  - `MilinkBridgeSnapshotMapper.fromUiState()` maps `volumeState.musicVolume` → `currentVolume`
  - `supportsVolumeControl` from `profile.supports(HeadphoneFeature.VOLUME)`
  - Bridge `SetVolume` command forwarded through `installVolumeControlHook()` → `MilinkBridgeClient` → `repository.setVolume()`
- Conclusion:
  - Volume state and control surface are fully replaced for OpenBuds targets.

### C7. Audio effect state

- Status: `Satisfied`
- MiLink dependency:
  - `HeadSetsDetail` checks `HeadsetDeviceInfo.audioEffectState`
  - Controller exposes `updateHeadsetAudioEffect(...)`
- Current implementation:
  - `hookProfileContextAudioEffect()` returns `snapshot.currentAudioEffectState` when `supportsAudioEffect`
  - `MilinkBridgeSnapshotMapper.fromUiState()` maps `state.audioEffectState.enabled` → `currentAudioEffectState` (0/1)
  - `supportsAudioEffect` from `profile.supports(HeadphoneFeature.AUDIO_EFFECT)`
  - Bridge `SetAudioEffect` forwarded through `installAudioEffectControlHook()` → `MilinkBridgeClient` → `repository.setAudioEffect()`
- Brand backing:
  - Sony: DSEE upscaling via AUDIO_PARAM (0xE6-0xE9) with `AudioInquiredType.UPSCALING(0x01)`
  - QCY: `CMDID_SPACE_AUDIO(45)` toggle
  - Profiles: LinkBuds S, WF-1000XM5, WH-1000XM4, QCY C30S
- Conclusion:
  - Audio effect state and control surface are fully replaced for OpenBuds targets.

### C8. Ring / find earbud state

- Status: `Not satisfied`
- Current implementation:
  - `MilinkDeviceSnapshot.supportsRing = false` (framework stub)
  - `HeadphoneFeature.RING` enum + adapter interface stubs exist for future protocol discovery
  - Neither Sony Tandem (v13.0.5) nor QCY protocol exposes a BLE ring/find command
  - Both OEM "find earbuds" features are phone-side (GPS + phone speaker alarm)
  - Callback pump can emit `onRingStateChanged`, but only if a snapshot supports ring
- MiLink dependency:
  - `HeadSetsDetail` ring card uses first-party ring control paths through `C6451o0`
- Conclusion:
  - This remains a missing capability. Framework ready when/if BLE protocol support is discovered.

## D. Query surface used by MiLink

### D1. `getSupportAncMode(...)`

- Status: `Mostly satisfied`
- MiLink native path:
  - `C4737b0.m19891p() -> Query.getSupportAncMode(targetAddress, vidPid)`
  - `QueryLocal` largely resolves this by `deviceId`
- Current implementation:
  - A dedicated `getSupportAncMode` replacement now exists for live OpenBuds targets
  - Latest local adjustments are aimed at keeping three-state ANC devices on the first-party three-state branch
- Conclusion:
  - Functionally much closer to first-party behavior
  - Still needs device-side confirmation for three-state ANC models

### D2. `isMmaHeadset(...)`

- Status: `Mostly satisfied`
- MiLink native path:
  - `C4737b0.m19861q() -> Query.isMmaHeadset(...)`
  - `QueryLocal` depends on multipoint host structures and support-control state
- Current implementation:
  - A dedicated `isMmaHeadset` replacement now exists for live active OpenBuds targets
- Conclusion:
  - Native dependence is reduced substantially
  - Still depends on active/live runtime matching rather than full model parity

### D3. `getBondStateWithTargetHost(...)`

- Status: `Partially satisfied`
- Current implementation:
  - A dedicated replacement now exists for live OpenBuds targets
- Conclusion:
  - Local active-host flows are now covered
  - Remote-host and broader multipoint semantics still need more validation

### D4. `switchToHeadsetActivity(...)`

- Status: `Partially satisfied`
- Current behavior:
  - "More settings" entry exists through native path
  - Current solution does not replace the full query semantics behind it
- Conclusion:
  - Entry is available, equivalence is not guaranteed.

## E. Control surface

### E1. ANC mode control

- Status: `Satisfied`
- Current implementation:
  - `hookAncControl()` for:
    - `openAnc`
    - `openTransparent`
    - `closeAnc`
  - `hook ProfileImpl.updateHeadsetMode(...)`
  - `MiTwsControlMapper` maps these to bridge `SetNoiseControl`
  - `MilinkBridgeService.executeAcceptedCommand()` currently handles only this command type
- Conclusion:
  - ANC control is the most complete control surface in the current solution.

### E2. Volume control

- Status: `Satisfied`
- MiLink native path:
  - `C4737b0.m19865u() -> updateHeadsetVolume(...)`
- Current implementation:
  - `installVolumeControlHook()` hooks `ProfileImpl.updateHeadsetVolume`
  - Forwards to bridge `COMMAND_SET_VOLUME` → `repository.setVolume()`
  - Optimistic UI update while protocol proceeds async
- Conclusion:
  - Volume control is fully bridged.

### E3. Audio effect / spatial audio control

- Status: `Satisfied`
- MiLink native path:
  - `C4737b0.m19863s() -> updateHeadsetAudioEffect(...)`
- Current implementation:
  - `installAudioEffectControlHook()` hooks `ProfileImpl.updateHeadsetAudioEffect` and forwards to bridge `COMMAND_SET_AUDIO_EFFECT`
  - `MilinkBridgeService.executeAcceptedCommand()` → `repository.setAudioEffect()`
  - Optional optimistic UI update
- Conclusion:
  - Audio effect control is fully bridged.

### E4. Ring / find earbud control

- Status: `Not satisfied`
- MiLink native path:
  - `C6451o0` uses:
    - `C4737b0.m19893y(...)`
    - `C4737b0.m19886c0(...)`
    - `C4737b0.m19887d0(...)`
    - `C4737b0.m19890m(...)`
- Current implementation:
  - No ring bridge command (framework stub only)
  - No ring state source (framework stub only)
  - Neither Sony Tandem nor QCY protocol has a BLE ring/find command
- Conclusion:
  - Clear gap. Framework ready for future protocol discovery.

### E5. Deep first-party settings surface

- Status: `Not satisfied`
- Current implementation:
  - Current solution enables entry into the native setting jump path
  - It does not replace the deeper first-party control semantics behind it
- Conclusion:
  - Outside current equivalence boundary.

## F. Data-model and runtime-graph equivalence

### F1. `HeadsetDeviceInfo` parity

- Status: `Mostly satisfied`
- Observation:
  - MiLink UI often reads `HeadsetDeviceInfo` indirectly through `C4734a` and `C4737b0`
  - Current solution does not rebuild the full first-party `HeadsetDeviceInfo` generation chain
  - It replaces enough runtime state and callback behavior to satisfy current headset page flows
  - `HeadsetInfo.powers` now matches MiLink's 6-slot runtime expectation and
    `deviceType` now follows OpenBuds `formFactor` instead of a single hardcoded value
- Conclusion:
  - Core fields are effectively covered where needed
  - Full model parity is not achieved
  - Unsupported volume/audio-effect fields should not be filled by synthetic
    runtime defaults when OpenBuds has no real backing state.

### F2. Full `RemoteProtocol / QueryLocal / Profile / Registry` equivalence

- Status: `Not satisfied`
- Current architecture:
  - The solution replaces selected facade points
  - It does not fully replace MiLink's complete first-party remote protocol stack
- Conclusion:
  - This is a facade-based compatibility solution, not a full first-party protocol emulation.

## Final verdict

### What is already true

Current hooks are sufficient to:

- classify OpenBuds devices as first-party MiTWS headsets
- drive MiLink into the native headset UI path
- populate battery state
- populate ANC state
- provide wear-state gating
- maintain MiTWS callback flow
- support ANC switching through MiLink UI

### What is not yet true

Current hooks are **not** sufficient to claim:

- full first-party MiTWS capability parity
- full query-surface parity
- full control-surface parity
- full runtime-model parity
- complete indistinguishability from a real Xiaomi first-party MiTWS device

### Strict conclusion

The current implementation is:

> a strong first-party MiTWS facade for MiLink's current headset UI and ANC paths

It is **not yet**:

> a full first-party MiTWS-equivalent replacement across all MiLink logic surfaces

Operationally, as of 2026-06-04:

- card disappearance caused by wrong process scoping has been mitigated
- heavy card jitter has been reduced substantially
- low-frequency residual card shrink / ANC no-op behavior still remains and is
  most strongly associated with `:core` runtime polling, especially
  `disconnectMma`

## Highest-priority equivalence gaps

1. Ring / find-earbud state and command surface (framework stub — no BLE protocol exists in either Sony or QCY reference)
2. Deep first-party settings surface (`switchToHeadsetActivity` not replaced)
3. Full `RemoteProtocol / QueryLocal / Profile / Registry` equivalence (facade-based, not protocol emulation)

### 2026-06-05 Phase 4 additions

- **Multi-device snapshot retention**: `MilinkBridgeService` no longer clears all snapshots on each state update. Disconnected devices retain snapshots for 5 min (SNAPSHOT_STALE_MS), giving MiLink a multi-device appearance without touching the single-connection transport layer.
- **`DeviceCapabilityRegistry`**: Static per-model capability lookup (`supports(modelName, feature)`). Maps 4 device profiles. Enables capability queries without a live connection.
- **4 new MiLink capability flags**: `supportsEq`, `supportsLeaStatus`, `supportsQuickAccess`, `supportsAmbientLevel` added to snapshot and contract.

For the implementation order and structural fix strategy, use the V2 main plan
and target implementation checklist instead of extending this document.
