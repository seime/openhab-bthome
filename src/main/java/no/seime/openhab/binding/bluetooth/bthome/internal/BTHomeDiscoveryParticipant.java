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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.bluetooth.BluetoothBindingConstants;
import org.openhab.binding.bluetooth.BluetoothService;
import org.openhab.binding.bluetooth.discovery.BluetoothDiscoveryDevice;
import org.openhab.binding.bluetooth.discovery.BluetoothDiscoveryParticipant;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;

import java.util.*;

/**
 * This discovery participant is able to recognize ruuvitag devices and create discovery results for them.
 *
 * @author Arne Seime - Initial contribution
 *
 */
@NonNullByDefault
// Commented out as we need access to the supplied serviceData - which isn't provided by openHAB
// @Component
public class BTHomeDiscoveryParticipant implements BluetoothDiscoveryParticipant {

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(BTHomeBindingConstants.THING_TYPE_DEVICE);
    }

    @Override
    public @Nullable ThingUID getThingUID(BluetoothDiscoveryDevice device) {

        // TODO no data received on at least b-parasite sensor
        @Nullable
        BluetoothService service = device.getServices(UUID.fromString(BTHomeBindingConstants.SERVICEDATA_UUID));
        if (service != null) {
            return new ThingUID(BTHomeBindingConstants.THING_TYPE_DEVICE, device.getAdapter().getUID(),
                    device.getAddress().toString().toLowerCase().replace(":", ""));
        }
        return null;
    }

    @Override
    public @Nullable DiscoveryResult createResult(BluetoothDiscoveryDevice device) {
        ThingUID thingUID = getThingUID(device);
        if (thingUID == null) {
            return null;
        }
        String label = device.getName() == null ? "Unnamed Device (BTHome)" : device.getName() + " (BTHome)";
        Map<String, Object> properties = new HashMap<>();
        properties.put(BluetoothBindingConstants.CONFIGURATION_ADDRESS, device.getAddress().toString());
        Integer txPower = device.getTxPower();
        if (txPower != null) {
            properties.put(BluetoothBindingConstants.PROPERTY_TXPOWER, Integer.toString(txPower));
        }

        // Create the discovery result and add to the inbox
        return DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                .withRepresentationProperty(BluetoothBindingConstants.CONFIGURATION_ADDRESS)
                .withBridge(device.getAdapter().getUID()).withLabel(label).build();
    }
}
