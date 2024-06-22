# BTHome Bluetooth Binding for openHAB

<img src="logo.png" width="400"/>
<img src="openHAB_workswith.png" width="400"/>

This binding adds support for [BTHome protocal standard devices](https://bthome.io/)

## Supported Things

Following thing type is supported by this extension:

* Any BTHome device that supports the BTHome V2 protocol standard

| Thing Type ID | Description                |
|---------------|----------------------------|
| bthome        | BTHome V2 compliant device |

> **NOTE**: This binding only supports a **few** datatypes pr 22.06.2024. If you need support for more datatypes, please
> provide a PR or open an issue. It has so far only been tested with
> a [b-Parasite plant sensor](https://github.com/rbaron/b-parasite)

## Discovery

Discovery does not work as openHAB discovery mechanism doesn't provide the necessary service data (no manufacturerId is
used by BTHome, only a custom service UUID). You need to know the Bluetooth address of the device to add it to openHAB.

Please upvote https://github.com/openhab/openhab-addons/issues/16910 to get this supported.

## Thing Configuration

Supported configuration parameters for the things:

| Property                           | Type    | Default | Required | Description                                                                                                                                                                                                      |
|------------------------------------|---------|---------|----------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `address`                          | String  |         | Yes      | Bluetooth address of the device (in format `XX:XX:XX:XX:XX:XX`)                                                                                                                                                  |
| `expectedReportingIntervalSeconds` | integer | 3600    | No       | Expected reporting interval in seconds. If the device hasn't phoned home within this deadline, channels are marked as `UNDEF` and device will become `OFFLINE`. Note: A 10% grace period is added to this value. |

## Channels

Channels are created dynamically based on the device's capabilities.

## Example

`bthome.things` with Bluetooth adapter config included (in this example, an ESP32 running ESPHome firmware acting as a
Bluetooth proxy):

```
Bridge bluetooth:esphome:proxy "My ESP Bluetooth proxy" [ backgroundDiscovery=true ] {
    bthome my-device "BTHome broadcasting device" [ address="00:00:00:00:00:00", expectedReportingIntervalSeconds = 600]
}
```
