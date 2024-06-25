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

    private @NonNullByDefault({}) ScheduledFuture<?> heartbeatFuture;

    private int lastPacketId = -1;

    public BTHomeHandler(Thing thing, BTHomeChannelTypeProvider dynamicChannelTypeProvider) {
        super(thing);
        this.dynamicChannelTypeProvider = dynamicChannelTypeProvider;
    }

    private long heartbeatDelay = 3600;

    @Override
    public void initialize() {
        super.initialize();

        updateStatus(ThingStatus.UNKNOWN, ThingStatusDetail.NONE, "Waiting for device to wake up.");

        BTHomeConfiguration config = getConfig().as(BTHomeConfiguration.class);
        heartbeatDelay = (long) (config.expectedReportingIntervalSeconds * 1.1);
        heartbeatFuture = scheduler.scheduleWithFixedDelay(this::heartbeat, heartbeatDelay, heartbeatDelay,
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
    public void dispose() {
        try {
            super.dispose();
        } finally {
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(true);
                heartbeatFuture = null;
            }
        }
    }

    @Override
    protected List<Channel> createDynamicChannels() {
        return new ArrayList<>();
    }

    private byte[] cachedBthomeData = new byte[0];

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

            Map<String, byte[]> serviceData = scanNotification.getServiceData();
            byte[] updatedBthomeData = serviceData.get(BTHomeBindingConstants.SERVICEDATA_UUID);

            // Cache the data for the refresh command
            if (updatedBthomeData != null) {
                logger.debug("Received updated BTHome data");
                cachedBthomeData = updatedBthomeData;
                processDataPacket(cachedBthomeData);
            }
        }
    }

    public void processDataPacket(byte[] bthomeData) {
        if (bthomeData.length != 0) {
            try {
                BthomeServiceData deviceData = new BthomeServiceData(new ByteBufferKaitaiStream(bthomeData));
                ArrayList<BthomeServiceData.BthomeMeasurement> measurements = deviceData.measurement();

                // Check if we have a new packetId
                Optional<BthomeServiceData.BthomeMeasurement> packetField = measurements.stream()
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

                Map<BthomeServiceData.BthomeObjectId, List<BthomeServiceData.BthomeMeasurement>> measurementsPerType = measurements
                        .stream().collect(groupingBy(e -> e.objectId()));

                List<Channel> currentChannels = getThing().getChannels();
                List<Channel> allChannels = new ArrayList<>(currentChannels);
                allChannels.addAll(createMissingChannels(currentChannels, measurementsPerType));

                for (BthomeServiceData.BthomeObjectId objectId : measurementsPerType.keySet()) {
                    List<BthomeServiceData.BthomeMeasurement> measurementsOfType = measurementsPerType.get(objectId);

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

    private void updateChannelValue(BthomeServiceData.BthomeMeasurement measurement,
            BthomeServiceData.BthomeObjectId bthomeObjectId, Channel channel) {
        switch (bthomeObjectId) {
            case MISC_PACKET_ID: {
                BthomeServiceData.BthomeMiscPacketId m = (BthomeServiceData.BthomeMiscPacketId) measurement.data();
                State state = new DecimalType(m.packetId());
                updateState(channel.getUID(), state);
                break;
            }

            case SENSOR_BATTERY: {
                BthomeServiceData.BthomeSensorBattery m = (BthomeServiceData.BthomeSensorBattery) measurement.data();
                State state = toNumericState(channel, m.unit(), m.battery());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_TEMPERATURE_0_01: {
                BthomeServiceData.BthomeSensorTemperature001 m = (BthomeServiceData.BthomeSensorTemperature001) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.temperature());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_HUMIDITY_0_01: {
                BthomeServiceData.BthomeSensorHumidity m = (BthomeServiceData.BthomeSensorHumidity) measurement.data();
                State state = toNumericState(channel, m.unit(), m.humidity());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_PRESSURE_0_01: {
                BthomeServiceData.BthomeSensorPressure001 m = (BthomeServiceData.BthomeSensorPressure001) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.pressure());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_ILLUMINANCE_0_01: {
                BthomeServiceData.BthomeSensorIlluminance001 m = (BthomeServiceData.BthomeSensorIlluminance001) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.illuminance());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_MASS_KG_0_01: {
                BthomeServiceData.BthomeSensorMassKg001 m = (BthomeServiceData.BthomeSensorMassKg001) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.mass());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_MASS_LB_0_01: {
                BthomeServiceData.BthomeSensorMassLb001 m = (BthomeServiceData.BthomeSensorMassLb001) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.mass());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_DEWPOINT_0_01: {
                BthomeServiceData.BthomeSensorDewpoint001 m = (BthomeServiceData.BthomeSensorDewpoint001) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.dewPoint());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_COUNT: {
                BthomeServiceData.BthomeSensorCount m = (BthomeServiceData.BthomeSensorCount) measurement.data();
                State state = toNumericState(channel, null, m.count());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_ENERGY_0_001: {
                BthomeServiceData.BthomeSensorEnergy0001 m = (BthomeServiceData.BthomeSensorEnergy0001) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.energy());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_POWER_0_01: {
                BthomeServiceData.BthomeSensorPower001 m = (BthomeServiceData.BthomeSensorPower001) measurement.data();
                State state = toNumericState(channel, m.unit(), m.power());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_VOLTAGE_0_001: {
                BthomeServiceData.BthomeSensorVoltage0001 m = (BthomeServiceData.BthomeSensorVoltage0001) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.voltage());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_PM2_5: {
                BthomeServiceData.BthomeSensorPm25 m = (BthomeServiceData.BthomeSensorPm25) measurement.data();
                State state = toNumericState(channel, m.unit(), m.pm25());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_PM10: {
                BthomeServiceData.BthomeSensorPm10 m = (BthomeServiceData.BthomeSensorPm10) measurement.data();
                State state = toNumericState(channel, m.unit(), m.pm10());
                updateState(channel.getUID(), state);
                break;
            }
            case BINARY_GENERIC_BOOLEAN: {
                BthomeServiceData.BthomeBinaryGenericBoolean m = (BthomeServiceData.BthomeBinaryGenericBoolean) measurement
                        .data();
                State state = toSwitchState(m.genericBoolean());
                updateState(channel.getUID(), state);
                break;
            }
            // case BINARY_POWER: { BthomeServiceData.BthomeBinaryPower m =
            // (BthomeServiceData.BthomeBinaryPower) measurement
            // .data();
            // State state = toSwitchState(m.power());
            // updateState(channel.getUID(), state);break;}
            // case BINARY_OPENING: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            case SENSOR_CO2: {
                BthomeServiceData.BthomeSensorCo2 m = (BthomeServiceData.BthomeSensorCo2) measurement.data();
                State state = toNumericState(channel, m.unit(), m.co2());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_TVOC: {
                BthomeServiceData.BthomeSensorTvoc m = (BthomeServiceData.BthomeSensorTvoc) measurement.data();
                State state = toNumericState(channel, m.unit(), m.tvoc());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_MOISTURE_0_01: {
                BthomeServiceData.BthomeSensorMoisture001 m = (BthomeServiceData.BthomeSensorMoisture001) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.moisture());
                updateState(channel.getUID(), state);
                break;
            }
            // TODO implement these
            // case BINARY_BATTERY: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_BATTERY_CHARGING: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome)
            // measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_CARBON_MONOXIDE: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome)
            // measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_COLD: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_CONNECTIVITY: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome)
            // measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_DOOR: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_GARAGE_DOOR: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome)
            // measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_GAS: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_HEAT: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_LIGHT: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_LOCK: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_MOISTURE: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            case BINARY_MOTION: {
                BthomeServiceData.BthomeBinaryMotion m = (BthomeServiceData.BthomeBinaryMotion) measurement.data();
                State state = toSwitchState(m.motion());
                updateState(channel.getUID(), state);
                break;
            }
            // case BINARY_MOVING: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_OCCUPANCY: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_PLUG: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_PRESENCE: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_PROBLEM: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_RUNNING: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_SAFETY: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_SMOKE: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_SOUND: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_TAMPER: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            // case BINARY_VIBRATION: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            case BINARY_WINDOW: {
                BthomeServiceData.BthomeBinaryWindow m = (BthomeServiceData.BthomeBinaryWindow) measurement.data();
                State state = toOpenClosedState(m.window());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_HUMIDITY: {
                BthomeServiceData.BthomeSensorHumidity m = (BthomeServiceData.BthomeSensorHumidity) measurement.data();
                State state = toNumericState(channel, m.unit(), m.humidity());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_MOISTURE: {
                BthomeServiceData.BthomeSensorMoisture m = (BthomeServiceData.BthomeSensorMoisture) measurement.data();
                State state = toNumericState(channel, m.unit(), m.moisture());
                updateState(channel.getUID(), state);
                break;
            }
            case EVENT_BUTTON: {
                BthomeServiceData.BthomeEventButton m = (BthomeServiceData.BthomeEventButton) measurement.data();
                BthomeServiceData.ButtonEventType event = m.event();
                triggerChannel(channel.getUID(), event.toString());
                break;
            }
            // case EVENT_DIMMER: { BthomeServiceData.Bthome m = (BthomeServiceData.Bthome) measurement
            // .data();
            // State state = toNumericState(channel, m.unit(), m.())
            // updateState(channel.getUID(), state);break;}
            case SENSOR_COUNT_UINT16: {
                BthomeServiceData.BthomeSensorCountUint16 m = (BthomeServiceData.BthomeSensorCountUint16) measurement
                        .data();
                State state = toNumericState(channel, null, m.count());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_COUNT_UINT32: {
                BthomeServiceData.BthomeSensorCountUint32 m = (BthomeServiceData.BthomeSensorCountUint32) measurement
                        .data();
                State state = toNumericState(channel, null, m.count());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_ROTATION_0_1: {
                BthomeServiceData.BthomeSensorRotation01 m = (BthomeServiceData.BthomeSensorRotation01) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.rotation());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_DISTANCE_MM: {
                BthomeServiceData.BthomeSensorDistanceMm m = (BthomeServiceData.BthomeSensorDistanceMm) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.distance());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_DISTANCE_M_0_1: {
                BthomeServiceData.BthomeSensorDistanceM01 m = (BthomeServiceData.BthomeSensorDistanceM01) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.distance());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_DURATION_0_001: {
                BthomeServiceData.BthomeSensorDuration0001 m = (BthomeServiceData.BthomeSensorDuration0001) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.duration());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_CURRENT_0_001: {
                BthomeServiceData.BthomeSensorCurrent0001 m = (BthomeServiceData.BthomeSensorCurrent0001) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.current());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_SPEED_0_01: {
                BthomeServiceData.BthomeSensorSpeed001 m = (BthomeServiceData.BthomeSensorSpeed001) measurement.data();
                State state = toNumericState(channel, m.unit(), m.speed());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_TEMPERATURE_0_1: {
                BthomeServiceData.BthomeSensorTemperature01 m = (BthomeServiceData.BthomeSensorTemperature01) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.temperature());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_UV_INDEX_0_1: {
                BthomeServiceData.BthomeSensorUvIndex01 m = (BthomeServiceData.BthomeSensorUvIndex01) measurement
                        .data();
                State state = toNumericState(channel, null, m.uvIndex());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_VOLUME_0_1: {
                BthomeServiceData.BthomeSensorVolume01 m = (BthomeServiceData.BthomeSensorVolume01) measurement.data();
                State state = toNumericState(channel, m.unit(), m.volume());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_VOLUME: {
                BthomeServiceData.BthomeSensorVolume m = (BthomeServiceData.BthomeSensorVolume) measurement.data();
                State state = toNumericState(channel, m.unit(), m.volume());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_VOLUME_FLOW_RATE_0_001: {
                BthomeServiceData.BthomeSensorVolumeFlowRate0001 m = (BthomeServiceData.BthomeSensorVolumeFlowRate0001) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.volumeFlowRate());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_VOLTAGE_0_1: {
                BthomeServiceData.BthomeSensorVoltage01 m = (BthomeServiceData.BthomeSensorVoltage01) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.voltage());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_GAS: {
                BthomeServiceData.BthomeSensorGas m = (BthomeServiceData.BthomeSensorGas) measurement.data();
                State state = toNumericState(channel, m.unit(), m.gas());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_GAS_UINT32: {
                BthomeServiceData.BthomeSensorGasUint32 m = (BthomeServiceData.BthomeSensorGasUint32) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.gas());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_ENERGY_0_001_UINT32: {
                BthomeServiceData.BthomeSensorEnergy0001Uint32 m = (BthomeServiceData.BthomeSensorEnergy0001Uint32) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.energy());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_VOLUME_0_001: {
                BthomeServiceData.BthomeSensorVolume0001 m = (BthomeServiceData.BthomeSensorVolume0001) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.volume());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_WATER: {
                BthomeServiceData.BthomeSensorWater m = (BthomeServiceData.BthomeSensorWater) measurement.data();
                State state = toNumericState(channel, m.unit(), m.water());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_TIMESTAMP: {
                BthomeServiceData.BthomeSensorTimestamp m = (BthomeServiceData.BthomeSensorTimestamp) measurement
                        .data();
                State state = new DateTimeType(
                        ZonedDateTime.ofInstant(Instant.ofEpochSecond(m.value()), ZoneId.systemDefault()));
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_ACCELERATION: {
                BthomeServiceData.BthomeSensorAcceleration m = (BthomeServiceData.BthomeSensorAcceleration) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.acceleration());
                updateState(channel.getUID(), state);
                break;
            }
            case SENSOR_GYROSCOPE: {
                BthomeServiceData.BthomeSensorGyroscope m = (BthomeServiceData.BthomeSensorGyroscope) measurement
                        .data();
                State state = toNumericState(channel, m.unit(), m.gyroscope());
                updateState(channel.getUID(), state);
                break;
            }

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

    private State toOpenClosedState(BthomeServiceData.Bool8 value) {
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
