package no.seime.openhab.binding.bluetooth.bthome.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.bluetooth.BluetoothBindingConstants;
import org.openhab.core.thing.type.ChannelKind;
import org.openhab.core.thing.type.ChannelTypeUID;

import no.seime.openhab.binding.bluetooth.bthome.internal.datastructure.BthomeServiceData;

@NonNullByDefault
public enum BTHomeTypeMapping {
    // Add mappings from https://bthome.io/format/

    PACKET_ID(BthomeServiceData.BthomeObjectId.MISC_PACKET_ID, "packet-id", ChannelKind.STATE, "Number"),
    ACCELERATION(BthomeServiceData.BthomeObjectId.SENSOR_ACCELERATION, "acceleration", ChannelKind.STATE,
            "Number:Acceleration"),
    BATTERY_PERCENTAGE(BthomeServiceData.BthomeObjectId.SENSOR_BATTERY, "battery", ChannelKind.STATE,
            "Number:Dimensionless"),
    CO2(BthomeServiceData.BthomeObjectId.SENSOR_CO2, "co2", ChannelKind.STATE, "Number:Concentration"),
    COUNT_SMALL(BthomeServiceData.BthomeObjectId.SENSOR_COUNT, "count", ChannelKind.STATE, "Number"),
    COUNT_MEDIUM(BthomeServiceData.BthomeObjectId.SENSOR_COUNT_UINT16, "count", ChannelKind.STATE, "Number"),
    COUNT_LARGE(BthomeServiceData.BthomeObjectId.SENSOR_COUNT_UINT32, "count", ChannelKind.STATE, "Number"),

    CURRENT(BthomeServiceData.BthomeObjectId.SENSOR_CURRENT_0_001, "current", ChannelKind.STATE,
            "Number:ElectricCurrent"),
    DEWPOINT(BthomeServiceData.BthomeObjectId.SENSOR_DEWPOINT_0_01, "dewpoint", ChannelKind.STATE,
            "Number:Temperature"),

    DURATION(BthomeServiceData.BthomeObjectId.SENSOR_DURATION_0_001, "duration", ChannelKind.STATE, "Number:Time"),

    DISTANCE_MM(BthomeServiceData.BthomeObjectId.SENSOR_DISTANCE_MM, "distance", ChannelKind.STATE, "Number:Length"),
    DISTANCE_M(BthomeServiceData.BthomeObjectId.SENSOR_DISTANCE_M_0_1, "distance", ChannelKind.STATE, "Number:Length"),

    ENERGY_SMALL(BthomeServiceData.BthomeObjectId.SENSOR_ENERGY_0_001, "energy", ChannelKind.STATE, "Number:Energy"),
    ENERGY_LARGE(BthomeServiceData.BthomeObjectId.SENSOR_ENERGY_0_001_UINT32, "energy", ChannelKind.STATE,
            "Number:Energy"),

    GAS(BthomeServiceData.BthomeObjectId.SENSOR_GAS, "gas", ChannelKind.STATE, "Number:Volume"),
    GAS_LARGE(BthomeServiceData.BthomeObjectId.SENSOR_GAS_UINT32, "gas", ChannelKind.STATE, "Number:Volume"),

    GYROSCOPE(BthomeServiceData.BthomeObjectId.SENSOR_GYROSCOPE, "gyroscope", ChannelKind.STATE, "Number:Angle"),

    HUMIDITY(BthomeServiceData.BthomeObjectId.SENSOR_HUMIDITY, "humidity", ChannelKind.STATE, "Number:Dimensionless"),
    HUMIDITY_DETAILED(BthomeServiceData.BthomeObjectId.SENSOR_HUMIDITY_0_01, "humidity", ChannelKind.STATE,
            "Number:Dimensionless"),

    ILLUMINANCE(BthomeServiceData.BthomeObjectId.SENSOR_ILLUMINANCE_0_01, "illuminance", ChannelKind.STATE,
            "Number:Illuminance"),

    MASS_KG(BthomeServiceData.BthomeObjectId.SENSOR_MASS_KG_0_01, "mass", ChannelKind.STATE, "Number:Mass"),
    MASS_POUND(BthomeServiceData.BthomeObjectId.SENSOR_MASS_LB_0_01, "mass", ChannelKind.STATE, "Number:Mass"),

    MOISTURE(BthomeServiceData.BthomeObjectId.SENSOR_MOISTURE, "moisture", ChannelKind.STATE, "Number:Dimensionless"),
    MOISTURE_WITH_DECIMAL(BthomeServiceData.BthomeObjectId.SENSOR_MOISTURE_0_01, "moisture", ChannelKind.STATE,
            "Number:Dimensionless"),

    PM25(BthomeServiceData.BthomeObjectId.SENSOR_PM2_5, "pm25", ChannelKind.STATE, "Number:Density"),
    PM10(BthomeServiceData.BthomeObjectId.SENSOR_PM10, "pm10", ChannelKind.STATE, "Number:Density"),

    POWER(BthomeServiceData.BthomeObjectId.SENSOR_POWER_0_01, "power", ChannelKind.STATE, "Number:Power"),

    PRESSURE(BthomeServiceData.BthomeObjectId.SENSOR_PRESSURE_0_01, "pressure", ChannelKind.STATE, "Number:Pressure"),

    RAW(BthomeServiceData.BthomeObjectId.SENSOR_RAW, "raw", ChannelKind.STATE, "String"),
    ROTATION(BthomeServiceData.BthomeObjectId.SENSOR_ROTATION_0_1, "rotation", ChannelKind.STATE, "Number:Angle"),

    SPEED(BthomeServiceData.BthomeObjectId.SENSOR_SPEED_0_01, "speed", ChannelKind.STATE, "Number:Speed"),

    TEMPERATURE(BthomeServiceData.BthomeObjectId.SENSOR_TEMPERATURE_0_1, "temperature", ChannelKind.STATE,
            "Number:Temperature"),
    TEMPERATURE_DECIMAL(BthomeServiceData.BthomeObjectId.SENSOR_TEMPERATURE_0_01, "temperature", ChannelKind.STATE,
            "Number:Temperature"),
    TEXT(BthomeServiceData.BthomeObjectId.SENSOR_TEXT, "text", ChannelKind.STATE, "String"),

    TIMESTAMP(BthomeServiceData.BthomeObjectId.SENSOR_TIMESTAMP, "timestamp", ChannelKind.STATE, "DateTime"),

    TVOC(BthomeServiceData.BthomeObjectId.SENSOR_TVOC, "tvoc", ChannelKind.STATE, "Number:Density"),

    VOLTAGE(BthomeServiceData.BthomeObjectId.SENSOR_VOLTAGE_0_1, "voltage", ChannelKind.STATE,
            "Number:ElectricPotential"),
    VOLTAGE_DECIMAL(BthomeServiceData.BthomeObjectId.SENSOR_VOLTAGE_0_001, "voltage", ChannelKind.STATE,
            "Number:ElectricPotential"),

    VOLUME_0(BthomeServiceData.BthomeObjectId.SENSOR_VOLUME, "volume", ChannelKind.STATE, "Number:Volume"),
    VOLUME_1(BthomeServiceData.BthomeObjectId.SENSOR_VOLUME_0_1, "volume", ChannelKind.STATE, "Number:Volume"),
    VOLUME_2(BthomeServiceData.BthomeObjectId.SENSOR_VOLUME_0_001, "volume", ChannelKind.STATE, "Number:Volume"),
    VOLUME_STORAGE(BthomeServiceData.BthomeObjectId.SENSOR_VOLUME_STORAGE, "volume-storage", ChannelKind.STATE,
            "Number:Volume"),

    VOLUME_FLOW_RATE(BthomeServiceData.BthomeObjectId.SENSOR_VOLUME_FLOW_RATE_0_001, "volume-flow-rate",
            ChannelKind.STATE, "Number:VolumetricFlowRate"),

    UV(BthomeServiceData.BthomeObjectId.SENSOR_UV_INDEX_0_1, "uv-index", ChannelKind.STATE, "Number"),

    WATER(BthomeServiceData.BthomeObjectId.SENSOR_WATER, "water", ChannelKind.STATE, "Number:Volume"),

    // START BINARY SENSORS
    BINARY_BATTERY_LOW(BthomeServiceData.BthomeObjectId.BINARY_BATTERY, "battery-low", ChannelKind.STATE, "Switch"),
    BINARY_BATTERY_CHARGING(BthomeServiceData.BthomeObjectId.BINARY_BATTERY_CHARGING, "battery-charging",
            ChannelKind.STATE, "Switch"),
    BINARY_CARBON_MONOXIDE(BthomeServiceData.BthomeObjectId.BINARY_CARBON_MONOXIDE, "carbon-monoxide-detected",
            ChannelKind.STATE, "Switch"),
    BINARY_COLD(BthomeServiceData.BthomeObjectId.BINARY_COLD, "cold", ChannelKind.STATE, "Switch"),
    BINARY_CONNECTIVITY(BthomeServiceData.BthomeObjectId.BINARY_CONNECTIVITY, "connectivity", ChannelKind.STATE,
            "Switch"),
    BINARY_DOOR(BthomeServiceData.BthomeObjectId.BINARY_DOOR, "door", ChannelKind.STATE, "Contact"),
    BINARY_GARAGE_DOOR(BthomeServiceData.BthomeObjectId.BINARY_GARAGE_DOOR, "garage-door", ChannelKind.STATE,
            "Contact"),
    BINARY_GAS(BthomeServiceData.BthomeObjectId.BINARY_GAS, "gas-detected", ChannelKind.STATE, "Switch"),

    BINARY_GENERIC(BthomeServiceData.BthomeObjectId.BINARY_GENERIC_BOOLEAN, "generic", ChannelKind.STATE, "Switch"),

    BINARY_HEAT(BthomeServiceData.BthomeObjectId.BINARY_HEAT, "heat-detected", ChannelKind.STATE, "Switch"),
    BINARY_LIGHT(BthomeServiceData.BthomeObjectId.BINARY_LIGHT, "light-detected", ChannelKind.STATE, "Switch"),

    BINARY_LOCK(BthomeServiceData.BthomeObjectId.BINARY_LOCK, "lock", ChannelKind.STATE, "Switch"),

    BINARY_MOISTURE(BthomeServiceData.BthomeObjectId.BINARY_MOISTURE, "moisture-detected", ChannelKind.STATE, "Switch"),
    BINARY_MOTION(BthomeServiceData.BthomeObjectId.BINARY_MOTION, "motion", ChannelKind.STATE, "Switch"),

    BINARY_MOVING(BthomeServiceData.BthomeObjectId.BINARY_MOVING, "moving", ChannelKind.STATE, "Switch"),

    BINARY_OCCUPANCY(BthomeServiceData.BthomeObjectId.BINARY_OCCUPANCY, "occupancy", ChannelKind.STATE, "Switch"),

    BINARY_OPENING(BthomeServiceData.BthomeObjectId.BINARY_OPENING, "opening", ChannelKind.STATE, "Contact"),

    BINARY_PLUG(BthomeServiceData.BthomeObjectId.BINARY_PLUG, "plug", ChannelKind.STATE, "Switch"),

    BINARY_POWER(BthomeServiceData.BthomeObjectId.BINARY_POWER, "power-on", ChannelKind.STATE, "Switch"),

    BINARY_PRESENCE(BthomeServiceData.BthomeObjectId.BINARY_PRESENCE, "presence", ChannelKind.STATE, "Switch"),

    BINARY_PROBLEM(BthomeServiceData.BthomeObjectId.BINARY_PROBLEM, "problem", ChannelKind.STATE, "Switch"),

    BINARY_RUNNING(BthomeServiceData.BthomeObjectId.BINARY_RUNNING, "running", ChannelKind.STATE, "Switch"),

    BINARY_SAFETY(BthomeServiceData.BthomeObjectId.BINARY_SAFETY, "safety", ChannelKind.STATE, "Switch"),

    BINARY_SMOKE(BthomeServiceData.BthomeObjectId.BINARY_SMOKE, "smoke", ChannelKind.STATE, "Switch"),

    BINARY_SOUND(BthomeServiceData.BthomeObjectId.BINARY_SOUND, "sound", ChannelKind.STATE, "Switch"),

    BINARY_TAMPER(BthomeServiceData.BthomeObjectId.BINARY_TAMPER, "tamper", ChannelKind.STATE, "Switch"),

    BINARY_VIBRATION(BthomeServiceData.BthomeObjectId.BINARY_VIBRATION, "vibration", ChannelKind.STATE, "Switch"),

    BINARY_WINDOW(BthomeServiceData.BthomeObjectId.BINARY_WINDOW, "window", ChannelKind.STATE, "Contact"),

    EVENT_BUTTON(BthomeServiceData.BthomeObjectId.EVENT_BUTTON, "button", ChannelKind.TRIGGER, "String"),

    EVENT_DIMMER(BthomeServiceData.BthomeObjectId.EVENT_DIMMER, "dimmer", ChannelKind.TRIGGER, "String");

    // TODO add more mappings

    private static final String CHANNEL_TYPE_PREFIX = "bthome-";

    private final BthomeServiceData.BthomeObjectId bthomeObjectId;
    private final String channelName;
    private final ChannelKind channelKind;
    private final String itemType;
    private final ChannelTypeUID channelTypeUID;

    BTHomeTypeMapping(BthomeServiceData.BthomeObjectId bthomeObjectId, String channelName, ChannelKind channelKind,
            String itemType) {
        this.bthomeObjectId = bthomeObjectId;
        this.channelName = channelName;
        this.channelKind = channelKind;
        this.itemType = itemType;
        this.channelTypeUID = new ChannelTypeUID(BluetoothBindingConstants.BINDING_ID,
                CHANNEL_TYPE_PREFIX + channelName);
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

    /**
     * Find the (first) mapping using the given channel name. Mappings sharing a channel name share the same
     * static channel type.
     */
    @Nullable
    public static BTHomeTypeMapping fromChannelName(String channelName) {
        for (BTHomeTypeMapping typeMapping : values()) {
            if (typeMapping.channelName.equals(channelName)) {
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

    /**
     * @return UID of the static channel type defined in OH-INF/thing/channel-types.xml
     */
    public ChannelTypeUID getChannelTypeUID() {
        return channelTypeUID;
    }
}
