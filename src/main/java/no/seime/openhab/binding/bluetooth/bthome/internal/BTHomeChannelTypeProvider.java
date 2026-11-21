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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.AbstractStorageBasedTypeProvider;
import org.openhab.core.thing.type.ChannelType;
import org.openhab.core.thing.type.ChannelTypeProvider;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Storage based channel type provider. Channel types are now statically defined in OH-INF/thing/channel-types.xml;
 * this provider only remains to clean up the per-thing channel types (bluetooth:bthome-&lt;thingId&gt;-&lt;channel&gt;)
 * that earlier versions of the binding created dynamically. It can be removed in a later release.
 *
 * @author Arne Seime - Initial contribution
 */
@Component(service = { BTHomeChannelTypeProvider.class, ChannelTypeProvider.class })
@NonNullByDefault
public class BTHomeChannelTypeProvider extends AbstractStorageBasedTypeProvider {
    private final Logger logger = LoggerFactory.getLogger(BTHomeChannelTypeProvider.class);

    @Activate
    public BTHomeChannelTypeProvider(@Reference StorageService storageService, @Reference ThingRegistry thingRegistry) {
        super(storageService);
        removeUnreferencedChannelTypes(thingRegistry);
    }

    /**
     * Remove all stored legacy channel types that are not referenced by any BTHome thing. Types still in use (managed
     * things, or file based things keeping their runtime channels across a bundle update) are removed when the thing
     * handler migrates the channels to the static channel types.
     */
    private void removeUnreferencedChannelTypes(ThingRegistry thingRegistry) {
        Set<@Nullable ChannelTypeUID> referenced = thingRegistry.getAll().stream()
                .filter(t -> BTHomeBindingConstants.THING_TYPE_DEVICE.equals(t.getThingTypeUID()))
                .flatMap(t -> t.getChannels().stream()).map(Channel::getChannelTypeUID).collect(Collectors.toSet());
        List<ChannelTypeUID> unreferenced = getChannelTypes(null).stream().map(ChannelType::getUID)
                .filter(uid -> !referenced.contains(uid)).toList();
        if (!unreferenced.isEmpty()) {
            logger.info("Removing {} legacy BTHome channel types no longer in use", unreferenced.size());
            removeChannelTypes(unreferenced);
        }
    }

    public void removeChannelTypes(Collection<ChannelTypeUID> channelTypeUIDs) {
        channelTypeUIDs.forEach(this::removeChannelType);
    }

    public void removeChannelTypesForThing(ThingUID uid) {
        String legacyPrefix = "bthome-" + uid.getId() + "-";
        getChannelTypes(null).stream().map(ChannelType::getUID).filter(c -> c.getId().startsWith(legacyPrefix)).toList()
                .forEach(this::removeChannelType);
    }
}
