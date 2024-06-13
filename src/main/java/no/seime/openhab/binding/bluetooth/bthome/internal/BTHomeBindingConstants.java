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

import org.openhab.binding.bluetooth.BluetoothBindingConstants;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link BTHomeBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Arne Seime - Initial contribution
 */
public class BTHomeBindingConstants {

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_DEVICE = new ThingTypeUID(BluetoothBindingConstants.BINDING_ID,
            "bthome");
    public static final String SERVICEDATA_UUID = "0000fcd2-0000-1000-8000-00805f9b34fb";
}
