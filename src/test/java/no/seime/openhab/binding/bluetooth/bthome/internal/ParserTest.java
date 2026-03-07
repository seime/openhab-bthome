package no.seime.openhab.binding.bluetooth.bthome.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import io.kaitai.struct.ByteBufferKaitaiStream;
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

    @Test
    public void testEncryption() throws Exception {
        // Example from bthome-ble: test_encryption_example_2
        byte[] key = Hex.decode("231d39c1d7cc1ab1aee224cd096db932");
        byte[] mac = Hex.decode("5448E68F80A5");
        // Nonce: MAC (6) + UUID (2, 0xD2 0xFC) + sw_version (1, 0x41) + counter (4, 0x00112233)
        // Note: mac is NOT reversed here, as it matches the bytes from python's binascii.unhexlify("5448E68F80A5")
        byte[] nonce = Hex.decode("5448E68F80A5D2FC4100112233");
        byte[] ciphertext = Hex.decode("87b9");
        byte[] mic = Hex.decode("72b0231f");

        byte[] encryptedData = new byte[ciphertext.length + mic.length];
        System.arraycopy(ciphertext, 0, encryptedData, 0, ciphertext.length);
        System.arraycopy(mic, 0, encryptedData, ciphertext.length, mic.length);

        CCMBlockCipher cipher = new CCMBlockCipher(new AESEngine());
        // AAD is null for V2
        AEADParameters parameters = new AEADParameters(new KeyParameter(key), 32, nonce, null);
        cipher.init(false, parameters);
        byte[] decryptedPayload = cipher.processPacket(encryptedData, 0, encryptedData.length);

        assertEquals("2101", Hex.toHexString(decryptedPayload));

        // Note: Decrypted payload only contains measurements.
        // We can't use BthomeServiceData directly because it expects deviceInformation at the start.
        KaitaiStream decryptedStream = new ByteBufferKaitaiStream(decryptedPayload);
        // We need a dummy BthomeServiceData instance as parent for BthomeMeasurement
        // Since BthomeServiceData doesn't have a default constructor that doesn't read from stream,
        // we'll use one initialized with dummy data.
        BthomeServiceData dummy = new BthomeServiceData(new ByteBufferKaitaiStream(new byte[] { 0x40 }));
        BthomeServiceData.BthomeMeasurement measurement = new BthomeServiceData.BthomeMeasurement(decryptedStream,
                dummy, dummy);
        assertEquals(BthomeServiceData.BthomeObjectId.BINARY_MOTION, measurement.objectId());
    }
}
