package openrtm.stfs;

import openrtm.titleids.TitleIds;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PackageService
{
	static final int CONTENT_HASH_OFFSET = 0x32C;
	static final int HEADER_SIZE_OFFSET = 0x340;
	static final int METADATA_OFFSET = 0x344;
	static final int CONTENT_TYPE_OFFSET = 0x344;
	static final int METADATA_VERSION_OFFSET = 0x348;
	static final int TITLE_ID_OFFSET = 0x360;
	static final int CONSOLE_ID_OFFSET = 0x36C;
	static final int CREATOR_ID_OFFSET = 0x371;
	static final int VOLUME_DESCRIPTOR_OFFSET = 0x379;
	static final int VOLUME_TYPE_OFFSET = 0x3A9;
	static final int DEVICE_ID_OFFSET = 0x3FD;
	static final int DISPLAY_NAMES_OFFSET = 0x411;
	static final int DESCRIPTIONS_OFFSET = 0xD11;
	static final int PUBLISHER_OFFSET = 0x1611;
	static final int TITLE_NAME_OFFSET = 0x1691;
	static final int FLAGS_OFFSET = 0x1711;
	static final int THUMBNAIL_SIZE_OFFSET = 0x1712;
	static final int TITLE_THUMBNAIL_SIZE_OFFSET = 0x1716;
	static final int THUMBNAIL_OFFSET = 0x171A;

	private static final int CERTIFICATE_OFFSET = 0x004;
	private static final int CERTIFICATE_LENGTH = 0x1A8;
	private static final int SIGNATURE_OFFSET = 0x1AC;
	private static final int SIGNATURE_LENGTH = 0x80;
	private static final int SIGNED_HEADER_OFFSET = 0x22C;
	private static final int SIGNED_HEADER_LENGTH = 0x118;
	private static final int FIXED_TEXT_BYTES = 0x100;
	private static final int SHORT_TEXT_BYTES = 0x80;
	private static final HexFormat HEX = HexFormat.of().withUpperCase();
	private static final Map<Integer, String> CONTENT_TYPES = contentTypes();

	public Info inspect(Path file) throws IOException
	{
		Path source = requireFile(file);
		try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ))
		{
			long fileSize = channel.size();
			if (fileSize < DEVICE_ID_OFFSET + 20L)
			{
				throw new IOException("File is too small to be an Xbox 360 package");
			}
			byte[] fixed = read(channel, 0, (int) Math.min(fileSize, 0xB000));
			SignatureType signatureType = SignatureType.from(fixed);
			long headerSize = Integer.toUnsignedLong(int32(fixed, HEADER_SIZE_OFFSET));
			long alignedHeader = alignToBlock(headerSize);
			if (headerSize < DEVICE_ID_OFFSET + 20L || alignedHeader > fileSize)
			{
				throw new IOException("Invalid Xbox 360 package header size: " + headerSize);
			}
			if (alignedHeader > fixed.length)
			{
				fixed = read(channel, 0, Math.toIntExact(alignedHeader));
			}

			int metadataVersion = int32(fixed, METADATA_VERSION_OFFSET);
			int thumbnailCapacity = metadataVersion >= 2 ? 0x3D00 : 0x4000;
			int thumbnailSize = checkedThumbnailSize(fixed, THUMBNAIL_SIZE_OFFSET, thumbnailCapacity);
			int titleThumbnailOffset = THUMBNAIL_OFFSET + thumbnailCapacity
				+ (metadataVersion >= 2 ? 3 * FIXED_TEXT_BYTES : 0);
			int titleThumbnailSize = checkedThumbnailSize(fixed, TITLE_THUMBNAIL_SIZE_OFFSET, thumbnailCapacity);
			if ((long) titleThumbnailOffset + thumbnailCapacity > alignedHeader)
			{
				throw new IOException("Package thumbnail data extends beyond its header");
			}

			byte[] expectedHash = Arrays.copyOfRange(fixed, CONTENT_HASH_OFFSET, CONTENT_HASH_OFFSET + 20);
			byte[] actualHash = digest(channel, METADATA_OFFSET, alignedHeader - METADATA_OFFSET);
			Boolean signatureValid = null;
			if (signatureType == SignatureType.CON)
			{
				signatureValid = ConCertificate.verify(
					Arrays.copyOfRange(fixed, CERTIFICATE_OFFSET, CERTIFICATE_OFFSET + CERTIFICATE_LENGTH),
					Arrays.copyOfRange(fixed, SIGNATURE_OFFSET, SIGNATURE_OFFSET + SIGNATURE_LENGTH),
					Arrays.copyOfRange(fixed, SIGNED_HEADER_OFFSET, SIGNED_HEADER_OFFSET + SIGNED_HEADER_LENGTH));
			}

			int contentType = int32(fixed, CONTENT_TYPE_OFFSET);
			String titleId = HEX.formatHex(Arrays.copyOfRange(fixed, TITLE_ID_OFFSET, TITLE_ID_OFFSET + 4));
			String displayName = firstLocalizedText(fixed, DISPLAY_NAMES_OFFSET);
			String description = firstLocalizedText(fixed, DESCRIPTIONS_OFFSET);
			return new Info(
				source,
				signatureType,
				contentType,
				contentTypeName(contentType),
				titleId,
				TitleIds.displayName(titleId),
				HEX.formatHex(Arrays.copyOfRange(fixed, CREATOR_ID_OFFSET, CREATOR_ID_OFFSET + 8)),
				HEX.formatHex(Arrays.copyOfRange(fixed, CONSOLE_ID_OFFSET, CONSOLE_ID_OFFSET + 5)),
				HEX.formatHex(Arrays.copyOfRange(fixed, DEVICE_ID_OFFSET, DEVICE_ID_OFFSET + 20)),
				displayName,
				description,
				readUtf16(fixed, PUBLISHER_OFFSET, SHORT_TEXT_BYTES),
				readUtf16(fixed, TITLE_NAME_OFFSET, SHORT_TEXT_BYTES),
				fixed[FLAGS_OFFSET] & 0xFF,
				fileSize,
				headerSize,
				int32(fixed, VOLUME_TYPE_OFFSET) == 0,
				MessageDigest.isEqual(expectedHash, actualHash),
				signatureValid,
				Arrays.copyOfRange(fixed, THUMBNAIL_OFFSET, THUMBNAIL_OFFSET + thumbnailSize),
				Arrays.copyOfRange(fixed, titleThumbnailOffset, titleThumbnailOffset + titleThumbnailSize),
				HEX.formatHex(Arrays.copyOfRange(fixed, CONTENT_HASH_OFFSET, CONTENT_HASH_OFFSET + 20)),
				metadataVersion);
		}
	}

	public SaveResult save(Path source, Path destination, Edits edits, boolean createBackup) throws IOException
	{
		Path input = requireFile(source);
		Path output = requirePath(destination, "Choose an output file");
		Info original = inspect(input);
		if (original.signatureType() != SignatureType.CON)
		{
			throw new IOException("LIVE and PIRS packages can be inspected but cannot be edited or re-signed");
		}
		if (edits == null)
		{
			throw new IllegalArgumentException("Package changes are required");
		}

		Path parent = output.getParent() == null ? Path.of(".").toAbsolutePath() : output.getParent();
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, safePrefix(output.getFileName().toString()), ".openrtm.tmp");
		Path backup = null;
		try
		{
			Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			applyEdits(temporary, original, edits);
			resealConPackage(temporary);
			Info staged = inspect(temporary);
			if (!staged.headerHashValid() || !Boolean.TRUE.equals(staged.signatureValid()))
			{
				throw new IOException("The edited package failed header verification");
			}
			if (createBackup && input.equals(output))
			{
				backup = input.resolveSibling(input.getFileName() + ".bak");
				Files.copy(input, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			}
			replace(temporary, output);
			temporary = null;
			return new SaveResult(inspect(output), backup);
		}
		finally
		{
			if (temporary != null)
			{
				Files.deleteIfExists(temporary);
			}
		}
	}

	public List<InternalEntry> contents(Path packageFile) throws IOException
	{
		Info info = inspect(packageFile);
		if (!info.stfs())
		{
			throw new IOException("Internal browsing is available for STFS packages only");
		}
		return new StfsVolume(packageFile).entries().stream()
			.map(entry -> new InternalEntry(entry.path(), entry.directory(), entry.size()))
			.toList();
	}

	public void extract(Path packageFile, String internalPath, Path destination) throws IOException
	{
		StfsVolume volume = new StfsVolume(packageFile);
		StfsVolume.Entry entry = volume.find(internalPath);
		if (entry.directory())
		{
			throw new IOException("Select a file inside the package");
		}
		Path output = requirePath(destination, "Choose an output file");
		Path parent = output.getParent();
		if (parent != null)
		{
			Files.createDirectories(parent);
		}
		volume.extractFile(entry, output);
	}

	public byte[] readInternalFile(Path packageFile, String internalPath) throws IOException
	{
		StfsVolume volume = new StfsVolume(packageFile);
		StfsVolume.Entry entry = volume.find(internalPath);
		if (entry.directory())
		{
			throw new IOException("Select a file inside the package");
		}
		return volume.readFile(entry);
	}

	public SaveResult saveInternalFiles(Path source, Path destination, Map<String, byte[]> replacements,
	                                    boolean createBackup) throws IOException
	{
		Path input = requireFile(source);
		Path output = requirePath(destination, "Choose an output file");
		Info original = inspect(input);
		if (original.signatureType() != SignatureType.CON || !original.stfs())
		{
			throw new IOException("Only CON packages with STFS contents can be changed");
		}
		if (replacements == null || replacements.isEmpty())
		{
			throw new IllegalArgumentException("Choose at least one internal file to change");
		}
		Path parent = output.getParent() == null ? Path.of(".").toAbsolutePath() : output.getParent();
		Files.createDirectories(parent);
		Path temporary = Files.createTempFile(parent, safePrefix(output.getFileName().toString()), ".openrtm.tmp");
		Path backup = null;
		try
		{
			Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			new StfsVolume(temporary).rewrite(replacements);
			resealConPackage(temporary);
			Info staged = inspect(temporary);
			if (!staged.headerHashValid() || !Boolean.TRUE.equals(staged.signatureValid()))
			{
				throw new IOException("The edited package failed header verification");
			}
			if (createBackup && input.equals(output))
			{
				backup = input.resolveSibling(input.getFileName() + ".bak");
				Files.copy(input, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			}
			replace(temporary, output);
			temporary = null;
			return new SaveResult(inspect(output), backup);
		}
		finally
		{
			if (temporary != null)
			{
				Files.deleteIfExists(temporary);
			}
		}
	}

	public static String recommendedDirectory(Info info, String ownerOverride)
	{
		String owner = normalizeId(ownerOverride, 8, null);
		if (owner == null)
		{
			owner = info.creatorId();
		}
		return String.format(Locale.ROOT, "Hdd:\\Content\\%s\\%s\\%08X\\",
			owner, info.titleId(), info.contentType());
	}

	public static String safeRemoteFileName(String name)
	{
		String value = name == null ? "" : name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
		if (value.isBlank())
		{
			value = "content";
		}
		return value.length() <= 42 ? value : value.substring(0, 42);
	}

	public static AssignmentHeader assignmentHeader(byte[] header) throws IOException
	{
		if (header == null || header.length < DEVICE_ID_OFFSET + 20)
		{
			throw new IOException("Xbox 360 package header is incomplete");
		}
		SignatureType signatureType = SignatureType.from(header);
		return new AssignmentHeader(
			signatureType,
			HEX.formatHex(Arrays.copyOfRange(header, CREATOR_ID_OFFSET, CREATOR_ID_OFFSET + 8)),
			HEX.formatHex(Arrays.copyOfRange(header, CONSOLE_ID_OFFSET, CONSOLE_ID_OFFSET + 5)),
			HEX.formatHex(Arrays.copyOfRange(header, DEVICE_ID_OFFSET, DEVICE_ID_OFFSET + 20)),
			HEX.formatHex(Arrays.copyOfRange(header, TITLE_ID_OFFSET, TITLE_ID_OFFSET + 4)),
			int32(header, CONTENT_TYPE_OFFSET));
	}

	static void resealConPackage(Path file) throws IOException
	{
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE))
		{
			byte[] magic = read(channel, 0, 4);
			if (SignatureType.from(magic) != SignatureType.CON)
			{
				throw new IOException("Only CON packages can be re-signed");
			}
			long headerSize = Integer.toUnsignedLong(int32(read(channel, HEADER_SIZE_OFFSET, 4), 0));
			long alignedHeader = alignToBlock(headerSize);
			if (alignedHeader > channel.size())
			{
				throw new IOException("Package header extends beyond the file");
			}
			write(channel, CONTENT_HASH_OFFSET,
				digest(channel, METADATA_OFFSET, alignedHeader - METADATA_OFFSET));
			write(channel, CERTIFICATE_OFFSET, ConCertificate.certificate());
			write(channel, SIGNATURE_OFFSET,
				ConCertificate.sign(read(channel, SIGNED_HEADER_OFFSET, SIGNED_HEADER_LENGTH)));
			channel.force(true);
		}
	}

	private static void applyEdits(Path file, Info original, Edits edits) throws IOException
	{
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE))
		{
			if (edits.titleId() != null)
			{
				write(channel, TITLE_ID_OFFSET, parseId(edits.titleId(), 4, "Title ID"));
			}
			if (edits.creatorId() != null)
			{
				write(channel, CREATOR_ID_OFFSET, parseId(edits.creatorId(), 8, "Profile ID"));
			}
			if (edits.consoleId() != null)
			{
				write(channel, CONSOLE_ID_OFFSET, parseId(edits.consoleId(), 5, "Console ID"));
			}
			if (edits.deviceId() != null)
			{
				write(channel, DEVICE_ID_OFFSET, parseId(edits.deviceId(), 20, "Device ID"));
			}
			if (edits.displayName() != null)
			{
				for (int index = 0; index < 9; index++)
				{
					write(channel, DISPLAY_NAMES_OFFSET + (long) index * FIXED_TEXT_BYTES,
						encodeUtf16(edits.displayName(), FIXED_TEXT_BYTES));
				}
			}
			if (edits.description() != null)
			{
				for (int index = 0; index < 9; index++)
				{
					write(channel, DESCRIPTIONS_OFFSET + (long) index * FIXED_TEXT_BYTES,
						encodeUtf16(edits.description(), FIXED_TEXT_BYTES));
				}
			}
			if (edits.publisher() != null)
			{
				write(channel, PUBLISHER_OFFSET, encodeUtf16(edits.publisher(), SHORT_TEXT_BYTES));
			}
			if (edits.titleName() != null)
			{
				write(channel, TITLE_NAME_OFFSET, encodeUtf16(edits.titleName(), SHORT_TEXT_BYTES));
			}

			int capacity = original.metadataVersion() >= 2 ? 0x3D00 : 0x4000;
			int titleOffset = THUMBNAIL_OFFSET + capacity
				+ (original.metadataVersion() >= 2 ? 3 * FIXED_TEXT_BYTES : 0);
			if (edits.thumbnail() != null)
			{
				writeThumbnail(channel, edits.thumbnail(), THUMBNAIL_SIZE_OFFSET, THUMBNAIL_OFFSET, capacity);
			}
			if (edits.titleThumbnail() != null)
			{
				writeThumbnail(channel, edits.titleThumbnail(), TITLE_THUMBNAIL_SIZE_OFFSET, titleOffset, capacity);
			}
			channel.force(true);
		}
	}

	private static void writeThumbnail(FileChannel channel, byte[] image, long sizeOffset,
	                                   long dataOffset, int capacity) throws IOException
	{
		if (image.length > capacity)
		{
			throw new IOException("Thumbnail is too large; maximum size is " + capacity + " bytes");
		}
		if (image.length > 0)
		{
			BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image));
			if (decoded == null)
			{
				throw new IOException("Thumbnail must be a supported image file");
			}
		}
		byte[] size = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(image.length).array();
		write(channel, sizeOffset, size);
		byte[] padded = new byte[capacity];
		System.arraycopy(image, 0, padded, 0, image.length);
		write(channel, dataOffset, padded);
	}

	public static String contentTypeName(int value)
	{
		return CONTENT_TYPES.getOrDefault(value, String.format(Locale.ROOT, "Unknown (0x%08X)", value));
	}

	private static Map<Integer, String> contentTypes()
	{
		Map<Integer, String> values = new LinkedHashMap<>();
		values.put(0x00000001, "Xbox 360 Saved Game");
		values.put(0x00000002, "Marketplace Content");
		values.put(0x00000003, "Publisher Content");
		values.put(0x00004000, "Installed Xbox 360 Game");
		values.put(0x00005000, "Xbox Game");
		values.put(0x00007000, "Xbox 360 Game");
		values.put(0x00009000, "Avatar Item");
		values.put(0x00010000, "Gamer Profile");
		values.put(0x00020000, "Gamer Picture");
		values.put(0x00030000, "Theme");
		values.put(0x00050000, "Storage Download");
		values.put(0x00060000, "Xbox Saved Game");
		values.put(0x00070000, "Xbox Download");
		values.put(0x00080000, "Game Demo");
		values.put(0x00090000, "Video");
		values.put(0x000A0000, "Game Metadata");
		values.put(0x000B0000, "Title Update");
		values.put(0x000C0000, "Game Trailer");
		values.put(0x000D0000, "Xbox Live Arcade Game");
		values.put(0x000E0000, "XNA Title");
		values.put(0x000F0000, "License Store");
		values.put(0x00100000, "Movie");
		values.put(0x00200000, "TV");
		values.put(0x00300000, "Music Video");
		values.put(0x00400000, "Promotional Content");
		values.put(0x02000000, "Community Game");
		return Map.copyOf(values);
	}

	private static int checkedThumbnailSize(byte[] data, int offset, int capacity) throws IOException
	{
		long value = Integer.toUnsignedLong(int32(data, offset));
		if (value > capacity)
		{
			throw new IOException("Invalid thumbnail size: " + value);
		}
		return (int) value;
	}

	private static String firstLocalizedText(byte[] data, int start)
	{
		for (int index = 0; index < 9; index++)
		{
			String value = readUtf16(data, start + index * FIXED_TEXT_BYTES, FIXED_TEXT_BYTES);
			if (!value.isBlank())
			{
				return value;
			}
		}
		return "";
	}

	private static String readUtf16(byte[] data, int offset, int length)
	{
		int end = offset;
		int limit = Math.min(data.length, offset + length);
		while (end + 1 < limit && (data[end] != 0 || data[end + 1] != 0))
		{
			end += 2;
		}
		return new String(data, offset, Math.max(0, end - offset), StandardCharsets.UTF_16BE).trim();
	}

	private static byte[] encodeUtf16(String value, int length)
	{
		byte[] output = new byte[length];
		byte[] encoded = (value == null ? "" : value).getBytes(StandardCharsets.UTF_16BE);
		System.arraycopy(encoded, 0, output, 0, Math.min(encoded.length, length - 2));
		return output;
	}

	private static String normalizeId(String value, int bytes, String label)
	{
		if (value == null || value.isBlank())
		{
			return null;
		}
		String normalized = value.trim().replace(" ", "").replace("-", "").replace(":", "").toUpperCase(Locale.ROOT);
		if (normalized.startsWith("0X"))
		{
			normalized = normalized.substring(2);
		}
		if (!normalized.matches("[0-9A-F]{" + (bytes * 2) + "}"))
		{
			throw new IllegalArgumentException((label == null ? "ID" : label)
				+ " must contain exactly " + (bytes * 2) + " hexadecimal digits");
		}
		return normalized;
	}

	private static byte[] parseId(String value, int bytes, String label)
	{
		String normalized = normalizeId(value, bytes, label);
		return normalized == null ? new byte[bytes] : HexFormat.of().parseHex(normalized);
	}

	static long alignToBlock(long value) throws IOException
	{
		if (value > Long.MAX_VALUE - 0xFFFL)
		{
			throw new IOException("Package header is too large");
		}
		return (value + 0xFFFL) & ~0xFFFL;
	}

	static byte[] read(FileChannel channel, long offset, int length) throws IOException
	{
		ByteBuffer buffer = ByteBuffer.allocate(length);
		long position = offset;
		while (buffer.hasRemaining())
		{
			int count = channel.read(buffer, position);
			if (count < 0)
			{
				throw new EOFException("Unexpected end of Xbox 360 package");
			}
			if (count > 0)
			{
				position += count;
			}
		}
		return buffer.array();
	}

	static void write(FileChannel channel, long offset, byte[] value) throws IOException
	{
		ByteBuffer buffer = ByteBuffer.wrap(value);
		long position = offset;
		while (buffer.hasRemaining())
		{
			position += channel.write(buffer, position);
		}
	}

	static int int32(byte[] value, int offset)
	{
		return ByteBuffer.wrap(value, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
	}

	private static byte[] digest(FileChannel channel, long offset, long length) throws IOException
	{
		try
		{
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
			long position = offset;
			long remaining = length;
			while (remaining > 0)
			{
				buffer.clear();
				buffer.limit((int) Math.min(buffer.capacity(), remaining));
				int count = channel.read(buffer, position);
				if (count < 0)
				{
					throw new EOFException("Unexpected end of Xbox 360 package header");
				}
				if (count > 0)
				{
					digest.update(buffer.array(), 0, count);
					position += count;
					remaining -= count;
				}
			}
			return digest.digest();
		}
		catch (NoSuchAlgorithmException impossible)
		{
			throw new IllegalStateException("SHA-1 is unavailable", impossible);
		}
	}

	private static Path requireFile(Path file) throws IOException
	{
		Path value = requirePath(file, "Choose an Xbox 360 package");
		if (!Files.isRegularFile(value))
		{
			throw new IOException("Package does not exist: " + value);
		}
		return value;
	}

	private static Path requirePath(Path path, String message)
	{
		if (path == null)
		{
			throw new IllegalArgumentException(message);
		}
		return path.toAbsolutePath().normalize();
	}

	private static String safePrefix(String name)
	{
		String value = name.replaceAll("[^A-Za-z0-9._-]", "_");
		return value.length() >= 3 ? value : (value + "pkg").substring(0, 3);
	}

	private static void replace(Path source, Path destination) throws IOException
	{
		try
		{
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException ignored)
		{
			Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public enum SignatureType
	{
		CON("CON "), LIVE("LIVE"), PIRS("PIRS");

		private final String magic;

		SignatureType(String magic)
		{
			this.magic = magic;
		}

		static SignatureType from(byte[] data) throws IOException
		{
			if (data.length < 4)
			{
				throw new IOException("Package signature is missing");
			}
			String value = new String(data, 0, 4, StandardCharsets.US_ASCII);
			return Arrays.stream(values()).filter(type -> type.magic.equals(value)).findFirst()
				.orElseThrow(() -> new IOException("Not a CON, LIVE, or PIRS Xbox 360 package"));
		}
	}

	public record Info(Path path, SignatureType signatureType, int contentType, String contentTypeName,
	                   String titleId, String titleNameFromId, String creatorId, String consoleId,
	                   String deviceId, String displayName, String description, String publisher,
	                   String titleName, int flags, long packageSize, long headerSize, boolean stfs,
	                   boolean headerHashValid, Boolean signatureValid, byte[] thumbnail,
	                   byte[] titleThumbnail, String contentId, int metadataVersion)
	{
		public Info
		{
			thumbnail = thumbnail.clone();
			titleThumbnail = titleThumbnail.clone();
		}

		@Override
		public byte[] thumbnail()
		{
			return thumbnail.clone();
		}

		@Override
		public byte[] titleThumbnail()
		{
			return titleThumbnail.clone();
		}
	}

	public record Edits(String displayName, String titleName, String description, String publisher,
	                    String titleId, String creatorId, String consoleId, String deviceId,
	                    byte[] thumbnail, byte[] titleThumbnail)
	{
		public Edits
		{
			thumbnail = thumbnail == null ? null : thumbnail.clone();
			titleThumbnail = titleThumbnail == null ? null : titleThumbnail.clone();
		}
	}

	public record SaveResult(Info info, Path backup)
	{
	}

	public record InternalEntry(String path, boolean directory, long size)
	{
	}

	public record AssignmentHeader(SignatureType signatureType, String creatorId, String consoleId,
	                               String deviceId, String titleId, int contentType)
	{
	}
}
