package openrtm.kvchecker.security;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

public final class Cryptography
{
	private Cryptography()
	{
	}

	public static byte[] md5(byte[] data) throws GeneralSecurityException
	{
		return MessageDigest.getInstance("MD5").digest(data);
	}

	public static byte[] sha1(byte[] data) throws GeneralSecurityException
	{
		return MessageDigest.getInstance("SHA-1").digest(data);
	}

	public static byte[] hmacMd5(byte[] key, byte[] data) throws GeneralSecurityException
	{
		Mac mac = Mac.getInstance("HmacMD5");
		mac.init(new SecretKeySpec(key, "HmacMD5"));
		return mac.doFinal(data);
	}

	public static byte[] hmacSha1(byte[] key, byte[] data) throws GeneralSecurityException
	{
		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec(key, "HmacSHA1"));
		return mac.doFinal(data);
	}

	public static byte[] rc4(byte[] data, byte[] key)
	{
		byte[] state = new byte[256];
		for (int index = 0; index < state.length; index++)
		{
			state[index] = (byte) index;
		}
		int swapIndex = 0;
		for (int index = 0; index < state.length; index++)
		{
			swapIndex = (swapIndex + Byte.toUnsignedInt(state[index])
				+ Byte.toUnsignedInt(key[index % key.length])) & 0xFF;
			byte value = state[index];
			state[index] = state[swapIndex];
			state[swapIndex] = value;
		}
		byte[] output = data.clone();
		int left = 0;
		int right = 0;
		for (int index = 0; index < output.length; index++)
		{
			left = (left + 1) & 0xFF;
			right = (right + Byte.toUnsignedInt(state[left])) & 0xFF;
			byte value = state[left];
			state[left] = state[right];
			state[right] = value;
			int keyIndex = (Byte.toUnsignedInt(state[left]) + Byte.toUnsignedInt(state[right])) & 0xFF;
			output[index] ^= state[keyIndex];
		}
		return output;
	}

	public static byte[] rc4HmacEncrypt(byte[] key, byte[] data, int usage)
		throws GeneralSecurityException
	{
		byte[] derived = hmacMd5(key, littleEndian(usage));
		byte[] plainText = new byte[data.length + 8];
		byte[] confounder = {(byte) 0x9B, 0x6B, (byte) 0xFA, (byte) 0xCB,
			0x5C, 0x48, (byte) 0x81, (byte) 0x90};
		System.arraycopy(confounder, 0, plainText, 0, confounder.length);
		System.arraycopy(data, 0, plainText, confounder.length, data.length);
		byte[] checksum = hmacMd5(derived, plainText);
		byte[] streamKey = hmacMd5(derived, checksum);
		byte[] encrypted = rc4(plainText, streamKey);
		byte[] output = new byte[checksum.length + encrypted.length];
		System.arraycopy(checksum, 0, output, 0, checksum.length);
		System.arraycopy(encrypted, 0, output, checksum.length, encrypted.length);
		return output;
	}

	public static byte[] rc4HmacDecrypt(byte[] key, byte[] data, int usage)
		throws GeneralSecurityException
	{
		if (data.length < 16)
		{
			throw new GeneralSecurityException("Encrypted authentication data is incomplete");
		}
		byte[] derived = hmacMd5(key, littleEndian(usage));
		byte[] checksum = Arrays.copyOf(data, 16);
		byte[] streamKey = hmacMd5(derived, checksum);
		return rc4(Arrays.copyOfRange(data, 16, data.length), streamKey);
	}

	public static byte[] kdcNonce(byte[] key) throws GeneralSecurityException
	{
		byte[] signatureKey = {
			's', 'i', 'g', 'n', 'a', 't', 'u', 'r', 'e', 'k', 'e', 'y', 0
		};
		byte[] derived = hmacMd5(key, signatureKey);
		byte[] digest = md5(new byte[]{2, 4, 0, 0, 0, 0, 0, 0});
		return hmacMd5(derived, digest);
	}

	public static PublicKey publicKey(byte[] exponent, byte[] modulus)
		throws GeneralSecurityException
	{
		RSAPublicKeySpec specification = new RSAPublicKeySpec(unsigned(modulus), unsigned(exponent));
		return KeyFactory.getInstance("RSA").generatePublic(specification);
	}

	public static PrivateKey privateKey(byte[] exponent, byte[] parameters)
		throws GeneralSecurityException
	{
		if (parameters.length != 448)
		{
			throw new GeneralSecurityException("The key vault private key is incomplete");
		}
		BigInteger modulus = unsigned(reverseEightByteBlocks(Arrays.copyOfRange(parameters, 0, 128)));
		BigInteger primeP = unsigned(reverseEightByteBlocks(Arrays.copyOfRange(parameters, 128, 192)));
		BigInteger primeQ = unsigned(reverseEightByteBlocks(Arrays.copyOfRange(parameters, 192, 256)));
		BigInteger exponentP = unsigned(reverseEightByteBlocks(Arrays.copyOfRange(parameters, 256, 320)));
		BigInteger exponentQ = unsigned(reverseEightByteBlocks(Arrays.copyOfRange(parameters, 320, 384)));
		BigInteger coefficient = unsigned(reverseEightByteBlocks(Arrays.copyOfRange(parameters, 384, 448)));
		BigInteger publicExponent = unsigned(exponent);
		if (!primeP.multiply(primeQ).equals(modulus))
		{
			throw new GeneralSecurityException("The key vault private key is invalid");
		}
		BigInteger phi = primeP.subtract(BigInteger.ONE).multiply(primeQ.subtract(BigInteger.ONE));
		BigInteger privateExponent;
		try
		{
			privateExponent = publicExponent.modInverse(phi);
		}
		catch (ArithmeticException failure)
		{
			throw new GeneralSecurityException("The key vault private key is invalid", failure);
		}
		RSAPrivateCrtKeySpec specification = new RSAPrivateCrtKeySpec(modulus, publicExponent,
			privateExponent, primeP, primeQ, exponentP, exponentQ, coefficient);
		return KeyFactory.getInstance("RSA").generatePrivate(specification);
	}

	public static byte[] oaepSha1(PublicKey key, byte[] data) throws GeneralSecurityException
	{
		Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
		OAEPParameterSpec parameters = new OAEPParameterSpec("SHA-1", "MGF1",
			MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
		cipher.init(Cipher.ENCRYPT_MODE, key, parameters);
		return cipher.doFinal(data);
	}

	public static byte[] signSha1(PrivateKey key, byte[] data) throws GeneralSecurityException
	{
		Signature signature = Signature.getInstance("SHA1withRSA");
		signature.initSign(key);
		signature.update(data);
		return signature.sign();
	}

	public static byte[] reverse(byte[] data)
	{
		byte[] output = data.clone();
		for (int left = 0, right = output.length - 1; left < right; left++, right--)
		{
			byte value = output[left];
			output[left] = output[right];
			output[right] = value;
		}
		return output;
	}

	public static byte[] reverseEightByteBlocks(byte[] data)
	{
		if (data.length % 8 != 0)
		{
			throw new IllegalArgumentException("Key data must contain complete 8-byte blocks");
		}
		byte[] output = new byte[data.length];
		for (int source = data.length - 8, destination = 0; source >= 0; source -= 8, destination += 8)
		{
			System.arraycopy(data, source, output, destination, 8);
		}
		return output;
	}

	private static BigInteger unsigned(byte[] value)
	{
		return new BigInteger(1, value);
	}

	private static byte[] littleEndian(int value)
	{
		return new byte[]{(byte) value, (byte) (value >>> 8),
			(byte) (value >>> 16), (byte) (value >>> 24)};
	}
}
