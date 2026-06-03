# MiLink MiTWS First-Party Equivalence Implementation Checklist

Updated: 2026-06-03

This document turns the equivalence analysis into an implementation checklist for reaching a stronger first-party MiTWS replacement inside `com.milink.service`.

Goal:

> Move from "MiLink headset UI and ANC path work"  
> to "MiLink cannot practically distinguish OpenBuds from a real first-party MiTWS device across the important runtime and control surfaces"

This is not a promise that full byte-for-byte first-party parity is required.  
It is a staged engineering plan to close the most relevant gaps.

## Stage 0: Freeze current baseline

### Checklist

- [ ] Preserve the current working MiTWS facade path as the stable baseline
- [ ] Keep `TRACE_ONLY` mode usable for diagnostics
- [ ] Document the currently validated runtime properties on test devices
- [ ] Record the system properties used to enable facade mode and template selection

### Current files

- [MilinkRouteConfig.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/MilinkRouteConfig.kt)
- [MiTwsDeviceIdPolicy.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/MiTwsDeviceIdPolicy.kt)
- [MilinkMiTwsFacadeEntry.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/MilinkMiTwsFacadeEntry.kt)
- [MilinkMiTwsFacadeHook.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/MilinkMiTwsFacadeHook.kt)

### Expected outcome

Current working behavior remains reproducible while deeper equivalence work proceeds.

## Stage 1: Expand bridge snapshot to cover missing first-party state

Current snapshot only covers:

- connection
- protocol readiness
- battery
- wearing
- charging
- ANC mode
- ring support flag (currently always false)

### Checklist

- [ ] Add `supportsVolumeControl`
- [ ] Add current volume value
- [ ] Add `supportsAudioEffect`
- [ ] Add current audio effect state
- [ ] Add `supportsRing`
- [ ] Add current ring state
- [ ] Add any additional headset subtype/state needed by MiLink UI branches

### Target files

- [MilinkDeviceSnapshot.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/integration/milink/MilinkDeviceSnapshot.kt)
- [MilinkBridgeService.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/integration/milink/MilinkBridgeService.kt)
- upstream repository mapping in:
  - [HeadphoneRepository](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/data/HeadphoneRepository.kt)
  - related UI state and adapter layers

### Required validation

- [ ] Snapshot revision increments when volume changes
- [ ] Snapshot revision increments when audio effect changes
- [ ] Snapshot revision increments when ring state changes
- [ ] Snapshot remains backward compatible for existing ANC-only facade logic

## Stage 2: Replace missing callback surface

`MiTwsCallbackPump` already drives:

- `onConnectMmaStateChanged`
- `onBatteryLevel`
- `onAncStateChanged`
- `onReportAncState`
- `onDeviceIdUpdate`
- optionally `onRingStateChanged`

The missing work is to make the ring/audio-effect/volume surfaces actually real rather than placeholders or native leakage.

### Checklist

- [ ] Drive `onRingStateChanged` from real bridge state
- [ ] Verify whether MiLink expects additional callback side effects for volume or audio effect
- [ ] Trace whether there are hidden callback branches beyond `MMACallback` that must be mirrored
- [ ] Keep `ancBatteryModel` force-creation logic intact while extending callback coverage

### Target files

- [MiTwsCallbackPump.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/MiTwsCallbackPump.kt)
- [MilinkBridgeClient.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/MilinkBridgeClient.kt)

### Required validation

- [ ] Ring UI changes state without relying on native AirPods path
- [ ] No callback spam or UI thrash when revisions do not change
- [ ] Main-thread dispatch remains correct

## Stage 3: Implement missing control commands in the bridge

Current bridge command surface handles only noise control.

### Checklist

- [ ] Add volume command type
- [ ] Add audio-effect command type
- [ ] Add ring start command type
- [ ] Add ring stop command type
- [ ] Add any required "more settings" or route-related command only if MiLink truly needs it

### Target files

- [MilinkBridgeContract.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/integration/milink/MilinkBridgeContract.kt)
- [MilinkBridgeCommand.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/integration/milink/MilinkBridgeCommand.kt)
- [MilinkBridgeService.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/integration/milink/MilinkBridgeService.kt)
- [MiTwsControlMapper.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/MiTwsControlMapper.kt)

### Recommended command model

Add bridge commands as explicit typed operations rather than overloading ANC command bundles:

- `SetNoiseControl`
- `SetVolume`
- `SetAudioEffect`
- `StartRing`
- `StopRing`

### Required validation

- [ ] Command result includes success/reason/requestId consistently
- [ ] Failed commands degrade cleanly in MiLink UI
- [ ] Command timeout behavior remains bounded and predictable

## Stage 4: Hook missing control entry points inside MiLink

Before expanding point hooks further, see the mainline runtime-level design in:

- [MILINK_FIRST_PARTY_ADAPTER_PLAN_V2.md](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/docs/plan/MILINK_FIRST_PARTY_ADAPTER_PLAN_V2.md)

Specifically:

- `5.1 主线修订：MiTWS runtime projection`
- `M3+：MiTWS runtime projection 和剩余主线能力`

Current hook coverage is strong for ANC but incomplete elsewhere.

### ANC

Already present:

- [ ] `openAnc`
- [ ] `openTransparent`
- [ ] `closeAnc`
- [ ] `ProfileImpl.updateHeadsetMode(...)`

### Volume

Need to add:

- [ ] Trace where headset volume UI issues `updateHeadsetVolume(...)`
- [ ] Hook the MiLink-side volume command entry before it reaches native first-party remote protocol
- [ ] Forward to bridge `SetVolume`

### Audio effect

Need to add:

- [ ] Trace where audio effect UI issues `updateHeadsetAudioEffect(...)`
- [ ] Hook the corresponding MiLink-side method
- [ ] Forward to bridge `SetAudioEffect`

### Ring

Need to add:

- [ ] Trace `C4737b0.m19893y(...)`
- [ ] Trace `C4737b0.m19886c0(...)`
- [ ] Identify whether `HeadsetServiceClient` ring helpers must be bypassed entirely for OpenBuds devices
- [ ] Replace with bridge `StartRing` / `StopRing`

### Target files

- [MilinkMiTwsFacadeHook.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/MilinkMiTwsFacadeHook.kt)
- optionally add new focused hook helpers under:
  - `app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/`

### Required validation

- [ ] No control falls through to native Xiaomi protocol for OpenBuds devices
- [ ] Real Xiaomi first-party devices still pass through original logic unchanged

## Stage 5: Replace query-surface gaps

The current facade gets away with leaving several query methods native.  
That is good enough for current UI paths, but not for stricter equivalence.

### Query gaps to address

- [ ] `getSupportAncMode(...)`
- [ ] `isMmaHeadset(...)`
- [ ] `getBondStateWithTargetHost(...)`
- [ ] `switchToHeadsetActivity(...)` only if required for first-party settings parity

### Recommended approach

Prefer the narrowest stable replacement:

1. Trace the exact query callers in current HyperOS build
2. Replace only when native semantics diverge for OpenBuds devices
3. Keep real MiTWS devices on original paths

### Possible hook sites

- `com.miui.headset.runtime.QueryLocal`
- `com.miui.headset.runtime.QueryServer`
- higher call sites in `C4737b0` if query replacement at the source is too brittle

### Target outputs

- [ ] OpenBuds devices always report expected ANC capability
- [ ] OpenBuds devices always satisfy "is MMA headset" checks where MiLink needs them
- [ ] Bond-state-dependent flows do not regress

## Stage 6: Decide whether to emulate `HeadsetDeviceInfo` more fully

Right now the solution relies on a hybrid:

- facade hooks
- callback injection
- selective model repair

That is pragmatic, but it is not a full model replacement.

### Decision point

Choose one of two strategies:

#### Strategy A: Continue facade-first

- [ ] Keep replacing only the MiLink surfaces that matter
- [ ] Avoid rebuilding full first-party runtime model
- [ ] Accept that some deep non-user-visible differences remain

#### Strategy B: Build fuller `HeadsetDeviceInfo` parity

- [ ] Trace every field consumed from `HeadsetDeviceInfo`
- [ ] Ensure each field is fully backed by OpenBuds data or explicit compatibility defaults
- [ ] Replace remaining native leakage through `C4734a` / `C4737b0` dependent paths

### Recommendation

Use Strategy A unless:

- a real user-visible gap remains, or
- a hidden query/control path keeps breaking because MiLink depends on a field not yet modeled

## Stage 7: Ring capability implementation plan

This is the highest-value missing capability after ANC.

### Checklist

- [ ] Add ring support capability to snapshot
- [ ] Add ring state to snapshot
- [ ] Add ring commands to bridge
- [ ] Hook MiLink ring control entry points
- [ ] Hook or bypass AirPods-only ring helper logic for OpenBuds devices
- [ ] Feed ring state changes back through callback pump

### Milink-side references

- `C6451o0`
- `C4737b0.m19876K(...)`
- `C4737b0.m19886c0(...)`
- `C4737b0.m19887d0(...)`
- `C4737b0.m19890m(...)`

### Validation

- [ ] Ring card appears only when OpenBuds device really supports it
- [ ] Start ring updates UI to active state
- [ ] Stop ring updates UI back to idle state
- [ ] Wear-state restrictions are handled intentionally rather than by accidental fallthrough

## Stage 8: Audio effect capability implementation plan

### Checklist

- [ ] Identify OpenBuds-supported audio effect abstraction in repository state
- [ ] Extend snapshot with current audio effect state
- [ ] Add bridge command for effect changes
- [ ] Hook MiLink audio effect control path
- [ ] Update callback or model refresh path so UI reflects changes

### Milink-side references

- `C4737b0.m19863s(...)`
- `HeadSetsDetail.audioEffectVisible`
- `C6460w`

### Validation

- [ ] Audio effect card only appears when supported
- [ ] UI reflects state correctly after external state changes
- [ ] Control path never falls back to Xiaomi-native first-party device operations

## Stage 9: Volume capability implementation plan

### Checklist

- [ ] Confirm OpenBuds-side writable volume abstraction
- [ ] Extend snapshot with current volume
- [ ] Add bridge `SetVolume`
- [ ] Hook MiLink volume control path
- [ ] Verify refresh path after volume updates

### Milink-side references

- `C4737b0.m19865u(...)`
- `C4737b0.m19873G(...)`
- `C6447m0`

### Validation

- [ ] Volume slider displays current value from OpenBuds state
- [ ] Volume changes round-trip through bridge
- [ ] No jumpy desync between MiLink UI and OpenBuds repository state

## Stage 10: Verification checklist

### Functional verification

- [ ] Device appears as first-party headset sticker
- [ ] Device opens native headset detail page
- [ ] Title and icon are stable across reconnects
- [ ] Battery displays correctly
- [ ] ANC displays correctly
- [ ] ANC toggles correctly
- [ ] Wear-state gating behaves predictably
- [ ] Ring card behaves correctly if supported
- [ ] Audio effect card behaves correctly if supported
- [ ] Volume card behaves correctly if supported

### Regression verification

- [ ] Real Xiaomi first-party devices still use original behavior
- [ ] Facade disabled -> device drops back to non-first-party path cleanly
- [ ] Trace-only mode produces diagnostics without mutating behavior
- [ ] No repeated callback storms
- [ ] No crashes in `AncBatteryController`
- [ ] No stale `deviceId` drift across reconnects

### Robustness verification

- [ ] MiLink process restart recovers bridge session
- [ ] OpenBuds service reconnect recovers snapshot feed
- [ ] Missing bridge does not crash MiLink
- [ ] Unauthorized or disconnected devices are not misclassified as MiTWS

## Prioritized execution order

Recommended order:

1. Ring capability
2. Volume control
3. Audio effect state and control
4. Runtime projection on `ProfileContext + DiscoveryImpl`
5. Query-surface replacement where still needed
6. Optional deeper `HeadsetDeviceInfo` parity

Why this order:

- Ring is currently the clearest missing first-party capability
- Volume and audio effect are the next visible user-facing gaps
- Runtime projection is the preferred structural fix for reducing point-hook
  maintenance in native profile control paths
- Query-surface work should be driven by observed failures, not expanded blindly

## Non-goals unless evidence demands them

- Do not revive the old card redirection strategy
- Do not hook `MLCardViewHostService` unless a current MiLink version makes the facade path insufficient
- Do not expand into `com.android.bluetooth` unless a proven blocker requires it
- Do not rebuild the entire Xiaomi remote protocol stack unless selective facade replacement becomes impossible

## Completion standard

This implementation track should only be considered complete when:

1. all first-party headset UI sections exposed for the target device class are backed by OpenBuds state or intentionally disabled by capability, and
2. all visible controls exposed by MiLink for that target device class are routed through OpenBuds logic rather than Xiaomi-native first-party logic, and
3. the remaining native MiLink query/control paths no longer create observable differences for the target workflow set
