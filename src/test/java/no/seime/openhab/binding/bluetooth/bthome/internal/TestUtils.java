package no.seime.openhab.binding.bluetooth.bthome.internal;

import io.kaitai.struct.ByteBufferKaitaiStream;
import io.kaitai.struct.KaitaiStream;

public class TestUtils {
    public static KaitaiStream getByteArraAsStream(String byteStringWithCommas) {

        return new ByteBufferKaitaiStream(byteStringToByteArray(byteStringWithCommas));
    }

    public static byte[] byteStringToByteArray(String byteStringWithCommas) {
        String[] segments = byteStringWithCommas.split(",");

        int pos = 0;
        byte[] data = new byte[segments.length];
        for (String segment : segments) {

            data[pos++] = (byte) Integer.parseInt(segment.trim());
        }

        return data;
    }
}
