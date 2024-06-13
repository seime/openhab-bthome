# Secuyou Smart Lock

This extension adds support for [BTHome protocal standard devices](https://bthome.io/)

## Supported Things

Following thing type is supported by this extension:

* Any BTHome device that supports the BTHome protocol standard

| Thing Type ID | Description             |
|---------------|-------------------------|
| device        | BTHome compliant device |

## Discovery

As any other Bluetooth device, devices are discovered automatically by the corresponding bridge.

## Thing Configuration

Supported configuration parameters for the things:

| Property | Type   | Default | Required | Description                                                     |
|----------|--------|---------|----------|-----------------------------------------------------------------|
| address  | String |         | Yes      | Bluetooth address of the device (in format "XX:XX:XX:XX:XX:XX") |

## Channels

Channels are created dynamically based on the device's capabilities.

## Example

bthome.things with Bluetooth adapter config included

```
Bridge bluetooth:bluez:hci1 "My BLE dongle" [ address="00:00:00:00:00:00", backgroundDiscovery=false] {
    bthome-device my-device "BTHome broadcasting device" [ address="00:00:00:00:00:00"]
}
```

bthome.items:

```
```
