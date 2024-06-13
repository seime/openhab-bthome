package no.seime.openhab.binding.bluetooth.bthome.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.type.ChannelKind;

import no.seime.openhab.binding.bluetooth.bthome.internal.datastructure.BthomeServiceData;

@NonNullByDefault
public enum BTHomeTypeMapping {
    // Add mappings from https://bthome.io/format/

    ACCELERATION(BthomeServiceData.BthomeObjectId.SENSOR_ACCELERATION, "acceleration", ChannelKind.STATE,
            "Number:Acceleration"),
    BATTERY_PERCENTAGE(BthomeServiceData.BthomeObjectId.SENSOR_BATTERY, "battery", ChannelKind.STATE,
            "Number:Dimensionless"),
    CO2(BthomeServiceData.BthomeObjectId.SENSOR_CO2, "co2", ChannelKind.STATE, "Number:Concentration"),
    HUMIDITY(BthomeServiceData.BthomeObjectId.SENSOR_HUMIDITY, "humidity", ChannelKind.STATE, "Number:Dimensionless"),
    ILLUMINANCE(BthomeServiceData.BthomeObjectId.SENSOR_ILLUMINANCE_0_01, "light", ChannelKind.STATE,
            "Number:Illuminance"),
    MOISTURE(BthomeServiceData.BthomeObjectId.SENSOR_MOISTURE, "moisture", ChannelKind.STATE, "Number:Dimensionless"),
    MOISTURE_WITH_DECIMAL(BthomeServiceData.BthomeObjectId.SENSOR_MOISTURE_0_01, "moisture", ChannelKind.STATE,
            "Number:Dimensionless"),
    VOLTAGE(BthomeServiceData.BthomeObjectId.SENSOR_VOLTAGE_0_1, "voltage", ChannelKind.STATE,
            "Number:ElectricPotential"),
    VOLTAGE_DECIMAL(BthomeServiceData.BthomeObjectId.SENSOR_VOLTAGE_0_001, "voltage", ChannelKind.STATE,
            "Number:ElectricPotential"),
    TEMPERATURE(BthomeServiceData.BthomeObjectId.SENSOR_TEMPERATURE_0_1, "temperature", ChannelKind.STATE,
            "Number:Temperature"),
    TEMPERATURE_DECIMAL(BthomeServiceData.BthomeObjectId.SENSOR_TEMPERATURE_0_01, "temperature", ChannelKind.STATE,
            "Number:Temperature");

    // TODO add more mappings

    private final BthomeServiceData.BthomeObjectId bthomeObjectId;
    private final String channelName;
    private final ChannelKind channelKind;
    private final String itemType;

    BTHomeTypeMapping(BthomeServiceData.BthomeObjectId bthomeObjectId, String channelName, ChannelKind channelKind,
            String itemType) {

        this.bthomeObjectId = bthomeObjectId;
        this.channelName = channelName;
        this.channelKind = channelKind;
        this.itemType = itemType;
    }

    @Nullable
    public static BTHomeTypeMapping fromBthomeObjectId(BthomeServiceData.BthomeObjectId bthomeObjectId) {
        for (BTHomeTypeMapping typeMapping : values()) {
            if (typeMapping.bthomeObjectId == bthomeObjectId) {
                return typeMapping;
            }
        }
        return null;
    }

    public String getChannelName() {
        return channelName;
    }

    public ChannelKind getChannelKind() {
        return channelKind;
    }

    public String getItemType() {
        return itemType;
    }
}
