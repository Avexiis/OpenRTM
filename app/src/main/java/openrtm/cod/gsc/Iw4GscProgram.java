package openrtm.cod.gsc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Iw4GscProgram
{
	public record StringReference(int offset, String value)
	{
	}

	private final byte[] bytecode;
	private final List<StringReference> strings;

	Iw4GscProgram(byte[] bytecode, List<StringReference> strings)
	{
		this.bytecode = Arrays.copyOf(bytecode, bytecode.length);
		this.strings = List.copyOf(strings);
	}

	public int size()
	{
		return bytecode.length;
	}

	public byte[] bytecode()
	{
		return Arrays.copyOf(bytecode, bytecode.length);
	}

	public byte[] stringTable(long tableAddress, long codeAddress)
	{
		Map<String, Integer> offsets = new LinkedHashMap<>();
		int length = 4 + strings.size() * 8;
		for (StringReference reference : strings)
		{
			if (!offsets.containsKey(reference.value()))
			{
				offsets.put(reference.value(), length);
				length = Math.addExact(length, reference.value().length() + 1);
			}
		}
		ByteBuffer table = ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN);
		table.putInt(strings.size());
		for (StringReference reference : strings)
		{
			table.putInt((int) (codeAddress + reference.offset()));
			table.putInt((int) (tableAddress + offsets.get(reference.value())));
		}
		for (String value : offsets.keySet())
		{
			for (int index = 0; index < value.length(); index++)
			{
				char character = value.charAt(index);
				if (character == 0 || character > 255)
				{
					throw new IllegalArgumentException("A script string contains an unsupported character");
				}
				table.put((byte) character);
			}
			table.put((byte) 0);
		}
		return table.array();
	}
}
