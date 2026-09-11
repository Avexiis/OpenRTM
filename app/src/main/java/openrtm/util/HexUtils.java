package openrtm.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class HexUtils
{
	private HexUtils()
	{
	}

	public static long parseAddress(String text)
	{
		String value = normalizeNumber(text);
		if (value.isBlank())
		{
			throw new IllegalArgumentException("Address is empty");
		}
		if (value.startsWith("0x"))
		{
			return Long.parseUnsignedLong(value.substring(2), 16);
		}
		if (value.startsWith("$"))
		{
			return Long.parseUnsignedLong(value.substring(1), 16);
		}
		if (value.matches("(?i)[0-9a-f]+") && value.matches(".*(?i)[a-f].*"))
		{
			return Long.parseUnsignedLong(value, 16);
		}
		return Long.parseLong(value);
	}

	public static long parseUnsigned32(String text)
	{
		long value = parseAddress(text);
		if ((value & ~0xFFFF_FFFFL) != 0)
		{
			throw new IllegalArgumentException("Value does not fit in 32 bits");
		}
		return value;
	}

	public static int parseInt(String text)
	{
		long value = parseAddress(text);
		if (value < Integer.MIN_VALUE || value > 0xFFFF_FFFFL)
		{
			throw new IllegalArgumentException("Value does not fit in 32 bits");
		}
		return (int) value;
	}

	public static byte[] parseHex(String text)
	{
		String cleaned = text == null ? "" : text.replaceAll("(?i)0x", "").replaceAll("[^0-9a-fA-F]", "");
		if (cleaned.isBlank())
		{
			return new byte[0];
		}
		if ((cleaned.length() & 1) != 0)
		{
			throw new IllegalArgumentException("Hex must contain full bytes");
		}
		byte[] out = new byte[cleaned.length() / 2];
		for (int i = 0; i < out.length; i++)
		{
			int hi = Character.digit(cleaned.charAt(i * 2), 16);
			int lo = Character.digit(cleaned.charAt(i * 2 + 1), 16);
			if (hi < 0 || lo < 0)
			{
				throw new IllegalArgumentException("Invalid hex byte");
			}
			out[i] = (byte) ((hi << 4) | lo);
		}
		return out;
	}

	public static String formatHex(byte[] data)
	{
		return formatHex(data, 16);
	}

	public static String formatHex(byte[] data, int columns)
	{
		StringBuilder sb = new StringBuilder(data.length * 3);
		for (int i = 0; i < data.length; i++)
		{
			if (i > 0)
			{
				sb.append(i % columns == 0 ? System.lineSeparator() : " ");
			}
			sb.append(String.format(Locale.ROOT, "%02X", data[i] & 0xFF));
		}
		return sb.toString();
	}

	public static String hex32(long value)
	{
		return String.format(Locale.ROOT, "0x%08X", value & 0xFFFF_FFFFL);
	}

	public static byte[] int32Little(int value)
	{
		return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
	}

	public static byte[] int32Big(int value)
	{
		return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array();
	}

	public static byte[] uint16Big(int value)
	{
		return ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort((short) (value & 0xFFFF)).array();
	}

	public static byte[] asciiNull(String text)
	{
		byte[] raw = text == null ? new byte[0] : text.getBytes(StandardCharsets.US_ASCII);
		byte[] out = new byte[raw.length + 1];
		System.arraycopy(raw, 0, out, 0, raw.length);
		return out;
	}

	public static byte[] utf16BigNull(String text)
	{
		byte[] raw = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_16BE);
		byte[] out = new byte[raw.length + 2];
		System.arraycopy(raw, 0, out, 0, raw.length);
		return out;
	}

	public static byte[] bytes(int... values)
	{
		byte[] out = new byte[values.length];
		for (int i = 0; i < values.length; i++)
		{
			out[i] = (byte) (values[i] & 0xFF);
		}
		return out;
	}

	/**
	 * Reverses a byte array in-place (for endianness conversion).
	 */
	public static void reverseBytes(byte[] array)
	{
		for (int i = 0; i < array.length / 2; i++)
		{
			byte temp = array[i];
			array[i] = array[array.length - 1 - i];
			array[array.length - 1 - i] = temp;
		}
	}

	/**
	 * Converts a 4-byte big-endian array to an int.
	 */
	public static int reverseBytes(byte[] array, int length)
	{
		int result = 0;
		for (int i = 0; i < length && i < array.length; i++)
		{
			result = (result << 8) | (array[i] & 0xFF);
		}
		return result;
	}

	/**
	 * Converts a 4-byte big-endian array to an int (alias for reverseBytes).
	 */
	public static int reverseBytesToInt(byte[] array)
	{
		return reverseBytes(array, 4);
	}

	private static String normalizeNumber(String text)
	{
		return (text == null ? "" : text.trim().replace("_", "")).toLowerCase(Locale.ROOT);
	}
}
