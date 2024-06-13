package no.seime.openhab.binding.bluetooth.bthome.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.kaitai.struct.ByteBufferKaitaiStream;
import io.kaitai.struct.KaitaiStream;
import no.seime.openhab.binding.bluetooth.bthome.internal.datastructure.BthomeServiceData;

public class ParserTest {

    @Test
    public void testParseBParasite() {
        KaitaiStream stream = getByteArraAsStream("64, 2, -54, 9, 46, 40, 5, 0, 0, 0, 12, -10, 12, 47, 0, 1, 100");

        BthomeServiceData bthomeServiceData = new BthomeServiceData(stream);
        assertEquals(6, bthomeServiceData.measurement().size());
    }

    private KaitaiStream getByteArraAsStream(String byteStringWithCommas) {
        String[] segments = byteStringWithCommas.split(",");

        int pos = 0;
        byte[] data = new byte[segments.length];
        for (String segment : segments) {

            data[pos++] = (byte) Integer.parseInt(segment.trim());
        }

        return new ByteBufferKaitaiStream(data);
    }
}
