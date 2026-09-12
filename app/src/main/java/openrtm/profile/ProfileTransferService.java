package openrtm.profile;

import openrtm.console.ConsoleService;
import openrtm.stfs.PackageService;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ProfileTransferService
{
	private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private final ConsoleService console;
	private final PackageService packages = new PackageService();

	public ProfileTransferService(ConsoleService console)
	{
		this.console = console;
	}

	public DownloadResult download(String profileId, Path destination) throws IOException
	{
		String id = normalizeProfileId(profileId);
		Path output = destination.toAbsolutePath().normalize();
		Path parent = output.getParent();
		if (parent != null)
		{
			Files.createDirectories(parent);
		}
		Path temporary = Files.createTempFile(parent, "openrtm-profile-download-", ".tmp");
		int signedOutIndex = -1;
		try
		{
			try
			{
				console.downloadFile(remotePath(id), temporary);
			}
			catch (IOException locked)
			{
				if (!isAccessDenied(locked))
				{
					throw locked;
				}
				List<Integer> active = activeUsers();
				if (active.size() != 1)
				{
					throw new IOException("Sign out the target profile before downloading it", locked);
				}
				signedOutIndex = active.get(0);
				console.setProfileSignedIn(id, signedOutIndex, false);
				waitForState(signedOutIndex, 0);
				console.downloadFile(remotePath(id), temporary);
			}
			PackageService.Info info = packages.inspect(temporary);
			if (!info.creatorId().equalsIgnoreCase(id) || info.contentType() != 0x00010000)
			{
				throw new IOException("The downloaded package does not match the requested profile");
			}
			replace(temporary, output);
			temporary = null;
			return new DownloadResult(output, signedOutIndex >= 0);
		}
		finally
		{
			try
			{
				if (signedOutIndex >= 0)
				{
					console.setProfileSignedIn(id, signedOutIndex, true);
					waitForState(signedOutIndex, -1);
				}
			}
			finally
			{
				if (temporary != null)
				{
					Files.deleteIfExists(temporary);
				}
			}
		}
	}

	public UploadResult upload(Path profile) throws IOException
	{
		PackageService.Info info = packages.inspect(profile);
		if (info.contentType() != 0x00010000 || !info.creatorId().matches("(?i)[0-9a-f]{16}"))
		{
			throw new IOException("Choose an Xbox 360 gamer profile");
		}
		if (!activeUsers().isEmpty())
		{
			throw new IOException("Sign out all profiles on the console before uploading profile changes");
		}
		String remote = remotePath(info.creatorId());
		Path local = profile.toAbsolutePath().normalize();
		Path backup = local.resolveSibling(local.getFileName() + ".console-"
			+ BACKUP_TIME.format(LocalDateTime.now()) + ".bak");
		Path verification = Files.createTempFile("openrtm-profile-verification-", ".tmp");
		boolean uploadAttempted = false;
		try
		{
			console.downloadFile(remote, backup);
			uploadAttempted = true;
			console.uploadFile(local, remote);
			console.downloadFile(remote, verification);
			if (!MessageDigest.isEqual(sha256(local), sha256(verification)))
			{
				throw new IOException("Console verification did not match the edited profile");
			}
			return new UploadResult(backup);
		}
		catch (IOException failure)
		{
			if (uploadAttempted && Files.isRegularFile(backup))
			{
				try
				{
					console.uploadFile(backup, remote);
					console.downloadFile(remote, verification);
					if (!MessageDigest.isEqual(sha256(backup), sha256(verification)))
					{
						throw new IOException("The console backup could not be verified after recovery");
					}
				}
				catch (IOException restoreFailure)
				{
					failure.addSuppressed(restoreFailure);
				}
			}
			throw failure;
		}
		finally
		{
			Files.deleteIfExists(verification);
		}
	}

	private List<Integer> activeUsers()
	{
		List<Integer> active = new ArrayList<>();
		for (int index = 0; index < 4; index++)
		{
			if (console.userSigninState(index) != 0)
			{
				active.add(index);
			}
		}
		return active;
	}

	private void waitForState(int index, int state) throws IOException
	{
		for (int attempt = 0; attempt < 20; attempt++)
		{
			int current = console.userSigninState(index);
			if (state < 0 ? current != 0 : current == state)
			{
				return;
			}
			try
			{
				Thread.sleep(250);
			}
			catch (InterruptedException interrupted)
			{
				Thread.currentThread().interrupt();
				throw new IOException("Profile transfer was interrupted", interrupted);
			}
		}
		throw new IOException(state == 0 ? "The profile did not finish signing out"
			: "The profile did not finish signing in");
	}

	private static byte[] sha256(Path path) throws IOException
	{
		try
		{
			return MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
		}
		catch (NoSuchAlgorithmException impossible)
		{
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static boolean isAccessDenied(Throwable failure)
	{
		for (Throwable current = failure; current != null; current = current.getCause())
		{
			String message = current.getMessage();
			if (message != null && message.toLowerCase().contains("access denied"))
			{
				return true;
			}
		}
		return false;
	}

	private static void replace(Path source, Path destination) throws IOException
	{
		try
		{
			Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (AtomicMoveNotSupportedException ignored)
		{
			Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static String remotePath(String profileId)
	{
		return "Hdd:\\Content\\" + profileId + "\\FFFE07D1\\00010000\\" + profileId;
	}

	private static String normalizeProfileId(String profileId)
	{
		String value = profileId == null ? "" : profileId.trim().toUpperCase();
		if (value.startsWith("0X"))
		{
			value = value.substring(2);
		}
		if (!value.matches("[0-9A-F]{16}"))
		{
			throw new IllegalArgumentException("Profile ID must contain 16 hexadecimal digits");
		}
		return value;
	}

	public static final class DownloadResult
	{
		private final Path path;
		private final boolean restoredSignin;

		private DownloadResult(Path path, boolean restoredSignin)
		{
			this.path = path;
			this.restoredSignin = restoredSignin;
		}

		public Path path()
		{
			return path;
		}

		public boolean restoredSignin()
		{
			return restoredSignin;
		}
	}

	public static final class UploadResult
	{
		private final Path backup;

		private UploadResult(Path backup)
		{
			this.backup = backup;
		}

		public Path backup()
		{
			return backup;
		}
	}
}
