package no.seime.openhab.binding.bluetooth.bthome.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

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
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.internal.ThingImpl;
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
    void testParseMultiPackets() throws TextFormat.InvalidEscapeSequenceException {
        deviceHandler.initialize();
        BluetoothScanNotification scanNotificationMeasurements = extractBTHomeDataFromProtobufLogMessage(
                "\\002\\001\\006\\016\\026\\322\\374@\\000F\\001d\\002L\\b\\003\\201\\024");
        deviceHandler.onScanRecordReceived(scanNotificationMeasurements);
        BluetoothScanNotification scanNotificationBattery = extractBTHomeDataFromProtobufLogMessage(
                "\\002\\001\\006\\r\\026\\322\\374@\\000@\\f\\321\\v\\020\\000\\021\\001");
        deviceHandler.onScanRecordReceived(scanNotificationBattery);
        assertEquals(7, deviceHandler.getThing().getChannels().size());
        verifyChannelCreated("battery");
        verifyChannelCreated("temperature");
        verifyChannelCreated("humidity");
        verifyChannelCreated("packet-id");
        verifyChannelCreated("opening");
        verifyChannelCreated("voltage");
        verifyChannelCreated("power-on");
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

    private void verifyChannelCreated(String channelName) {
        assertTrue(
                deviceHandler.getThing().getChannels().stream().filter(e -> e.getUID().getId().equals(channelName))
                        .findFirst().isPresent(),
                "Channel not found, but found "
                        + deviceHandler.getThing().getChannels().stream().map(e -> e.getUID().getId()).toList());
    }

    private ThingImpl createThing() {
        ThingImpl thing = new ThingImpl(BTHomeBindingConstants.THING_TYPE_DEVICE, "device");
        thing.setConfiguration(configuration);
        return thing;
    }
}
