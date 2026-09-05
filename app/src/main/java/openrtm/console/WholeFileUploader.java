package openrtm.console;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

final class WholeFileUploader
{
	private static final long[] RETRY_DELAYS_MS = {2_000, 3_500, 5_000, 4_000, 6_500};

	private WholeFileUploader()
	{
	}

	static void upload(Path localPath, String remotePath, RemoteFile remote,
	                   BooleanSupplier cancelled, ResumableUploader.Progress progress) throws IOException
	{
		long total = Files.size(localPath);
		int reconnectAttempt = 0;
		boolean reconnectNeeded = false;

		while (true)
		{
			checkCancelled(cancelled);
			if (reconnectNeeded)
			{
				waitForRetry(reconnectAttempt++, cancelled, progress, total);
				try
				{
					remote.reconnect();
					progress.update(0, total, "Reconnected; enumerating console file");
					reconnectNeeded = false;
				}
				catch (ResumableUploader.RemoteException failure)
				{
					if (!failure.retryable())
					{
						throw transferFailure(failure);
					}
					continue;
				}
			}

			try
			{
				long present = remote.size(remotePath);
				if (present == total)
				{
					progress.update(0, total, "Verifying existing console file");
					if (matches(localPath, remotePath, remote, progress, total))
					{
						progress.update(total, total, "Existing console file verified");
						return;
					}
					progress.update(0, total, "Replacing corrupt console file");
					remote.delete(remotePath);
				}
				else if (present >= 0)
				{
					progress.update(0, total, "Replacing incomplete console file (" + present + " bytes)");
					remote.delete(remotePath);
				}

				progress.update(0, total, "Uploading whole file");
				remote.send(localPath, remotePath,
					completed -> progress.update(completed, total, "Uploading to console"));
				long finalSize = remote.size(remotePath);
				if (finalSize != total)
				{
					throw new ResumableUploader.RemoteException(true,
						"Console reports " + finalSize + " of " + total + " bytes", null);
				}
				progress.update(total, total, "Upload acknowledged by console");
				return;
			}
			catch (ResumableUploader.RemoteException failure)
			{
				if (!failure.retryable())
				{
					throw transferFailure(failure);
				}
				reconnectNeeded = true;
			}
		}
	}

	private static boolean matches(Path localPath, String remotePath, RemoteFile remote,
	                               ResumableUploader.Progress progress, long total)
		throws ResumableUploader.RemoteException
	{
		return remote.matches(localPath, remotePath,
			completed -> progress.update(completed, total, "Verifying console data"));
	}

	private static void waitForRetry(int attempt, BooleanSupplier cancelled,
	                                 ResumableUploader.Progress progress, long total) throws IOException
	{
		long delay = RETRY_DELAYS_MS[attempt % RETRY_DELAYS_MS.length];
		progress.update(0, total, "Connection lost; retrying in " + (delay / 1_000.0) + " seconds");
		long remaining = delay;
		while (remaining > 0)
		{
			checkCancelled(cancelled);
			long slice = Math.min(remaining, 250);
			try
			{
				Thread.sleep(slice);
			}
			catch (InterruptedException interrupted)
			{
				Thread.currentThread().interrupt();
				throw new IOException("Upload cancelled", interrupted);
			}
			remaining -= slice;
		}
	}

	private static void checkCancelled(BooleanSupplier cancelled) throws IOException
	{
		if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
		{
			throw new IOException("Upload cancelled");
		}
	}

	private static IOException transferFailure(ResumableUploader.RemoteException failure)
	{
		return new IOException(failure.getMessage(), failure.getCause());
	}

	interface RemoteFile
	{
		long size(String path) throws ResumableUploader.RemoteException;

		void delete(String path) throws ResumableUploader.RemoteException;

		void send(Path localPath, String path, LongConsumer progress) throws ResumableUploader.RemoteException;

		boolean matches(Path localPath, String path, LongConsumer progress) throws ResumableUploader.RemoteException;

		void reconnect() throws ResumableUploader.RemoteException;
	}
}
