package openrtm.console;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

final class LocalUploadPlan
{
	private LocalUploadPlan()
	{
	}

	static Plan build(Path localDirectory, String remoteDirectory) throws IOException
	{
		return build(localDirectory, remoteDirectory, () -> false);
	}

	static Plan build(Path localDirectory, String remoteDirectory, BooleanSupplier cancelled) throws IOException
	{
		if (!Files.isDirectory(localDirectory, LinkOption.NOFOLLOW_LINKS))
		{
			throw new IOException("Local folder does not exist: " + localDirectory);
		}

		List<Entry> entries = new ArrayList<>();
		long totalBytes = 0;
		try (Stream<Path> stream = Files.walk(localDirectory))
		{
			Iterator<Path> paths = stream.iterator();
			while (paths.hasNext())
			{
				if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
				{
					throw new IOException("File transfer cancelled");
				}
				Path path = paths.next();
				boolean directory = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
				boolean file = Files.isRegularFile(path);
				if (!directory && !file)
				{
					continue;
				}

				Path relative = localDirectory.relativize(path);
				validateRemoteSegments(relative);
				String remotePath = appendRemotePath(remoteDirectory, relative);
				long size = directory ? 0 : Files.size(path);
				totalBytes = saturatingAdd(totalBytes, size);
				entries.add(new Entry(path, remotePath, directory, size));
			}
		}
		return new Plan(List.copyOf(entries), totalBytes);
	}

	static String appendRemotePath(String parent, Path relative)
	{
		String remote = trimTrailingSlash(ensureRemoteDirectory(parent));
		for (Path segment : relative)
		{
			if (!segment.toString().isEmpty())
			{
				remote = remote + "\\" + segment;
			}
		}
		return remote;
	}

	static String childRemotePath(String parent, String child)
	{
		return ensureRemoteDirectory(parent) + child;
	}

	static String ensureRemoteDirectory(String path)
	{
		String normalized = path == null ? "" : path.trim().replace('/', '\\');
		return normalized.endsWith("\\") ? normalized : normalized + "\\";
	}

	static String remoteLeaf(String path)
	{
		String normalized = path == null ? "" : path.trim().replace('/', '\\');
		while (normalized.endsWith("\\"))
		{
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		int slash = normalized.lastIndexOf('\\');
		return slash >= 0 ? normalized.substring(slash + 1) : normalized;
	}

	private static String trimTrailingSlash(String path)
	{
		return path.endsWith("\\") ? path.substring(0, path.length() - 1) : path;
	}

	private static void validateRemoteSegments(Path relative) throws IOException
	{
		for (Path segment : relative)
		{
			String name = segment.toString();
			if (name.contains("\\") || name.contains("/") || name.indexOf('\0') >= 0)
			{
				throw new IOException("Local name cannot be represented on the console: " + name);
			}
		}
	}

	private static long saturatingAdd(long left, long right)
	{
		if (right > Long.MAX_VALUE - left)
		{
			return Long.MAX_VALUE;
		}
		return left + right;
	}

	record Plan(List<Entry> entries, long totalBytes)
	{
	}

	record Entry(Path localPath, String remotePath, boolean directory, long size)
	{
	}
}
