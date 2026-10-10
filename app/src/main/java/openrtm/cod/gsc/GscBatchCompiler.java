package openrtm.cod.gsc;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public final class GscBatchCompiler
{
	public record Failure(Path source, String message)
	{
	}

	public record Result(List<Path> compiled, List<Failure> failures)
	{
		public Result
		{
			compiled = List.copyOf(compiled);
			failures = List.copyOf(failures);
		}
	}

	private GscBatchCompiler()
	{
	}

	public static List<Path> sources(List<Path> paths)
	{
		Set<Path> sources = new LinkedHashSet<>();
		for (Path path : paths)
		{
			Path source = path.toAbsolutePath().normalize();
			if (!Files.isDirectory(source))
			{
				if (!Files.isRegularFile(source)
					|| !source.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gsc"))
				{
					throw new IllegalArgumentException("Select GSC source files or project folders to compile menus");
				}
				if (Files.isRegularFile(source.getParent().resolve("config.il")))
				{
					source = source.getParent();
				}
			}
			sources.add(source);
		}
		return List.copyOf(sources);
	}

	public static Result compile(List<Path> paths, Path directory, BiConsumer<Integer, String> progress) throws IOException
	{
		List<Path> sources = sources(paths);
		Path output = directory.toAbsolutePath().normalize();
		if (!Files.isDirectory(output))
		{
			throw new IllegalArgumentException("Select an output folder for compiled menus");
		}
		Set<String> names = new HashSet<>();
		try (Stream<Path> files = Files.list(output))
		{
			files.forEach(path -> names.add(path.getFileName().toString().toLowerCase(Locale.ROOT)));
		}
		List<Path> compiled = new ArrayList<>();
		List<Failure> failures = new ArrayList<>();
		for (int index = 0; index < sources.size(); index++)
		{
			Path source = sources.get(index);
			int completed = index;
			String prefix = "Menu " + (index + 1) + " of " + sources.size() + ": ";
			try
			{
				byte[] binary = Iw4GscCompiler.compile(source, (percent, message) ->
					progress.accept((completed * 100 + percent * 95 / 100) / sources.size(), prefix + message)).gscbin();
				progress.accept((index * 100 + 98) / sources.size(), prefix + "Saving compiled file");
				compiled.add(write(output, outputName(source), binary, names));
			}
			catch (IOException | RuntimeException failure)
			{
				String message = failure.getMessage();
				failures.add(new Failure(source, message == null || message.isBlank() ? "Could not compile this menu" : message));
			}
			progress.accept((index + 1) * 100 / sources.size(), prefix + "Finished");
		}
		return new Result(compiled, failures);
	}

	private static String outputName(Path source)
	{
		String name = GscProjectTitle.read(source).replaceAll("[\\\\/:*?\"<>|]", "_");
		if (name.length() > 100)
		{
			name = name.substring(0, 100);
		}
		name = name.replaceAll("[. ]+$", "");
		if (name.isEmpty())
		{
			return "GSC menu";
		}
		if (name.toUpperCase(Locale.ROOT).matches("(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"))
		{
			name = "_" + name;
		}
		return name;
	}

	private static Path write(Path directory, String name, byte[] binary, Set<String> names) throws IOException
	{
		Path temporary = Files.createTempFile(directory, "gsc-", ".tmp");
		try
		{
			Files.write(temporary, binary);
			for (int suffix = 1; ; suffix++)
			{
				String filename = name + (suffix == 1 ? "" : " (" + suffix + ")") + ".gscbin";
				if (!names.add(filename.toLowerCase(Locale.ROOT)))
				{
					continue;
				}
				Path destination = directory.resolve(filename);
				try
				{
					return Files.move(temporary, destination);
				}
				catch (FileAlreadyExistsException ignored)
				{
				}
			}
		}
		finally
		{
			Files.deleteIfExists(temporary);
		}
	}
}
