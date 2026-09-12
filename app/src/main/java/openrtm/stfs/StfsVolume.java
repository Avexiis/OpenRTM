package openrtm.stfs;

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
			int created = int32be(directory, offset + 0x38);
			int accessed = int32be(directory, offset + 0x3C);
			raw.add(new RawEntry(index, name, isDirectory, contiguous, allocated, firstBlock, parentIndex, size,
				created, accessed));
		}

		Map<Integer, RawEntry> byIndex = new HashMap<>();
		raw.forEach(entry -> byIndex.put(entry.index(), entry));
		List<Entry> parsed = new ArrayList<>(raw.size());
		for (RawEntry entry : raw)
		{
			String path = buildPath(entry, byIndex);
			parsed.add(new Entry(path, entry.name(), entry.directory(), entry.contiguous(), entry.allocatedBlocks(),
				entry.firstBlock(), entry.parentIndex(), entry.size(), entry.created(), entry.accessed()));
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

	void rewrite(Map<String, byte[]> replacements) throws IOException
	{
		if (readOnlyFormat)
		{
			throw new IOException("Only CON package contents can be changed");
		}
		Map<String, byte[]> pending = new HashMap<>();
		if (replacements != null)
		{
			for (Map.Entry<String, byte[]> replacement : replacements.entrySet())
			{
				String path = normalizePath(replacement.getKey());
				if (path.isBlank() || replacement.getValue() == null)
				{
					throw new IOException("Internal file replacements require a path and data");
				}
				pending.put(path.toLowerCase(Locale.ROOT), replacement.getValue().clone());
			}
		}

		List<BuildEntry> buildEntries = new ArrayList<>();
		for (Entry entry : entries())
		{
			byte[] data = null;
			if (!entry.directory())
			{
				String key = normalizePath(entry.path()).toLowerCase(Locale.ROOT);
				data = pending.remove(key);
				if (data == null)
				{
					data = readFile(entry);
				}
			}
			buildEntries.add(new BuildEntry(entry.path(), entry.name(), entry.directory(), data,
				entry.created(), entry.accessed()));
		}
		for (Map.Entry<String, byte[]> addition : pending.entrySet())
		{
			String path = normalizePath(addition.getKey());
			if (path.contains("/"))
			{
				throw new IOException("New internal files can only be added at the package root");
			}
			if (path.length() > 40 || !StandardCharsets.US_ASCII.newEncoder().canEncode(path))
			{
				throw new IOException("Internal file names must be 40 ASCII characters or fewer");
			}
			buildEntries.add(new BuildEntry(path, path, false, addition.getValue(), 0, 0));
		}
		writeRebuiltVolume(buildEntries);
		entries = null;
	}

	private void writeRebuiltVolume(List<BuildEntry> buildEntries) throws IOException
	{
		int directoryBlocks = Math.max(1,
			Math.toIntExact(((long) buildEntries.size() * DIRECTORY_ENTRY_SIZE + BLOCK_SIZE - 1) / BLOCK_SIZE));
		long nextBlock = directoryBlocks;
		for (BuildEntry entry : buildEntries)
		{
			if (!entry.directory())
			{
				entry.blockCount = Math.toIntExact(((long) entry.data.length + BLOCK_SIZE - 1) / BLOCK_SIZE);
				entry.firstBlock = entry.blockCount == 0 ? 0 : nextBlock;
				nextBlock += entry.blockCount;
			}
		}
		if (nextBlock <= 0 || nextBlock > 0xFFFFFFL)
		{
			throw new IOException("Rebuilt STFS volume is too large");
		}

		byte[] directoryData = buildDirectory(buildEntries, directoryBlocks);
		List<byte[]> logicalBlocks = new ArrayList<>(Math.toIntExact(nextBlock));
		for (int block = 0; block < directoryBlocks; block++)
		{
			logicalBlocks.add(Arrays.copyOfRange(directoryData, block * BLOCK_SIZE, (block + 1) * BLOCK_SIZE));
		}
		for (BuildEntry entry : buildEntries)
		{
			if (entry.directory())
			{
				continue;
			}
			for (int block = 0; block < entry.blockCount; block++)
			{
				byte[] data = new byte[BLOCK_SIZE];
				int sourceOffset = block * BLOCK_SIZE;
				int length = Math.min(BLOCK_SIZE, entry.data.length - sourceOffset);
				if (length > 0)
				{
					System.arraycopy(entry.data, sourceOffset, data, 0, length);
				}
				logicalBlocks.add(data);
			}
		}

		Path parent = packageFile.getParent() == null ? Path.of(".").toAbsolutePath() : packageFile.getParent();
		Path staged = Files.createTempFile(parent, "stfs-rebuild-", ".tmp");
		try
		{
			byte[] header;
			try (FileChannel source = FileChannel.open(packageFile, StandardOpenOption.READ))
			{
				header = readExact(source, 0, Math.toIntExact(backingOffset));
			}
			int descriptor = PackageService.VOLUME_DESCRIPTOR_OFFSET;
			header[descriptor + 2] = (byte) (header[descriptor + 2] & ~0x02);
			putUint16le(header, descriptor + 3, directoryBlocks);
			putUint24le(header, descriptor + 5, 0);
			putInt32be(header, descriptor + 28, logicalBlocks.size());
			putInt32be(header, descriptor + 32, 0);

			try (FileChannel output = FileChannel.open(staged, StandardOpenOption.READ, StandardOpenOption.WRITE,
				StandardOpenOption.TRUNCATE_EXISTING))
			{
				PackageService.write(output, 0, header);
				for (int logical = 0; logical < logicalBlocks.size(); logical++)
				{
					PackageService.write(output, calculatedDataOffset(logical),
						logicalBlocks.get(logical));
				}
				byte[] rootHash = writeHashTree(output, logicalBlocks, directoryBlocks);
				PackageService.write(output, descriptor + 8L, rootHash);
				output.force(true);
			}
			replaceFile(staged, packageFile);
			staged = null;
		}
		finally
		{
			if (staged != null)
			{
				Files.deleteIfExists(staged);
			}
		}
	}

	private byte[] buildDirectory(List<BuildEntry> buildEntries, int directoryBlocks) throws IOException
	{
		byte[] directory = new byte[directoryBlocks * BLOCK_SIZE];
		Map<String, Integer> indexes = new HashMap<>();
		for (int index = 0; index < buildEntries.size(); index++)
		{
			indexes.put(normalizePath(buildEntries.get(index).path).toLowerCase(Locale.ROOT), index);
		}
		for (int index = 0; index < buildEntries.size(); index++)
		{
			BuildEntry entry = buildEntries.get(index);
			int offset = index * DIRECTORY_ENTRY_SIZE;
			byte[] name = entry.name.getBytes(StandardCharsets.US_ASCII);
			if (name.length == 0 || name.length > 40)
			{
				throw new IOException("Invalid internal file name: " + entry.name);
			}
			System.arraycopy(name, 0, directory, offset, name.length);
			directory[offset + 0x28] = (byte) (name.length | (entry.directory() ? 0x80 : 0x40));
			putUint24le(directory, offset + 0x29, entry.blockCount);
			putUint24le(directory, offset + 0x2C, entry.blockCount);
			putUint24le(directory, offset + 0x2F, entry.firstBlock);
			String parentPath = parentPath(entry.path);
			int parentIndex = 0xFFFF;
			if (!parentPath.isBlank())
			{
				Integer found = indexes.get(parentPath.toLowerCase(Locale.ROOT));
				if (found == null || !buildEntries.get(found).directory())
				{
					throw new IOException("Missing internal parent directory: " + parentPath);
				}
				parentIndex = found;
			}
			putUint16be(directory, offset + 0x32, parentIndex);
			putInt32be(directory, offset + 0x34, entry.directory() ? 0 : entry.data.length);
			putInt32be(directory, offset + 0x38, entry.created);
			putInt32be(directory, offset + 0x3C, entry.accessed);
		}
		return directory;
	}

	private byte[] writeHashTree(FileChannel output, List<byte[]> blocks, int directoryBlocks) throws IOException
	{
		int hierarchy = blocks.size() > BLOCKS_PER_LEVEL[1] ? 2 : blocks.size() > BLOCKS_PER_LEVEL[0] ? 1 : 0;
		List<byte[]> current = new ArrayList<>();
		int groups = (blocks.size() + 0xA9) / 0xAA;
		for (int group = 0; group < groups; group++)
		{
			byte[] hashBlock = new byte[BLOCK_SIZE];
			for (int entry = 0; entry < 0xAA; entry++)
			{
				int logical = group * 0xAA + entry;
				if (logical >= blocks.size())
				{
					break;
				}
				int offset = entry * HASH_ENTRY_SIZE;
				System.arraycopy(sha1(blocks.get(logical)), 0, hashBlock, offset, 20);
				hashBlock[offset + 20] = (byte) 0x80;
				long next = logical < directoryBlocks - 1 ? logical + 1L : 0xFFFFFFL;
				putUint24be(hashBlock, offset + 21, next);
			}
			long representative = (long) group * 0xAA;
			PackageService.write(output, calculatedHashOffset(representative, 0, 0), hashBlock);
			PackageService.write(output, calculatedHashOffset(representative, 0, 1), hashBlock);
			current.add(hashBlock);
		}
		for (int level = 1; level <= hierarchy; level++)
		{
			List<byte[]> parents = new ArrayList<>();
			for (int group = 0; group * 0xAA < current.size(); group++)
			{
				byte[] hashBlock = new byte[BLOCK_SIZE];
				for (int entry = 0; entry < 0xAA && group * 0xAA + entry < current.size(); entry++)
				{
					System.arraycopy(sha1(current.get(group * 0xAA + entry)), 0,
						hashBlock, entry * HASH_ENTRY_SIZE, 20);
				}
				long representative = (long) group * BLOCKS_PER_LEVEL[level];
				PackageService.write(output, calculatedHashOffset(representative, level, 0), hashBlock);
				PackageService.write(output, calculatedHashOffset(representative, level, 1), hashBlock);
				parents.add(hashBlock);
			}
			current = parents;
		}
		return sha1(current.get(0));
	}

	private long calculatedDataOffset(long logical) throws IOException
	{
		long physical = (((logical + BLOCKS_PER_LEVEL[0]) / BLOCKS_PER_LEVEL[0]) << 1) + logical;
		if (logical >= BLOCKS_PER_LEVEL[0])
		{
			physical += ((logical + BLOCKS_PER_LEVEL[1]) / BLOCKS_PER_LEVEL[1]) << 1;
		}
		if (logical >= BLOCKS_PER_LEVEL[1])
		{
			physical += 2;
		}
		return calculatedOffset(physical, 0);
	}

	private long calculatedHashOffset(long logical, int level, int active) throws IOException
	{
		long physical;
		if (level == 0)
		{
			long group0 = logical / BLOCKS_PER_LEVEL[0];
			physical = group0 * 0xACL;
			if (group0 > 0)
			{
				long group1 = logical / BLOCKS_PER_LEVEL[1];
				physical += (group1 + 1) << 1;
				if (group1 > 0)
				{
					physical += 2;
				}
			}
		}
		else if (level == 1)
		{
			long group1 = logical / BLOCKS_PER_LEVEL[1];
			physical = group1 * 0x723AL + (group1 == 0 ? 0xACL : 2L);
		}
		else if (level == 2)
		{
			physical = 0x723AL;
		}
		else
		{
			throw new IOException("Invalid STFS hash level");
		}
		return calculatedOffset(physical, active);
	}

	private long calculatedOffset(long physical, int active) throws IOException
	{
		try
		{
			return Math.addExact(backingOffset,
				Math.multiplyExact(Math.addExact(physical, active), BLOCK_SIZE));
		}
		catch (ArithmeticException failure)
		{
			throw new IOException("STFS block offset overflow", failure);
		}
	}

	private static String parentPath(String path)
	{
		String normalized = normalizePath(path);
		int split = normalized.lastIndexOf('/');
		return split < 0 ? "" : normalized.substring(0, split);
	}

	private static void replaceFile(Path source, Path destination) throws IOException
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

	private static void putUint16le(byte[] value, int offset, int number)
	{
		value[offset] = (byte) number;
		value[offset + 1] = (byte) (number >>> 8);
	}

	private static void putUint16be(byte[] value, int offset, int number)
	{
		value[offset] = (byte) (number >>> 8);
		value[offset + 1] = (byte) number;
	}

	private static void putUint24le(byte[] value, int offset, long number)
	{
		value[offset] = (byte) number;
		value[offset + 1] = (byte) (number >>> 8);
		value[offset + 2] = (byte) (number >>> 16);
	}

	private static void putUint24be(byte[] value, int offset, long number)
	{
		value[offset] = (byte) (number >>> 16);
		value[offset + 1] = (byte) (number >>> 8);
		value[offset + 2] = (byte) number;
	}

	private static void putInt32be(byte[] value, int offset, int number)
	{
		ByteBuffer.wrap(value, offset, 4).order(ByteOrder.BIG_ENDIAN).putInt(number);
	}

	record Entry(String path, String name, boolean directory, boolean contiguous, long allocatedBlocks,
	             long firstBlock, int parentIndex, long size, int created, int accessed)
	{
	}

	private record RawEntry(int index, String name, boolean directory, boolean contiguous, long allocatedBlocks,
	                        long firstBlock, int parentIndex, long size, int created, int accessed)
	{
	}

	private static final class BuildEntry
	{
		private final String path;
		private final String name;
		private final boolean directory;
		private final byte[] data;
		private final int created;
		private final int accessed;
		private long firstBlock;
		private int blockCount;

		private BuildEntry(String path, String name, boolean directory, byte[] data, int created, int accessed)
		{
			this.path = path;
			this.name = name;
			this.directory = directory;
			this.data = data;
			this.created = created;
			this.accessed = accessed;
		}

		private boolean directory()
		{
			return directory;
		}
	}
}
