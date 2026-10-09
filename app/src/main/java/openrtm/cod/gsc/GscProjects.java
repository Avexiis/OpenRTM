package openrtm.cod.gsc;

import openrtm.config.ConfigManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class GscProjects
{
	private static final String STARTER = """
		init()
		{
		    level thread onPlayerConnect();
		}
		
		onPlayerConnect()
		{
		    for (;;)
		    {
		        level waittill("connected", player);
		        player thread onPlayerSpawned();
		    }
		}
		
		onPlayerSpawned()
		{
		    self endon("disconnect");
		    for (;;)
		    {
		        self waittill("spawned_player");
		        self iPrintLnBold("Welcome to your new menu!");
		    }
		}
		""";

	private GscProjects()
	{
	}

	public static Path directory()
	{
		return ConfigManager.shared().directory().resolve("GSC_Projects");
	}

	public static Path create(String name) throws IOException
	{
		String title = checkedName(name);
		Files.createDirectories(directory());
		Path project = directory().resolve(title);
		Files.createDirectory(project);
		try
		{
			Files.writeString(project.resolve("main.gsc"), STARTER, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			String escaped = title.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
			Files.writeString(project.resolve("config.il"), "<Project><Title>" + escaped
					+ "</Title><Game>MW2</Game><Mode>MP</Mode><Platform>Xbox</Platform></Project>\n",
				StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			return project;
		}
		catch (IOException failure)
		{
			Files.deleteIfExists(project.resolve("main.gsc"));
			Files.deleteIfExists(project.resolve("config.il"));
			Files.deleteIfExists(project);
			throw failure;
		}
	}

	public static String checkedName(String name)
	{
		String value = name == null ? "" : name.strip();
		if (value.isEmpty() || value.length() > 100
			|| value.matches(".*[\\\\/:*?\"<>|\\p{Cntrl}].*") || value.endsWith(".")
			|| value.toUpperCase(Locale.ROOT).matches("(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"))
		{
			throw new IllegalArgumentException("Enter a name without path separators or special file-name characters");
		}
		return value;
	}

	public static List<Path> files(Path selected) throws IOException
	{
		selected = selected.toAbsolutePath().normalize();
		if (!Files.exists(selected))
		{
			throw new IllegalArgumentException("The selected GSC path does not exist");
		}
		if (Files.isRegularFile(selected))
		{
			if (!selected.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gsc"))
			{
				throw new IllegalArgumentException("Select a .gsc file or project folder");
			}
			if (!Files.isRegularFile(selected.getParent().resolve("config.il")))
			{
				return List.of(selected);
			}
		}
		Path root = Files.isDirectory(selected) ? selected : selected.getParent();
		try (Stream<Path> files = Files.walk(root))
		{
			return files.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gsc"))
				.sorted(Comparator.comparing(path -> root.relativize(path).toString().toLowerCase(Locale.ROOT))).toList();
		}
	}
}
