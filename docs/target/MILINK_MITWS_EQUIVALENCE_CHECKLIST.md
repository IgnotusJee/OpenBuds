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

- Status: `Satisfied`
- Current implementation:
  - `hookBatteryLevel()`
  - `MiTwsCallbackPump -> onBatteryLevel(int[])`
  - `MiTwsStateMapper.batteryArray()`
- MiLink dependency:
  - `HeadSetsDetail` repeatedly consumes `C4737b0.m19868A()`
- Conclusion:
  - Battery is fully covered for current UI paths.

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

- Status: `Partially satisfied`
- MiLink dependency:
  - `HeadSetsDetail` reads `C4737b0.m19873G()`
  - Controller ultimately reads `HeadsetDeviceInfo.headsetVolume`
- Current implementation:
  - No explicit MiTWS volume getter/callback replacement is present in the current facade path
- Conclusion:
  - Volume may display through surviving native paths or cached model state
  - It is not fully replaced as a MiTWS state surface

### C7. Audio effect state

- Status: `Not satisfied`
- MiLink dependency:
  - `HeadSetsDetail` checks `HeadsetDeviceInfo.audioEffectState`
  - Controller exposes `updateHeadsetAudioEffect(...)`
- Current implementation:
  - Bridge snapshot contains no audio-effect field
  - Current hooks do not replace this state or control surface
- Conclusion:
  - Clear gap.

### C8. Ring / find earbud state

- Status: `Not satisfied`
- Current implementation:
  - `MilinkDeviceSnapshot.supportsRing = false`
  - Callback pump can emit `onRingStateChanged`, but only if a snapshot supports ring
- MiLink dependency:
  - `HeadSetsDetail` ring card uses first-party ring control paths through `C6451o0`
- Conclusion:
  - This is a major missing capability for full equivalence.

## D. Query surface used by MiLink

### D1. `getSupportAncMode(...)`

- Status: `Partially satisfied`
- MiLink native path:
  - `C4737b0.m19891p() -> Query.getSupportAncMode(targetAddress, vidPid)`
  - `QueryLocal` largely resolves this by `deviceId`
- Current implementation:
  - No dedicated hook on `QueryLocal.getSupportAncMode`
  - Current behavior mostly relies on the supplied `deviceId` template passing MiLink's native capability map
- Conclusion:
  - Functionally workable
  - Not a fully replaced query path

### D2. `isMmaHeadset(...)`

- Status: `Partially satisfied`
- MiLink native path:
  - `C4737b0.m19861q() -> Query.isMmaHeadset(...)`
  - `QueryLocal` depends on multipoint host structures and support-control state
- Current implementation:
  - No explicit hook on `QueryLocal.isMmaHeadset`
  - Higher-level facade success likely makes this non-fatal in current UI paths
- Conclusion:
  - Not fully replaced
  - Survives because upstream runtime behavior is already bent into a MiTWS-like shape

### D3. `getBondStateWithTargetHost(...)`

- Status: `Not satisfied`
- Current implementation:
  - No replacement hook identified
- Conclusion:
  - Still native behavior.

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

- Status: `Not satisfied`
- MiLink native path:
  - `C4737b0.m19865u() -> updateHeadsetVolume(...)`
- Current implementation:
  - No hook replacement
  - No bridge command
- Conclusion:
  - Clear gap.

### E3. Audio effect / spatial audio control

- Status: `Not satisfied`
- MiLink native path:
  - `C4737b0.m19863s() -> updateHeadsetAudioEffect(...)`
- Current implementation:
  - No hook replacement
  - No bridge command
- Conclusion:
  - Clear gap.

### E4. Ring / find earbud control

- Status: `Not satisfied`
- MiLink native path:
  - `C6451o0` uses:
    - `C4737b0.m19893y(...)`
    - `C4737b0.m19886c0(...)`
    - `C4737b0.m19887d0(...)`
    - `C4737b0.m19890m(...)`
- Current implementation:
  - No ring bridge command
  - No ring state source
- Conclusion:
  - Clear gap.

### E5. Deep first-party settings surface

- Status: `Not satisfied`
- Current implementation:
  - Current solution enables entry into the native setting jump path
  - It does not replace the deeper first-party control semantics behind it
- Conclusion:
  - Outside current equivalence boundary.

## F. Data-model and runtime-graph equivalence

### F1. `HeadsetDeviceInfo` parity

- Status: `Partially satisfied`
- Observation:
  - MiLink UI often reads `HeadsetDeviceInfo` indirectly through `C4734a` and `C4737b0`
  - Current solution does not rebuild the full first-party `HeadsetDeviceInfo` generation chain
  - It replaces enough runtime state and callback behavior to satisfy current headset page flows
- Conclusion:
  - Core fields are effectively covered where needed
  - Full model parity is not achieved

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

1. Ring / find-earbud state and command surface
2. Audio effect / spatial-audio state and command surface
3. Volume command surface
4. Runtime target naturalness inside `ProfileContext + DiscoveryImpl`
5. `:core` runtime stability under high-frequency `disconnectMma` polling
6. Remaining query-surface differences (`getSupportAncMode`, `isMmaHeadset`, bond state, settings-related queries)

For the implementation order and structural fix strategy, use the V2 main plan
and target implementation checklist instead of extending this document.
