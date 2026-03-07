package no.seime.openhab.binding.bluetooth.bthome.internal;

import java.util.Map;

import org.openhab.core.config.core.Configuration;
import org.openhab.core.thing.Thing;

public class TestableBTHomeHandler extends BTHomeHandler {
    public TestableBTHomeHandler(Thing thing, BTHomeChannelTypeProvider dynamicChannelTypeProvider) {
        super(thing, dynamicChannelTypeProvider);
    }

    private Configuration config;

    public void setConfig(Configuration config) {
        this.config = config;
    }

    @Override
    protected Configuration getConfig() {
        return config != null ? config : new Configuration((Map.of("address", "00:00:00:00:00:00")));
    }
}
