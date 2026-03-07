package no.seime.openhab.binding.bluetooth.bthome.internal;

import org.openhab.core.config.core.Configuration;

public class BTHomeConfiguration extends Configuration {
    public String address;
    public int expectedReportingIntervalSeconds = 3600;
    public String encryptionKey;

    public BTHomeConfiguration(String address, int expectedReportingIntervalSeconds, String encryptionKey) {
        this.address = address;
        this.expectedReportingIntervalSeconds = expectedReportingIntervalSeconds;
        this.encryptionKey = encryptionKey;
    }

    public BTHomeConfiguration() {
    }
}
