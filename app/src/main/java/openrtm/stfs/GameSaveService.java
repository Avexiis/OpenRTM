package openrtm.stfs;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

public final class GameSaveService {
    static final int CERTIFICATE_OFFSET = 0x004;
    static final int CERTIFICATE_LENGTH = 0x1A8;
    static final int SIGNATURE_OFFSET = 0x1AC;
    static final int SIGNATURE_LENGTH = 0x080;
    static final int SIGNED_HEADER_OFFSET = 0x22C;
    static final int SIGNED_HEADER_LENGTH = 0x118;
    static final int CONTENT_HASH_OFFSET = 0x32C;
    static final int HEADER_SIZE_OFFSET = 0x340;
    static final int METADATA_OFFSET = 0x344;
    static final int CONTENT_TYPE_OFFSET = 0x344;
    static final int TITLE_ID_OFFSET = 0x360;
    static final int CONSOLE_ID_OFFSET = 0x36C;
    static final int PROFILE_ID_OFFSET = 0x371;
    static final int VOLUME_TYPE_OFFSET = 0x3A9;
    static final int DEVICE_ID_OFFSET = 0x3FD;

    private static final byte[] CON_MAGIC = {'C', 'O', 'N', ' '};
    private static final int STFS_VOLUME = 0;
    private static final Set<Integer> SAVE_CONTENT_TYPES = Set.of(0x00000001, 0x00060000);
    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    public Info inspect(Path file) throws IOException {
        Path source = requireFile(file);
        try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize < DEVICE_ID_OFFSET + 20L) {
                throw new IOException("File is too small to be an Xbox 360 STFS package");
            }

            byte[] fixedHeader = read(channel, 0, DEVICE_ID_OFFSET + 20);
            if (!Arrays.equals(CON_MAGIC, Arrays.copyOf(fixedHeader, CON_MAGIC.length))) {
                throw new IOException("Only CON signed Xbox 360 game saves are supported");
            }

            long headerSize = Integer.toUnsignedLong(int32(fixedHeader, HEADER_SIZE_OFFSET));
            long alignedHeaderSize = alignToBlock(headerSize);
            if (headerSize < DEVICE_ID_OFFSET + 20L || alignedHeaderSize > fileSize) {
                throw new IOException("Invalid STFS header size: " + headerSize);
            }

            int contentType = int32(fixedHeader, CONTENT_TYPE_OFFSET);
            if (!SAVE_CONTENT_TYPES.contains(contentType)) {
                throw new IOException(String.format(Locale.ROOT,
                        "Package is not a supported game save (content type 0x%08X)", contentType));
            }
            int volumeType = int32(fixedHeader, VOLUME_TYPE_OFFSET);
            if (volumeType != STFS_VOLUME) {
                throw new IOException("Only STFS game saves are supported");
            }

            byte[] expectedHash = Arrays.copyOfRange(
                    fixedHeader, CONTENT_HASH_OFFSET, CONTENT_HASH_OFFSET + 20);
            byte[] actualHash = digest(channel, METADATA_OFFSET, alignedHeaderSize - METADATA_OFFSET);
            byte[] certificate = Arrays.copyOfRange(
                    fixedHeader, CERTIFICATE_OFFSET, CERTIFICATE_OFFSET + CERTIFICATE_LENGTH);
            byte[] signature = Arrays.copyOfRange(
                    fixedHeader, SIGNATURE_OFFSET, SIGNATURE_OFFSET + SIGNATURE_LENGTH);
            byte[] signedHeader = Arrays.copyOfRange(
                    fixedHeader, SIGNED_HEADER_OFFSET, SIGNED_HEADER_OFFSET + SIGNED_HEADER_LENGTH);

            return new Info(
                    source,
                    HEX.formatHex(Arrays.copyOfRange(fixedHeader, PROFILE_ID_OFFSET, PROFILE_ID_OFFSET + 8)),
                    HEX.formatHex(Arrays.copyOfRange(fixedHeader, CONSOLE_ID_OFFSET, CONSOLE_ID_OFFSET + 5)),
                    HEX.formatHex(Arrays.copyOfRange(fixedHeader, DEVICE_ID_OFFSET, DEVICE_ID_OFFSET + 20)),
                    HEX.formatHex(Arrays.copyOfRange(fixedHeader, TITLE_ID_OFFSET, TITLE_ID_OFFSET + 4)),
                    contentType,
                    contentType == 1 ? "Xbox 360 Saved Game" : "Xbox Saved Game",
                    fileSize,
                    headerSize,
                    MessageDigest.isEqual(expectedHash, actualHash),
                    ConCertificate.verify(certificate, signature, signedHeader));
        }
    }

    public SaveResult save(Path source, Path destination, Assignment assignment, boolean createBackup)
            throws IOException {
        Path input = requireFile(source);
        Path output = requirePath(destination, "Choose an output file");
        ParsedAssignment parsed = parse(assignment);
        inspect(input);

        Path parent = output.getParent();
        if (parent == null) parent = Path.of(".").toAbsolutePath().normalize();
        Files.createDirectories(parent);
        String name = output.getFileName() == null ? "save" : output.getFileName().toString();
        Path temporary = Files.createTempFile(parent, safePrefix(name), ".openrtm.tmp");
        Path backup = null;
        try {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            rewriteHeader(temporary, parsed);
            Info staged = inspect(temporary);
            if (!staged.headerHashValid() || !staged.signatureValid()) {
                throw new IOException("The staged package failed signature verification");
            }

            if (createBackup && input.equals(output)) {
                backup = input.resolveSibling(input.getFileName() + ".bak");
                Files.copy(input, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            replace(temporary, output);
            temporary = null;
            Info info = inspect(output);
            return new SaveResult(info, backup);
        } finally {
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }

    private static void rewriteHeader(Path file, ParsedAssignment assignment) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            byte[] sizeBytes = read(channel, HEADER_SIZE_OFFSET, 4);
            long headerSize = Integer.toUnsignedLong(int32(sizeBytes, 0));
            long alignedHeaderSize = alignToBlock(headerSize);
            if (alignedHeaderSize > channel.size()) throw new IOException("STFS header extends beyond the package");

            write(channel, CONSOLE_ID_OFFSET, assignment.consoleId());
            write(channel, PROFILE_ID_OFFSET, assignment.profileId());
            write(channel, DEVICE_ID_OFFSET, assignment.deviceId());

            byte[] contentHash = digest(channel, METADATA_OFFSET, alignedHeaderSize - METADATA_OFFSET);
            write(channel, CONTENT_HASH_OFFSET, contentHash);
            write(channel, 0, CON_MAGIC);
            write(channel, CERTIFICATE_OFFSET, ConCertificate.certificate());

            byte[] signedHeader = read(channel, SIGNED_HEADER_OFFSET, SIGNED_HEADER_LENGTH);
            byte[] signature = ConCertificate.sign(signedHeader);
            if (signature.length != SIGNATURE_LENGTH) {
                throw new IOException("Unexpected CON signature size: " + signature.length);
            }
            write(channel, SIGNATURE_OFFSET, signature);
            channel.force(true);
        }
    }

    private static ParsedAssignment parse(Assignment assignment) {
        if (assignment == null) throw new IllegalArgumentException("Assignment values are required");
        return new ParsedAssignment(
                parseId(assignment.profileId(), 8, "Profile ID"),
                parseId(assignment.consoleId(), 5, "Console ID"),
                parseId(assignment.deviceId(), 20, "Device ID"));
    }

    private static byte[] parseId(String text, int length, String label) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return new byte[length];
        if (value.regionMatches(true, 0, "0x", 0, 2)) value = value.substring(2);
        value = value.replace(" ", "").replace("-", "").replace(":", "").replace("_", "");
        if (!value.matches("[0-9A-Fa-f]{" + (length * 2) + "}")) {
            throw new IllegalArgumentException(label + " must contain exactly " + (length * 2) + " hexadecimal digits");
        }
        return HexFormat.of().parseHex(value);
    }

    private static byte[] digest(FileChannel channel, long offset, long length) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-1 is unavailable", impossible);
        }
        ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
        long position = offset;
        long remaining = length;
        while (remaining > 0) {
            buffer.clear();
            buffer.limit((int) Math.min(buffer.capacity(), remaining));
            int count = channel.read(buffer, position);
            if (count < 0) throw new EOFException("Unexpected end of STFS header");
            if (count == 0) continue;
            digest.update(buffer.array(), 0, count);
            position += count;
            remaining -= count;
        }
        return digest.digest();
    }

    private static byte[] read(FileChannel channel, long offset, int length) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        long position = offset;
        while (buffer.hasRemaining()) {
            int count = channel.read(buffer, position);
            if (count < 0) throw new EOFException("Unexpected end of Xbox 360 package");
            if (count == 0) continue;
            position += count;
        }
        return buffer.array();
    }

    private static void write(FileChannel channel, long offset, byte[] value) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(value);
        long position = offset;
        while (buffer.hasRemaining()) position += channel.write(buffer, position);
    }

    private static int int32(byte[] value, int offset) {
        return ByteBuffer.wrap(value, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    private static long alignToBlock(long value) throws IOException {
        if (value > Long.MAX_VALUE - 0xFFFL) throw new IOException("STFS header size is too large");
        return (value + 0xFFFL) & ~0xFFFL;
    }

    private static Path requireFile(Path file) throws IOException {
        Path value = requirePath(file, "Choose a game save");
        if (!Files.isRegularFile(value)) throw new IOException("Game save does not exist: " + value);
        return value;
    }

    private static Path requirePath(Path path, String message) {
        if (path == null) throw new IllegalArgumentException(message);
        return path.toAbsolutePath().normalize();
    }

    private static String safePrefix(String name) {
        String value = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (value.length() >= 3) return value;
        return (value + "save").substring(0, 3);
    }

    private static void replace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record Assignment(String profileId, String consoleId, String deviceId) {
    }

    public record Info(
            Path path,
            String profileId,
            String consoleId,
            String deviceId,
            String titleId,
            int contentType,
            String contentTypeName,
            long packageSize,
            long headerSize,
            boolean headerHashValid,
            boolean signatureValid) {
    }

    public record SaveResult(Info info, Path backup) {
    }

    private record ParsedAssignment(byte[] profileId, byte[] consoleId, byte[] deviceId) {
    }
}
