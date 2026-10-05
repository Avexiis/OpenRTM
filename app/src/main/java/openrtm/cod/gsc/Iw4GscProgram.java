package openrtm.cod.gsc;

import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntFunction;

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

	public byte[] link(ToIntFunction<String> stringResolver)
	{
		byte[] linked = Arrays.copyOf(bytecode, bytecode.length);
		for (StringReference reference : strings)
		{
			int value = stringResolver.applyAsInt(reference.value());
			if (value < 0 || value > 0xFFFF)
			{
				throw new IllegalStateException("The game rejected a script string");
			}
			linked[reference.offset()] = (byte) (value >>> 8);
			linked[reference.offset() + 1] = (byte) value;
		}
		return linked;
	}
}
