package openrtm.games;

import openrtm.util.HexUtils;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

public final class PayloadStore {
    private static final String RESOURCE = "/openrtm/payloads/cod-memory-payloads.properties";
    private final Properties properties = new Properties();

    public PayloadStore() {
        try (InputStream in = PayloadStore.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing payload resource: " + RESOURCE);
            properties.load(in);
        } catch (IOException ioe) {
            throw new IllegalStateException("Could not load payload resource", ioe);
        }
    }

    public byte[] blob(String key) {
        String hex = required(key + ".hex");
        byte[] data = HexUtils.parseHex(hex);
        int expectedLength = Integer.parseInt(required(key + ".length"));
        if (data.length != expectedLength) {
            throw new IllegalStateException(key + " length mismatch: expected " + expectedLength + ", got " + data.length);
        }
        String expectedSha = required(key + ".sha256");
        String actualSha = sha256(data);
        if (!expectedSha.equalsIgnoreCase(actualSha)) {
            throw new IllegalStateException(key + " sha256 mismatch");
        }
        return data;
    }

    public List<MemoryPayload> operations(String prefix) {
        int count = Integer.parseInt(required(prefix + ".count"));
        List<MemoryPayload> operations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String key = prefix + "." + i;
            long address = HexUtils.parseAddress(required(key + ".address"));
            operations.add(new MemoryPayload(address, blob(key)));
        }
        return operations;
    }

    private String required(String key) {
        String value = properties.getProperty(key);
        if (value == null) throw new IllegalStateException("Missing payload key: " + key);
        return value;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record MemoryPayload(long address, byte[] data) {
    }
}
