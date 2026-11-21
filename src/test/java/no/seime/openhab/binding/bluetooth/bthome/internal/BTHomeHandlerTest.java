package no.seime.openhab.binding.bluetooth.bthome.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.binding.bluetooth.notification.BluetoothScanNotification;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.internal.ThingImpl;
import org.openhab.core.thing.type.ChannelKind;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.State;

import com.google.protobuf.ByteString;
import com.google.protobuf.TextFormat;
import com.neovisionaries.bluetooth.ble.advertising.ADManufacturerSpecific;
import com.neovisionaries.bluetooth.ble.advertising.ADPayloadParser;
import com.neovisionaries.bluetooth.ble.advertising.ADStructure;
import com.neovisionaries.bluetooth.ble.advertising.ServiceData;

/**
 *
 * @author Arne Seime - Initial contribution
 */

@ExtendWith(MockitoExtension.class)
class BTHomeHandlerTest {

    private @Mock Configuration configuration;

    private @Mock BTHomeChannelTypeProvider channelTypeProvider;

    private Thing thing;

    private BTHomeHandler deviceHandler;

    private ThingHandlerCallback thingHandlerCallback;

    BTHomeConfiguration deviceConfiguration;

    @BeforeEach
    public void setUp() {

        deviceConfiguration = new BTHomeConfiguration();
        deviceConfiguration.address = "00:00:00:00:00:00";

        thing = createThing();
        deviceHandler = Mockito.spy(new TestableBTHomeHandler(thing, channelTypeProvider));
        thingHandlerCallback = Mockito.mock(ThingHandlerCallback.class);
        deviceHandler.setCallback(thingHandlerCallback);
    }

    @AfterEach
    public void shutdown() {
        deviceHandler.dispose();
    }

    @Test
    void testParseDuplicateField() {
        deviceHandler.initialize();
        deviceHandler.processDataPacket(
                TestUtils.byteStringToByteArray("64, 2, -54, 9, 46, 40, 5, 0, 0, 0, 12, -10, 12, 47, 0, 1, 100,1,100"));
        assertEquals(7, deviceHandler.getThing().getChannels().size());
        assertTrue(deviceHandler.getThing().getChannels().stream().filter(e -> e.getUID().getId().equals("battery_1"))
                .findFirst().isPresent());
        assertTrue(deviceHandler.getThing().getChannels().stream().filter(e -> e.getUID().getId().equals("battery_2"))
                .findFirst().isPresent());
        assertTrue(deviceHandler.getThing().getChannels().stream().filter(e -> e.getUID().getId().equals("battery"))
                .findFirst().isEmpty());

        // Both battery channels share the same static channel type
        ChannelTypeUID batteryType = new ChannelTypeUID("bluetooth", "bthome-battery");
        assertEquals(batteryType, getChannel("battery_1").getChannelTypeUID());
        assertEquals(batteryType, getChannel("battery_2").getChannelTypeUID());
        assertEquals("Battery 1", getChannel("battery_1").getLabel());
        Mockito.verify(channelTypeProvider, Mockito.never()).putChannelType(any());
    }

    @Test
    void testMigrateLegacyChannelTypes() {
        ChannelTypeUID legacyType = new ChannelTypeUID("bluetooth", "bthome-device-temperature");
        ChannelTypeUID legacyBatteryType = new ChannelTypeUID("bluetooth", "bthome-device-battery_2");
        ChannelTypeUID otherType = new ChannelTypeUID("bluetooth", "some-other-type");
        ThingImpl legacyThing = createThing();
        legacyThing.addChannel(ChannelBuilder.create(new ChannelUID(legacyThing.getUID(), "temperature"))
                .withType(legacyType).withLabel("Temperature").build());
        legacyThing.addChannel(ChannelBuilder.create(new ChannelUID(legacyThing.getUID(), "battery_2"))
                .withType(legacyBatteryType).withLabel("Battery_2_2").build());
        legacyThing.addChannel(
                ChannelBuilder.create(new ChannelUID(legacyThing.getUID(), "custom")).withType(otherType).build());

        deviceHandler = Mockito.spy(new TestableBTHomeHandler(legacyThing, channelTypeProvider));
        deviceHandler.setCallback(thingHandlerCallback);
        deviceHandler.initialize();

        assertEquals(3, deviceHandler.getThing().getChannels().size());
        assertEquals(new ChannelTypeUID("bluetooth", "bthome-temperature"),
                getChannel("temperature").getChannelTypeUID());
        assertEquals(new ChannelTypeUID("bluetooth", "bthome-battery"), getChannel("battery_2").getChannelTypeUID());
        assertEquals("Temperature", getChannel("temperature").getLabel());
        assertEquals(otherType, getChannel("custom").getChannelTypeUID());
        Mockito.verify(channelTypeProvider).removeChannelTypes(Set.of(legacyType, legacyBatteryType));
    }

    @Test
    void testParseDuplicateNonDuplicateField() {
        deviceHandler.initialize();
        deviceHandler.processDataPacket(TestUtils.byteStringToByteArray(
                "64, 2, -54, 9, 46, 40, 5, 0, 0, 0, 12, -10, 12, 47, 0, 1, 100,33,1,45,0,63,2,12,-16,2,0"));
        assertEquals(9, deviceHandler.getThing().getChannels().size());
        verifyChannelCreated("battery");
        verifyChannelCreated("illuminance");
        verifyChannelCreated("moisture");
        verifyChannelCreated("voltage");
        verifyChannelCreated("temperature");
        verifyChannelCreated("humidity");
        verifyChannelCreated("motion");
        verifyChannelCreated("window");
        verifyChannelCreated("rotation");

        verifyStateUpdated("battery", new QuantityType<>(100, Units.PERCENT));
        verifyStateUpdated("illuminance", new QuantityType<>(0, Units.LUX));
        verifyStateUpdated("moisture", new QuantityType<>(0, Units.PERCENT));
        verifyStateUpdated("voltage", new QuantityType<>(3.318, Units.VOLT));
        verifyStateUpdated("temperature", new QuantityType<>(25.060000000000002, SIUnits.CELSIUS));
        verifyStateUpdated("humidity", new QuantityType<>(40, Units.PERCENT));
        verifyStateUpdated("motion", OnOffType.ON);
        verifyStateUpdated("window", OpenClosedType.CLOSED);
        verifyStateUpdated("rotation", new QuantityType<>(307.40000000000003, Units.DEGREE_ANGLE));

        assertEquals("2", deviceHandler.getThing().getProperties().get("deviceType"));
    }

    @Test
    void testMeasurementsSplitAcrossPackets() {
        deviceHandler.initialize();

        // Packet 1: packet id 1, temperature 25.06, humidity 50.55
        deviceHandler.processDataPacket(Hex.decode("40000102CA0903BF13"));
        assertChannels("packet-id", "temperature", "humidity");
        verifyStateUpdated("temperature", new QuantityType<>(25.06, SIUnits.CELSIUS));

        // Packet 2: packet id 2, battery 100, button press - existing channels must be kept
        deviceHandler.processDataPacket(Hex.decode("40000201643A01"));
        assertChannels("packet-id", "temperature", "humidity", "battery", "button");
        verifyStateUpdated("battery", new QuantityType<>(100, Units.PERCENT));
        ChannelUID buttonUID = getChannel("button").getUID();
        Mockito.verify(thingHandlerCallback).channelTriggered(Mockito.any(), Mockito.eq(buttonUID),
                Mockito.eq("PRESS"));

        // Packet 3: same measurements as packet 1 again - no new channels
        deviceHandler.processDataPacket(Hex.decode("40000302CA0903BF13"));
        assertChannels("packet-id", "temperature", "humidity", "battery", "button");

        // Packet 4: two battery measurements - numbered channels are added next to the existing one
        deviceHandler.processDataPacket(Hex.decode("4000040164010A"));
        assertChannels("packet-id", "temperature", "humidity", "battery", "button", "battery_1", "battery_2");
        verifyStateUpdated("battery_1", new QuantityType<>(100, Units.PERCENT));
        verifyStateUpdated("battery_2", new QuantityType<>(10, Units.PERCENT));

        // Kind and accepted item type per channel
        assertEquals("Number:Temperature", getChannel("temperature").getAcceptedItemType());
        assertEquals("Number:Dimensionless", getChannel("battery_2").getAcceptedItemType());
        assertEquals(ChannelKind.TRIGGER, getChannel("button").getKind());
        assertEquals(ChannelKind.STATE, getChannel("humidity").getKind());
    }

    private void assertChannels(String... expected) {
        List<String> actual = deviceHandler.getThing().getChannels().stream().map(c -> c.getUID().getId()).toList();
        assertEquals(expected.length, actual.size(), "Unexpected channels " + actual);
        assertEquals(Set.of(expected), Set.copyOf(actual));
    }

    @Test
    void testEncryptedPacket() throws Exception {
        // Example from bthome-ble: test_encryption_example_2
        // MAC: 54:48:E6:8F:80:A5
        // Bindkey: 231d39c1d7cc1ab1aee224cd096db932
        // Encrypted payload (after D2FC): 41 87b9 00112233 72b0231f

        Configuration config = new Configuration(
                Map.of("address", "54:48:E6:8F:80:A5", "encryptionKey", "231d39c1d7cc1ab1aee224cd096db932"));
        ((TestableBTHomeHandler) deviceHandler).setConfig(config);
        deviceHandler.initialize();

        deviceHandler.processDataPacket(Hex.decode("4187b90011223372b0231f"));

        // If decryption works, it should find a motion measurement (21 01 -> ON)
        assertEquals(1, deviceHandler.getThing().getChannels().size());
        verifyChannelCreated("motion");
        verifyStateUpdated("motion", OnOffType.ON);
    }

    private byte[] reverse(byte[] array) {
        byte[] result = new byte[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[array.length - 1 - i];
        }
        return result;
    }

    private static BluetoothScanNotification extractBTHomeDataFromProtobufLogMessage(String content)
            throws TextFormat.InvalidEscapeSequenceException {
        ByteString bs = TextFormat.unescapeBytes(content);
        List<ADStructure> advertisementStructures = ADPayloadParser.getInstance().parse(bs.toByteArray());

        BluetoothScanNotification notification = new BluetoothScanNotification();
        advertisementStructures.stream().forEach(structure -> {
            if (structure instanceof ADManufacturerSpecific manufacturerSpecific) {
                notification.setManufacturerData(manufacturerSpecific.getData());
            } else if (structure instanceof ServiceData serviceData) {
                // UUID 2 bytes included in serviceData.getData(), trim away
                byte[] dataIncludingUUID = serviceData.getData();
                byte[] dataExcludingUUID = Arrays.copyOfRange(dataIncludingUUID, 2, dataIncludingUUID.length);
                notification.getServiceData().put(serviceData.getServiceUUID().toString(), dataExcludingUUID);
            }
        });

        return notification;
    }

    private void verifyStateUpdated(String channelName, State state) {
        Channel channel = deviceHandler.getThing().getChannels().stream()
                .filter(e -> e.getUID().getId().equals(channelName)).findFirst().get();
        Mockito.verify(thingHandlerCallback).stateUpdated(channel.getUID(), state);
    }

    private Channel getChannel(String channelName) {
        return deviceHandler.getThing().getChannels().stream().filter(e -> e.getUID().getId().equals(channelName))
                .findFirst().orElseThrow();
    }

    private void verifyChannelCreated(String channelName) {
        assertTrue(
                deviceHandler.getThing().getChannels().stream().filter(e -> e.getUID().getId().equals(channelName))
                        .findFirst().isPresent(),
                "Channel not found, but found "
                        + deviceHandler.getThing().getChannels().stream().map(e -> e.getUID().getId()).toList());
    }

    private ThingImpl createThing() {
        ThingImpl thing = new ThingImpl(BTHomeBindingConstants.THING_TYPE_DEVICE, "device");
        return thing;
    }
}
