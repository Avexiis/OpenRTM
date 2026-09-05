package openrtm.console;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

final class ResumableUploader
{
	static final int CHUNK_SIZE = 512 * 1024;
	private static final long[] RETRY_DELAYS_MS = {2_000, 3_500, 5_000, 4_000, 6_500};

	private ResumableUploader()
	{
	}

	static void upload(Path localPath, String remotePath, RemoteFile remote,
	                   BooleanSupplier cancelled, Progress progress) throws IOException
	{
		long total;
		try (FileChannel local = FileChannel.open(localPath, StandardOpenOption.READ))
		{
			total = local.size();
			long verified = 0;
			int reconnectAttempt = 0;
			boolean reconnectNeeded = false;

			while (true)
			{
				checkCancelled(cancelled);
				if (reconnectNeeded)
				{
					waitForRetry(reconnectAttempt++, cancelled, progress, verified, total);
					try
					{
						remote.reconnect();
						progress.update(verified, total, "Reconnected; enumerating console file");
						reconnectNeeded = false;
					}
					catch (RemoteException failure)
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
					if (present < 0)
					{
						remote.resize(remotePath, 0, true);
						present = 0;
						verified = 0;
					}
					else if (present > total)
					{
						progress.update(verified, total, "Truncating oversized console file");
						remote.resize(remotePath, total, false);
						present = total;
					}

					if (present < verified)
					{
						verified = alignedCheckpoint(present);
					}
					progress.update(verified, total, "Verifying " + present + " bytes already on console");
					verified = verifyRange(local, remote, remotePath, verified, present, total, cancelled, progress);
					verified = uploadRange(local, remote, remotePath, verified, total, cancelled, progress);

					remote.resize(remotePath, total, false);
					long finalSize = remote.size(remotePath);
					if (finalSize != total)
					{
						throw new RemoteException(true, "Console reports " + finalSize + " of " + total + " bytes", null);
					}
					progress.update(total, total, "Upload verified");
					return;
				}
				catch (RemoteException failure)
				{
					if (!failure.retryable())
					{
						throw transferFailure(failure);
					}
					reconnectNeeded = true;
				}
			}
		}
	}

	private static long verifyRange(FileChannel local, RemoteFile remote, String remotePath,
	                                long start, long end, long total, BooleanSupplier cancelled,
	                                Progress progress) throws IOException, RemoteException
	{
		long offset = start;
		while (offset < end)
		{
			checkCancelled(cancelled);
			int length = (int) Math.min(CHUNK_SIZE, end - offset);
			byte[] expected = readLocal(local, offset, length);
			byte[] actual = remote.read(remotePath, offset, length);
			if (!Arrays.equals(expected, actual))
			{
				progress.update(offset, total, "Repairing corrupt data at byte " + offset);
				writeAndVerify(remote, remotePath, offset, expected);
			}
			offset += length;
			progress.update(offset, total, "Verified existing console data");
		}
		return offset;
	}

	private static long uploadRange(FileChannel local, RemoteFile remote, String remotePath,
	                                long start, long total, BooleanSupplier cancelled,
	                                Progress progress) throws IOException, RemoteException
	{
		long offset = start;
		while (offset < total)
		{
			checkCancelled(cancelled);
			int length = (int) Math.min(CHUNK_SIZE, total - offset);
			byte[] data = readLocal(local, offset, length);
			writeAndVerify(remote, remotePath, offset, data);
			offset += length;
			progress.update(offset, total, "Uploading to console");
		}
		return offset;
	}

	private static void writeAndVerify(RemoteFile remote, String remotePath, long offset, byte[] data)
		throws RemoteException
	{
		remote.write(remotePath, offset, data);
		byte[] actual = remote.read(remotePath, offset, data.length);
		if (Arrays.equals(data, actual))
		{
			return;
		}

		remote.write(remotePath, offset, data);
		actual = remote.read(remotePath, offset, data.length);
		if (!Arrays.equals(data, actual))
		{
			throw new RemoteException(false, "Console data remains corrupt at byte " + offset, null);
		}
	}

	private static byte[] readLocal(FileChannel local, long offset, int length) throws IOException
	{
		ByteBuffer buffer = ByteBuffer.allocate(length);
		long position = offset;
		while (buffer.hasRemaining())
		{
			int read = local.read(buffer, position);
			if (read < 0)
			{
				throw new IOException("Local file ended unexpectedly at byte " + position);
			}
			if (read == 0)
			{
				continue;
			}
			position += read;
		}
		return buffer.array();
	}

	private static long alignedCheckpoint(long size)
	{
		return Math.max(0, size - (size % CHUNK_SIZE));
	}

	private static void waitForRetry(int attempt, BooleanSupplier cancelled, Progress progress,
	                                 long completed, long total) throws IOException
	{
		long delay = RETRY_DELAYS_MS[attempt % RETRY_DELAYS_MS.length];
		progress.update(completed, total, "Connection lost; retrying in " + (delay / 1_000.0) + " seconds");
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

	private static IOException transferFailure(RemoteException failure)
	{
		return new IOException(failure.getMessage(), failure.getCause());
	}

	interface RemoteFile
	{
		long size(String path) throws RemoteException;

		byte[] read(String path, long offset, int length) throws RemoteException;

		void write(String path, long offset, byte[] data) throws RemoteException;

		void resize(String path, long size, boolean create) throws RemoteException;

		void reconnect() throws RemoteException;
	}

	@FunctionalInterface
	interface Progress
	{
		void update(long completed, long total, String message);
	}

	static final class RemoteException extends Exception
	{
		private final boolean retryable;

		RemoteException(boolean retryable, String message, Throwable cause)
		{
			super(message, cause);
			this.retryable = retryable;
		}

		boolean retryable()
		{
			return retryable;
		}
	}
}
