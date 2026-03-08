# BTHome Bluetooth Binding for openHAB

<img src="logo.png" width="400"/>

[<img src="https://github.com/seime/support-me/blob/main/openHAB_workswith.png" width=300>](https://www.openhab.org)

[<img src="https://github.com/seime/support-me/blob/main/beer_me.png" width=150>](https://buymeacoffee.com/arnes)

This binding adds support for [BTHome protocal standard devices](https://bthome.io/)

This project is using https://github.com/koenvervloesem/BTHome-Kaitai-Struct to parse the BTHome protocol data.

## Supported Things

Any BTHome device that supports the BTHome V2 protocol standard

| Thing Type ID | Description                    |
|---------------|--------------------------------|
| bthome        | Any BTHome V2 compliant device |

## Discovery

I recommend **disabling** background discovery of Bluetooth devices on your adapter as you may be overwhelmed by lots of
devices that aren't supported by neither this nor the generic Bluetooth binding.

Choose the **Bluetooth Binding** when doing discovery, not the *BTHome Binding*.

Discovery is based on a list of known device names, so custom devices may not be discovered. Add those manually.

## Thing Configuration

Supported configuration parameters for the things:

| Property                           | Type    | Default | Required | Description                                                                                                                                                                                                      |
|------------------------------------|---------|---------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `address`                          | String  |         | Yes      | Bluetooth address of the device (in format `XX:XX:XX:XX:XX:XX`)                                                                                                                                                  |
| `expectedReportingIntervalSeconds` | integer | 3600    | No       | Expected reporting interval in seconds. If the device hasn't phoned home within this deadline, channels are marked as `UNDEF` and device will become `OFFLINE`. Note: A 10% grace period is added to this value. |
| `encryptionKey`                    | String  |         | No       | 32-character hex bindkey for encryption.                                                                                                                                                                         

## Channels

Channels are created dynamically based on the device's capabilities. All are read-only.

## Example

`bthome.things` with Bluetooth adapter config included (in this example, an ESP32 running ESPHome firmware acting as a
Bluetooth proxy):

```
Bridge bluetooth:esphome:proxy "My ESP Bluetooth proxy" [ backgroundDiscovery=false ] {
    bthome my-device "BTHome broadcasting device" [ address="00:00:00:00:00:00", expectedReportingIntervalSeconds = 600, encryptionKey = "0123456789abcdef0123456789abcdef" ]
}
```
