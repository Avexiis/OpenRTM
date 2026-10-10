package openrtm.cod.gsc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

public final class Iw4GscBinary
{
	private static final int MAXIMUM_SIZE = 0x37FFF;
	private static final int MAXIMUM_FILE_SIZE = 16 * 1024 * 1024;
	private final ByteBuffer input;
	private final ByteBuffer output;
	private final List<Iw4GscProgram.StringReference> strings = new ArrayList<>();
	private final List<Iw4GscProgram.ExportReference> references = new ArrayList<>();
	private final Map<String, Integer> tokens = new LinkedHashMap<>();

	private Iw4GscBinary(byte[] file)
	{
		if (file.length < 5 || file.length > MAXIMUM_FILE_SIZE)
		{
			throw invalid();
		}
		input = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);
		int size = input.getInt();
		if (size <= 0 || size > MAXIMUM_SIZE)
		{
			throw new IllegalArgumentException("This file is not a supported MW2 compiled menu, or it is too large");
		}
		output = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
	}

	public static boolean isCompiled(Path path)
	{
		return !Files.isDirectory(path) && path.getFileName() != null
			&& path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gscbin");
	}

	public static Iw4GscProgram read(Path path, BiConsumer<Integer, String> progress)
	{
		progress.accept(0, "Reading compiled menu");
		try (InputStream file = Files.newInputStream(path))
		{
			return new Iw4GscBinary(file.readNBytes(MAXIMUM_FILE_SIZE + 1)).decode(progress);
		}
		catch (IOException failure)
		{
			throw new IllegalArgumentException("Unable to read the compiled menu", failure);
		}
	}

	public static Iw4GscProgram read(byte[] file)
	{
		return new Iw4GscBinary(file).decode((percent, message) -> {});
	}

	private Iw4GscProgram decode(BiConsumer<Integer, String> progress)
	{
		int reported = -1;
		while (output.hasRemaining())
		{
			int percent = output.position() * 100 / output.capacity();
			if (percent != reported)
			{
				progress.accept(percent, "Preparing compiled menu");
				reported = percent;
			}
			int offset = output.position();
			int opcode = next();
			output.put((byte) (opcode == 0x69 ? 0x76 : opcode));
			switch (opcode)
			{
				case 0x04, 0x5E, 0x5F, 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67,
					0x70, 0x72, 0x73, 0x75 -> token();
				case 0x1D, 0x2F -> string();
				case 0x05, 0x0C, 0x0D, 0x11, 0x15, 0x16, 0x27, 0x28, 0x2B, 0x2C,
					0x6A, 0x6F, 0x88 -> copy(1);
				case 0x69 -> {
					references.add(new Iw4GscProgram.ExportReference(offset, 1, null));
					copy(1);
				}
				case 0x17, 0x18, 0x3D, 0x44, 0x46, 0x4F, 0x59, 0x5B,
					0x77, 0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x7E, 0x7F, 0x80, 0x81, 0x82, 0x83 -> operand(2);
				case 0x19, 0x54 -> operand(4);
				case 0x1C -> {
					alignFloat();
					copy(4);
				}
				case 0x30 -> {
					alignFloat();
					copy(12);
				}
				case 0x20, 0x21, 0x23, 0x5C -> copy(3);
				case 0x25, 0x26, 0x29, 0x2A -> copy(4);
				case 0x7D, 0x84 -> {
					copy(1);
					operand(2);
				}
				case 0x00, 0x01, 0x02, 0x03, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0E, 0x0F,
					0x12, 0x13, 0x14, 0x1E, 0x1F, 0x22, 0x24, 0x31, 0x33, 0x34, 0x35, 0x36,
					0x37, 0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3E, 0x3F, 0x40, 0x41, 0x42, 0x43, 0x45,
					0x47, 0x48, 0x49, 0x4A, 0x4B, 0x4E, 0x50, 0x51, 0x52, 0x53, 0x55, 0x56,
					0x57, 0x58, 0x5A, 0x5D, 0x68, 0x6B, 0x6C, 0x6D, 0x6E, 0x71, 0x74, 0x85,
					0x86, 0x87, 0x89, 0x8A, 0x8B, 0xFF -> {
				}
				default -> throw new IllegalArgumentException("This compiled menu uses an unsupported format. Select its GSC source to compile it.");
			}
		}
		if (input.hasRemaining())
		{
			throw invalid();
		}
		progress.accept(100, "Compiled menu ready");
		return new Iw4GscProgram(output.array(), strings, references);
	}

	private void string()
	{
		alignOperand(2);
		int offset = output.position();
		String value = text();
		ensureOutput(2);
		output.putShort((short) 0);
		strings.add(new Iw4GscProgram.StringReference(offset, value));
	}

	private void token()
	{
		alignOperand(2);
		if (!input.hasRemaining())
		{
			throw invalid();
		}
		if (Byte.toUnsignedInt(input.get(input.position())) != '&')
		{
			copy(2);
			return;
		}
		String reference = text();
		String name = reference.substring(1);
		if (name.isEmpty())
		{
			throw invalid();
		}
		int token = Iw4GscTables.token(name);
		if (token < 0)
		{
			token = tokens.computeIfAbsent(name, ignored -> 0xF35 + tokens.size());
		}
		if (token > 0xFFFF)
		{
			throw invalid();
		}
		references.add(new Iw4GscProgram.ExportReference(output.position(), 2, reference));
		ensureOutput(2);
		output.putShort((short) token);
	}

	private String text()
	{
		int start = input.position();
		while (next() != 0)
		{
		}
		return new String(input.array(), start, input.position() - start - 1, StandardCharsets.ISO_8859_1);
	}

	private void operand(int width)
	{
		alignOperand(width);
		copy(width);
	}

	private void alignOperand(int width)
	{
		while ((output.position() & 31) + width > 32)
		{
			padding();
		}
	}

	private void alignFloat()
	{
		while (output.position() % 4 != 0)
		{
			padding();
		}
	}

	private void padding()
	{
		if (next() != 0xFF)
		{
			throw invalid();
		}
		ensureOutput(1);
		output.put((byte) 0xFF);
	}

	private void copy(int length)
	{
		ensureOutput(length);
		if (input.remaining() < length)
		{
			throw invalid();
		}
		for (int index = 0; index < length; index++)
		{
			output.put(input.get());
		}
	}

	private int next()
	{
		if (!input.hasRemaining())
		{
			throw invalid();
		}
		return Byte.toUnsignedInt(input.get());
	}

	private void ensureOutput(int length)
	{
		if (output.remaining() < length)
		{
			throw invalid();
		}
	}

	private static IllegalArgumentException invalid()
	{
		return new IllegalArgumentException("The compiled menu is incomplete or damaged");
	}
}
