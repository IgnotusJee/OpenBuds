# Brand Integration Guide

How to add support for a new headphone brand in OpenBuds.

## 1. Implement `HeadphoneAdapter`

Create an object implementing `dev.ignotus.openbuds.headphones.HeadphoneAdapter`. See existing implementations:

- `SonyTandemHeadphoneAdapter` — Sony Tandem-family devices
- `QcyHeadphoneAdapter` — QCY GATT-TLV devices

### Required methods

```kotlin
object MyBrandHeadphoneAdapter : HeadphoneAdapter {
    override val id: String = "my-brand"
    override val brand: String = "My Brand"
    override val protocolName: String = "My Protocol"

    // Match a scanned/bonded device to this adapter
    override fun match(device: DiscoveredSonyDevice, reportedModelName: String?): ConnectedHeadphoneProfile?

    // Create a minimal "unknown device" profile that can at least do battery read
    override fun fallbackProfile(device: DiscoveredSonyDevice): ConnectedHeadphoneProfile

    // Build refresh / config / playback commands
    override fun buildRefreshCommands(profile): List<HeadphoneCommand>
    override fun buildSetNoiseControlModeCommands(profile, mode, ambientLevel, ambientMode): List<HeadphoneCommand>
    override fun buildSetEqBandCommands(profile, rawSteps, preset, context): List<HeadphoneCommand>
    override fun buildPlaybackCommands(profile, control): List<HeadphoneCommand>
    // ... more as needed

    // Parse raw bytes from the transport into typed responses
    override fun parse(profile, channel, raw): ParsedHeadphoneResponse
}
```

### Profile template

Create a device-specific profile in a sub-package (e.g. `headphones/mybranddevices/`):

```kotlin
object MyModelProfile {
    val template = ProfileTemplate(
        modelName = "My Model",
        series = null,
        infoLayoutHint = InfoLayoutHint.BRAND_MODEL,
        capabilities = HeadphoneCapabilities(
            features = setOf(HeadphoneFeature.DEVICE_INFO, HeadphoneFeature.BATTERY, ...),
            ...
        ),
        featureProtocolMap = features.associateWith { HeadphoneProtocolVariant.MY_BRAND },
    )
}
```

## 2. Implement `HeadphoneTransportClient`

Create a BLE transport client that implements `dev.ignotus.openbuds.ble.HeadphoneTransportClient`:

```kotlin
class MyBrandBleClient(context: Context, listener: SonyBleClientListener) : HeadphoneTransportClient {
    override val id: String = "my-brand"

    override fun matches(device: DiscoveredSonyDevice, reportedModelName: String?): Boolean
    override fun startScan(strictFilter: Boolean)
    override fun stopScan()
    override fun connect(device: DiscoveredSonyDevice)
    override fun disconnect()
    override fun sendToChannel(channel: TandemChannel, bytes: ByteArray)
    override fun availableChannels(): Set<TandemChannel>
}
```

## 3. Add protocol response types

Add a `sealed interface` inside `ParsedHeadphoneResponse` in `SonyTandemTypes.kt`:

```kotlin
sealed interface ParsedHeadphoneResponse {
    // ... existing SonyTandem, Qcy ...

    sealed interface MyBrand : ParsedHeadphoneResponse {
        data class Battery(...) : MyBrand
        data class DeviceInfo(...) : MyBrand
        // ...
    }
}
```

## 4. Create a `ResponseMapper`

Extract state-mutation logic into `<Brand>ResponseMapper` (see `QcyResponseMapper`):

```kotlin
object MyBrandResponseMapper {
    fun apply(state: HeadphoneUiState, response: ParsedHeadphoneResponse.MyBrand): HeadphoneUiState
}
```

## 5. Register in infrastructure

### Transport selector

In `HeadphoneRepository` constructor:

```kotlin
private val myBrandClient = MyBrandBleClient(appContext, this)
private val transport = HeadphoneTransportSelector(
    clients = listOf(sonyClient, qcyClient, myBrandClient),
)
```

### Adapter registry

In `HeadphoneAdapterRegistry`:

```kotlin
private val adapters: List<HeadphoneAdapter> = listOf(
    SonyTandemHeadphoneAdapter,
    QcyHeadphoneAdapter,
    MyBrandHeadphoneAdapter,
)
```

## 6. Minimum tests

1. **Frame test**: build/parse your protocol frames
2. **Adapter parse test**: per-channel dispatch + field decoding
3. **Routing regression test**: new channel enum values don't break `TandemGattRouting`

## Reference implementations

| Component | File |
|-----------|------|
| Transport client | `ble/qcy/QcyBleClient.kt` |
| Adapter | `headphones/qcy/QcyHeadphoneAdapter.kt` |
| Profile template | `headphones/qcy/devices/QcyC30SProfile.kt` |
| Response mapper | `data/qcy/QcyResponseMapper.kt` |
| Protocol constants | `protocol/qcy/QcyProtocol.kt`, `protocol/qcy/QcyGatt.kt` |
| Protocol reference | `references/QCY/QCY_C30S_PROTOCOL.md` |
