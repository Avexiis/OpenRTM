package openrtm.console;

import com.jjrpc.JRPC;
import com.jjrpc.xdevkit.XboxFeatures;
import openrtm.config.AppSettings;
import openrtm.titleids.TitleIds;
import openrtm.util.HexUtils;

import java.io.IOException;
import java.io.EOFException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConsoleService {
    private static final long XAM_GAMERTAG_ADDRESS = 0x81AA28FCL;
    private static final long[] TRANSFER_RETRY_DELAYS_MS = {2_000, 3_500, 5_000, 4_000, 6_500};

    private final AppSettings settings = new AppSettings();
    private JRPC.IXboxConsole console;
    private volatile JRPC.XbdmXboxConsole xbdmConsole;
    private String currentHost = "";
    private volatile boolean fileTransferInProgress;
    private volatile boolean fileTransferCancelled;
    private Boolean partialFileIoSupported;

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
        boolean hostChanged = !target.equalsIgnoreCase(currentHost);
        closeConsole();
        JRPC.IXboxConsole[] out = new JRPC.IXboxConsole[1];
        boolean ok = JRPC.Connect(null, out, target);
        if (!ok) return false;
        console = out[0];
        xbdmConsole = console instanceof JRPC.XbdmXboxConsole xbdm ? xbdm : null;
        if (xbdmConsole != null) xbdmConsole.setConversationTimeout(15_000);
        if (hostChanged) partialFileIoSupported = null;
        currentHost = target;
        settings.rememberHost(target);
        try {
            JRPC.XNotify(console, JRPC.XNotiyLogo.FLASHING_HAPPY_FACE, "OpenRTM Connected!");
        } catch (RuntimeException failure) {
            closeConsole();
            throw failure;
        }
        return true;
    }

    public synchronized boolean reconnect() {
        String target = currentHost.isBlank() ? savedHost() : currentHost;
        return !target.isBlank() && connect(target);
    }

    public synchronized void disconnect() {
        closeConsole();
    }

    private void closeConsole() {
        if (xbdmConsole != null) xbdmConsole.Close();
        console = null;
        xbdmConsole = null;
    }

    public synchronized boolean isConnected() {
        return console != null && (xbdmConsole == null || xbdmConsole.IsConnected());
    }

    public synchronized boolean connectionAlive() {
        if (!isConnected()) return false;
        try {
            rawCommand("getpid");
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    public synchronized Map<String, String> readInfo() {
        JRPC.IXboxConsole c = requireConsole();
        Map<String, String> info = new LinkedHashMap<>();
        info.put("IP", safe(() -> JRPC.XboxIP(c)));
        info.put("CPU Key", safe(() -> JRPC.GetCPUKey(c)));
        info.put("Gamertag", safe(this::readCurrentGamertag));
        info.put("Title", safe(() -> TitleIds.displayName(HexUtils.hex32(JRPC.XamGetCurrentTitleId(c)))));
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

    public synchronized void writeUtf16BigNull(long address, String value) {
        writeMemory(address, HexUtils.utf16BigNull(value));
    }

    public synchronized String readCurrentGamertag() {
        byte[] data = readMemory(XAM_GAMERTAG_ADDRESS, 0x1E);
        String value = new String(data, StandardCharsets.UTF_16BE);
        int zero = value.indexOf('\0');
        if (zero >= 0) value = value.substring(0, zero);
        return value.trim();
    }

    public synchronized void uploadFile(Path localPath, String remotePath) throws IOException {
        uploadFile(localPath, remotePath, (completed, total, message) -> {
        });
    }

    public synchronized void uploadFile(Path localPath, String remotePath, TransferProgress progress) throws IOException {
        if (!Files.isRegularFile(localPath)) throw new IOException("Local file does not exist: " + localPath);
        beginFileTransfer();
        try {
            uploadFileWithinTransfer(localPath, remotePath, 0, Files.size(localPath), progress);
        } finally {
            endFileTransfer();
        }
    }

    public synchronized void uploadDirectory(Path localDirectory, String remoteDirectory,
                                             TransferProgress progress) throws IOException {
        beginFileTransfer();
        try {
            LocalUploadPlan.Plan plan = LocalUploadPlan.build(
                    localDirectory, remoteDirectory, () -> fileTransferCancelled);
            long completed = 0;
            progress.update(0, plan.totalBytes(), "Preparing folder upload");
            for (LocalUploadPlan.Entry entry : plan.entries()) {
                checkFileTransferCancelled();
                if (entry.directory()) {
                    retryRemote(() -> {
                        makeDirectory(entry.remotePath());
                        return null;
                    }, completed, plan.totalBytes(), progress);
                    progress.update(completed, plan.totalBytes(), "Created " + entry.remotePath());
                    continue;
                }

                long base = completed;
                uploadFileWithinTransfer(entry.localPath(), entry.remotePath(), base, plan.totalBytes(), progress);
                completed = saturatingAdd(completed, entry.size());
            }
            progress.update(plan.totalBytes(), plan.totalBytes(), "Folder upload complete");
        } finally {
            endFileTransfer();
        }
    }

    public synchronized void uploadDirectory(Path localDirectory, String remoteDirectory) throws IOException {
        uploadDirectory(localDirectory, remoteDirectory, (completed, total, message) -> {
        });
    }

    public boolean isUploadInProgress() {
        return fileTransferInProgress;
    }

    public void cancelUpload() {
        cancelFileTransfer();
    }

    public boolean isFileTransferInProgress() {
        return fileTransferInProgress;
    }

    public void cancelFileTransfer() {
        fileTransferCancelled = true;
        JRPC.XbdmXboxConsole active = xbdmConsole;
        if (fileTransferInProgress && active != null) active.Abort();
    }

    public synchronized void downloadFile(String remotePath, Path localPath) throws IOException {
        downloadFile(remotePath, localPath, (completed, total, message) -> {
        });
    }

    public synchronized void downloadFile(String remotePath, Path localPath, TransferProgress progress) throws IOException {
        beginFileTransfer();
        try {
            progress.update(0, -1, "Inspecting console file");
            long size = retryRemote(() -> remoteFileSize(remotePath), 0, -1, progress);
            if (size < 0) throw new IOException("Console file does not exist: " + remotePath);
            downloadFileWithinTransfer(remotePath, localPath, 0, size, size, progress);
            progress.update(size, size, "Download complete");
        } finally {
            endFileTransfer();
        }
    }

    public synchronized void downloadDirectory(String remoteDirectory, Path localDirectory,
                                               TransferProgress progress) throws IOException {
        beginFileTransfer();
        try {
            progress.update(0, -1, "Enumerating console folder");
            List<RemoteDownload> files = new ArrayList<>();
            collectRemoteDirectory(remoteDirectory, localDirectory, files, progress);

            long total = 0;
            for (RemoteDownload file : files) total = saturatingAdd(total, file.size());
            long completed = 0;
            progress.update(0, total, "Downloading folder");
            for (RemoteDownload file : files) {
                checkFileTransferCancelled();
                long base = completed;
                downloadFileWithinTransfer(file.remotePath(), file.localPath(), base, total, file.size(), progress);
                completed = saturatingAdd(completed, file.size());
            }
            progress.update(total, total, "Folder download complete");
        } finally {
            endFileTransfer();
        }
    }

    public synchronized void downloadDirectory(String remoteDirectory, Path localDirectory) throws IOException {
        downloadDirectory(remoteDirectory, localDirectory, (completed, total, message) -> {
        });
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
        String response = rawCommand("mkdir name=" + JRPC.XbdmXboxConsole.quoteXbdm(remotePath));
        try {
            throwIfXbdmError(response, "mkdir");
        } catch (RuntimeException createFailure) {
            try {
                listDirectory(LocalUploadPlan.ensureRemoteDirectory(remotePath));
            } catch (RuntimeException missingDirectory) {
                createFailure.addSuppressed(missingDirectory);
                throw createFailure;
            }
        }
    }

    public synchronized void deletePath(String remotePath, boolean directory) {
        String response = rawCommand("delete name=" + JRPC.XbdmXboxConsole.quoteXbdm(remotePath)
                + (directory ? " dir" : ""));
        throwIfXbdmError(response, "delete");
    }

    private void uploadFileWithinTransfer(Path localPath, String remotePath, long base,
                                          long total, TransferProgress progress) throws IOException {
        String name = localPath.getFileName() == null ? localPath.toString() : localPath.getFileName().toString();
        ResumableUploader.Progress fileProgress = (completed, ignored, message) ->
                progress.update(saturatingAdd(base, completed), total, name + ": " + message);

        if (supportsPartialFileIo()) {
            try {
                ResumableUploader.upload(localPath, remotePath, remoteFile(), () -> fileTransferCancelled, fileProgress);
                return;
            } catch (IOException failure) {
                if (!isUnsupportedPartialFileCommand(failure)) throw failure;
                partialFileIoSupported = false;
                fileProgress.update(0, Files.size(localPath),
                        "Console uses whole-file transfers; restarting this file");
            }
        }

        WholeFileUploader.upload(localPath, remotePath, wholeRemoteFile(), () -> fileTransferCancelled, fileProgress);
    }

    private void downloadFileWithinTransfer(String remotePath, Path localPath, long base,
                                            long total, long expectedSize, TransferProgress progress) throws IOException {
        checkFileTransferCancelled();
        String name = LocalUploadPlan.remoteLeaf(remotePath);
        retryRemote(() -> {
            requireXbdm().ReceiveFile(remotePath, localPath,
                    completed -> progress.update(saturatingAdd(base, completed), total, "Downloading " + name));
            return null;
        }, base, total, progress);
        long actualSize = Files.size(localPath);
        if (actualSize != expectedSize) {
            throw new IOException("Downloaded size mismatch for " + name + ": expected " + expectedSize + ", got " + actualSize);
        }
    }

    private void collectRemoteDirectory(String remoteDirectory, Path localDirectory,
                                        List<RemoteDownload> files, TransferProgress progress) throws IOException {
        checkFileTransferCancelled();
        Files.createDirectories(localDirectory);
        String parent = LocalUploadPlan.ensureRemoteDirectory(remoteDirectory);
        List<FileEntry> entries = retryRemote(() -> listDirectory(parent), 0, -1, progress);
        for (FileEntry entry : entries) {
            checkFileTransferCancelled();
            String name = safeRemoteLeaf(entry.name());
            String remoteChild = LocalUploadPlan.childRemotePath(parent, name);
            Path localChild = localDirectory.resolve(name).normalize();
            if (!localChild.startsWith(localDirectory.normalize())) {
                throw new IOException("Unsafe console path: " + entry.name());
            }
            if (entry.directory()) {
                collectRemoteDirectory(remoteChild, localChild, files, progress);
            } else {
                files.add(new RemoteDownload(remoteChild, localChild, entry.size()));
            }
        }
    }

    private static String safeRemoteLeaf(String path) throws IOException {
        String name = LocalUploadPlan.remoteLeaf(path);
        if (name.isBlank() || name.equals(".") || name.equals("..") || name.indexOf('\0') >= 0) {
            throw new IOException("Unsafe console path: " + path);
        }
        return name;
    }

    private void beginFileTransfer() {
        if (fileTransferInProgress) throw new IllegalStateException("Another file transfer is already running");
        fileTransferCancelled = false;
        fileTransferInProgress = true;
    }

    private void endFileTransfer() {
        fileTransferInProgress = false;
    }

    private void checkFileTransferCancelled() throws IOException {
        if (fileTransferCancelled || Thread.currentThread().isInterrupted()) {
            throw new IOException("File transfer cancelled");
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) return Long.MAX_VALUE;
        return left + right;
    }

    private <T> T retryRemote(RemoteSupplier<T> operation, long completed, long total,
                              TransferProgress progress) throws IOException {
        int attempt = 0;
        while (true) {
            checkFileTransferCancelled();
            try {
                return operation.get();
            } catch (Exception failure) {
                if (!isRetryableRemoteFailure(failure)) {
                    String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
                    throw new IOException(message, failure);
                }
            }

            long delay = TRANSFER_RETRY_DELAYS_MS[attempt++ % TRANSFER_RETRY_DELAYS_MS.length];
            progress.update(completed, total, "Connection lost; retrying in " + (delay / 1_000.0) + " seconds");
            waitForTransferRetry(delay);
            try {
                if (reconnect()) progress.update(completed, total, "Reconnected to console");
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void waitForTransferRetry(long delay) throws IOException {
        long remaining = delay;
        while (remaining > 0) {
            checkFileTransferCancelled();
            long slice = Math.min(remaining, 250);
            try {
                Thread.sleep(slice);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("File transfer cancelled", interrupted);
            }
            remaining -= slice;
        }
    }

    private ResumableUploader.RemoteFile remoteFile() {
        return new ResumableUploader.RemoteFile() {
            @Override
            public long size(String path) throws ResumableUploader.RemoteException {
                return remoteCall(() -> remoteFileSize(path));
            }

            @Override
            public byte[] read(String path, long offset, int length) throws ResumableUploader.RemoteException {
                return remoteCall(() -> requireXbdm().ReadFilePartial(path, offset, length));
            }

            @Override
            public void write(String path, long offset, byte[] data) throws ResumableUploader.RemoteException {
                remoteRun(() -> requireXbdm().WriteFilePartial(path, offset, data));
            }

            @Override
            public void resize(String path, long size, boolean create) throws ResumableUploader.RemoteException {
                remoteRun(() -> requireXbdm().SetFileSize(path, size, create, create));
            }

            @Override
            public void reconnect() throws ResumableUploader.RemoteException {
                remoteRun(() -> {
                    if (!ConsoleService.this.reconnect()) throw new IOException("Console is unavailable");
                });
            }
        };
    }

    private WholeFileUploader.RemoteFile wholeRemoteFile() {
        return new WholeFileUploader.RemoteFile() {
            @Override
            public long size(String path) throws ResumableUploader.RemoteException {
                return remoteCall(() -> remoteFileSize(path));
            }

            @Override
            public void delete(String path) throws ResumableUploader.RemoteException {
                remoteRun(() -> deletePath(path, false));
            }

            @Override
            public void send(Path localPath, String path, java.util.function.LongConsumer progress)
                    throws ResumableUploader.RemoteException {
                remoteRun(() -> requireXbdm().SendFile(localPath, path, progress));
            }

            @Override
            public boolean matches(Path localPath, String path, java.util.function.LongConsumer progress)
                    throws ResumableUploader.RemoteException {
                return remoteCall(() -> requireXbdm().FileMatches(localPath, path, progress));
            }

            @Override
            public void reconnect() throws ResumableUploader.RemoteException {
                remoteRun(() -> {
                    if (!ConsoleService.this.reconnect()) throw new IOException("Console is unavailable");
                });
            }
        };
    }

    private boolean supportsPartialFileIo() {
        if (partialFileIoSupported != null) return partialFileIoSupported;
        try {
            List<String> commands = rawCommand("help").lines()
                    .map(String::trim)
                    .map(value -> value.toLowerCase(Locale.ROOT))
                    .toList();
            partialFileIoSupported = commands.contains("writefile") && commands.contains("fileeof");
            return partialFileIoSupported;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private long remoteFileSize(String remotePath) {
        String normalized = remotePath.replace('/', '\\');
        int slash = normalized.lastIndexOf('\\');
        String directory = slash >= 0 ? normalized.substring(0, slash + 1) : normalized;
        String fileName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        for (FileEntry entry : listDirectory(directory)) {
            String entryName = entry.name().replace('/', '\\');
            int entrySlash = entryName.lastIndexOf('\\');
            if (entrySlash >= 0) entryName = entryName.substring(entrySlash + 1);
            if (!entry.directory() && entryName.equalsIgnoreCase(fileName)) return entry.size();
        }
        return -1;
    }

    private <T> T remoteCall(RemoteSupplier<T> action) throws ResumableUploader.RemoteException {
        try {
            return action.get();
        } catch (Exception failure) {
            throw remoteFailure(failure);
        }
    }

    private void remoteRun(RemoteRunnable action) throws ResumableUploader.RemoteException {
        remoteCall(() -> {
            action.run();
            return null;
        });
    }

    private static ResumableUploader.RemoteException remoteFailure(Exception failure) {
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        return new ResumableUploader.RemoteException(isRetryableRemoteFailure(failure), message, failure);
    }

    static boolean isRetryableRemoteFailure(Exception failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException
                    || cause instanceof SocketException || cause instanceof EOFException) return true;
        }
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);
        if (failure instanceof java.nio.file.FileSystemException) return false;
        return failure instanceof IOException
                || lower.startsWith("i/o:")
                || lower.contains("connect failed")
                || lower.contains("connection reset")
                || lower.contains("closed")
                || lower.contains("unexpected eof")
                || lower.contains("timed out");
    }

    private static boolean isUnsupportedPartialFileCommand(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("407- unknown command")) return true;
        }
        return false;
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

    private record RemoteDownload(String remotePath, Path localPath, long size) {
    }

    @FunctionalInterface
    public interface TransferProgress {
        void update(long completed, long total, String message);
    }

    @FunctionalInterface
    private interface RemoteSupplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface RemoteRunnable {
        void run() throws Exception;
    }
}
