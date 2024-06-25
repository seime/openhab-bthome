package no.seime.openhab.binding.bluetooth.bthome.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.thing.type.ChannelKind;

import no.seime.openhab.binding.bluetooth.bthome.internal.datastructure.BthomeServiceData;

@NonNullByDefault
public enum BTHomeTypeMapping {
    // Add mappings from https://bthome.io/format/

    PACKET_ID(BthomeServiceData.BthomeObjectId.MISC_PACKET_ID, "packet-id", ChannelKind.STATE, "Number", "text"),
    ACCELERATION(BthomeServiceData.BthomeObjectId.SENSOR_ACCELERATION, "acceleration", ChannelKind.STATE,
            "Number:Acceleration", "motion"),
    ROTATION(BthomeServiceData.BthomeObjectId.SENSOR_ROTATION_0_1, "rotation", ChannelKind.STATE, "Number:Angle",
            "incline"),

    MOTION(BthomeServiceData.BthomeObjectId.BINARY_MOTION, "motion", ChannelKind.STATE, "Switch", "motion"),
    WINDOW(BthomeServiceData.BthomeObjectId.BINARY_WINDOW, "window", ChannelKind.STATE, "Contact", "window"),
    BATTERY_PERCENTAGE(BthomeServiceData.BthomeObjectId.SENSOR_BATTERY, "battery", ChannelKind.STATE,
            "Number:Dimensionless", "batterylevel"),
    CO2(BthomeServiceData.BthomeObjectId.SENSOR_CO2, "co2", ChannelKind.STATE, "Number:Concentration", "carbondioxide"),
    HUMIDITY(BthomeServiceData.BthomeObjectId.SENSOR_HUMIDITY, "humidity", ChannelKind.STATE, "Number:Dimensionless",
            "humidity"),
    ILLUMINANCE(BthomeServiceData.BthomeObjectId.SENSOR_ILLUMINANCE_0_01, "illuminance", ChannelKind.STATE,
            "Number:Illuminance", "light"),
    MOISTURE(BthomeServiceData.BthomeObjectId.SENSOR_MOISTURE, "moisture", ChannelKind.STATE, "Number:Dimensionless",
            "humidity"),
    MOISTURE_WITH_DECIMAL(BthomeServiceData.BthomeObjectId.SENSOR_MOISTURE_0_01, "moisture", ChannelKind.STATE,
            "Number:Dimensionless", "humidity"),
    VOLTAGE(BthomeServiceData.BthomeObjectId.SENSOR_VOLTAGE_0_1, "voltage", ChannelKind.STATE,
            "Number:ElectricPotential", "energy"),
    VOLTAGE_DECIMAL(BthomeServiceData.BthomeObjectId.SENSOR_VOLTAGE_0_001, "voltage", ChannelKind.STATE,
            "Number:ElectricPotential", "energy"),
    TEMPERATURE(BthomeServiceData.BthomeObjectId.SENSOR_TEMPERATURE_0_1, "temperature", ChannelKind.STATE,
            "Number:Temperature", "temperature"),
    TEMPERATURE_DECIMAL(BthomeServiceData.BthomeObjectId.SENSOR_TEMPERATURE_0_01, "temperature", ChannelKind.STATE,
            "Number:Temperature", "temperature"),
    EVENT_BUTTON(BthomeServiceData.BthomeObjectId.EVENT_BUTTON, "button", ChannelKind.TRIGGER, "String", "motion");

    // TODO add more mappings

    private final BthomeServiceData.BthomeObjectId bthomeObjectId;
    private final String channelName;
    private final ChannelKind channelKind;
    private final String itemType;
    private final String category;

    BTHomeTypeMapping(BthomeServiceData.BthomeObjectId bthomeObjectId, String channelName, ChannelKind channelKind,
            String itemType, String category) {

        this.bthomeObjectId = bthomeObjectId;
        this.channelName = channelName;
        this.channelKind = channelKind;
        this.itemType = itemType;
        this.category = category;
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

    public String getCategory() {
        return category;
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
