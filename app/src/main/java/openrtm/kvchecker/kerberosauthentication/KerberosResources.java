package openrtm.kvchecker.kerberosauthentication;

import java.io.IOException;
import java.io.InputStream;

final class KerberosResources
{
	private static final String DIRECTORY = "/openrtm/kvchecker/kerberosauthentication/";

	private KerberosResources()
	{
	}

	static byte[] xmacsRequest()
	{
		return load("xmacsreq.bin", 1171);
	}

	static byte[] authenticationRequestOne()
	{
		return load("apreq1.bin", 363);
	}

	static byte[] authenticationRequestTwo()
	{
		return load("apreq2.bin", 391);
	}

	static byte[] authenticationResponse()
	{
		return load("apresp.bin", 745);
	}

	static byte[] ticketRequest()
	{
		return load("tgsreq.bin", 1029);
	}

	static byte[] authenticator()
	{
		return load("authenticator.bin", 129);
	}

	static byte[] serviceRequest()
	{
		return load("servreq.bin", 126);
	}

	static byte[] xmacsPublicKey()
	{
		return load("rsa_pub.bin", 272);
	}

	private static byte[] load(String name, int expectedLength)
	{
		try (InputStream input = KerberosResources.class.getResourceAsStream(DIRECTORY + name))
		{
			if (input == null)
			{
				throw new IllegalStateException("A required KV Checker component is unavailable");
			}
			byte[] data = input.readAllBytes();
			if (data.length != expectedLength)
			{
				throw new IllegalStateException("A required KV Checker component is unavailable");
			}
			return data;
		}
		catch (IOException failure)
		{
			throw new IllegalStateException("A required KV Checker component is unavailable", failure);
		}
	}
}
