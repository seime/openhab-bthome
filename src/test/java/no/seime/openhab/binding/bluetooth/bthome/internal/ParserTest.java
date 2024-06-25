package no.seime.openhab.binding.bluetooth.bthome.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.kaitai.struct.KaitaiStream;
import no.seime.openhab.binding.bluetooth.bthome.internal.datastructure.BthomeServiceData;

public class ParserTest {

    @Test
    public void testParseBParasite() {
        KaitaiStream stream = TestUtils
                .getByteArraAsStream("64, 2, -54, 9, 46, 40, 5, 0, 0, 0, 12, -10, 12, 47, 0, 1, 100,1,100");

        BthomeServiceData bthomeServiceData = new BthomeServiceData(stream);
        assertEquals(7, bthomeServiceData.measurement().size());
    }
}
