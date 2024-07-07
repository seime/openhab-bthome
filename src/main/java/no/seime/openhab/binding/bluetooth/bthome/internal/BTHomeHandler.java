/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
 * <p>
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 * <p>
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 */
package no.seime.openhab.binding.bluetooth.bthome.internal;

import static java.util.stream.Collectors.groupingBy;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.measure.Unit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.bluetooth.BeaconBluetoothHandler;
import org.openhab.binding.bluetooth.notification.BluetoothScanNotification;
import org.openhab.core.library.types.*;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.*;
import org.openhab.core.types.*;
import org.openhab.core.types.util.UnitUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kaitai.struct.ByteBufferKaitaiStream;
import no.seime.openhab.binding.bluetooth.bthome.internal.datastructure.BthomeServiceData;

/**
 * The {@link BTHomeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Arne Seime - Initial contribution
 */
@NonNullByDefault
public class BTHomeHandler extends BeaconBluetoothHandler {

    private final Logger logger = LoggerFactory.getLogger(BTHomeHandler.class);
    private final AtomicBoolean receivedStatus = new AtomicBoolean();
    private final BTHomeChannelTypeProvider dynamicChannelTypeProvider;

    private @NonNullByDefault({}) ScheduledFuture<?> watchDogFuture;

    private int lastPacketId = -1;
    private long heartbeatDelay = 3600;
    private byte[] cachedBthomeData = new byte[0];

    public BTHomeHandler(Thing thing, BTHomeChannelTypeProvider dynamicChannelTypeProvider) {
        super(thing);
        this.dynamicChannelTypeProvider = dynamicChannelTypeProvider;
    }

    @Override
    public void initialize() {
        super.initialize();

        initInternal();
    }

    private void initInternal() {
        updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.NONE, "Waiting for device to wake up.");
        BTHomeConfiguration config = getConfig().as(BTHomeConfiguration.class);
        heartbeatDelay = (long) (config.expectedReportingIntervalSeconds * 1.1);
        watchDogFuture = scheduler.scheduleWithFixedDelay(this::heartbeat, heartbeatDelay, heartbeatDelay,
                TimeUnit.SECONDS);
    }

    private void heartbeat() {
        synchronized (receivedStatus) {
            if (!receivedStatus.getAndSet(false) && getThing().getStatus() == ThingStatus.ONLINE) {
                getThing().getChannels().stream().map(Channel::getUID).filter(this::isLinked)
                        .forEach(c -> updateState(c, UnDefType.UNDEF));
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "No data received for some time");
            }
        }
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        super.bridgeStatusChanged(bridgeStatusInfo);
        if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            cachedBthomeData = new byte[0];
            cancelWatchdog();
            initInternal();
        }
    }

    @Override
    public void dispose() {
        logger.info("[] Disposing BTHomeHandler", getThing().getUID());
        try {
            super.dispose();
        } finally {
            cancelWatchdog();
        }
    }

    private void cancelWatchdog() {
        if (watchDogFuture != null) {
            watchDogFuture.cancel(true);
            watchDogFuture = null;
        }
    }

    @Override
    protected List<Channel> createDynamicChannels() {
        return new ArrayList<>();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            processDataPacket(cachedBthomeData);
        }
    }

    @Override
    public void onScanRecordReceived(BluetoothScanNotification scanNotification) {
        synchronized (receivedStatus) {
            receivedStatus.set(true);
            super.onScanRecordReceived(scanNotification);

            try {
                Map<String, byte[]> serviceData = scanNotification.getServiceData();
                byte[] updatedBthomeData = serviceData.get(BTHomeBindingConstants.SERVICEDATA_UUID);

                // Cache the data for the refresh command
                if (updatedBthomeData != null) {
                    logger.debug("[] Received updated BTHome data", getThing().getUID());
                    cachedBthomeData = updatedBthomeData;
                    processDataPacket(cachedBthomeData);
                }
            } catch (Exception e) {
                logger.error("Error processing BTHome data", e);
            }
        }
    }

    public void processDataPacket(byte[] bthomeData) {
        if (bthomeData.length != 0) {
            try {
                BthomeServiceData deviceData = new BthomeServiceData(new ByteBufferKaitaiStream(bthomeData));
                boolean isEncrypted = deviceData.deviceInformation().encryption();
                if (isEncrypted) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Device sent encrypted data, but encryption is not yet supported in the binding.");
                    return;
                }

                ArrayList<BthomeServiceData.BthomeMeasurement> allDataFields = deviceData.measurement();
                if (allDataFields.isEmpty()) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Device sent no measurements.");
                    return;
                }

                // Check if we have a new packetId
                Optional<BthomeServiceData.BthomeMeasurement> packetField = allDataFields.stream()
                        .filter(e -> e.data() instanceof BthomeServiceData.BthomeMiscPacketId).findFirst();
                if (packetField.isPresent()) {
                    int newPacketId = ((BthomeServiceData.BthomeMiscPacketId) packetField.get().data()).packetId();
                    if (newPacketId == lastPacketId) {
                        // Already processed
                        return;
                    }
                    lastPacketId = newPacketId;
                }

                updateStatus(ThingStatus.ONLINE);

                // Thing properties
                List<BthomeServiceData.BthomeMeasurement> deviceProperties = allDataFields.stream()
                        .filter(prop -> prop.objectId().id() >= 0xF0).collect(Collectors.toList());
                if (!deviceProperties.isEmpty()) {
                    Map<String, String> updatedProperties = parseDeviceProperties(deviceProperties);
                    updateThing(editThing().withProperties(updatedProperties).build());
                }

                // Measurements
                List<BthomeServiceData.BthomeMeasurement> deviceMeasurements = allDataFields.stream()
                        .filter(prop -> prop.objectId().id() < 0xF0).collect(Collectors.toList());

                Map<BthomeServiceData.BthomeObjectId, List<BthomeServiceData.BthomeMeasurement>> allGroupedMeasurements = deviceMeasurements
                        .stream().collect(groupingBy(e -> e.objectId()));

                List<Channel> currentChannels = getThing().getChannels();
                List<Channel> allChannels = new ArrayList<>(currentChannels);
                allChannels.addAll(createMissingChannels(currentChannels, allGroupedMeasurements));

                for (BthomeServiceData.BthomeObjectId objectId : allGroupedMeasurements.keySet()) {
                    List<BthomeServiceData.BthomeMeasurement> measurementsOfType = allGroupedMeasurements.get(objectId);

                    int counter = 0;
                    for (BthomeServiceData.BthomeMeasurement measurement : measurementsOfType) {
                        counter++;
                        Channel channel = getChannel(objectId, allChannels, measurementsOfType.size() > 1, counter);
                        if (channel != null) {
                            updateChannelValue(measurement, objectId, channel);
                        } else {
                            logger.warn("No channel found for measurement: {}", objectId);
                        }
                    }

                }
            } catch (Exception e) {
                logger.error("Error processing BTHome data", e);
                getThing().getChannels().stream().map(Channel::getUID).filter(this::isLinked)
                        .forEach(c -> updateState(c, UnDefType.UNDEF));
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Error processing BTHome data. Only latest version (V2) is supported: " + e.getMessage());
            }

        } else {
            // Received Bluetooth scan with no service data
            // This happens -- we ignore this silently.
        }
    }

    private Map<String, String> parseDeviceProperties(List<BthomeServiceData.BthomeMeasurement> deviceProperties) {
        Map<String, String> updatedProperties = new HashMap<>();
        for (BthomeServiceData.BthomeMeasurement prop : deviceProperties) {
            switch (prop.objectId()) {
                case DEVICE_TYPE -> {
                    BthomeServiceData.BthomeDeviceType deviceType = (BthomeServiceData.BthomeDeviceType) prop.data();
                    updatedProperties.put("deviceType", String.valueOf(deviceType.deviceTypeId()));
                }
                case DEVICE_FW_VERSION_UINT24 -> {
                    BthomeServiceData.BthomeDeviceFwVersionUint24 firmwareVersion = (BthomeServiceData.BthomeDeviceFwVersionUint24) prop
                            .data();
                    updatedProperties.put("firmwareVersion", String.format("%d.%d.%d", firmwareVersion.fwVersionMajor(),
                            firmwareVersion.fwVersionMinor(), firmwareVersion.fwVersionPatch()));
                }
                case DEVICE_FW_VERSION_UINT32 -> {
                    BthomeServiceData.BthomeDeviceFwVersionUint32 firmwareVersion = (BthomeServiceData.BthomeDeviceFwVersionUint32) prop
                            .data();
                    updatedProperties.put("firmwareVersion",
                            String.format("%d.%d.%d.%d", firmwareVersion.fwVersionMajor(),
                                    firmwareVersion.fwVersionMinor(), firmwareVersion.fwVersionPatch(),
                                    firmwareVersion.fwVersionBuild()));
                }
                default -> {
                    logger.warn("Unknown device property: {}", prop.objectId());
                }
            }
        }
        return updatedProperties;
    }

    private void updateChannelValue(BthomeServiceData.BthomeMeasurement measurement,
            BthomeServiceData.BthomeObjectId bthomeObjectId, Channel channel) {

        State state = null;
        switch (bthomeObjectId) {

            case MISC_PACKET_ID: {
                BthomeServiceData.BthomeMiscPacketId m = (BthomeServiceData.BthomeMiscPacketId) measurement.data();
                state = toNumericState(channel, null, m.packetId());
                break;
            }
            case SENSOR_ACCELERATION: {
                BthomeServiceData.BthomeSensorAcceleration m = (BthomeServiceData.BthomeSensorAcceleration) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.acceleration());
                break;
            }

            case SENSOR_BATTERY: {
                BthomeServiceData.BthomeSensorBattery m = (BthomeServiceData.BthomeSensorBattery) measurement.data();
                state = toNumericState(channel, m.unit(), m.battery());
                break;
            }
            case SENSOR_CO2: {
                BthomeServiceData.BthomeSensorCo2 m = (BthomeServiceData.BthomeSensorCo2) measurement.data();
                state = toNumericState(channel, m.unit(), m.co2());
                break;
            }

            case SENSOR_COUNT: {
                BthomeServiceData.BthomeSensorCount m = (BthomeServiceData.BthomeSensorCount) measurement.data();
                state = toNumericState(channel, null, m.count());
                break;
            }
            case SENSOR_COUNT_UINT16: {
                BthomeServiceData.BthomeSensorCountUint16 m = (BthomeServiceData.BthomeSensorCountUint16) measurement
                        .data();
                state = toNumericState(channel, null, m.count());
                break;
            }
            case SENSOR_COUNT_UINT32: {
                BthomeServiceData.BthomeSensorCountUint32 m = (BthomeServiceData.BthomeSensorCountUint32) measurement
                        .data();
                state = toNumericState(channel, null, m.count());
                break;
            }

            case SENSOR_CURRENT_0_001: {
                BthomeServiceData.BthomeSensorCurrent0001 m = (BthomeServiceData.BthomeSensorCurrent0001) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.current());
                break;
            }

            case SENSOR_DEWPOINT_0_01: {
                BthomeServiceData.BthomeSensorDewpoint001 m = (BthomeServiceData.BthomeSensorDewpoint001) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.dewPoint());
                break;
            }

            case SENSOR_DISTANCE_MM: {
                BthomeServiceData.BthomeSensorDistanceMm m = (BthomeServiceData.BthomeSensorDistanceMm) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.distance());
                break;
            }
            case SENSOR_DISTANCE_M_0_1: {
                BthomeServiceData.BthomeSensorDistanceM01 m = (BthomeServiceData.BthomeSensorDistanceM01) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.distance());
                break;
            }

            case SENSOR_DURATION_0_001: {
                BthomeServiceData.BthomeSensorDuration0001 m = (BthomeServiceData.BthomeSensorDuration0001) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.duration());
                break;
            }

            case SENSOR_ENERGY_0_001: {
                BthomeServiceData.BthomeSensorEnergy0001 m = (BthomeServiceData.BthomeSensorEnergy0001) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.energy());
                break;
            }

            case SENSOR_ENERGY_0_001_UINT32: {
                BthomeServiceData.BthomeSensorEnergy0001Uint32 m = (BthomeServiceData.BthomeSensorEnergy0001Uint32) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.energy());
                break;
            }

            case SENSOR_GAS: {
                BthomeServiceData.BthomeSensorGas m = (BthomeServiceData.BthomeSensorGas) measurement.data();
                state = toNumericState(channel, m.unit(), m.gas());
                break;
            }
            case SENSOR_GAS_UINT32: {
                BthomeServiceData.BthomeSensorGasUint32 m = (BthomeServiceData.BthomeSensorGasUint32) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.gas());
                break;
            }

            case SENSOR_GYROSCOPE: {
                BthomeServiceData.BthomeSensorGyroscope m = (BthomeServiceData.BthomeSensorGyroscope) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.gyroscope());
                break;
            }

            case SENSOR_HUMIDITY: {
                BthomeServiceData.BthomeSensorHumidity m = (BthomeServiceData.BthomeSensorHumidity) measurement.data();
                state = toNumericState(channel, m.unit(), m.humidity());
                break;
            }
            case SENSOR_HUMIDITY_0_01: {
                BthomeServiceData.BthomeSensorHumidity m = (BthomeServiceData.BthomeSensorHumidity) measurement.data();
                state = toNumericState(channel, m.unit(), m.humidity());
                break;
            }
            case SENSOR_ILLUMINANCE_0_01: {
                BthomeServiceData.BthomeSensorIlluminance001 m = (BthomeServiceData.BthomeSensorIlluminance001) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.illuminance());
                break;
            }

            case SENSOR_MASS_KG_0_01: {
                BthomeServiceData.BthomeSensorMassKg001 m = (BthomeServiceData.BthomeSensorMassKg001) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.mass());
                break;
            }
            case SENSOR_MASS_LB_0_01: {
                BthomeServiceData.BthomeSensorMassLb001 m = (BthomeServiceData.BthomeSensorMassLb001) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.mass());
                break;
            }
            case SENSOR_MOISTURE_0_01: {
                BthomeServiceData.BthomeSensorMoisture001 m = (BthomeServiceData.BthomeSensorMoisture001) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.moisture());
                break;
            }
            case SENSOR_MOISTURE: {
                BthomeServiceData.BthomeSensorMoisture m = (BthomeServiceData.BthomeSensorMoisture) measurement.data();
                state = toNumericState(channel, m.unit(), m.moisture());
                break;
            }

            case SENSOR_PM2_5: {
                BthomeServiceData.BthomeSensorPm25 m = (BthomeServiceData.BthomeSensorPm25) measurement.data();
                state = toNumericState(channel, m.unit(), m.pm25());
                break;
            }
            case SENSOR_PM10: {
                BthomeServiceData.BthomeSensorPm10 m = (BthomeServiceData.BthomeSensorPm10) measurement.data();
                state = toNumericState(channel, m.unit(), m.pm10());
                break;
            }

            case SENSOR_POWER_0_01: {
                BthomeServiceData.BthomeSensorPower001 m = (BthomeServiceData.BthomeSensorPower001) measurement.data();
                state = toNumericState(channel, m.unit(), m.power());
                break;
            }
            case SENSOR_PRESSURE_0_01: {
                BthomeServiceData.BthomeSensorPressure001 m = (BthomeServiceData.BthomeSensorPressure001) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.pressure());
                break;
            }

            case SENSOR_RAW: {
                BthomeServiceData.BthomeSensorRaw m = (BthomeServiceData.BthomeSensorRaw) measurement.data();
                state = new StringType(Base64.getEncoder().encodeToString(m.value()));
                break;
            }

            case SENSOR_ROTATION_0_1: {
                BthomeServiceData.BthomeSensorRotation01 m = (BthomeServiceData.BthomeSensorRotation01) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.rotation());
                break;
            }

            case SENSOR_SPEED_0_01: {
                BthomeServiceData.BthomeSensorSpeed001 m = (BthomeServiceData.BthomeSensorSpeed001) measurement.data();
                state = toNumericState(channel, m.unit(), m.speed());
                break;
            }

            case SENSOR_TEMPERATURE_0_01: {
                BthomeServiceData.BthomeSensorTemperature001 m = (BthomeServiceData.BthomeSensorTemperature001) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.temperature());
                break;
            }

            case SENSOR_TEMPERATURE_0_1: {
                BthomeServiceData.BthomeSensorTemperature01 m = (BthomeServiceData.BthomeSensorTemperature01) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.temperature());
                break;
            }

            case SENSOR_TEXT: {
                BthomeServiceData.BthomeSensorText m = (BthomeServiceData.BthomeSensorText) measurement.data();
                state = new StringType(m.value());
                break;
            }

            case SENSOR_TIMESTAMP: {
                BthomeServiceData.BthomeSensorTimestamp m = (BthomeServiceData.BthomeSensorTimestamp) measurement
                        .data();
                state = new DateTimeType(
                        ZonedDateTime.ofInstant(Instant.ofEpochSecond(m.value()), ZoneId.systemDefault()));
                break;
            }

            case SENSOR_TVOC: {
                BthomeServiceData.BthomeSensorTvoc m = (BthomeServiceData.BthomeSensorTvoc) measurement.data();
                state = toNumericState(channel, m.unit(), m.tvoc());
                break;
            }

            case SENSOR_VOLTAGE_0_001: {
                BthomeServiceData.BthomeSensorVoltage0001 m = (BthomeServiceData.BthomeSensorVoltage0001) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.voltage());
                break;
            }

            case SENSOR_VOLTAGE_0_1: {
                BthomeServiceData.BthomeSensorVoltage01 m = (BthomeServiceData.BthomeSensorVoltage01) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.voltage());
                break;
            }
            case SENSOR_VOLUME: {
                BthomeServiceData.BthomeSensorVolume m = (BthomeServiceData.BthomeSensorVolume) measurement.data();
                state = toNumericState(channel, m.unit(), m.volume());
                break;
            }
            case SENSOR_VOLUME_0_1: {
                BthomeServiceData.BthomeSensorVolume01 m = (BthomeServiceData.BthomeSensorVolume01) measurement.data();
                state = toNumericState(channel, m.unit(), m.volume());
                break;
            }
            case SENSOR_VOLUME_0_001: {
                BthomeServiceData.BthomeSensorVolume0001 m = (BthomeServiceData.BthomeSensorVolume0001) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.volume());
                break;
            }
            case SENSOR_VOLUME_FLOW_RATE_0_001: {
                BthomeServiceData.BthomeSensorVolumeFlowRate0001 m = (BthomeServiceData.BthomeSensorVolumeFlowRate0001) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.volumeFlowRate());
                break;
            }
            case SENSOR_VOLUME_STORAGE: {
                BthomeServiceData.BthomeSensorVolumeStorage m = (BthomeServiceData.BthomeSensorVolumeStorage) measurement
                        .data();
                state = toNumericState(channel, m.unit(), m.volumeStorage());
                break;
            }

            case SENSOR_UV_INDEX_0_1: {
                BthomeServiceData.BthomeSensorUvIndex01 m = (BthomeServiceData.BthomeSensorUvIndex01) measurement
                        .data();
                state = toNumericState(channel, null, m.uvIndex());
                break;
            }
            case SENSOR_WATER: {
                BthomeServiceData.BthomeSensorWater m = (BthomeServiceData.BthomeSensorWater) measurement.data();
                state = toNumericState(channel, m.unit(), m.water());
                break;
            }

            // BINARY

            case BINARY_BATTERY: {
                BthomeServiceData.BthomeBinaryBattery m = (BthomeServiceData.BthomeBinaryBattery) measurement.data();
                state = toSwitchState(m.battery());
                break;
            }
            case BINARY_BATTERY_CHARGING: {
                BthomeServiceData.BthomeBinaryBatteryCharging m = (BthomeServiceData.BthomeBinaryBatteryCharging) measurement
                        .data();
                state = toSwitchState(m.batteryCharging());
                break;
            }
            case BINARY_CARBON_MONOXIDE: {
                BthomeServiceData.BthomeBinaryCarbonMonoxide m = (BthomeServiceData.BthomeBinaryCarbonMonoxide) measurement
                        .data();
                state = toSwitchState(m.carbonMonoxide());
                break;
            }
            case BINARY_COLD: {
                BthomeServiceData.BthomeBinaryCold m = (BthomeServiceData.BthomeBinaryCold) measurement.data();
                state = toSwitchState(m.cold());
                break;
            }
            case BINARY_CONNECTIVITY: {
                BthomeServiceData.BthomeBinaryConnectivity m = (BthomeServiceData.BthomeBinaryConnectivity) measurement
                        .data();
                state = toSwitchState(m.connectivity());
                break;
            }
            case BINARY_DOOR: {
                BthomeServiceData.BthomeBinaryDoor m = (BthomeServiceData.BthomeBinaryDoor) measurement.data();
                state = toContactState(m.door());
                break;
            }
            case BINARY_GARAGE_DOOR: {
                BthomeServiceData.BthomeBinaryGarageDoor m = (BthomeServiceData.BthomeBinaryGarageDoor) measurement
                        .data();
                state = toContactState(m.garageDoor());
                break;
            }
            case BINARY_GAS: {
                BthomeServiceData.BthomeBinaryGas m = (BthomeServiceData.BthomeBinaryGas) measurement.data();
                state = toSwitchState(m.gas());
                break;
            }
            case BINARY_GENERIC_BOOLEAN: {
                BthomeServiceData.BthomeBinaryGenericBoolean m = (BthomeServiceData.BthomeBinaryGenericBoolean) measurement
                        .data();
                state = toSwitchState(m.genericBoolean());
                break;
            }
            case BINARY_HEAT: {
                BthomeServiceData.BthomeBinaryHeat m = (BthomeServiceData.BthomeBinaryHeat) measurement.data();
                state = toSwitchState(m.heat());
                break;
            }
            case BINARY_LIGHT: {
                BthomeServiceData.BthomeBinaryLight m = (BthomeServiceData.BthomeBinaryLight) measurement.data();
                state = toSwitchState(m.light());
                break;
            }
            case BINARY_LOCK: {
                BthomeServiceData.BthomeBinaryLock m = (BthomeServiceData.BthomeBinaryLock) measurement.data();
                state = toSwitchState(m.lock());
                break;
            }
            case BINARY_MOISTURE: {
                BthomeServiceData.BthomeBinaryMoisture m = (BthomeServiceData.BthomeBinaryMoisture) measurement.data();
                state = toSwitchState(m.moisture());
                break;
            }
            case BINARY_MOTION: {
                BthomeServiceData.BthomeBinaryMotion m = (BthomeServiceData.BthomeBinaryMotion) measurement.data();
                state = toSwitchState(m.motion());
                break;
            }
            case BINARY_MOVING: {
                BthomeServiceData.BthomeBinaryMoving m = (BthomeServiceData.BthomeBinaryMoving) measurement.data();
                state = toSwitchState(m.moving());
                break;
            }
            case BINARY_OCCUPANCY: {
                BthomeServiceData.BthomeBinaryOccupancy m = (BthomeServiceData.BthomeBinaryOccupancy) measurement
                        .data();
                state = toSwitchState(m.occupancy());
                break;
            }
            case BINARY_OPENING: {
                BthomeServiceData.BthomeBinaryOpening m = (BthomeServiceData.BthomeBinaryOpening) measurement.data();
                state = toContactState(m.opening());
                break;
            }
            case BINARY_PLUG: {
                BthomeServiceData.BthomeBinaryPlug m = (BthomeServiceData.BthomeBinaryPlug) measurement.data();
                state = toSwitchState(m.plug());
                break;
            }
            case BINARY_POWER: {
                BthomeServiceData.BthomeBinaryPower m = (BthomeServiceData.BthomeBinaryPower) measurement.data();
                state = toSwitchState(m.power());
                break;
            }
            case BINARY_PRESENCE: {
                BthomeServiceData.BthomeBinaryPresence m = (BthomeServiceData.BthomeBinaryPresence) measurement.data();
                state = toSwitchState(m.presence());
                break;
            }
            case BINARY_PROBLEM: {
                BthomeServiceData.BthomeBinaryProblem m = (BthomeServiceData.BthomeBinaryProblem) measurement.data();
                state = toSwitchState(m.problem());
                break;
            }
            case BINARY_RUNNING: {
                BthomeServiceData.BthomeBinaryRunning m = (BthomeServiceData.BthomeBinaryRunning) measurement.data();
                state = toSwitchState(m.running());
                break;
            }
            case BINARY_SAFETY: {
                BthomeServiceData.BthomeBinarySafety m = (BthomeServiceData.BthomeBinarySafety) measurement.data();
                state = toSwitchState(m.safety());
                break;
            }
            case BINARY_SMOKE: {
                BthomeServiceData.BthomeBinarySmoke m = (BthomeServiceData.BthomeBinarySmoke) measurement.data();
                state = toSwitchState(m.smoke());
                break;
            }
            case BINARY_SOUND: {
                BthomeServiceData.BthomeBinarySound m = (BthomeServiceData.BthomeBinarySound) measurement.data();
                state = toSwitchState(m.sound());
                break;
            }
            case BINARY_TAMPER: {
                BthomeServiceData.BthomeBinaryTamper m = (BthomeServiceData.BthomeBinaryTamper) measurement.data();
                state = toSwitchState(m.tamper());
                break;
            }
            case BINARY_VIBRATION: {
                BthomeServiceData.BthomeBinaryVibration m = (BthomeServiceData.BthomeBinaryVibration) measurement
                        .data();
                state = toSwitchState(m.vibration());
                break;
            }
            case BINARY_WINDOW: {
                BthomeServiceData.BthomeBinaryWindow m = (BthomeServiceData.BthomeBinaryWindow) measurement.data();
                state = toContactState(m.window());
                break;
            }

            case EVENT_BUTTON: {
                BthomeServiceData.BthomeEventButton m = (BthomeServiceData.BthomeEventButton) measurement.data();
                BthomeServiceData.ButtonEventType event = m.event();
                triggerChannel(channel.getUID(), event.toString());
                break;
            }
            case EVENT_DIMMER: {
                BthomeServiceData.BthomeEventDimmer m = (BthomeServiceData.BthomeEventDimmer) measurement.data();
                BthomeServiceData.DimmerEventType event = m.event();
                // Will trigger values NONE, ROTATE_LEFT_X, ROTATE_RIGHT_X where X is the number of steps
                triggerChannel(channel.getUID(), event.toString() + (m.steps() > 0 ? "_" + m.steps() : ""));
                break;
            }
        }
        if (state != null) {
            updateState(channel.getUID(), state);
        }
    }

    @Nullable
    private Channel getChannel(BthomeServiceData.BthomeObjectId bthomeObjectId, List<Channel> currentChannels,
            boolean multipleChannelsPerMeasurement, int counter) {
        BTHomeTypeMapping typeMapping = BTHomeTypeMapping.fromBthomeObjectId(bthomeObjectId);
        if (typeMapping == null) {
            return null;
        }

        ChannelUID channelUID = new ChannelUID(getThing().getUID(),
                typeMapping.getChannelName() + (multipleChannelsPerMeasurement ? "_" + counter : ""));
        return currentChannels.stream().filter(c -> c.getUID().equals(channelUID)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No channel found for channel UID: " + channelUID));
    }

    private List<Channel> createMissingChannels(List<Channel> currentChannels,
            Map<BthomeServiceData.BthomeObjectId, List<BthomeServiceData.BthomeMeasurement>> measurements) {

        List<Channel> newChannels = new ArrayList<>();

        Set<BthomeServiceData.BthomeObjectId> bthomeObjectIds = measurements.keySet();

        for (BthomeServiceData.BthomeObjectId bthomeObjectId : bthomeObjectIds) {

            @Nullable
            BTHomeTypeMapping typeMapping = BTHomeTypeMapping.fromBthomeObjectId(bthomeObjectId);
            if (typeMapping == null) {
                logger.warn(
                        "No type mapping found for object id: {}, ignoring (Note: Lack of support in binding, create a PR or issue at https://github.com/seime/openhab-bthome)",
                        bthomeObjectId);
                continue;
            }

            List<BthomeServiceData.BthomeMeasurement> bthomeMeasurements = measurements.get(bthomeObjectId);

            List<Channel> channels = getOrCreateChannels(currentChannels, typeMapping,
                    bthomeMeasurements != null ? bthomeMeasurements.size() : 0);
            newChannels.addAll(channels);

        }

        if (!newChannels.isEmpty()) {
            updateThing(editThing().withChannels(newChannels).build());
        }

        return newChannels;
    }

    private State toSwitchState(BthomeServiceData.Bool8 value) {
        return value.value() ? OnOffType.ON : OnOffType.OFF;
    }

    private State toContactState(BthomeServiceData.Bool8 value) {
        return value.value() ? OpenClosedType.OPEN : OpenClosedType.CLOSED;
    }

    private State toNumericState(Channel channel, @Nullable String unitString, Number value) {

        if (unitString != null) {
            Unit<?> unit = UnitUtils.parseUnit(unitString);
            if (unit != null) {
                return new QuantityType<>(value, unit);
            } else {
                logger.warn("Unit '{}' unknown to openHAB, returning DecimalType for state '{}' on channel '{}'",
                        unitString, value, channel.getUID());
                return new DecimalType(value);

            }
        } else {
            return new DecimalType(value);
        }
    }

    private List<Channel> getOrCreateChannels(List<Channel> currentChannels, BTHomeTypeMapping typeMapping,
            int numMeasurements) {

        boolean multipleMeasurementsOfSameType = numMeasurements > 1;

        List<Channel> channels = new ArrayList<>();

        for (int counter = 1; counter <= numMeasurements; counter++) {
            String channelName = typeMapping.getChannelName() + (multipleMeasurementsOfSameType ? "_" + counter : "");
            ChannelUID channelUID = new ChannelUID(getThing().getUID(), channelName);
            Channel existingChannel = currentChannels.stream().filter(c -> c.getUID().equals(channelUID)).findFirst()
                    .orElse(null);
            if (existingChannel == null) {
                String channelLabel = channelName.substring(0, 1).toUpperCase() + channelName.substring(1)
                        + (multipleMeasurementsOfSameType ? "_" + counter : "");

                if (typeMapping.getChannelKind() == ChannelKind.TRIGGER) {
                    ChannelType newChannelType = createTriggerChannelType(channelName, channelLabel, Set.of("Property"),
                            typeMapping.getCategory());
                    Channel newChannel = ChannelBuilder.create(channelUID).withLabel(channelLabel)
                            .withKind(typeMapping.getChannelKind()).withType(newChannelType.getUID()).build();
                    dynamicChannelTypeProvider.putChannelType(newChannelType);
                    channels.add(newChannel);
                } else {
                    ChannelType newChannelType = createStateChannelType(channelName, channelName,
                            typeMapping.getItemType(), Set.of("Property"), typeMapping.getCategory());

                    Channel newChannel = ChannelBuilder.create(channelUID).withLabel(channelLabel)
                            .withKind(typeMapping.getChannelKind()).withType(newChannelType.getUID())
                            .withAcceptedItemType(typeMapping.getItemType()).build();

                    dynamicChannelTypeProvider.putChannelType(newChannelType);
                    channels.add(newChannel);
                }
            } else {
                channels.add(existingChannel);
            }
        }

        return channels;
    }

    protected ChannelType createStateChannelType(final String channelIdPrefix, final String label,
            final String itemType, @Nullable final Set<String> tags, @Nullable String category) {
        String uid = String.format("bthome-%s-%s", getThing().getUID().getId(), channelIdPrefix);
        final ChannelTypeUID channelTypeUID = new ChannelTypeUID("bluetooth", uid);

        StateDescriptionFragmentBuilder stateDescription = StateDescriptionFragmentBuilder.create().withReadOnly(true);

        final StateChannelTypeBuilder channelTypeBuilder = ChannelTypeBuilder.state(channelTypeUID, label, itemType)
                .withStateDescriptionFragment(stateDescription.build());
        if (tags != null && !tags.isEmpty()) {
            channelTypeBuilder.withTags(tags);
        }

        if (category != null) {
            channelTypeBuilder.withCategory(category);
        }

        // channelTypeBuilder.withAutoUpdatePolicy(AutoUpdatePolicy.VETO);

        ChannelType channelType = channelTypeBuilder.build();
        logger.debug("Created new channel type {}", channelType.getUID());
        return channelType;
    }

    // TODO combine with state version
    protected ChannelType createTriggerChannelType(final String channelIdPrefix, final String label,
            @Nullable final Set<String> tags, @Nullable String category) {
        String uid = String.format("bthome-%s-%s", getThing().getUID().getId(), channelIdPrefix);
        final ChannelTypeUID channelTypeUID = new ChannelTypeUID("bluetooth", uid);

        final TriggerChannelTypeBuilder channelTypeBuilder = ChannelTypeBuilder.trigger(channelTypeUID, label);
        if (tags != null && !tags.isEmpty()) {
            channelTypeBuilder.withTags(tags);
        }

        if (category != null) {
            channelTypeBuilder.withCategory(category);
        }

        ChannelType channelType = channelTypeBuilder.build();
        logger.debug("Created new channel type {}", channelType.getUID());
        return channelType;
    }

    @Override
    public void handleRemoval() {
        dynamicChannelTypeProvider.removeChannelTypesForThing(thing.getUID());
        super.handleRemoval();
    }
}
