/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package no.seime.openhab.binding.bluetooth.bthome.internal;

import java.util.*;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.bluetooth.BluetoothBindingConstants;
import org.openhab.binding.bluetooth.discovery.BluetoothDiscoveryDevice;
import org.openhab.binding.bluetooth.discovery.BluetoothDiscoveryParticipant;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;

/**
 * This discovery participant is able to recognize BTHome devices by matching the device name with known names.
 *
 * @author Arne Seime - Initial contribution
 *
 */
@NonNullByDefault
@Component
public class BTHomeDiscoveryParticipant implements BluetoothDiscoveryParticipant {

    // @formatter:off
    private final Map<String, String> fullNames = Map.ofEntries(
                Map.entry("SBBT-002C", "Shelly BLU Button1"),
                Map.entry("SBBT-102C", "Shelly BLU Button1 ZB"),
                Map.entry("SBDW-002C", "Shelly BLU Door/Window"),
                Map.entry("SBMO-003Z", "Shelly BLU Motion"),
                Map.entry("SBHT-003C", "Shelly BLU H&T"),
                Map.entry("SBHT-203C", "Shelly BLU H&T ZB"),
                Map.entry("SBHT-103C", "Shelly BLU H&T Display ZB"),
                Map.entry("SBBT-004CEU", "Shelly BLU Wall Switch 4"),
                Map.entry("SBBT-004CUS", "Shelly BLU RC Button 4"),
                Map.entry("SBTR-001AEU", "Shelly BLU TRV")
        );
    // @formatter:on

    // @formatter:off
    private final Map<String, String> prefixes = Map.ofEntries(
                Map.entry("ATC", "Xiaomi Temperature/Humidity sensor"),
                Map.entry("LYWSD03MMC", "Xiaomi Temperature/Humidity sensor"),
                Map.entry("prst", "b-parasite Plant sensor")
        );
    // @formatter:on

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(BTHomeBindingConstants.THING_TYPE_DEVICE);
    }

    @Override
    public @Nullable DiscoveryResult createResult(BluetoothDiscoveryDevice device) {
        String deviceName = device.getName();
        if (deviceName != null) {
            ThingUID thingUID = getThingUID(device);
            if (thingUID != null) {
                String decodedName = fullnameOrPrefixMatches(deviceName);
                if (decodedName != null) {
                    String label = String.format("%s - %s", deviceName, decodedName);
                    Map<String, Object> properties = new HashMap<>();
                    properties.put(BluetoothBindingConstants.CONFIGURATION_ADDRESS, device.getAddress().toString());

                    return DiscoveryResultBuilder.create(thingUID).withBridge(device.getAdapter().getUID())
                            .withProperties(properties)
                            .withRepresentationProperty(BluetoothBindingConstants.CONFIGURATION_ADDRESS)
                            .withLabel(label).build();
                }
            }
        }
        return null;
    }

    @Override
    public @Nullable ThingUID getThingUID(BluetoothDiscoveryDevice device) {
        @Nullable
        String name = device.getName();
        if (name != null && fullnameOrPrefixMatches(name) != null) {
            return new ThingUID(BTHomeBindingConstants.THING_TYPE_DEVICE, device.getAdapter().getUID(),
                    device.getAddress().toString().toLowerCase().replace(":", ""));
        }
        return null;
    }

    private @Nullable String fullnameOrPrefixMatches(@NonNull String name) {
        if (fullNames.containsKey(name)) {
            return fullNames.get(name);
        } else {
            Optional<String> first = prefixes.keySet().stream().filter(e -> e.startsWith(name)).findFirst();
            if (first.isPresent()) {
                return prefixes.get(first.get());
            }
        }
        return null;
    }
}
