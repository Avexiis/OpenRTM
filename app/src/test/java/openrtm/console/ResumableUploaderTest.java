package openrtm.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumableUploaderTest {
    @TempDir
    Path tempDirectory;

    @Test
    void verifiesAndRepairsAnExistingCorruptFile() throws Exception {
        byte[] localData = data(ResumableUploader.CHUNK_SIZE + 19_321);
        Path local = tempDirectory.resolve("game.iso");
        Files.write(local, localData);

        FakeRemote remote = new FakeRemote(localData);
        remote.data[97] ^= 0x5A;
        remote.data[ResumableUploader.CHUNK_SIZE + 42] ^= 0x2C;

        ResumableUploader.upload(local, "Hdd:\\game.iso", remote, () -> false, (done, total, message) -> {
        });

        assertArrayEquals(localData, remote.data);
        assertEquals(2, remote.writes);
    }

    @Test
    void reconnectsEnumeratesPartialDataAndResumesAfterDroppedWrite() throws Exception {
        byte[] localData = data(ResumableUploader.CHUNK_SIZE * 2 + 15_777);
        Path local = tempDirectory.resolve("large.iso");
        Files.write(local, localData);

        int existing = ResumableUploader.CHUNK_SIZE + 12_345;
        FakeRemote remote = new FakeRemote(Arrays.copyOf(localData, existing));
        remote.dropNextWrite = true;
        long started = System.nanoTime();

        ResumableUploader.upload(local, "Hdd:\\large.iso", remote, () -> false, (done, total, message) -> {
        });

        assertArrayEquals(localData, remote.data);
        assertEquals(1, remote.reconnects);
        assertTrue(remote.sizeChecks >= 3);
        assertTrue(System.nanoTime() - started >= 1_900_000_000L);
    }

    private static byte[] data(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31 + 7);
        return data;
    }

    private static final class FakeRemote implements ResumableUploader.RemoteFile {
        private byte[] data;
        private int writes;
        private int reconnects;
        private int sizeChecks;
        private boolean dropNextWrite;

        private FakeRemote(byte[] data) {
            this.data = data.clone();
        }

        @Override
        public long size(String path) {
            sizeChecks++;
            return data.length;
        }

        @Override
        public byte[] read(String path, long offset, int length) {
            int start = Math.toIntExact(offset);
            return Arrays.copyOfRange(data, start, Math.min(data.length, start + length));
        }

        @Override
        public void write(String path, long offset, byte[] bytes) throws ResumableUploader.RemoteException {
            writes++;
            int start = Math.toIntExact(offset);
            if (dropNextWrite) {
                dropNextWrite = false;
                int partial = bytes.length / 2;
                ensureLength(start + partial);
                System.arraycopy(bytes, 0, data, start, partial);
                throw new ResumableUploader.RemoteException(true, "connection dropped", null);
            }
            ensureLength(start + bytes.length);
            System.arraycopy(bytes, 0, data, start, bytes.length);
        }

        @Override
        public void resize(String path, long size, boolean create) {
            data = Arrays.copyOf(data, Math.toIntExact(size));
        }

        @Override
        public void reconnect() {
            reconnects++;
        }

        private void ensureLength(int length) {
            if (data.length < length) data = Arrays.copyOf(data, length);
        }
    }
}
