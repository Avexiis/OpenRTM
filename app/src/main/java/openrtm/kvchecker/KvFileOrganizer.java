package openrtm.kvchecker;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class KvFileOrganizer
{
	public Discovery discover(Path selectedFolder) throws IOException
	{
		Path root = selectedFolder.toAbsolutePath().normalize();
		if (!Files.isDirectory(root))
		{
			throw new IOException("Choose a folder to search");
		}
		List<Path> files = new ArrayList<>();
		int[] unreadable = {0};
		Files.walkFileTree(root, new FileVisitor<>()
		{
			@Override
			public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
			{
				return Thread.currentThread().isInterrupted()
					? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
			{
				if (Thread.currentThread().isInterrupted())
				{
					return FileVisitResult.TERMINATE;
				}
				if (attributes.isRegularFile() && isKeyVaultName(file))
				{
					files.add(file.toAbsolutePath().normalize());
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException failure)
			{
				unreadable[0]++;
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path directory, IOException failure)
			{
				if (failure != null)
				{
					unreadable[0]++;
				}
				return FileVisitResult.CONTINUE;
			}
		});
		files.sort(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER));
		return new Discovery(files, unreadable[0]);
	}

	public Path move(Path source, Path selectedFolder, String consoleSerial,
	                 KvCheckerService.Result result) throws IOException
	{
		if (consoleSerial == null || !consoleSerial.matches("[0-9]{12}"))
		{
			throw new IOException("The key vault does not contain a valid console serial");
		}
		Path file = source.toAbsolutePath().normalize();
		if (!Files.isRegularFile(file) || !isBin(file))
		{
			throw new IOException("The key vault file is no longer available");
		}
		String group = result == KvCheckerService.Result.BANNED ? "banned" : "unbanned";
		Path destination = selectedFolder.toAbsolutePath().normalize()
			.resolve(group).resolve(consoleSerial).resolve("KV.bin");
		if (file.equals(destination))
		{
			return destination;
		}
		Files.createDirectories(destination.getParent());
		Files.move(file, destination);
		return destination;
	}

	private static boolean isKeyVaultName(Path path)
	{
		return path.getFileName() != null
			&& path.getFileName().toString().equalsIgnoreCase("KV.bin");
	}

	private static boolean isBin(Path path)
	{
		return path.getFileName() != null
			&& path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bin");
	}

	public static final class Discovery
	{
		private final List<Path> files;
		private final int unreadableEntries;

		private Discovery(List<Path> files, int unreadableEntries)
		{
			this.files = Collections.unmodifiableList(new ArrayList<>(files));
			this.unreadableEntries = unreadableEntries;
		}

		public List<Path> files()
		{
			return files;
		}

		public int unreadableEntries()
		{
			return unreadableEntries;
		}
	}
}
