package openrtm.kvchecker.kerberosauthentication;

import openrtm.kvchecker.security.Cryptography;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class KerberosClient
{
	private static final String AUTHENTICATION_HOST = "XEAS.XBOXLIVE.COM";
	private static final String TICKET_HOST = "XETGS.XBOXLIVE.COM";
	private static final int KERBEROS_PORT = 88;
	private static final int RECEIVE_TIMEOUT_MS = 750;
	private static final int EXCHANGE_TIMEOUT_SECONDS = 8;
	private static final long WINDOWS_EPOCH_OFFSET = 116444736000000000L;
	private static final long BANNED_STATUS = 0x8015190DL;
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
		.ofPattern("yyyyMMddHHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);

	public void validate(KeyVaultFile keyVault) throws KeyVaultFile.InvalidKeyVaultException
	{
		try
		{
			Cryptography.privateKey(keyVault.publicExponent(), keyVault.privateKeyParameters());
			clientName(keyVault.consoleId());
			Cryptography.sha1(keyVault.certificateHashSource());
		}
		catch (GeneralSecurityException | RuntimeException failure)
		{
			throw new KeyVaultFile.InvalidKeyVaultException(
				"The file is not a valid Xbox 360 key vault");
		}
	}

	public boolean isBanned(KeyVaultFile keyVault, BooleanSupplier canceled)
		throws IOException, GeneralSecurityException
	{
		byte[] logonKey = xmacsLogonKey(keyVault, canceled);
		byte[] clientName = clientName(keyVault.consoleId());
		byte[] certificateHash = Cryptography.sha1(keyVault.certificateHashSource());

		byte[] firstRequest = KerberosResources.authenticationRequestOne();
		System.arraycopy(clientName, 0, firstRequest, 258, 24);
		System.arraycopy(certificateHash, 0, firstRequest, 36, 20);
		byte[] firstTimestamp = Cryptography.rc4HmacEncrypt(logonKey, timestamp(), 1);
		System.arraycopy(firstTimestamp, 0, firstRequest, 176, 52);
		byte[] firstResponse = exchange(AUTHENTICATION_HOST, firstRequest, 16, canceled);
		byte[] responseNonce = Arrays.copyOfRange(firstResponse, firstResponse.length - 16,
			firstResponse.length);

		byte[] secondRequest = KerberosResources.authenticationRequestTwo();
		System.arraycopy(clientName, 0, secondRequest, 286, 24);
		System.arraycopy(certificateHash, 0, secondRequest, 36, 20);
		byte[] secondTimestamp = Cryptography.rc4HmacEncrypt(logonKey, timestamp(), 1);
		System.arraycopy(secondTimestamp, 0, secondRequest, 204, 52);
		System.arraycopy(responseNonce, 0, secondRequest, 68, 16);
		byte[] secondResponse = exchange(AUTHENTICATION_HOST, secondRequest, 513, canceled);

		byte[] encryptedTicketKey = Arrays.copyOfRange(secondResponse, secondResponse.length - 210,
			secondResponse.length);
		byte[] decryptedTicketKey = Cryptography.rc4HmacDecrypt(logonKey, encryptedTicketKey, 8);
		byte[] ticketKey = Arrays.copyOfRange(decryptedTicketKey, 27, 43);
		byte[] ticket = Arrays.copyOfRange(secondResponse, 168, 513);

		byte[] ticketRequest = KerberosResources.ticketRequest();
		System.arraycopy(ticket, 0, ticketRequest, 437, ticket.length);
		byte[] authenticator = KerberosResources.authenticator();
		System.arraycopy(clientName, 0, authenticator, 40, 15);
		byte[] timeText = TIMESTAMP.format(Instant.now()).getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(timeText, 0, authenticator, 109, 15);
		byte[] requestDigest = Cryptography.md5(Arrays.copyOfRange(ticketRequest, 954, 1029));
		System.arraycopy(requestDigest, 0, authenticator, 82, 16);
		byte[] encryptedAuthenticator = Cryptography.rc4HmacEncrypt(ticketKey, authenticator, 7);
		System.arraycopy(encryptedAuthenticator, 0, ticketRequest, 799, 153);

		byte[] kdcNonce = Cryptography.kdcNonce(ticketKey);
		byte[] encryptedServiceRequest = Cryptography.rc4HmacEncrypt(kdcNonce,
			KerberosResources.serviceRequest(), 1201);
		System.arraycopy(encryptedServiceRequest, 0, ticketRequest, 55, 150);
		byte[] titleData = Arrays.copyOfRange(secondRequest, 116, 182);
		byte[] titleAuthentication = titleAuthenticationData(kdcNonce, titleData);
		System.arraycopy(titleAuthentication, 0, ticketRequest, 221, 82);

		byte[] ticketResponse = exchange(TICKET_HOST, ticketRequest, 266, canceled);
		byte[] encryptedStatus = Arrays.copyOfRange(ticketResponse, 50, 134);
		byte[] status = Cryptography.rc4HmacDecrypt(kdcNonce, encryptedStatus, 1202);
		byte[] validationBlock = Arrays.copyOfRange(ticketResponse, 58, 266);
		Cryptography.rc4HmacDecrypt(kdcNonce, validationBlock, 1202);
		long code = Integer.toUnsignedLong(ByteBuffer.wrap(status, 8, 4)
			.order(ByteOrder.LITTLE_ENDIAN).getInt());
		return code == BANNED_STATUS;
	}

	private byte[] xmacsLogonKey(KeyVaultFile keyVault, BooleanSupplier canceled)
		throws IOException, GeneralSecurityException
	{
		byte[] publicKeyData = KerberosResources.xmacsPublicKey();
		byte[] exponent = Arrays.copyOfRange(publicKeyData, 4, 8);
		byte[] modulus = Cryptography.reverseEightByteBlocks(
			Arrays.copyOfRange(publicKeyData, 16, 272));
		PublicKey publicKey = Cryptography.publicKey(exponent, modulus);
		byte[] randomKey = new byte[16];
		new SecureRandom().nextBytes(randomKey);
		byte[] encryptedKey = Cryptography.reverse(Cryptography.oaepSha1(publicKey, randomKey));

		byte[] request = KerberosResources.xmacsRequest();
		System.arraycopy(encryptedKey, 0, request, 44, 256);
		byte[] serialData = keyVault.serialData();
		byte[] certificate = keyVault.consoleCertificate();
		byte[] consoleExponent = keyVault.publicExponent();
		PrivateKey privateKey = Cryptography.privateKey(consoleExponent,
			keyVault.privateKeyParameters());
		byte[] fileTime = fileTime();
		byte[] encryptedTimestamp = Cryptography.rc4HmacEncrypt(randomKey, timestamp(), 1);
		byte[] randomDigest = Cryptography.sha1(randomKey);
		byte[] signatureInput = new byte[fileTime.length + serialData.length + randomDigest.length];
		System.arraycopy(fileTime, 0, signatureInput, 0, fileTime.length);
		System.arraycopy(serialData, 0, signatureInput, fileTime.length, serialData.length);
		System.arraycopy(randomDigest, 0, signatureInput, fileTime.length + serialData.length,
			randomDigest.length);
		byte[] signature = Cryptography.reverse(Cryptography.signSha1(privateKey, signatureInput));

		System.arraycopy(fileTime, 0, request, 300, 8);
		System.arraycopy(serialData, 0, request, 308, 12);
		System.arraycopy(signature, 0, request, 320, 128);
		System.arraycopy(certificate, 0, request, 448, 424);
		System.arraycopy(encryptedTimestamp, 0, request, 992, 52);
		System.arraycopy(clientName(keyVault.consoleId()), 0, request, 1072, 15);

		byte[] response = exchange(AUTHENTICATION_HOST, request, 161, canceled);
		byte[] encryptedResponse = Arrays.copyOfRange(response, 53, 161);
		byte[] decryptedResponse = Cryptography.rc4HmacDecrypt(
			Cryptography.kdcNonce(randomKey), encryptedResponse, 1203);
		return Arrays.copyOfRange(decryptedResponse, 76, 92);
	}

	private byte[] exchange(String host, byte[] request, int minimumResponseLength,
	                        BooleanSupplier canceled) throws IOException
	{
		InetAddress address = InetAddress.getByName(host);
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(EXCHANGE_TIMEOUT_SECONDS);
		try (DatagramSocket socket = new DatagramSocket())
		{
			socket.connect(address, KERBEROS_PORT);
			socket.setSoTimeout(RECEIVE_TIMEOUT_MS);
			DatagramPacket outgoing = new DatagramPacket(request, request.length);
			while (System.nanoTime() < deadline)
			{
				checkCanceled(canceled);
				socket.send(outgoing);
				byte[] buffer = new byte[4096];
				DatagramPacket incoming = new DatagramPacket(buffer, buffer.length);
				try
				{
					socket.receive(incoming);
					if (incoming.getLength() < minimumResponseLength)
					{
						throw new IOException("The Xbox network service returned an incomplete response");
					}
					return Arrays.copyOf(incoming.getData(), incoming.getLength());
				}
				catch (SocketTimeoutException ignored)
				{
				}
			}
		}
		throw new SocketTimeoutException("The Xbox network service did not respond");
	}

	private static byte[] titleAuthenticationData(byte[] key, byte[] titleData)
		throws GeneralSecurityException
	{
		byte[] hash = Cryptography.hmacSha1(key, titleData);
		byte[] output = new byte[82];
		System.arraycopy(hash, 0, output, 0, 16);
		System.arraycopy(titleData, 0, output, 16, titleData.length);
		return output;
	}

	private static byte[] clientName(byte[] consoleId)
	{
		long numericId = 0;
		for (byte value : consoleId)
		{
			numericId = (numericId | Byte.toUnsignedLong(value)) << 8;
		}
		numericId >>>= 8;
		int finalDigit = (int) numericId & 15;
		StringBuilder name = new StringBuilder("XE.")
			.append(numericId >>> 4).append(finalDigit).append("@xbox.com");
		if (name.length() != 24)
		{
			for (int index = 0; index < 24 - (name.length() - 1); index++)
			{
				name.insert(3, '0');
			}
		}
		byte[] value = name.toString().getBytes(StandardCharsets.US_ASCII);
		if (value.length < 24)
		{
			throw new IllegalArgumentException("The key vault console ID is invalid");
		}
		return value;
	}

	private static byte[] timestamp()
	{
		byte[] value = {
			0x30, 0x1A, (byte) 0xA0, 0x11, 0x18, 0x0F,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			(byte) 0xA1, 0x05, 0x02, 0x03, 0x0B, 0x35, 0x43
		};
		byte[] timeText = TIMESTAMP.format(Instant.now()).getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(timeText, 0, value, 6, 15);
		return value;
	}

	private static byte[] fileTime()
	{
		Instant now = Instant.now();
		long value = WINDOWS_EPOCH_OFFSET + now.getEpochSecond() * 10_000_000L + now.getNano() / 100;
		return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(value).array();
	}

	private static void checkCanceled(BooleanSupplier canceled) throws InterruptedIOException
	{
		if (Thread.currentThread().isInterrupted() || canceled.getAsBoolean())
		{
			throw new InterruptedIOException("Check canceled");
		}
	}
}
