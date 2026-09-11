package openrtm.stfs;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.HexFormat;

final class ConCertificate
{
	private static final BigInteger PUBLIC_EXPONENT = BigInteger.valueOf(0x1_0001L);
	private static final byte[] MODULUS = hex(
		"A31D6CE5FA95FDE89021FAD10C64192B86589B172B1005B8D1F84CEF534CD54E" +
			"5CAE86EF927B90D1E062FD7C54559EE0E7BEFA3F9E156F6C384EAF070C61AB51" +
			"5E2353141888CB6FCBC5D630F406ED2423EF256D009177249BE5A3C02790C297" +
			"F7749D6F17837EB537DE51E8D71CE156D956C8C3C3209D64C32F8C9192306FDB");
	private static final byte[] P = hex(
		"CCE75DFE72B6FDE71DE31A0EAC337AB921E88A849BDA9F1E5834687AB11D7E1C" +
			"1852657B978EA76A9DEE5A77523B718F33D0495EC330397236BF1DD9F224E871");
	private static final byte[] Q = hex(
		"CBCA5874D403629306501F42F6AA5936A7A1F3975C9AC86A27CF85052A66416A" +
			"7F2F84C81813C61D8DC7322F72193FA4ED71E761C0CF61AE8BA068A77D83230B");
	private static final byte[] DP = hex(
		"4CCA74E67435724858621114E8A24E5EED7F49D252DA8701874AF4D0EE69C026" +
			"655313E752B04ABBE13E3FB7322146F8C5114D3DEF66B650C085B579458F6171");
	private static final byte[] DQ = hex(
		"AFDC46E7528A3547A11C054E392499E64354CBABE3DB22761132D09CBB911084" +
			"818B152FC32F5538EDBF673C705EFF8028F3B173B6FA7F562BE1DA4E274EC22F");
	private static final byte[] INVERSE_Q = hex(
		"286ABBD19395941A6EEDD70EC0612BC2EFE1863D3412886F94A4486EC9871E46" +
			"004600528E9F47C08CABBC49AC5B13F2EC278D1B6E5106A6F1621AEB782E8848");
	private static final byte[] CERTIFICATE = hex(
		"01A80912BA26E3583830333339352D3030310000000000000000000230392D3138" +
			"2D303600010001C32F8C9192306FDBD956C8C3C3209D6437DE51E8D71CE156F7" +
			"749D6F17837EB59BE5A3C02790C29723EF256D00917724CBC5D630F406ED245E" +
			"2353141888CB6F384EAF070C61AB51E7BEFA3F9E156F6CE062FD7C54559EE05C" +
			"AE86EF927B90D1D1F84CEF534CD54E86589B172B1005B89021FAD10C64192BA3" +
			"1D6CE5FA95FDE8F9700D2A89399ED54E658744F94F2090894137502EF83008CC" +
			"6ECDD157E7C3B796B02A8059CB7E43FBDB7E0CEF6C5E000B1E87E10264A70824" +
			"32B9531500E9E3530C15E15D59C609ABD173B5EEC5E750BCC2B22598BAA00A84" +
			"F4F82D1AD2C97FDCCF5D02219A25E069116CFC88060149F474408DD891DB83C9" +
			"60CE0D7F97AA2A36A5F00C1063E9A9394FBB476C4422F1BE3A4901ED5B470043" +
			"21BDFBB2959A5FB446F4A712244B0B7FB88EBB528322581E06B7AD7A3A167EC8" +
			"D737819E8AF2C4660888FEA70E8F9D875F0E7B489A0662F72425CDB04F736897" +
			"0CE4ADE8559AB4FA65B5A358FE814054AA1F002AF1DD8A1F454E9DFF82465A5A" +
			"9025A0580FF227");
	private static final PrivateKey PRIVATE_KEY = privateKey();

	private ConCertificate()
	{
	}

	static byte[] certificate()
	{
		return CERTIFICATE.clone();
	}

	static byte[] sign(byte[] data)
	{
		try
		{
			Signature signer = Signature.getInstance("SHA1withRSA");
			signer.initSign(PRIVATE_KEY);
			signer.update(data);
			byte[] signature = signer.sign();
			reverse(signature);
			return signature;
		}
		catch (GeneralSecurityException failure)
		{
			throw new IllegalStateException("Could not sign Xbox 360 package", failure);
		}
	}

	static boolean verify(byte[] certificate, byte[] storedSignature, byte[] data)
	{
		if (certificate.length != 0x1A8 || storedSignature.length != 0x80)
		{
			return false;
		}
		try
		{
			byte[] exponent = Arrays.copyOfRange(certificate, 0x24, 0x28);
			byte[] xboxModulus = Arrays.copyOfRange(certificate, 0x28, 0xA8);
			byte[] modulus = reverseQwords(xboxModulus);
			byte[] signature = storedSignature.clone();
			reverse(signature);

			RSAPublicKeySpec keySpec = new RSAPublicKeySpec(unsigned(modulus), unsigned(exponent));
			Signature verifier = Signature.getInstance("SHA1withRSA");
			verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(keySpec));
			verifier.update(data);
			return verifier.verify(signature);
		}
		catch (GeneralSecurityException failure)
		{
			return false;
		}
	}

	private static PrivateKey privateKey()
	{
		try
		{
			BigInteger modulus = unsigned(MODULUS);
			BigInteger p = unsigned(P);
			BigInteger q = unsigned(Q);
			if (!p.multiply(q).equals(modulus))
			{
				throw new IllegalStateException("CON signing key is inconsistent");
			}
			BigInteger pMinusOne = p.subtract(BigInteger.ONE);
			BigInteger qMinusOne = q.subtract(BigInteger.ONE);
			BigInteger lambda = pMinusOne.divide(pMinusOne.gcd(qMinusOne)).multiply(qMinusOne);
			BigInteger privateExponent = PUBLIC_EXPONENT.modInverse(lambda);
			RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(
				modulus, PUBLIC_EXPONENT, privateExponent, p, q,
				unsigned(DP), unsigned(DQ), unsigned(INVERSE_Q));
			return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
		}
		catch (GeneralSecurityException failure)
		{
			throw new ExceptionInInitializerError(failure);
		}
	}

	private static byte[] reverseQwords(byte[] input)
	{
		byte[] value = input.clone();
		for (int start = 0; start < value.length; start += 8)
		{
			reverse(value, start, Math.min(start + 8, value.length));
		}
		reverse(value);
		return value;
	}

	private static void reverse(byte[] value)
	{
		reverse(value, 0, value.length);
	}

	private static void reverse(byte[] value, int from, int to)
	{
		for (int left = from, right = to - 1; left < right; left++, right--)
		{
			byte next = value[left];
			value[left] = value[right];
			value[right] = next;
		}
	}

	private static BigInteger unsigned(byte[] value)
	{
		return new BigInteger(1, value);
	}

	private static byte[] hex(String value)
	{
		return HexFormat.of().parseHex(value);
	}
}
