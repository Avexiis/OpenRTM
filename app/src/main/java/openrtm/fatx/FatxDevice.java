package openrtm.fatx;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class FatxDevice implements Closeable
{
	private static final int MAGIC = 0x58544146;
	private static final int ENTRY_SIZE = 0x40;
	private static final int NAME_SIZE = 0x2A;
	private static final long USB_DATA = 0x20000000L;
	private static final long HDD_DATA = 0x130EB0000L;
	private final Backing backing;
	private final List<Partition> partitions;

	private FatxDevice(Backing backing) throws IOException
	{
		this.backing = backing;
		partitions = locatePartitions();
		if (partitions.isEmpty())
		{
			backing.close();
			throw new IOException("No Xbox 360 FATX partitions were found");
		}
	}

	public static FatxDevice open(Path source) throws IOException
	{
		if (source == null)
		{
			throw new IllegalArgumentException("Choose an Xbox 360 storage device, image, or folder");
		}
		Path path = source.toAbsolutePath().normalize();
		if (Files.isDirectory(path))
		{
			Path dataDirectory = Files.isDirectory(path.resolve("Xbox360")) ? path.resolve("Xbox360") : path;
			List<Path> segments = new ArrayList<>();
			try (Stream<Path> files = Files.list(dataDirectory))
			{
				files.filter(Files::isRegularFile)
					.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).startsWith("data"))
					.sorted(Comparator.comparing(file -> file.getFileName().toString().toLowerCase(Locale.ROOT)))
					.forEach(segments::add);
			}
			if (segments.size() < 3)
			{
				throw new IOException("The selected folder does not contain Xbox 360 Data files");
			}
			return new FatxDevice(new SplitBacking(segments));
		}
		return new FatxDevice(new FileBacking(path));
	}

	public static List<Path> discover()
	{
		List<Path> found = new ArrayList<>();
		Path devices = Path.of("/dev");
		if (Files.isDirectory(devices))
		{
			try (Stream<Path> entries = Files.list(devices))
			{
				entries.filter(path -> path.getFileName().toString().matches("(?:sd[a-z]|nvme\\d+n\\d+|mmcblk\\d+)"))
					.filter(Files::isReadable)
					.sorted()
					.forEach(found::add);
			}
			catch (IOException ignored)
			{
			}
		}
		return Collections.unmodifiableList(found);
	}

	public List<Partition> partitions()
	{
		return Collections.unmodifiableList(partitions);
	}

	public List<Entry> list(Entry directory) throws IOException
	{
		if (directory == null || !directory.directory())
		{
			throw new IOException("Select a folder on the Xbox 360 storage device");
		}
		List<Long> chain = clusterChain(directory.partition, directory.startingCluster);
		List<Entry> entries = new ArrayList<>();
		boolean finished = false;
		for (long cluster : chain)
		{
			long clusterOffset = clusterOffset(directory.partition, cluster);
			byte[] data = backing.read(clusterOffset, directory.partition.clusterSize);
			for (int offset = 0; offset + ENTRY_SIZE <= data.length; offset += ENTRY_SIZE)
			{
				int nameLength = data[offset] & 0xFF;
				if (nameLength == 0 || nameLength == 0xFF)
				{
					finished = true;
					break;
				}
				if (nameLength == 0xE5)
				{
					continue;
				}
				if (nameLength > NAME_SIZE)
				{
					throw new IOException("Invalid FATX directory entry name length");
				}
				int attributes = data[offset + 1] & 0xFF;
				String name = new String(data, offset + 2, nameLength, StandardCharsets.US_ASCII);
				long firstCluster = uint32(data, offset + 0x2C);
				long size = uint32(data, offset + 0x30);
				String path = directory.path.equals("/") ? "/" + name : directory.path + "/" + name;
				entries.add(new Entry(directory.partition, name, path, (attributes & 0x10) != 0,
					attributes, firstCluster, size, clusterOffset + offset));
			}
			if (finished)
			{
				break;
			}
		}
		entries.sort(Comparator.comparing((Entry entry) -> !entry.directory)
			.thenComparing(entry -> entry.name.toLowerCase(Locale.ROOT)));
		return Collections.unmodifiableList(entries);
	}

	public void extract(Entry file, Path destination) throws IOException
	{
		if (file == null || file.directory())
		{
			throw new IOException("Select a file to extract");
		}
		Path output = destination.toAbsolutePath().normalize();
		Path parent = output.getParent();
		if (parent != null)
		{
			Files.createDirectories(parent);
		}
		try (FileChannel target = FileChannel.open(output, StandardOpenOption.CREATE,
			StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
		{
			long remaining = file.size;
			long position = 0;
			for (long cluster : clusterChain(file.partition, file.startingCluster))
			{
				int length = (int) Math.min(file.partition.clusterSize, remaining);
				byte[] data = backing.read(clusterOffset(file.partition, cluster), length);
				ByteBuffer buffer = ByteBuffer.wrap(data);
				while (buffer.hasRemaining())
				{
					position += target.write(buffer, position);
				}
				remaining -= length;
				if (remaining == 0)
				{
					break;
				}
			}
			if (remaining != 0)
			{
				throw new EOFException("FATX file cluster chain ended early");
			}
			target.force(true);
		}
	}

	public Entry importFile(Entry directory, Path localFile) throws IOException
	{
		if (localFile == null || !Files.isRegularFile(localFile))
		{
			throw new IOException("Choose a local file to import");
		}
		requireWritableDirectory(directory);
		String name = localFile.getFileName().toString();
		validateName(name);
		long size = Files.size(localFile);
		if (size > 0xFFFFFFFFL)
		{
			throw new IOException("FATX files must be smaller than 4 GB");
		}
		long required = size == 0 ? 0 : (size + directory.partition.clusterSize - 1)
			/ directory.partition.clusterSize;
		if (required > Integer.MAX_VALUE)
		{
			throw new IOException("The selected file requires too many FATX clusters");
		}
		Entry existing = findChild(directory, name);
		List<Long> allocated = allocate(directory.partition, (int) required);
		try
		{
			writeChain(directory.partition, allocated);
			writeFileData(directory.partition, allocated, localFile, size);
			return finishImport(directory, name, size, allocated, existing);
		}
		catch (IOException failure)
		{
			freeChain(directory.partition, allocated);
			throw failure;
		}
	}

	public Entry importFile(Entry directory, String name, byte[] data) throws IOException
	{
		requireWritableDirectory(directory);
		validateName(name);
		Entry existing = findChild(directory, name);
		List<Long> allocated = allocate(directory.partition,
			(data.length + directory.partition.clusterSize - 1) / directory.partition.clusterSize);
		try
		{
			writeChain(directory.partition, allocated);
			writeData(directory.partition, allocated, data, (byte) 0);
			return finishImport(directory, name, data.length, allocated, existing);
		}
		catch (IOException failure)
		{
			freeChain(directory.partition, allocated);
			throw failure;
		}
	}

	public Entry createDirectory(Entry directory, String name) throws IOException
	{
		requireWritableDirectory(directory);
		validateName(name);
		if (findChild(directory, name) != null)
		{
			throw new IOException("An entry with that name already exists");
		}
		List<Long> allocated = allocate(directory.partition, 1);
		try
		{
			writeChain(directory.partition, allocated);
			writeData(directory.partition, allocated, new byte[0], (byte) 0xFF);
			long entryAddress = findDirectorySlot(directory);
			writeDirectoryEntry(entryAddress, name, 0x10, allocated.get(0), 0);
			backing.force();
			return new Entry(directory.partition, name,
				directory.path.equals("/") ? "/" + name : directory.path + "/" + name,
				true, 0x10, allocated.get(0), 0, entryAddress);
		}
		catch (IOException failure)
		{
			freeChain(directory.partition, allocated);
			throw failure;
		}
	}

	public void delete(Entry entry) throws IOException
	{
		if (entry == null || entry.entryAddress < 0)
		{
			throw new IOException("Select an entry to delete");
		}
		if (entry.directory())
		{
			for (Entry child : list(entry))
			{
				delete(child);
			}
		}
		freeChain(entry.partition, clusterChain(entry.partition, entry.startingCluster));
		backing.write(entry.entryAddress, new byte[]{(byte) 0xE5});
		backing.force();
	}

	@Override
	public void close() throws IOException
	{
		backing.close();
	}

	private List<Partition> locatePartitions() throws IOException
	{
		long size = backing.size();
		List<PartitionSpec> candidates = new ArrayList<>();
		candidates.add(new PartitionSpec("Content", 0, size, false));
		candidates.add(new PartitionSpec("System Cache", 0x8000400L, 0x47FF000L, true));
		candidates.add(new PartitionSpec("System Auxiliary", 0x8115200L, 0x8000000L, true));
		candidates.add(new PartitionSpec("System Extended", 0x12000400L, 0xDFFFC00L, true));
		candidates.add(new PartitionSpec("Content", USB_DATA, Math.max(0, size - USB_DATA), true));
		candidates.add(new PartitionSpec("System Cache", 0x80000L, 0x80000000L, false));
		candidates.add(new PartitionSpec("Game Cache", 0x80080000L, 0xA0E30000L, false));
		candidates.add(new PartitionSpec("System Auxiliary", 0x10C080000L, 0xCE30000L, false));
		candidates.add(new PartitionSpec("System Extended", 0x118EB0000L, 0x8000000L, false));
		candidates.add(new PartitionSpec("System Partition", 0x120EB0000L, 0x10000000L, false));
		candidates.add(new PartitionSpec("Content", HDD_DATA, Math.max(0, size - HDD_DATA), false));
		List<Partition> found = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (PartitionSpec candidate : candidates)
		{
			if (!seen.add(candidate.offset) || candidate.size < 0x2000 || candidate.offset + 16 > size)
			{
				continue;
			}
			byte[] header = backing.read(candidate.offset, 16);
			ByteBuffer values = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN);
			if (values.getInt() != MAGIC)
			{
				continue;
			}
			long partitionId = Integer.toUnsignedLong(values.getInt());
			long sectorsPerCluster = Integer.toUnsignedLong(values.getInt());
			long rootCluster = Integer.toUnsignedLong(values.getInt());
			if (sectorsPerCluster != 2 && sectorsPerCluster != 4 && sectorsPerCluster != 8
				&& sectorsPerCluster != 16 && sectorsPerCluster != 32
				&& sectorsPerCluster != 64 && sectorsPerCluster != 128)
			{
				continue;
			}
			long availableSize = Math.min(candidate.size, size - candidate.offset);
			found.add(createPartition(candidate.name, candidate.offset, availableSize, partitionId,
				(int) sectorsPerCluster, rootCluster, candidate.usb));
		}
		return found;
	}

	private Partition createPartition(String name, long offset, long size, long id, int sectors,
	                                  long rootCluster, boolean usb) throws IOException
	{
		int clusterSize = Math.multiplyExact(sectors, 0x200);
		long preliminaryClusters = (size / clusterSize) + 1;
		int fatEntrySize = (usb && offset == USB_DATA) || preliminaryClusters >= 0xFFF0 ? 4 : 2;
		long fatSize = align4096(Math.multiplyExact(preliminaryClusters, fatEntrySize));
		if (size <= 0x1000 + fatSize)
		{
			throw new IOException("FATX partition is too small");
		}
		long clusterCount = (size - 0x1000 - fatSize) / clusterSize;
		long clusterStart = offset + 0x1000 + fatSize;
		if (rootCluster < 1 || rootCluster > clusterCount)
		{
			throw new IOException("FATX root directory cluster is invalid");
		}
		Partition partition = new Partition(name, offset, size, id, clusterSize, fatEntrySize,
			clusterCount, clusterStart, rootCluster);
		partition.root = new Entry(partition, name, "/", true, 0x10, rootCluster, 0, -1);
		return partition;
	}

	private Entry findChild(Entry directory, String name) throws IOException
	{
		for (Entry child : list(directory))
		{
			if (child.name.equalsIgnoreCase(name))
			{
				return child;
			}
		}
		return null;
	}

	private long findDirectorySlot(Entry directory) throws IOException
	{
		List<Long> chain = clusterChain(directory.partition, directory.startingCluster);
		for (long cluster : chain)
		{
			long base = clusterOffset(directory.partition, cluster);
			byte[] data = backing.read(base, directory.partition.clusterSize);
			for (int offset = 0; offset + ENTRY_SIZE <= data.length; offset += ENTRY_SIZE)
			{
				int marker = data[offset] & 0xFF;
				if (marker == 0 || marker == 0xFF || marker == 0xE5)
				{
					return base + offset;
				}
			}
		}
		List<Long> extra = allocate(directory.partition, 1);
		writeChainLink(directory.partition, chain.get(chain.size() - 1), extra.get(0));
		writeChainLink(directory.partition, extra.get(0), lastMarker(directory.partition));
		writeData(directory.partition, extra, new byte[0], (byte) 0xFF);
		return clusterOffset(directory.partition, extra.get(0));
	}

	private List<Long> allocate(Partition partition, int count) throws IOException
	{
		List<Long> free = new ArrayList<>(count);
		for (long cluster = 1; cluster <= partition.clusterCount && free.size() < count; cluster++)
		{
			if (readFat(partition, cluster) == 0)
			{
				free.add(cluster);
			}
		}
		if (free.size() != count)
		{
			throw new IOException("Xbox 360 storage device does not have enough free space");
		}
		return free;
	}

	private List<Long> clusterChain(Partition partition, long startingCluster) throws IOException
	{
		if (startingCluster == 0)
		{
			return Collections.emptyList();
		}
		List<Long> chain = new ArrayList<>();
		Set<Long> visited = new HashSet<>();
		long current = startingCluster;
		long last = lastMarker(partition);
		while (current != 0 && current != last)
		{
			if (current < 1 || current > partition.clusterCount || !visited.add(current))
			{
				throw new IOException("Invalid or cyclic FATX cluster chain");
			}
			chain.add(current);
			current = readFat(partition, current);
		}
		return chain;
	}

	private long readFat(Partition partition, long cluster) throws IOException
	{
		byte[] data = backing.read(partition.offset + 0x1000 + cluster * partition.fatEntrySize,
			partition.fatEntrySize);
		ByteBuffer value = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
		return partition.fatEntrySize == 2 ? Short.toUnsignedLong(value.getShort())
			: Integer.toUnsignedLong(value.getInt());
	}

	private void writeChain(Partition partition, List<Long> clusters) throws IOException
	{
		for (int index = 0; index < clusters.size(); index++)
		{
			long next = index + 1 < clusters.size() ? clusters.get(index + 1) : lastMarker(partition);
			writeChainLink(partition, clusters.get(index), next);
		}
	}

	private void writeChainLink(Partition partition, long cluster, long next) throws IOException
	{
		ByteBuffer value = ByteBuffer.allocate(partition.fatEntrySize).order(ByteOrder.BIG_ENDIAN);
		if (partition.fatEntrySize == 2)
		{
			value.putShort((short) next);
		}
		else
		{
			value.putInt((int) next);
		}
		backing.write(partition.offset + 0x1000 + cluster * partition.fatEntrySize, value.array());
	}

	private void freeChain(Partition partition, List<Long> clusters) throws IOException
	{
		for (long cluster : clusters)
		{
			writeChainLink(partition, cluster, 0);
		}
	}

	private void writeData(Partition partition, List<Long> clusters, byte[] data, byte fill) throws IOException
	{
		int sourceOffset = 0;
		for (long cluster : clusters)
		{
			byte[] block = new byte[partition.clusterSize];
			if (fill != 0)
			{
				Arrays.fill(block, fill);
			}
			int length = Math.min(block.length, data.length - sourceOffset);
			if (length > 0)
			{
				System.arraycopy(data, sourceOffset, block, 0, length);
				sourceOffset += length;
			}
			backing.write(clusterOffset(partition, cluster), block);
		}
	}

	private void writeFileData(Partition partition, List<Long> clusters, Path file, long size) throws IOException
	{
		try (FileChannel source = FileChannel.open(file, StandardOpenOption.READ))
		{
			long remaining = size;
			for (long cluster : clusters)
			{
				byte[] block = new byte[partition.clusterSize];
				int length = (int) Math.min(block.length, remaining);
				ByteBuffer buffer = ByteBuffer.wrap(block, 0, length);
				while (buffer.hasRemaining())
				{
					if (source.read(buffer) < 0)
					{
						throw new EOFException("Local file ended while it was being imported");
					}
				}
				backing.write(clusterOffset(partition, cluster), block);
				remaining -= length;
			}
			if (remaining != 0)
			{
				throw new EOFException("Local file could not be fully imported");
			}
		}
	}

	private Entry finishImport(Entry directory, String name, long size, List<Long> allocated,
	                           Entry existing) throws IOException
	{
		long entryAddress = existing == null ? findDirectorySlot(directory) : existing.entryAddress;
		long firstCluster = allocated.isEmpty() ? 0 : allocated.get(0);
		writeDirectoryEntry(entryAddress, name, 0, firstCluster, size);
		if (existing != null)
		{
			freeChain(existing.partition, clusterChain(existing.partition, existing.startingCluster));
		}
		backing.force();
		return new Entry(directory.partition, name,
			directory.path.equals("/") ? "/" + name : directory.path + "/" + name,
			false, 0, firstCluster, size, entryAddress);
	}

	private void writeDirectoryEntry(long address, String name, int attributes, long cluster, long size)
		throws IOException
	{
		byte[] entry = new byte[ENTRY_SIZE];
		byte[] encoded = name.getBytes(StandardCharsets.US_ASCII);
		entry[0] = (byte) encoded.length;
		entry[1] = (byte) attributes;
		System.arraycopy(encoded, 0, entry, 2, encoded.length);
		Arrays.fill(entry, 2 + encoded.length, 0x2C, (byte) 0xFF);
		ByteBuffer values = ByteBuffer.wrap(entry).order(ByteOrder.BIG_ENDIAN);
		values.putInt(0x2C, (int) cluster);
		values.putInt(0x30, (int) size);
		int time = packedTime(LocalDateTime.now());
		values.putInt(0x34, time);
		values.putInt(0x38, time);
		values.putInt(0x3C, time);
		backing.write(address, entry);
	}

	private static void requireWritableDirectory(Entry directory) throws IOException
	{
		if (directory == null || !directory.directory())
		{
			throw new IOException("Select a folder on the Xbox 360 storage device");
		}
	}

	private static void validateName(String name)
	{
		if (name == null || name.isBlank() || name.length() > NAME_SIZE
			|| !StandardCharsets.US_ASCII.newEncoder().canEncode(name)
			|| name.matches(".*[\\\\/:*?\"<>|].*"))
		{
			throw new IllegalArgumentException("FATX names must contain 1 to 42 supported characters");
		}
	}

	private static long clusterOffset(Partition partition, long cluster)
	{
		return partition.clusterStart + partition.clusterSize * (cluster - 1);
	}

	private static long lastMarker(Partition partition)
	{
		return partition.fatEntrySize == 2 ? 0xFFFFL : 0xFFFFFFFFL;
	}

	private static long uint32(byte[] value, int offset)
	{
		return Integer.toUnsignedLong(ByteBuffer.wrap(value, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt());
	}

	private static long align4096(long value)
	{
		return (value + 0xFFFL) & ~0xFFFL;
	}

	private static int packedTime(LocalDateTime time)
	{
		return ((time.getYear() - 1980) & 0x7F) << 25
			| (time.getMonthValue() & 0xF) << 21
			| (time.getDayOfMonth() & 0x1F) << 16
			| (time.getHour() & 0x1F) << 11
			| (time.getMinute() & 0x3F) << 5
			| ((time.getSecond() / 2) & 0x1F);
	}

	public static final class Partition
	{
		private final String name;
		private final long offset;
		private final long size;
		private final long id;
		private final int clusterSize;
		private final int fatEntrySize;
		private final long clusterCount;
		private final long clusterStart;
		private final long rootCluster;
		private Entry root;

		private Partition(String name, long offset, long size, long id, int clusterSize, int fatEntrySize,
		                  long clusterCount, long clusterStart, long rootCluster)
		{
			this.name = name;
			this.offset = offset;
			this.size = size;
			this.id = id;
			this.clusterSize = clusterSize;
			this.fatEntrySize = fatEntrySize;
			this.clusterCount = clusterCount;
			this.clusterStart = clusterStart;
			this.rootCluster = rootCluster;
		}

		public String name()
		{
			return name;
		}

		public long size()
		{
			return size;
		}

		public long id()
		{
			return id;
		}

		public Entry root()
		{
			return root;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	public static final class Entry
	{
		private final Partition partition;
		private final String name;
		private final String path;
		private final boolean directory;
		private final int attributes;
		private final long startingCluster;
		private final long size;
		private final long entryAddress;

		private Entry(Partition partition, String name, String path, boolean directory, int attributes,
		              long startingCluster, long size, long entryAddress)
		{
			this.partition = partition;
			this.name = name;
			this.path = path;
			this.directory = directory;
			this.attributes = attributes;
			this.startingCluster = startingCluster;
			this.size = size;
			this.entryAddress = entryAddress;
		}

		public Partition partition()
		{
			return partition;
		}

		public String name()
		{
			return name;
		}

		public String path()
		{
			return path;
		}

		public boolean directory()
		{
			return directory;
		}

		public int attributes()
		{
			return attributes;
		}

		public long size()
		{
			return size;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private static final class PartitionSpec
	{
		private final String name;
		private final long offset;
		private final long size;
		private final boolean usb;

		private PartitionSpec(String name, long offset, long size, boolean usb)
		{
			this.name = name;
			this.offset = offset;
			this.size = size;
			this.usb = usb;
		}
	}

	private interface Backing extends Closeable
	{
		long size() throws IOException;

		byte[] read(long offset, int length) throws IOException;

		void write(long offset, byte[] data) throws IOException;

		void force() throws IOException;
	}

	private static final class FileBacking implements Backing
	{
		private final FileChannel channel;

		private FileBacking(Path file) throws IOException
		{
			channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
		}

		@Override
		public long size() throws IOException
		{
			return channel.size();
		}

		@Override
		public byte[] read(long offset, int length) throws IOException
		{
			ByteBuffer data = ByteBuffer.allocate(length);
			long position = offset;
			while (data.hasRemaining())
			{
				int count = channel.read(data, position);
				if (count < 0)
				{
					throw new EOFException("Unexpected end of Xbox 360 storage device");
				}
				position += count;
			}
			return data.array();
		}

		@Override
		public void write(long offset, byte[] data) throws IOException
		{
			ByteBuffer value = ByteBuffer.wrap(data);
			long position = offset;
			while (value.hasRemaining())
			{
				position += channel.write(value, position);
			}
		}

		@Override
		public void force() throws IOException
		{
			channel.force(true);
		}

		@Override
		public void close() throws IOException
		{
			channel.close();
		}
	}

	private static final class SplitBacking implements Backing
	{
		private final List<FileChannel> channels = new ArrayList<>();
		private final List<Long> starts = new ArrayList<>();
		private long size;

		private SplitBacking(List<Path> files) throws IOException
		{
			try
			{
				for (Path file : files)
				{
					starts.add(size);
					FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
					channels.add(channel);
					size += channel.size();
				}
			}
			catch (IOException failure)
			{
				close();
				throw failure;
			}
		}

		@Override
		public long size()
		{
			return size;
		}

		@Override
		public byte[] read(long offset, int length) throws IOException
		{
			byte[] output = new byte[length];
			int completed = 0;
			while (completed < length)
			{
				int index = segment(offset + completed);
				FileChannel channel = channels.get(index);
				long local = offset + completed - starts.get(index);
				int count = (int) Math.min(length - completed, channel.size() - local);
				ByteBuffer part = ByteBuffer.wrap(output, completed, count);
				while (part.hasRemaining())
				{
					int read = channel.read(part, local);
					if (read < 0)
					{
						throw new EOFException("Unexpected end of Xbox 360 Data file");
					}
					local += read;
				}
				completed += count;
			}
			return output;
		}

		@Override
		public void write(long offset, byte[] data) throws IOException
		{
			int completed = 0;
			while (completed < data.length)
			{
				int index = segment(offset + completed);
				FileChannel channel = channels.get(index);
				long local = offset + completed - starts.get(index);
				int count = (int) Math.min(data.length - completed, channel.size() - local);
				ByteBuffer part = ByteBuffer.wrap(data, completed, count);
				while (part.hasRemaining())
				{
					local += channel.write(part, local);
				}
				completed += count;
			}
		}

		@Override
		public void force() throws IOException
		{
			for (FileChannel channel : channels)
			{
				channel.force(true);
			}
		}

		@Override
		public void close() throws IOException
		{
			IOException failure = null;
			for (FileChannel channel : channels)
			{
				try
				{
					channel.close();
				}
				catch (IOException closeFailure)
				{
					failure = closeFailure;
				}
			}
			if (failure != null)
			{
				throw failure;
			}
		}

		private int segment(long offset) throws IOException
		{
			if (offset < 0 || offset >= size)
			{
				throw new EOFException("Xbox 360 storage offset is outside the Data files");
			}
			for (int index = starts.size() - 1; index >= 0; index--)
			{
				if (offset >= starts.get(index))
				{
					return index;
				}
			}
			throw new EOFException("Xbox 360 storage offset is invalid");
		}
	}
}
