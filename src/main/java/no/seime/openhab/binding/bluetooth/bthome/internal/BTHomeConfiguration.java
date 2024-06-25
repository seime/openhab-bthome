package no.seime.openhab.binding.bluetooth.bthome.internal;

import org.openhab.core.config.core.Configuration;

public class BTHomeConfiguration extends Configuration {
    public String address;
    public int expectedReportingIntervalSeconds = 3600;

    public BTHomeConfiguration(String address, int expectedReportingIntervalSeconds) {
        this.address = address;
        this.expectedReportingIntervalSeconds = expectedReportingIntervalSeconds;
    }

    public BTHomeConfiguration() {
    }
}
