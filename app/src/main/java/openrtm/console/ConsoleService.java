package openrtm.console;

import com.jjrpc.JRPC;
import com.jjrpc.xdevkit.XboxFeatures;
import openrtm.config.AppSettings;
import openrtm.util.HexUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConsoleService {
    private static final long XAM_GAMERTAG_ADDRESS = 0x81AA28FCL;
    private static final long XAM_SPOOF_GAMERTAG_ADDRESS = 0x81AA261CL;
    private static final long SPOOF_IP_ADDRESS = 0xC24313E0L;

    private final AppSettings settings = new AppSettings();
    private JRPC.IXboxConsole console;
    private JRPC.XbdmXboxConsole xbdmConsole;

    public synchronized String savedHost() {
        return settings.lastHost().orElse("");
    }

    public synchronized List<String> recentHosts() {
        return settings.recentHosts();
    }

    public synchronized boolean autoConnect() {
        return settings.autoConnect();
    }

    public synchronized void autoConnect(boolean value) {
        settings.autoConnect(value);
    }

    public synchronized Path settingsDirectory() {
        return settings.configDirectory();
    }

    public synchronized boolean connect(String host) {
        String target = host == null ? "" : host.trim();
        if (target.isBlank()) throw new IllegalArgumentException("Enter a console host or IP address");
        JRPC.IXboxConsole[] out = new JRPC.IXboxConsole[1];
        boolean ok = JRPC.Connect(null, out, target);
        if (!ok) return false;
        console = out[0];
        xbdmConsole = console instanceof JRPC.XbdmXboxConsole xbdm ? xbdm : null;
        if (xbdmConsole != null) xbdmConsole.setConversationTimeout(15_000);
        settings.rememberHost(target);
        return true;
    }

    public synchronized void disconnect() {
        if (xbdmConsole != null) xbdmConsole.Close();
        console = null;
        xbdmConsole = null;
    }

    public synchronized boolean isConnected() {
        return console != null && (xbdmConsole == null || xbdmConsole.IsConnected());
    }

    public synchronized Map<String, String> readInfo() {
        JRPC.IXboxConsole c = requireConsole();
        Map<String, String> info = new LinkedHashMap<>();
        info.put("IP", safe(() -> JRPC.XboxIP(c)));
        info.put("CPU Key", safe(() -> JRPC.GetCPUKey(c)));
        info.put("Gamertag", safe(this::readCurrentGamertag));
        info.put("Title ID", safe(() -> HexUtils.hex32(JRPC.XamGetCurrentTitleId(c))));
        info.put("Console Type", safe(() -> JRPC.ConsoleType(c)));
        info.put("Kernel", safe(() -> Long.toString(JRPC.GetKernelVersion(c))));
        //info.put("DM Version", safe(() -> XboxFeatures.getDMVersion(c))); //who cares
        //info.put("Box ID", safe(() -> XboxFeatures.getBoxID(c))); //nonfunctional
        info.put("Console ID", safe(() -> XboxFeatures.getConsoleID(c)));
        info.put("SMC", safe(() -> XboxFeatures.getSMCVersion(c)));
        info.put("CPU Temp", safe(() -> JRPC.GetTemperature(c, JRPC.TemperatureType.CPU) + " C"));
        info.put("GPU Temp", safe(() -> JRPC.GetTemperature(c, JRPC.TemperatureType.GPU) + " C"));
        info.put("EDRAM Temp", safe(() -> JRPC.GetTemperature(c, JRPC.TemperatureType.EDRAM) + " C"));
        info.put("Board Temp", safe(() -> JRPC.GetTemperature(c, JRPC.TemperatureType.MotherBoard) + " C"));
        return info;
    }

    public synchronized String systemInfoRaw() {
        return rawCommand("systeminfo");
    }

    public synchronized String rawCommand(String command) {
        if (xbdmConsole != null) return xbdmConsole.SendRawCommand(command);
        String[] out = new String[1];
        requireConsole().SendTextCommand(1L, command, out);
        return out[0] == null ? "" : out[0];
    }

    public synchronized void xNotify(String message, JRPC.XNotiyLogo logo) {
        JRPC.XNotify(requireConsole(), logo, message);
    }

    public synchronized void rebootWarm() {
        XboxFeatures.reboot(requireConsole(), XboxFeatures.XboxReboot.Warm);
    }

    public synchronized void rebootCold() {
        XboxFeatures.reboot(requireConsole(), XboxFeatures.XboxReboot.Cold);
    }

    public synchronized void shutdown() {
        rawCommand("shutdown");
    }

    public synchronized void ejectDvd(boolean eject) {
        rawCommand("dvdeject eject=" + (eject ? "1" : "0"));
    }

    public synchronized byte[] readMemory(long address, int length) {
        return JRPC.GetMemory(requireConsole(), address, length);
    }

    public synchronized void writeMemory(long address, byte[] data) {
        JRPC.SetMemory(requireConsole(), address, data);
    }

    public synchronized void writeByte(long address, int value) {
        JRPC.WriteByte(requireConsole(), address, (byte) value);
    }

    public synchronized void writeBool(long address, boolean value) {
        JRPC.WriteBool(requireConsole(), address, value);
    }

    public synchronized void writeUInt16BE(long address, int value) {
        JRPC.WriteUInt16(requireConsole(), address, value);
    }

    public synchronized void writeUInt32BE(long address, long value) {
        JRPC.WriteUInt32(requireConsole(), address, value);
    }

    public synchronized void writeInt32LE(long address, int value) {
        writeMemory(address, HexUtils.int32Little(value));
    }

    public synchronized void writeAsciiNull(long address, String value) {
        writeMemory(address, HexUtils.asciiNull(value));
    }

    public synchronized void writeFixedAscii(long address, String value, int length) {
        byte[] raw = value == null ? new byte[0] : value.getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[length];
        System.arraycopy(raw, 0, out, 0, Math.min(raw.length, out.length));
        writeMemory(address, out);
    }

    public synchronized void writeUtf16BigNull(long address, String value) {
        writeMemory(address, HexUtils.utf16BigNull(value));
    }

    public synchronized void cbuf(long address, String command) {
        JRPC.CallVoid(requireConsole(), address, 0, command);
    }

    public synchronized void titleCallVoid(long address, Object... args) {
        JRPC.CallVoid(requireConsole(), JRPC.ThreadType.Title, address, args);
    }

    public synchronized void callVoid(long address, Object... args) {
        JRPC.CallVoid(requireConsole(), address, args);
    }

    public synchronized long titleCallLong(long address, Object... args) {
        Object value = JRPC.<Object>Call(requireConsole(), JRPC.ThreadType.Title, address, args);
        if (value instanceof Number n) return n.longValue();
        return Long.parseUnsignedLong(Objects.toString(value, "0"), 16);
    }

    public synchronized long readUInt32(long address) {
        return JRPC.ReadUInt32(requireConsole(), address);
    }

    public synchronized int readInt32(long address) {
        return JRPC.ReadInt32(requireConsole(), address);
    }

    public synchronized String readString(long address, int length) {
        byte[] data = readMemory(address, length);
        int end = 0;
        while (end < data.length && data[end] != 0) end++;
        return new String(data, 0, end, StandardCharsets.US_ASCII);
    }

    public synchronized String readCurrentGamertag() {
        byte[] data = readMemory(XAM_GAMERTAG_ADDRESS, 0x1E);
        String value = new String(data, StandardCharsets.UTF_16BE);
        int zero = value.indexOf('\0');
        if (zero >= 0) value = value.substring(0, zero);
        return value.trim();
    }

    public synchronized void xamSpoofGamertag(String gamertag) {
        writeUtf16BigNull(XAM_SPOOF_GAMERTAG_ADDRESS, gamertag);
    }

    public synchronized void spoofIp(String ipAddress) {
        String[] parts = ipAddress.trim().split("\\.");
        if (parts.length != 4) throw new IllegalArgumentException("IP must have four octets");
        byte[] data = new byte[4];
        for (int i = 0; i < parts.length; i++) data[i] = (byte) (Integer.parseInt(parts[i]) & 0xFF);
        writeMemory(SPOOF_IP_ADDRESS, data);
    }

    public synchronized void uploadFile(Path localPath, String remotePath) throws IOException {
        requireXbdm().SendFile(localPath, remotePath);
    }

    public synchronized void downloadFile(String remotePath, Path localPath) throws IOException {
        requireXbdm().ReceiveFile(remotePath, localPath);
    }

    public synchronized byte[] readFilePartial(String remotePath, long offset, int length) throws IOException {
        return requireXbdm().ReadFilePartial(remotePath, offset, length);
    }

    public synchronized void writeFilePartial(String remotePath, long offset, byte[] data) throws IOException {
        requireXbdm().WriteFilePartial(remotePath, offset, data);
    }

    public synchronized void setFileSize(String remotePath, long size, boolean canCreate, boolean mustCreate) {
        requireXbdm().SetFileSize(remotePath, size, canCreate, mustCreate);
    }

    public synchronized List<String> listDrives() {
        String response = rawCommand("drivelist");
        throwIfXbdmError(response, "drivelist");
        return parseDriveList(response);
    }

    public synchronized List<FileEntry> listDirectory(String remotePath) {
        String response = rawCommand("dirlist name=" + JRPC.XbdmXboxConsole.quoteXbdm(remotePath));
        throwIfXbdmError(response, "dirlist");
        return parseDirectory(response);
    }

    public synchronized void makeDirectory(String remotePath) {
        rawCommand("mkdir name=" + JRPC.XbdmXboxConsole.quoteXbdm(remotePath));
    }

    public synchronized void deletePath(String remotePath, boolean directory) {
        rawCommand("delete name=" + JRPC.XbdmXboxConsole.quoteXbdm(remotePath) + (directory ? " dir" : ""));
    }

    private JRPC.IXboxConsole requireConsole() {
        if (console == null) throw new IllegalStateException("Not connected");
        return console;
    }

    private JRPC.XbdmXboxConsole requireXbdm() {
        if (xbdmConsole == null) throw new IllegalStateException("XBDM file transfer requires the built-in JJRPC console");
        return xbdmConsole;
    }

    private static String safe(Supplier<String> supplier) {
        try {
            String value = supplier.get();
            return value == null || value.isBlank() ? "" : value;
        } catch (Throwable t) {
            return "error: " + t.getMessage();
        }
    }

    private static List<String> parseDriveList(String response) {
        List<String> drives = new ArrayList<>();
        Matcher matcher = Pattern.compile("(?i)drivename=\"?([^\"\\r\\n]+)\"?").matcher(response);
        while (matcher.find()) {
            String name = matcher.group(1).trim();
            if (!name.isBlank()) drives.add(normalizeDriveName(name));
        }
        return drives;
    }

    private static String normalizeDriveName(String value) {
        String name = value.trim();
        if (name.endsWith(":\\")) return name;
        if (name.endsWith(":")) return name + "\\";
        return name + ":\\";
    }

    private static void throwIfXbdmError(String response, String operation) {
        for (String line : response.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank()) continue;
            int status = statusCode(trimmed);
            if (status >= 400) throw new IllegalStateException(operation + " failed: " + trimmed);
            return;
        }
    }

    private static int statusCode(String line) {
        if (line.length() < 3) return -1;
        try {
            return Integer.parseInt(line.substring(0, 3));
        } catch (NumberFormatException nfe) {
            return -1;
        }
    }

    private static List<FileEntry> parseDirectory(String response) {
        List<FileEntry> entries = new ArrayList<>();
        for (String line : response.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("202") || trimmed.equals(".")) continue;
            if (statusCode(trimmed) >= 100) continue;
            Map<String, String> fields = parseFields(trimmed);
            String name = fields.getOrDefault("name", trimmed);
            long size = parseNumber(fields.getOrDefault("size", "0"));
            if (fields.containsKey("sizelo") || fields.containsKey("sizehi")) {
                size = (parseNumber(fields.getOrDefault("sizehi", "0")) << 32)
                        | (parseNumber(fields.getOrDefault("sizelo", "0")) & 0xFFFF_FFFFL);
            }
            entries.add(new FileEntry(name, size, fields.containsKey("directory"), trimmed));
        }
        return entries;
    }

    private static Map<String, String> parseFields(String line) {
        Map<String, String> fields = new LinkedHashMap<>();
        Pattern p = Pattern.compile("(?i)([a-z0-9_]+)=((\"[^\"]*\")|\\S+)|\\b(directory)\\b");
        Matcher m = p.matcher(line);
        while (m.find()) {
            if (m.group(4) != null) {
                fields.put("directory", "true");
            } else {
                String value = m.group(2);
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                fields.put(m.group(1).toLowerCase(Locale.ROOT), value);
            }
        }
        return fields;
    }

    private static long parseNumber(String value) {
        if (value == null || value.isBlank()) return 0;
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("0x")) return Long.parseUnsignedLong(v.substring(2), 16);
        return Long.parseLong(v);
    }

    public record FileEntry(String name, long size, boolean directory, String raw) {
    }
}
