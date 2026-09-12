package openrtm.profile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ProfileDatabase
{
	public static final int ACHIEVEMENTS = 1;
	public static final int IMAGES = 2;
	public static final int SETTINGS = 3;
	public static final int TITLES = 4;
	public static final int STRINGS = 5;
	private static final int HEADER_SIZE = 0x18;
	private static final int ENTRY_SIZE = 0x12;
	private static final int FREE_ENTRY_SIZE = 0x08;
	private static final long SYNC_INDEX_ID = 0x0100000000L;
	private static final long SYNC_INFO_ID = 0x0200000000L;

	private final int version;
	private final int reserved;
	private int entryCapacity;
	private int freeEntryCapacity;
	private final List<Record> records = new ArrayList<>();

	public ProfileDatabase(byte[] source) throws IOException
	{
		if (source == null || source.length < HEADER_SIZE)
		{
			throw new IOException("Profile database is incomplete");
		}
		ByteBuffer input = ByteBuffer.wrap(source).order(ByteOrder.BIG_ENDIAN);
		if (input.getInt() != 0x58444246)
		{
			throw new IOException("File is not an Xbox 360 profile database");
		}
		version = Short.toUnsignedInt(input.getShort());
		reserved = Short.toUnsignedInt(input.getShort());
		if (version != 1)
		{
			throw new IOException("Unsupported profile database version: " + version);
		}
		entryCapacity = input.getInt();
		int entryCount = input.getInt();
		freeEntryCapacity = input.getInt();
		int freeEntryCount = input.getInt();
		if (entryCapacity < 0 || entryCount < 0 || entryCount > entryCapacity
			|| freeEntryCapacity < 1 || freeEntryCount < 0 || freeEntryCount > freeEntryCapacity)
		{
			throw new IOException("Invalid profile database table sizes");
		}
		long dataOffsetLong = dataOffset(entryCapacity, freeEntryCapacity);
		if (dataOffsetLong > source.length)
		{
			throw new IOException("Profile database tables extend beyond the file");
		}
		int dataOffset = (int) dataOffsetLong;
		for (int index = 0; index < entryCount; index++)
		{
			int namespace = Short.toUnsignedInt(input.getShort());
			long id = input.getLong();
			long offset = Integer.toUnsignedLong(input.getInt());
			long length = Integer.toUnsignedLong(input.getInt());
			long start = dataOffsetLong + offset;
			long end = start + length;
			if (start < dataOffsetLong || end < start || end > source.length || length > Integer.MAX_VALUE)
			{
				throw new IOException("Profile database record extends beyond the file");
			}
			records.add(new Record(namespace, id,
				Arrays.copyOfRange(source, (int) start, (int) end)));
		}
	}

	public List<Record> records(int namespace)
	{
		List<Record> matches = new ArrayList<>();
		for (Record record : records)
		{
			if (record.namespace == namespace && record.id != SYNC_INDEX_ID && record.id != SYNC_INFO_ID)
			{
				matches.add(record.copy());
			}
		}
		return Collections.unmodifiableList(matches);
	}

	public boolean contains(int namespace, long id)
	{
		return find(namespace, id) != null;
	}

	public byte[] data(int namespace, long id) throws IOException
	{
		Record record = find(namespace, id);
		if (record == null)
		{
			throw new IOException("Profile database record was not found");
		}
		return record.data();
	}

	public void put(int namespace, long id, byte[] data, boolean pendingSync) throws IOException
	{
		if (data == null)
		{
			throw new IllegalArgumentException("Profile database data is required");
		}
		putDirect(namespace, id, data);
		if (pendingSync && namespace != IMAGES && namespace != STRINGS)
		{
			markPending(namespace, id);
		}
	}

	public void remove(int namespace, long id)
	{
		records.removeIf(record -> record.namespace == namespace && record.id == id);
	}

	public byte[] toByteArray() throws IOException
	{
		while (entryCapacity < records.size())
		{
			entryCapacity = Math.addExact(entryCapacity, 0x200);
		}
		freeEntryCapacity = Math.max(1, freeEntryCapacity);
		long base = dataOffset(entryCapacity, freeEntryCapacity);
		long dataLength = 0;
		for (Record record : records)
		{
			dataLength = Math.addExact(dataLength, record.data.length);
		}
		long totalLength = Math.addExact(base, dataLength);
		if (totalLength > Integer.MAX_VALUE)
		{
			throw new IOException("Profile database is too large");
		}
		List<Record> sorted = new ArrayList<>(records);
		sorted.sort(Comparator.comparingInt((Record record) -> record.namespace)
			.thenComparing((left, right) -> Long.compareUnsigned(left.id, right.id)));
		ByteBuffer output = ByteBuffer.allocate((int) totalLength).order(ByteOrder.BIG_ENDIAN);
		output.putInt(0x58444246);
		output.putShort((short) version);
		output.putShort((short) reserved);
		output.putInt(entryCapacity);
		output.putInt(sorted.size());
		output.putInt(freeEntryCapacity);
		output.putInt(1);
		int offset = 0;
		for (Record record : sorted)
		{
			output.putShort((short) record.namespace);
			output.putLong(record.id);
			output.putInt(offset);
			output.putInt(record.data.length);
			offset = Math.addExact(offset, record.data.length);
		}
		output.position(HEADER_SIZE + entryCapacity * ENTRY_SIZE);
		output.putInt(offset);
		output.putInt(~offset);
		output.position((int) base);
		for (Record record : sorted)
		{
			output.put(record.data);
		}
		return output.array();
	}

	private void markPending(int namespace, long id) throws IOException
	{
		Record info = find(namespace, SYNC_INFO_ID);
		long next = 1;
		long last = 0;
		long serverTime = 0;
		if (info != null)
		{
			if (info.data.length != 24)
			{
				throw new IOException("Invalid profile synchronization state");
			}
			ByteBuffer state = ByteBuffer.wrap(info.data).order(ByteOrder.BIG_ENDIAN);
			next = state.getLong();
			last = state.getLong();
			serverTime = state.getLong();
		}
		Record index = find(namespace, SYNC_INDEX_ID);
		byte[] indexData = index == null ? new byte[0] : index.data.clone();
		if (indexData.length % 16 != 0)
		{
			throw new IOException("Invalid profile synchronization index");
		}
		ByteBuffer entries = ByteBuffer.wrap(indexData).order(ByteOrder.BIG_ENDIAN);
		List<long[]> synced = new ArrayList<>();
		List<long[]> pending = new ArrayList<>();
		for (int offset = 0; offset < indexData.length; offset += 16)
		{
			long entryId = entries.getLong(offset);
			long syncId = entries.getLong(offset + 8);
			if (entryId == 0 || entryId == id)
			{
				continue;
			}
			if (syncId == 0)
			{
				synced.add(new long[]{entryId, syncId});
			}
			else
			{
				pending.add(new long[]{entryId, syncId});
			}
		}
		long syncId = next;
		pending.add(new long[]{id, syncId});
		int usedLength = Math.multiplyExact(synced.size() + pending.size(), 16);
		indexData = new byte[Math.max(indexData.length, usedLength)];
		entries = ByteBuffer.wrap(indexData).order(ByteOrder.BIG_ENDIAN);
		for (long[] entry : synced)
		{
			entries.putLong(entry[0]);
			entries.putLong(entry[1]);
		}
		for (long[] entry : pending)
		{
			entries.putLong(entry[0]);
			entries.putLong(entry[1]);
		}
		long updatedNext = Long.compareUnsigned(next, last) < 0 ? last + 1 : next + 1;
		ByteBuffer state = ByteBuffer.allocate(24).order(ByteOrder.BIG_ENDIAN);
		state.putLong(updatedNext);
		state.putLong(last);
		state.putLong(serverTime);
		putDirect(namespace, SYNC_INFO_ID, state.array());
		putDirect(namespace, SYNC_INDEX_ID, indexData);
	}

	private void putDirect(int namespace, long id, byte[] data)
	{
		Record existing = find(namespace, id);
		if (existing == null)
		{
			records.add(new Record(namespace, id, data));
		}
		else
		{
			existing.data = data.clone();
		}
	}

	private Record find(int namespace, long id)
	{
		for (Record record : records)
		{
			if (record.namespace == namespace && record.id == id)
			{
				return record;
			}
		}
		return null;
	}

	private static long dataOffset(int entries, int freeEntries)
	{
		return (long) HEADER_SIZE + (long) entries * ENTRY_SIZE + (long) freeEntries * FREE_ENTRY_SIZE;
	}

	public static final class Record
	{
		private final int namespace;
		private final long id;
		private byte[] data;

		private Record(int namespace, long id, byte[] data)
		{
			this.namespace = namespace;
			this.id = id;
			this.data = data.clone();
		}

		public int namespace()
		{
			return namespace;
		}

		public long id()
		{
			return id;
		}

		public byte[] data()
		{
			return data.clone();
		}

		private Record copy()
		{
			return new Record(namespace, id, data);
		}
	}
}
