package openrtm.profile;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

final class ProfileGamertagReader
{
	private static final byte[][] KEYS = {
		{(byte) 0xE1, (byte) 0xBC, 0x15, (byte) 0x9C, 0x73, (byte) 0xB1, (byte) 0xEA,
			(byte) 0xE9, (byte) 0xAB, 0x31, 0x70, (byte) 0xF3, (byte) 0xAD, 0x47, (byte) 0xEB,
			(byte) 0xF3},
		{(byte) 0xDA, (byte) 0xB6, (byte) 0x9A, (byte) 0xD9, (byte) 0x8E, 0x28, 0x76,
			0x4F, (byte) 0x97, 0x7E, (byte) 0xE2, 0x48, 0x7E, 0x4F, 0x3F, 0x68}
	};
	private static final int HASH_SIZE = 16;
	private static final int ENCRYPTED_SIZE = 0x184;
	private static final int GAMERTAG_OFFSET = 16;
	private static final int GAMERTAG_SIZE = 32;

	private ProfileGamertagReader()
	{
	}

	static String read(byte[] account) throws IOException
	{
		if (account == null || account.length < HASH_SIZE + ENCRYPTED_SIZE)
		{
			throw new IOException("The profile account data is incomplete");
		}
		for (byte[] key : KEYS)
		{
			String gamertag = decrypt(account, key);
			if (gamertag != null)
			{
				return gamertag;
			}
		}
		throw new IOException("The profile account data failed validation");
	}

	private static String decrypt(byte[] account, byte[] key) throws IOException
	{
		try
		{
			byte[] storedHash = Arrays.copyOfRange(account, 0, HASH_SIZE);
			byte[] derivedKey = Arrays.copyOf(hmac(key, storedHash), HASH_SIZE);
			Cipher cipher = Cipher.getInstance("RC4");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(derivedKey, "RC4"));
			byte[] decrypted = cipher.doFinal(account, HASH_SIZE, ENCRYPTED_SIZE);
			byte[] calculatedHash = Arrays.copyOf(hmac(key, decrypted), HASH_SIZE);
			if (!MessageDigest.isEqual(storedHash, calculatedHash))
			{
				return null;
			}
			String value = new String(decrypted, GAMERTAG_OFFSET, GAMERTAG_SIZE,
				StandardCharsets.UTF_16BE);
			int terminator = value.indexOf('\0');
			return sanitize(terminator < 0 ? value : value.substring(0, terminator));
		}
		catch (GeneralSecurityException failure)
		{
			throw new IOException("Profile identity decoding is unavailable", failure);
		}
	}

	private static byte[] hmac(byte[] key, byte[] value) throws GeneralSecurityException
	{
		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec(key, "HmacSHA1"));
		return mac.doFinal(value);
	}

	private static String sanitize(String value) throws IOException
	{
		StringBuilder result = new StringBuilder();
		for (int index = 0; index < value.length(); index++)
		{
			char character = value.charAt(index);
			if (character == '^' && index + 1 < value.length() && Character.isDigit(value.charAt(index + 1)))
			{
				index++;
				continue;
			}
			if (character >= ' ' && character <= '~' && character != '<' && character != '>' && character != '/')
			{
				result.append(character);
			}
		}
		String gamertag = result.toString().trim();
		if (gamertag.isEmpty())
		{
			throw new IOException("The profile does not contain a gamertag");
		}
		return gamertag;
	}
}
