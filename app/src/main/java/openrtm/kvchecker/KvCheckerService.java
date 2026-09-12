package openrtm.kvchecker;

import openrtm.kvchecker.kerberosauthentication.KerberosClient;
import openrtm.kvchecker.kerberosauthentication.KeyVaultFile;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.function.BooleanSupplier;

public final class KvCheckerService
{
	private final KerberosClient kerberos = new KerberosClient();

	public String validate(Path file) throws IOException
	{
		KeyVaultFile keyVault = KeyVaultFile.open(file);
		kerberos.validate(keyVault);
		return keyVault.consoleSerial();
	}

	public Result check(Path file, BooleanSupplier canceled)
		throws IOException, GeneralSecurityException
	{
		KeyVaultFile keyVault = KeyVaultFile.open(file);
		kerberos.validate(keyVault);
		return kerberos.isBanned(keyVault, canceled) ? Result.BANNED : Result.UNBANNED;
	}

	public enum Result
	{
		BANNED,
		UNBANNED
	}
}
