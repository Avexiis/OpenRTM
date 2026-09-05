package openrtm.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.LongConsumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WholeFileUploaderTest {
    @TempDir
    Path tempDirectory;

    @Test
    void replacesSameSizeCorruptFile() throws Exception {
        byte[] expected = data(24_321);
        Path local = tempDirectory.resolve("movie.wmv");
        Files.write(local, expected);
        FakeRemote remote = new FakeRemote(expected.clone());
        remote.data[10] ^= 0x55;

        WholeFileUploader.upload(local, "Hdd:\\movie.wmv", remote, () -> false,
                (completed, total, message) -> {
                });

        assertArrayEquals(expected, remote.data);
        assertEquals(1, remote.deletes);
        assertEquals(1, remote.sends);
    }

    @Test
    void reconnectsAndReplacesPartialFileAfterDroppedSend() throws Exception {
        byte[] expected = data(31_777);
        Path local = tempDirectory.resolve("movie.wmv");
        Files.write(local, expected);
        FakeRemote remote = new FakeRemote(null);
        remote.dropNextSend = true;

        WholeFileUploader.upload(local, "Hdd:\\movie.wmv", remote, () -> false,
                (completed, total, message) -> {
                });

        assertArrayEquals(expected, remote.data);
        assertEquals(1, remote.reconnects);
        assertEquals(1, remote.deletes);
        assertEquals(2, remote.sends);
    }

    private static byte[] data(int length) {
        byte[] value = new byte[length];
        for (int i = 0; i < value.length; i++) value[i] = (byte) (i * 17 + 3);
        return value;
    }

    private static final class FakeRemote implements WholeFileUploader.RemoteFile {
        private byte[] data;
        private int deletes;
        private int sends;
        private int reconnects;
        private boolean dropNextSend;

        private FakeRemote(byte[] data) {
            this.data = data;
        }

        @Override
        public long size(String path) {
            return data == null ? -1 : data.length;
        }

        @Override
        public void delete(String path) {
            deletes++;
            data = null;
        }

        @Override
        public void send(Path localPath, String path, LongConsumer progress)
                throws ResumableUploader.RemoteException {
            sends++;
            try {
                byte[] local = Files.readAllBytes(localPath);
                if (dropNextSend) {
                    dropNextSend = false;
                    data = Arrays.copyOf(local, local.length / 2);
                    throw new ResumableUploader.RemoteException(true, "connection dropped", null);
                }
                data = local;
                progress.accept(data.length);
            } catch (java.io.IOException failure) {
                throw new ResumableUploader.RemoteException(false, failure.getMessage(), failure);
            }
        }

        @Override
        public boolean matches(Path localPath, String path, LongConsumer progress)
                throws ResumableUploader.RemoteException {
            try {
                byte[] local = Files.readAllBytes(localPath);
                progress.accept(data.length);
                return Arrays.equals(local, data);
            } catch (java.io.IOException failure) {
                throw new ResumableUploader.RemoteException(false, failure.getMessage(), failure);
            }
        }

        @Override
        public void reconnect() {
            reconnects++;
        }
    }
}
