package openrtm.stfs;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class StfsVolume
{
	private static final int BLOCK_SIZE = 0x1000;
	private static final int DIRECTORY_ENTRY_SIZE = 0x40;
	private static final int HASH_ENTRY_SIZE = 0x18;
	private static final long[] BLOCKS_PER_LEVEL = {0xAAL, 0x70E4L, 0x4AF768L};

	private final Path packageFile;
	private final long backingOffset;
	private final boolean readOnlyFormat;
	private final int rootActiveIndex;
	private final int directoryBlockCount;
	private final long directoryFirstBlock;
	private final long totalBlocks;
	private final int formatShift;
	private final long[] blockValues;
	private final int rootHierarchy;
	private List<Entry> entries;

	StfsVolume(Path packageFile) throws IOException
	{
		this.packageFile = packageFile.toAbsolutePath().normalize();
		try (FileChannel channel = FileChannel.open(this.packageFile, StandardOpenOption.READ))
		{
			byte[] fixed = PackageService.read(channel, 0, PackageService.VOLUME_TYPE_OFFSET + 4);
			if (PackageService.int32(fixed, PackageService.VOLUME_TYPE_OFFSET) != 0)
			{
				throw new IOException("Package does not contain an STFS volume");
			}
			long headerSize = Integer.toUnsignedLong(PackageService.int32(fixed, PackageService.HEADER_SIZE_OFFSET));
			backingOffset = PackageService.alignToBlock(headerSize);
			int descriptor = PackageService.VOLUME_DESCRIPTOR_OFFSET;
			if ((fixed[descriptor] & 0xFF) != 0x24)
			{
				throw new IOException("Invalid STFS volume descriptor");
			}
			int flags = fixed[descriptor + 2] & 0xFF;
			readOnlyFormat = (flags & 1) != 0;
			rootActiveIndex = (flags >>> 1) & 1;
			directoryBlockCount = uint16le(fixed, descriptor + 3);
			directoryFirstBlock = uint24le(fixed, descriptor + 5);
			totalBlocks = Integer.toUnsignedLong(PackageService.int32(fixed, descriptor + 28));
			if (directoryBlockCount <= 0 || directoryFirstBlock >= totalBlocks)
			{
				throw new IOException("Invalid STFS directory allocation");
			}
			formatShift = readOnlyFormat ? 0 : 1;
			blockValues = readOnlyFormat ? new long[]{0xABL, 0x718FL} : new long[]{0xACL, 0x723AL};
			rootHierarchy = totalBlocks > BLOCKS_PER_LEVEL[1] ? 2 : totalBlocks > BLOCKS_PER_LEVEL[0] ? 1 : 0;
			if (backingOffset > channel.size())
			{
				throw new IOException("STFS data begins beyond the package file");
			}
		}
	}

	List<Entry> entries() throws IOException
	{
		if (entries != null)
		{
			return entries;
		}
		byte[] directory = readChain(directoryFirstBlock, directoryBlockCount, false);
		List<RawEntry> raw = new ArrayList<>();
		for (int offset = 0, index = 0; offset + DIRECTORY_ENTRY_SIZE <= directory.length;
		     offset += DIRECTORY_ENTRY_SIZE, index++)
		{
			int attributes = directory[offset + 0x28] & 0xFF;
			int nameLength = attributes & 0x3F;
			if (nameLength == 0)
			{
				continue;
			}
			if (nameLength > 40)
			{
				throw new IOException("Invalid STFS directory name length");
			}
			String name = new String(directory, offset, nameLength, StandardCharsets.US_ASCII);
			boolean contiguous = (attributes & 0x40) != 0;
			boolean isDirectory = (attributes & 0x80) != 0;
			long allocated = uint24le(directory, offset + 0x29);
			long firstBlock = uint24le(directory, offset + 0x2F);
			int parentIndex = uint16be(directory, offset + 0x32);
			long size = Integer.toUnsignedLong(int32be(directory, offset + 0x34));
			raw.add(new RawEntry(index, name, isDirectory, contiguous, allocated, firstBlock, parentIndex, size));
		}

		Map<Integer, RawEntry> byIndex = new HashMap<>();
		raw.forEach(entry -> byIndex.put(entry.index(), entry));
		List<Entry> parsed = new ArrayList<>(raw.size());
		for (RawEntry entry : raw)
		{
			String path = buildPath(entry, byIndex);
			parsed.add(new Entry(path, entry.name(), entry.directory(), entry.contiguous(), entry.allocatedBlocks(),
				entry.firstBlock(), entry.parentIndex(), entry.size()));
		}
		entries = List.copyOf(parsed);
		return entries;
	}

	Entry find(String internalPath) throws IOException
	{
		String requested = normalizePath(internalPath);
		return entries().stream()
			.filter(entry -> normalizePath(entry.path()).equalsIgnoreCase(requested))
			.findFirst()
			.orElseThrow(() -> new IOException("Package file not found: " + internalPath));
	}

	byte[] readFile(Entry entry) throws IOException
	{
		if (entry.directory())
		{
			throw new IOException("Cannot read a directory as a file");
		}
		if (entry.size() > Integer.MAX_VALUE)
		{
			throw new IOException("Internal file is too large to load into memory");
		}
		int blocks = Math.toIntExact((entry.size() + BLOCK_SIZE - 1) / BLOCK_SIZE);
		byte[] data = readChain(entry.firstBlock(), blocks, entry.contiguous());
		return Arrays.copyOf(data, (int) entry.size());
	}

	void extractFile(Entry entry, Path destination) throws IOException
	{
		if (entry.directory())
		{
			throw new IOException("Cannot extract a directory as a file");
		}
		int blockCount = Math.toIntExact((entry.size() + BLOCK_SIZE - 1) / BLOCK_SIZE);
		List<Long> blocks = chain(entry.firstBlock(), blockCount, entry.contiguous());
		try (FileChannel input = FileChannel.open(packageFile, StandardOpenOption.READ);
		     FileChannel output = FileChannel.open(destination, StandardOpenOption.CREATE,
			     StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
		{
			long remaining = entry.size();
			long outputOffset = 0;
			for (long logical : blocks)
			{
				byte[] block = readDataBlock(input, logical);
				int length = (int) Math.min(BLOCK_SIZE, remaining);
				ByteBuffer buffer = ByteBuffer.wrap(block, 0, length);
				while (buffer.hasRemaining())
				{
					outputOffset += output.write(buffer, outputOffset);
				}
				remaining -= length;
			}
			output.force(true);
		}
	}

	void replaceSameSize(Entry entry, byte[] replacement) throws IOException
	{
		if (entry.directory() || replacement.length != entry.size())
		{
			throw new IOException("Internal replacement must be a file with the same size");
		}
		List<Long> blocks = chain(entry.firstBlock(),
			Math.toIntExact((entry.size() + BLOCK_SIZE - 1) / BLOCK_SIZE), entry.contiguous());
		try (FileChannel channel = FileChannel.open(packageFile, StandardOpenOption.READ, StandardOpenOption.WRITE))
		{
			Set<Long> changed = new HashSet<>();
			for (int index = 0; index < blocks.size(); index++)
			{
				long logical = blocks.get(index);
				byte[] block = readDataBlock(channel, logical);
				int sourceOffset = index * BLOCK_SIZE;
				int length = Math.min(BLOCK_SIZE, replacement.length - sourceOffset);
				System.arraycopy(replacement, sourceOffset, block, 0, length);
				PackageService.write(channel, dataOffset(logical), block);
				writeDataHash(channel, logical, sha1(block));
				changed.add(logical);
			}
			rehashTree(channel, changed);
			channel.force(true);
		}
	}

	private byte[] readChain(long firstBlock, int blockCount, boolean contiguous) throws IOException
	{
		List<Long> blocks = chain(firstBlock, blockCount, contiguous);
		byte[] output = new byte[Math.multiplyExact(blocks.size(), BLOCK_SIZE)];
		try (FileChannel channel = FileChannel.open(packageFile, StandardOpenOption.READ))
		{
			for (int index = 0; index < blocks.size(); index++)
			{
				byte[] block = readDataBlock(channel, blocks.get(index));
				System.arraycopy(block, 0, output, index * BLOCK_SIZE, BLOCK_SIZE);
			}
		}
		return output;
	}

	private List<Long> chain(long firstBlock, int blockCount, boolean contiguous) throws IOException
	{
		List<Long> blocks = new ArrayList<>(blockCount);
		Set<Long> seen = new HashSet<>();
		long current = firstBlock;
		try (FileChannel channel = FileChannel.open(packageFile, StandardOpenOption.READ))
		{
			for (int index = 0; index < blockCount; index++)
			{
				if (current < 0 || current >= totalBlocks || !seen.add(current))
				{
					throw new IOException("Invalid or cyclic STFS block chain");
				}
				blocks.add(current);
				if (index + 1 < blockCount)
				{
					current = contiguous ? current + 1 : nextBlock(channel, current);
				}
			}
		}
		return blocks;
	}

	private long nextBlock(FileChannel channel, long logicalBlock) throws IOException
	{
		byte[] hashBlock = readHashBlock(channel, logicalBlock, 0);
		int offset = (int) (logicalBlock % 0xAA) * HASH_ENTRY_SIZE + 20;
		return uint24be(hashBlock, offset + 1);
	}

	private byte[] readDataBlock(FileChannel channel, long logicalBlock) throws IOException
	{
		return readExact(channel, dataOffset(logicalBlock), BLOCK_SIZE);
	}

	private byte[] readHashBlock(FileChannel channel, long logicalBlock, int level) throws IOException
	{
		int active = activeIndex(channel, logicalBlock, level);
		return readExact(channel, hashBlockOffset(logicalBlock, level, active), BLOCK_SIZE);
	}

	private int activeIndex(FileChannel channel, long logicalBlock, int requestedLevel) throws IOException
	{
		int active = rootActiveIndex;
		for (int level = rootHierarchy; level > requestedLevel; level--)
		{
			byte[] parent = readExact(channel, hashBlockOffset(logicalBlock, level, active), BLOCK_SIZE);
			int entry = (int) ((logicalBlock / BLOCKS_PER_LEVEL[level - 1]) % 0xAA);
			long state = Integer.toUnsignedLong(int32be(parent, entry * HASH_ENTRY_SIZE + 20));
			active = (int) ((state >>> 30) & 1);
		}
		return active;
	}

	private void writeDataHash(FileChannel channel, long logicalBlock, byte[] hash) throws IOException
	{
		int active = activeIndex(channel, logicalBlock, 0);
		long hashOffset = hashBlockOffset(logicalBlock, 0, active)
			+ (logicalBlock % 0xAA) * HASH_ENTRY_SIZE;
		PackageService.write(channel, hashOffset, hash);
	}

	private void rehashTree(FileChannel channel, Set<Long> changedBlocks) throws IOException
	{
		Set<Long> affected = new HashSet<>(changedBlocks);
		for (int level = 0; level < rootHierarchy; level++)
		{
			Set<Long> next = new HashSet<>();
			for (long logical : affected)
			{
				int childActive = activeIndex(channel, logical, level);
				byte[] child = readExact(channel, hashBlockOffset(logical, level, childActive), BLOCK_SIZE);
				int parentActive = activeIndex(channel, logical, level + 1);
				long parentOffset = hashBlockOffset(logical, level + 1, parentActive);
				int parentEntry = (int) ((logical / BLOCKS_PER_LEVEL[level]) % 0xAA);
				PackageService.write(channel, parentOffset + (long) parentEntry * HASH_ENTRY_SIZE, sha1(child));
				next.add(logical);
			}
			affected = next;
		}
		byte[] root = readExact(channel, hashBlockOffset(0, rootHierarchy, rootActiveIndex), BLOCK_SIZE);
		PackageService.write(channel, PackageService.VOLUME_DESCRIPTOR_OFFSET + 8L, sha1(root));
	}

	private long dataOffset(long logicalBlock) throws IOException
	{
		long physical = (((logicalBlock + BLOCKS_PER_LEVEL[0]) / BLOCKS_PER_LEVEL[0]) << formatShift) + logicalBlock;
		if (logicalBlock >= BLOCKS_PER_LEVEL[0])
		{
			physical += ((logicalBlock + BLOCKS_PER_LEVEL[1]) / BLOCKS_PER_LEVEL[1]) << formatShift;
		}
		if (logicalBlock >= BLOCKS_PER_LEVEL[1])
		{
			physical += 1L << formatShift;
		}
		return checkedOffset(physical, 0);
	}

	private long hashBlockOffset(long logicalBlock, int level, int active) throws IOException
	{
		long physical;
		switch (level)
		{
			case 0 -> {
				long group0 = logicalBlock / BLOCKS_PER_LEVEL[0];
				physical = group0 * blockValues[0];
				if (group0 > 0)
				{
					long group1 = logicalBlock / BLOCKS_PER_LEVEL[1];
					physical += (group1 + 1) << formatShift;
					if (group1 > 0)
					{
						physical += 1L << formatShift;
					}
				}
			}
			case 1 -> {
				long group1 = logicalBlock / BLOCKS_PER_LEVEL[1];
				physical = group1 * blockValues[1];
				physical += group1 == 0 ? blockValues[0] : 1L << formatShift;
			}
			case 2 -> physical = blockValues[1];
			default -> throw new IOException("Invalid STFS hash level");
		}
		return checkedOffset(physical, active);
	}

	private long checkedOffset(long physicalBlock, int active) throws IOException
	{
		try
		{
			return Math.addExact(backingOffset, Math.multiplyExact(Math.addExact(physicalBlock, active), BLOCK_SIZE));
		}
		catch (ArithmeticException overflow)
		{
			throw new IOException("STFS block offset overflow", overflow);
		}
	}

	private static byte[] readExact(FileChannel channel, long offset, int length) throws IOException
	{
		ByteBuffer buffer = ByteBuffer.allocate(length);
		long position = offset;
		while (buffer.hasRemaining())
		{
			int count = channel.read(buffer, position);
			if (count < 0)
			{
				throw new EOFException("STFS block extends beyond the package");
			}
			if (count > 0)
			{
				position += count;
			}
		}
		return buffer.array();
	}

	private static byte[] sha1(byte[] data)
	{
		try
		{
			return MessageDigest.getInstance("SHA-1").digest(data);
		}
		catch (NoSuchAlgorithmException impossible)
		{
			throw new IllegalStateException("SHA-1 is unavailable", impossible);
		}
	}

	private static String buildPath(RawEntry entry, Map<Integer, RawEntry> entries) throws IOException
	{
		List<String> parts = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();
		RawEntry current = entry;
		parts.add(current.name());
		while (current.parentIndex() != 0xFFFF)
		{
			if (!seen.add(current.index()))
			{
				throw new IOException("Cyclic STFS directory hierarchy");
			}
			current = entries.get(current.parentIndex());
			if (current == null || !current.directory())
			{
				throw new IOException("Invalid STFS parent directory reference");
			}
			parts.add(0, current.name());
		}
		return String.join("/", parts);
	}

	private static String normalizePath(String value)
	{
		String path = value == null ? "" : value.trim().replace('\\', '/');
		while (path.startsWith("/"))
		{
			path = path.substring(1);
		}
		return path;
	}

	private static int uint16le(byte[] value, int offset)
	{
		return Short.toUnsignedInt(ByteBuffer.wrap(value, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort());
	}

	private static int uint16be(byte[] value, int offset)
	{
		return Short.toUnsignedInt(ByteBuffer.wrap(value, offset, 2).order(ByteOrder.BIG_ENDIAN).getShort());
	}

	private static long uint24le(byte[] value, int offset)
	{
		return Integer.toUnsignedLong((value[offset] & 0xFF) | ((value[offset + 1] & 0xFF) << 8)
			| ((value[offset + 2] & 0xFF) << 16));
	}

	private static long uint24be(byte[] value, int offset)
	{
		return Integer.toUnsignedLong(((value[offset] & 0xFF) << 16) | ((value[offset + 1] & 0xFF) << 8)
			| (value[offset + 2] & 0xFF));
	}

	private static int int32be(byte[] value, int offset)
	{
		return ByteBuffer.wrap(value, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
	}

	record Entry(String path, String name, boolean directory, boolean contiguous, long allocatedBlocks,
	             long firstBlock, int parentIndex, long size)
	{
	}

	private record RawEntry(int index, String name, boolean directory, boolean contiguous, long allocatedBlocks,
	                        long firstBlock, int parentIndex, long size)
	{
	}
}
