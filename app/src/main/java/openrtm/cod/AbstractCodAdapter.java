package openrtm.cod;

import openrtm.console.ConsoleService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

abstract class AbstractCodAdapter implements CodAdapter
{
	private final ConsoleService console;
	private final CodGame game;

	AbstractCodAdapter(ConsoleService console, CodGame game)
	{
		this.console = console;
		this.game = game;
	}

	@Override
	public final CodGame game()
	{
		return game;
	}

	@Override
	public List<StatGroup> statGroups()
	{
		return List.of();
	}

	@Override
	public final Map<String, Long> readStats(String group)
	{
		requireTitle();
		return onReadStats(group);
	}

	@Override
	public final void writeStats(String group, Map<String, Long> values)
	{
		requireTitle();
		onWriteStats(group, values);
	}

	@Override
	public int classCount()
	{
		return 0;
	}

	@Override
	public boolean classNamesReadable()
	{
		return false;
	}

	@Override
	public final List<String> readClassNames()
	{
		requireTitle();
		return onReadClassNames();
	}

	@Override
	public final void writeClassNames(List<String> names)
	{
		requireTitle();
		onWriteClassNames(names);
	}

	@Override
	public final void unlockAll()
	{
		requireTitle();
		onUnlockAll();
	}

	@Override
	public final void unlockClient(int client)
	{
		requireTitle();
		onUnlockClient(checkedClient(client));
	}

	@Override
	public final void derankSignedInProfile()
	{
		requireTitle();
		onDerankSignedInProfile();
	}

	@Override
	public boolean clientNamesReadable()
	{
		return false;
	}

	@Override
	public int maximumClients()
	{
		return 18;
	}

	@Override
	public final List<ClientInfo> readClients()
	{
		requireTitle();
		return onReadClients();
	}

	@Override
	public List<Action> actions()
	{
		return List.of();
	}

	@Override
	public final void runAction(String key, int client)
	{
		requireTitle();
		onRunAction(key, checkedClient(client));
	}

	@Override
	public List<Toggle> toggles()
	{
		return List.of();
	}

	@Override
	public final void setToggle(String key, boolean enabled, int client)
	{
		requireTitle();
		onSetToggle(key, enabled, checkedClient(client));
	}

	@Override
	public List<NumberOption> numberOptions()
	{
		return List.of();
	}

	@Override
	public final void setNumber(String key, long value, int client)
	{
		requireTitle();
		onSetNumber(key, value, checkedClient(client));
	}

	@Override
	public List<ChoiceOption> choiceOptions()
	{
		return List.of();
	}

	@Override
	public final void setChoice(String key, String value, int client)
	{
		requireTitle();
		onSetChoice(key, value, checkedClient(client));
	}

	@Override
	public List<TextOption> textOptions()
	{
		return List.of();
	}

	@Override
	public final void setText(String key, String value, int client)
	{
		requireTitle();
		onSetText(key, value == null ? "" : value, checkedClient(client));
	}

	protected Map<String, Long> onReadStats(String group)
	{
		throw unsupported("read stats");
	}

	protected void onWriteStats(String group, Map<String, Long> values)
	{
		throw unsupported("write stats");
	}

	protected List<String> onReadClassNames()
	{
		throw unsupported("read class names");
	}

	protected void onWriteClassNames(List<String> names)
	{
		throw unsupported("write class names");
	}

	protected abstract void onUnlockAll();

	protected void onUnlockClient(int client)
	{
		throw unsupported("unlocking a selected player");
	}

	protected abstract void onDerankSignedInProfile();

	protected List<ClientInfo> onReadClients()
	{
		throw unsupported("reading players");
	}

	protected void onRunAction(String key, int client)
	{
		throw unknown(key);
	}

	protected void onSetToggle(String key, boolean enabled, int client)
	{
		throw unknown(key);
	}

	protected void onSetNumber(String key, long value, int client)
	{
		throw unknown(key);
	}

	protected void onSetChoice(String key, String value, int client)
	{
		throw unknown(key);
	}

	protected void onSetText(String key, String value, int client)
	{
		throw unknown(key);
	}

	protected final void command(long address, String command)
	{
		console.callTitleVoid(address, 0, command);
	}

	protected final void serverCommand(long address, int client, String command)
	{
		console.callTitleVoid(address, client, 0, command);
	}

	protected final long call(long address, Object... arguments)
	{
		return console.callTitle(address, arguments);
	}

	protected final byte[] read(long address, int length)
	{
		return console.readMemory(address, length);
	}

	protected final int readIntLittle(long address)
	{
		return ByteBuffer.wrap(read(address, 4)).order(ByteOrder.LITTLE_ENDIAN).getInt();
	}

	protected final long readUnsignedIntLittle(long address)
	{
		return Integer.toUnsignedLong(readIntLittle(address));
	}

	protected final int readIntBig(long address)
	{
		return ByteBuffer.wrap(read(address, 4)).order(ByteOrder.BIG_ENDIAN).getInt();
	}

	protected final void write(long address, byte[] data)
	{
		console.writeMemory(address, data);
	}

	protected final void writeIntLittle(long address, long value)
	{
		console.writeMemory(address, ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) value).array());
	}

	protected final void writeIntBig(long address, long value)
	{
		console.writeMemory(address, ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((int) value).array());
	}

	protected final void writeShortBig(long address, long value)
	{
		console.writeMemory(address, ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) value).array());
	}

	protected final void writeFloatBig(long address, float value)
	{
		console.writeMemory(address, ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(value).array());
	}

	protected final void writeByte(long address, long value)
	{
		console.writeByte(address, (int) value);
	}

	protected final String readAscii(long address, int length)
	{
		byte[] data = read(address, length);
		int end = 0;
		while (end < data.length && data[end] != 0)
		{
			end++;
		}
		return new String(data, 0, end, StandardCharsets.US_ASCII).trim();
	}

	protected final List<ClientInfo> readClientTable(long base, long stride, long nameOffset, int length)
	{
		List<ClientInfo> clients = new ArrayList<>();
		for (int slot = 0; slot < maximumClients(); slot++)
		{
			requireTitle();
			String name = cleanClientName(readAscii(base + slot * stride + nameOffset, length));
			if (!name.isBlank())
			{
				clients.add(new ClientInfo(slot, name));
			}
		}
		return List.copyOf(clients);
	}

	protected final List<ClientInfo> readPointerClientTable(long base, long stride, long pointerOffset, long nameOffset,
		int length)
	{
		List<ClientInfo> clients = new ArrayList<>();
		for (int slot = 0; slot < maximumClients(); slot++)
		{
			requireTitle();
			long pointer = Integer.toUnsignedLong(readIntBig(base + slot * stride + pointerOffset));
			if (pointer == 0)
			{
				continue;
			}
			String name = cleanClientName(readAscii(pointer + nameOffset, length));
			if (!name.isBlank())
			{
				clients.add(new ClientInfo(slot, name));
			}
		}
		return List.copyOf(clients);
	}

	protected final String cleanClientName(String value)
	{
		return value == null ? "" : value.replaceAll("\\^[0-9]", "").replaceAll("[^\\x20-\\x7E]", "").trim();
	}

	protected final void writeAscii(long address, String value, int length)
	{
		byte[] output = new byte[length];
		byte[] input = cleanText(value).getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(input, 0, output, 0, Math.min(input.length, Math.max(0, length - 1)));
		write(address, output);
	}

	protected final Map<String, Long> readDirect(Map<String, Long> addresses)
	{
		Map<String, Long> values = new LinkedHashMap<>();
		addresses.forEach((key, address) -> values.put(key, readUnsignedIntLittle(address)));
		return values;
	}

	protected final void writeDirect(Map<String, Long> addresses, Map<String, Long> values)
	{
		addresses.forEach((key, address) -> {
			Long value = values.get(key);
			if (value != null)
			{
				writeIntLittle(address, value);
			}
		});
	}

	protected final String cleanText(String value)
	{
		String cleaned = value == null ? "" : value.replaceAll("[\\r\\n\\t]", " ").trim();
		if (cleaned.indexOf(';') >= 0 || cleaned.indexOf('"') >= 0 || cleaned.indexOf('\\') >= 0)
		{
			throw new IllegalArgumentException("Text cannot contain quotes, semicolons, or backslashes");
		}
		return cleaned;
	}

	protected static byte[] filled(int length, int value)
	{
		byte[] data = new byte[length];
		Arrays.fill(data, (byte) value);
		return data;
	}

	protected static StatField stat(String key, String label)
	{
		return new StatField(key, label, 0, Integer.MAX_VALUE);
	}

	protected static StatField stat(String key, String label, long maximum)
	{
		return new StatField(key, label, 0, maximum);
	}

	protected static Map<String, Long> addresses(Object... values)
	{
		Map<String, Long> result = new LinkedHashMap<>();
		for (int i = 0; i < values.length; i += 2)
		{
			result.put((String) values[i], ((Number) values[i + 1]).longValue());
		}
		return Map.copyOf(result);
	}

	protected final void requireTitle()
	{
		String active = console.currentTitleId().toUpperCase(Locale.ROOT);
		if (!game.titleId().equals(active))
		{
			throw new IllegalStateException("Open " + game.displayName() + " on the console before using this tab. Active title: " + active);
		}
	}

	private int checkedClient(int client)
	{
		if (client < 0 || client > 17)
		{
			throw new IllegalArgumentException("Client slot must be between 0 and 17");
		}
		return client;
	}

	private IllegalArgumentException unknown(String key)
	{
		return new IllegalArgumentException("Unknown option for " + game.tabName() + ": " + key);
	}

	private UnsupportedOperationException unsupported(String operation)
	{
		return new UnsupportedOperationException(game.tabName() + " does not support " + operation);
	}
}
