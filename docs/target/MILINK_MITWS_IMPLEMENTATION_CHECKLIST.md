# MiLink MiTWS First-Party Equivalence Implementation Checklist

Updated: 2026-06-04

This document turns the equivalence analysis into an implementation checklist for reaching a stronger first-party MiTWS replacement inside `com.milink.service`.

Goal:

> Move from "MiLink headset UI and ANC path work"  
> to "MiLink cannot practically distinguish OpenBuds from a real first-party MiTWS device across the important runtime and control surfaces"

This is not a promise that full byte-for-byte first-party parity is required.  
It is a staged engineering plan to close the most relevant gaps.

## Stage 0: Freeze current baseline

### Checklist

- [x] Preserve the current working MiTWS facade path as the stable baseline
- [x] Keep `TRACE_ONLY` mode usable for diagnostics
- [x] Document the currently validated runtime properties on test devices
- [x] Record the system properties used to enable facade mode and template selection
- [x] Confirm that `com.milink.service:ui` and `com.milink.service:core` are the
  only currently validated critical MiTWS mainline processes
- [x] Exclude `:audio`, `:provider`, `persistent`, `com.milink.runtime`,
  `com.milink.crossdeviceservice`, and other non-critical subprocesses from the
  MiTWS mainline install path

### Current files

- [MilinkRouteConfig.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/MilinkRouteConfig.kt)
- [MiTwsDeviceIdPolicy.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/MiTwsDeviceIdPolicy.kt)
- [MilinkMiTwsFacadeEntry.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/MilinkMiTwsFacadeEntry.kt)
- [MilinkMiTwsFacadeHook.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/MilinkMiTwsFacadeHook.kt)

### Expected outcome

Current working behavior remains reproducible while deeper equivalence work proceeds.

### Current validated runtime notes

- `:ui` and `:core` must both keep `bridge + facade`
- removing bridge from `:core` causes the headset card to disappear
- after narrowing process scope, card disappearance and heavy style jitter are
  significantly reduced
- remaining low-frequency jitter/no-op behavior is currently most correlated
  with `:core` high-frequency `disconnectMma` polling
- runtime projection now exists for active device / connected devices /
  battery / ANC / switch state / device type / `HeadsetInfo` assembly, but
  first-open ANC timing still needs more verification
- `HeadsetInfo.powers` must remain the 6-slot first-party shape used by
  `RemoteCodecKt`
- minimal query replacement now exists for `getSupportAncMode`,
  `isMmaHeadset`, and `getBondStateWithTargetHost`, but the latest
  three-state ANC and first-paint battery fallback changes still need
  device-side verification

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

- [x] Preserve correct first-party battery payload shape for `HeadsetInfo`
- [x] Project OpenBuds form factor into MiLink runtime `deviceType`
- [x] Add `supportsVolumeControl`
- [x] Add current volume value
- [x] Add `supportsAudioEffect`
- [x] Add current audio effect state
- [x] Add `supportsRing` (framework stub — `false` until BLE protocol discovered)
- [x] Add current ring state (framework stub — `false` until BLE protocol discovered)
- [x] Add any additional headset subtype/state needed by MiLink UI branches

Current interim rule:

- keep `volume`, `audio effect`, and `ring` hidden or unsupported until they
  are backed by real OpenBuds state
- do not project fake default values for these fields through runtime hooks

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

- [x] Add volume command type
- [x] Add audio-effect command type
- [x] Add ring start command type (framework stub only)
- [x] Add ring stop command type (framework stub only)
- [x] Add any required "more settings" or route-related command only if MiLink truly needs it

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

Known current behavior:

- ANC switching is usable
- card/detail behavior is much more stable after process narrowing
- low-frequency residual shrink/no-op still exists and should be treated as a
  `:core` runtime stability issue before expanding control surface further
- bridge loss may preserve MiTWS classification, but must not preserve active
  runtime projection or unsupported first-party control semantics

### ANC

Already present:

- [x] `openAnc`
- [x] `openTransparent`
- [x] `closeAnc`
- [x] `ProfileImpl.updateHeadsetMode(...)`
- [ ] verify first-open ANC card visibility does not wait for a later async refresh

### Volume

Already present:

- [x] Trace where headset volume UI issues `updateHeadsetVolume(...)`
- [x] Hook the MiLink-side volume command entry before it reaches native first-party remote protocol
- [x] Forward to bridge `SetVolume`

### Audio effect

Already present:

- [x] Trace where audio effect UI issues `updateHeadsetAudioEffect(...)`
- [x] Hook the corresponding MiLink-side method (`installAudioEffectControlHook()`)
- [x] Forward to bridge `SetAudioEffect`

### Ring

Framework stub only — no BLE protocol exists:

- [x] Trace `C4737b0.m19893y(...)` — diagnostic trace hook in place
- [ ] Ring bridge command (framework stub — `StartRing`/`StopRing` rejected at execution layer)
- [ ] Ring control hook (not installed — `supportsRing=false` gates this out)

### Target files

- [MilinkMiTwsFacadeHook.kt](/C:/Users/Ignotus/Documents/Workspace/OpenBuds/app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/MilinkMiTwsFacadeHook.kt)
- optionally add new focused hook helpers under:
  - `app/src/main/java/dev/ignotus/openbuds/lsposed/mitws/`

### Required validation

- [ ] No control falls through to native Xiaomi protocol for OpenBuds devices
- [ ] Real Xiaomi first-party devices still pass through original logic unchanged
- [ ] `:ui` and `:core` continue to agree on snapshot and control state under
  long-running use

## Stage 5: Replace query-surface gaps

The current facade gets away with leaving several query methods native.  
That is good enough for current UI paths, but not for stricter equivalence.

### Query gaps to address

- [x] `getSupportAncMode(...)`
- [x] `isMmaHeadset(...)`
- [x] `getBondStateWithTargetHost(...)`
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

- [ ] OpenBuds devices always report expected ANC capability, including three-state ANC devices
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

**2026-06-05 status: Framework stub only.** Neither Sony Tandem v13.0.5 nor QCY protocol exposes a BLE ring/find command. Both OEM apps use phone-side GPS + speaker alarm.

Framework ready:
- [x] Add ring support capability to snapshot (`supportsRing` field exists, hardcoded `false`)
- [x] Add ring state to snapshot (`ringing` field exists, hardcoded `false`)
- [x] Add ring commands to bridge (`COMMAND_START_RING`/`COMMAND_STOP_RING`/`COMMAND_RING_FIND` constants exist)
- [x] `HeadphoneFeature.RING` enum + adapter interface stubs + registry delegation
- [ ] Hook MiLink ring control entry points — pending protocol discovery
- [ ] Feed ring state changes back through callback pump — pending protocol discovery

### Validation

- [ ] Ring card appears only when OpenBuds device really supports it
- [ ] Start ring updates UI to active state
- [ ] Stop ring updates UI back to idle state

## Stage 8: Audio effect capability implementation plan

**2026-06-05 status: Complete.**

- [x] Extend snapshot with current audio effect state (`currentAudioEffectState` from `AudioEffectState.enabled`)
- [x] Add bridge command for effect changes (`SetAudioEffect` → `repository.setAudioEffect()`)
- [x] Hook MiLink audio effect control path (`installAudioEffectControlHook()`)

### Milink-side references

- `C4737b0.m19863s(...)`
- `HeadSetsDetail.audioEffectVisible`
- `C6460w`

### Validation

- [ ] Audio effect card only appears when supported
- [ ] UI reflects state correctly after external state changes
- [ ] Control path never falls back to Xiaomi-native first-party device operations

## Stage 9: Volume capability implementation plan

**2026-06-05 status: Complete.** See `docs/plan/MILINK_FIRST_PARTY_ADAPTER_PLAN_V2.md` M3+ Volume 完成记录 for details.

- [x] Confirm OpenBuds-side writable volume abstraction
- [x] Extend snapshot with current volume
- [x] Add bridge `SetVolume`
- [x] Hook MiLink volume control path
- [x] Verify refresh path after volume updates

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
- [ ] `:core` high-frequency `disconnectMma` polling no longer produces visible
  card shrink or ANC no-op behavior in ordinary use

## Prioritized execution order

Recommended order (updated 2026-06-05):

1. ~~Runtime stabilization inside `:core` (`disconnectMma` polling / runtime target consistency)~~ → observation mode active, TODO pending
2. ~~Runtime projection on `ProfileContext + DiscoveryImpl`~~ → complete
3. ~~Volume control~~ → complete
4. ~~Audio effect state and control~~ → complete
5. Ring capability — framework stub; blocked on BLE protocol discovery
6. Query-surface replacement where still needed (`switchToHeadsetActivity` only)
7. Optional deeper `HeadsetDeviceInfo` parity

Why this order:

- ring is now the only user-visible first-party control surface not yet replaced
- ring implementation is blocked at the protocol layer (no Sony/QCY BLE command exists)
- remaining query-surface differences (`switchToHeadsetActivity`) are lower priority
- current stability is adequate for daily use; `disconnectMma` observation may lead to targeted fix if evidence confirms

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
