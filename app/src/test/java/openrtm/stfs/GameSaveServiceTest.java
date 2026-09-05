package openrtm.stfs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSaveServiceTest {
    private static final int ALIGNED_HEADER_SIZE = 0xA000;

    @TempDir
    Path temporaryDirectory;

    @Test
    void changesAssignmentAndProducesValidHashAndSignature() throws Exception {
        Path source = temporaryDirectory.resolve("original-save");
        Path output = temporaryDirectory.resolve("resigned-save");
        byte[] original = packageBytes();
        Files.write(source, original);
        GameSaveService service = new GameSaveService();

        GameSaveService.SaveResult result = service.save(
                source,
                output,
                new GameSaveService.Assignment(
                        "E0000123456789AB",
                        "0123456789",
                        "00112233445566778899AABBCCDDEEFF00112233"),
                false);

        GameSaveService.Info info = result.info();
        assertEquals("E0000123456789AB", info.profileId());
        assertEquals("0123456789", info.consoleId());
        assertEquals("00112233445566778899AABBCCDDEEFF00112233", info.deviceId());
        assertEquals("4D5307E6", info.titleId());
        assertTrue(info.headerHashValid());
        assertTrue(info.signatureValid());
        assertNull(result.backup());
        assertArrayEquals(original, Files.readAllBytes(source));

        byte[] rewritten = Files.readAllBytes(output);
        assertArrayEquals(HexFormat.of().parseHex("E0000123456789AB"),
                Arrays.copyOfRange(rewritten, 0x371, 0x379));
        assertArrayEquals(HexFormat.of().parseHex("0123456789"),
                Arrays.copyOfRange(rewritten, 0x36C, 0x371));
        assertArrayEquals(HexFormat.of().parseHex("00112233445566778899AABBCCDDEEFF00112233"),
                Arrays.copyOfRange(rewritten, 0x3FD, 0x411));
        assertArrayEquals(
                MessageDigest.getInstance("SHA-1").digest(
                        Arrays.copyOfRange(rewritten, 0x344, ALIGNED_HEADER_SIZE)),
                Arrays.copyOfRange(rewritten, 0x32C, 0x340));

        byte[] expected = original.clone();
        copyRange(rewritten, expected, 0x004, 0x22C);
        copyRange(rewritten, expected, 0x32C, 0x340);
        copyRange(rewritten, expected, 0x36C, 0x379);
        copyRange(rewritten, expected, 0x3FD, 0x411);
        assertArrayEquals(expected, rewritten);
    }

    @Test
    void inPlaceSaveCreatesBackupBeforeReplacingPackage() throws Exception {
        Path source = temporaryDirectory.resolve("savegame");
        byte[] original = packageBytes();
        Files.write(source, original);
        GameSaveService service = new GameSaveService();

        GameSaveService.SaveResult result = service.save(
                source,
                source,
                new GameSaveService.Assignment("", "", ""),
                true);

        assertEquals(source.resolveSibling("savegame.bak"), result.backup());
        assertArrayEquals(original, Files.readAllBytes(result.backup()));
        assertEquals("0000000000000000", result.info().profileId());
        assertEquals("0000000000", result.info().consoleId());
        assertEquals("0000000000000000000000000000000000000000", result.info().deviceId());
        assertTrue(result.info().headerHashValid());
        assertTrue(result.info().signatureValid());
    }

    @Test
    void rejectsWrongLengthAndNonSavePackages() throws Exception {
        Path source = temporaryDirectory.resolve("savegame");
        Files.write(source, packageBytes());
        GameSaveService service = new GameSaveService();

        assertThrows(IllegalArgumentException.class, () -> service.save(
                source, source,
                new GameSaveService.Assignment("1234", "0123456789", ""),
                false));

        byte[] wrongType = packageBytes();
        putInt(wrongType, GameSaveService.CONTENT_TYPE_OFFSET, 0x00010000);
        Files.write(source, wrongType);
        assertThrows(java.io.IOException.class, () -> service.inspect(source));
    }

    @Test
    void detectsHeaderAndSignatureTamperingIndependently() throws Exception {
        Path source = temporaryDirectory.resolve("savegame");
        Files.write(source, packageBytes());
        GameSaveService service = new GameSaveService();
        service.save(source, source, new GameSaveService.Assignment("", "", ""), false);

        byte[] data = Files.readAllBytes(source);
        data[GameSaveService.DEVICE_ID_OFFSET] ^= 0x01;
        Files.write(source, data);
        GameSaveService.Info metadataTampered = service.inspect(source);
        assertFalse(metadataTampered.headerHashValid());
        assertTrue(metadataTampered.signatureValid());

        data[GameSaveService.CONTENT_HASH_OFFSET] ^= 0x01;
        Files.write(source, data);
        GameSaveService.Info hashTampered = service.inspect(source);
        assertFalse(hashTampered.headerHashValid());
        assertFalse(hashTampered.signatureValid());
    }

    private static byte[] packageBytes() {
        byte[] data = new byte[ALIGNED_HEADER_SIZE + 0x3000];
        for (int i = GameSaveService.METADATA_OFFSET; i < data.length; i++) {
            data[i] = (byte) (i * 29 + 11);
        }
        data[0] = 'C';
        data[1] = 'O';
        data[2] = 'N';
        data[3] = ' ';
        putInt(data, GameSaveService.HEADER_SIZE_OFFSET, ALIGNED_HEADER_SIZE);
        putInt(data, GameSaveService.CONTENT_TYPE_OFFSET, 0x00000001);
        putInt(data, GameSaveService.TITLE_ID_OFFSET, 0x4D5307E6);
        putInt(data, GameSaveService.VOLUME_TYPE_OFFSET, 0);
        return data;
    }

    private static void putInt(byte[] data, int offset, int value) {
        ByteBuffer.wrap(data, offset, 4).order(ByteOrder.BIG_ENDIAN).putInt(value);
    }

    private static void copyRange(byte[] source, byte[] destination, int from, int to) {
        System.arraycopy(source, from, destination, from, to - from);
    }
}
