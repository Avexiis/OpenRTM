package openrtm.kvchecker.kerberosauthentication;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

public final class KeyVaultFile
{
	private static final int FILE_SIZE = 0x4000;
	private final Path path;
	private final byte[] data;

	private KeyVaultFile(Path path, byte[] data)
	{
		this.path = path;
		this.data = data;
	}

	public static KeyVaultFile open(Path source) throws IOException
	{
		if (source == null || source.getFileName() == null
			|| !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bin"))
		{
			throw new InvalidKeyVaultException("Only .bin files can be checked");
		}
		Path path = source.toAbsolutePath().normalize();
		if (!Files.isRegularFile(path))
		{
			throw new InvalidKeyVaultException("Choose a key vault file");
		}
		if (Files.size(path) != FILE_SIZE)
		{
			throw new InvalidKeyVaultException("Key vault files must be exactly 16 KB");
		}
		byte[] data = Files.readAllBytes(path);
		if (!validStructure(data))
		{
			throw new InvalidKeyVaultException("The file is not a valid Xbox 360 key vault");
		}
		return new KeyVaultFile(path, data);
	}

	public Path path()
	{
		return path;
	}

	public String consoleSerial()
	{
		return new String(serialData(), StandardCharsets.US_ASCII);
	}

	byte[] serialData()
	{
		return range(176, 12);
	}

	byte[] publicExponent()
	{
		return range(668, 4);
	}

	byte[] privateKeyParameters()
	{
		return range(680, 448);
	}

	byte[] consoleCertificate()
	{
		return range(2504, 424);
	}

	byte[] consoleId()
	{
		return range(2506, 5);
	}

	byte[] certificateHashSource()
	{
		return range(2504, 168);
	}

	private byte[] range(int offset, int length)
	{
		return Arrays.copyOfRange(data, offset, offset + length);
	}

	private static boolean validStructure(byte[] data)
	{
		if (data.length != FILE_SIZE || data[2511] != 88
			|| data[2518] != 45 || data[2521] != 49)
		{
			return false;
		}
		for (int index = 176; index < 188; index++)
		{
			if (data[index] < 48 || data[index] > 57)
			{
				return false;
			}
		}
		return true;
	}

	public static final class InvalidKeyVaultException extends IOException
	{
		public InvalidKeyVaultException(String message)
		{
			super(message);
		}
	}
}
