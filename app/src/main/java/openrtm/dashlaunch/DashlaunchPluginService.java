package openrtm.dashlaunch;

import openrtm.console.ConsoleService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DashlaunchPluginService
{
	private static final String REMOTE_PATH = "Hdd:\\Launch.ini";
	private static final Pattern SECTION = Pattern.compile("^\\s*\\[([^]]+)]\\s*$");
	private static final Pattern PLUGIN = Pattern.compile("(?i)^\\s*plugin([1-5])\\s*=\\s*(.*)$");
	private final ConsoleService console;

	public DashlaunchPluginService(ConsoleService console)
	{
		this.console = console;
	}

	public Document load() throws IOException
	{
		Path temporary = Files.createTempFile("openrtm-launch-", ".ini");
		try
		{
			console.downloadFile(REMOTE_PATH, temporary);
			String text = Files.readString(temporary, StandardCharsets.ISO_8859_1);
			return Document.parse(text);
		}
		finally
		{
			Files.deleteIfExists(temporary);
		}
	}

	public Document save(Document source, List<String> values) throws IOException
	{
		if (source == null)
		{
			throw new IllegalStateException("Load the plugin settings before saving");
		}
		List<String> plugins = normalize(values);
		Document updated = source.withPlugins(plugins);
		Path temporary = Files.createTempFile("openrtm-launch-", ".ini");
		try
		{
			Files.writeString(temporary, updated.text(), StandardCharsets.ISO_8859_1);
			console.uploadFile(temporary, REMOTE_PATH);
			return updated;
		}
		finally
		{
			Files.deleteIfExists(temporary);
		}
	}

	private static List<String> normalize(List<String> values)
	{
		if (values == null || values.size() != 5)
		{
			throw new IllegalArgumentException("Exactly five plugin slots are required");
		}
		List<String> normalized = new ArrayList<>();
		for (int index = 0; index < values.size(); index++)
		{
			String value = values.get(index) == null ? "" : values.get(index).trim().replace('/', '\\');
			if (!value.isEmpty() && !value.matches("(?i)[A-Z][A-Z0-9]*:\\\\.+\\.xex"))
			{
				throw new IllegalArgumentException("Plugin " + (index + 1) + " must be a console XEX path");
			}
			normalized.add(value);
		}
		return List.copyOf(normalized);
	}

	public static final class Document
	{
		private final String text;
		private final String lineSeparator;
		private final List<String> plugins;

		private Document(String text, String lineSeparator, List<String> plugins)
		{
			this.text = text;
			this.lineSeparator = lineSeparator;
			this.plugins = List.copyOf(plugins);
		}

		public static Document parse(String text)
		{
			String content = text == null ? "" : text;
			String separator = content.contains("\r\n") ? "\r\n" : content.contains("\r") ? "\r" : "\n";
			List<String> lines = Arrays.asList(content.split("\\r\\n|\\n|\\r", -1));
			int start = sectionStart(lines);
			int end = sectionEnd(lines, start);
			List<String> plugins = new ArrayList<>(List.of("", "", "", "", ""));
			if (start >= 0)
			{
				for (int index = start + 1; index < end; index++)
				{
					Matcher matcher = PLUGIN.matcher(lines.get(index));
					if (matcher.matches())
					{
						plugins.set(Integer.parseInt(matcher.group(1)) - 1, matcher.group(2).trim());
					}
				}
			}
			return new Document(content, separator, plugins);
		}

		public List<String> plugins()
		{
			return plugins;
		}

		private String text()
		{
			return text;
		}

		private Document withPlugins(List<String> values)
		{
			List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\r\\n|\\n|\\r", -1)));
			int start = sectionStart(lines);
			if (start < 0)
			{
				if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty())
				{
					lines.add("");
				}
				lines.add("[Plugins]");
				start = lines.size() - 1;
			}
			int end = sectionEnd(lines, start);
			boolean[] found = new boolean[5];
			for (int index = start + 1; index < end; index++)
			{
				Matcher matcher = PLUGIN.matcher(lines.get(index));
				if (matcher.matches())
				{
					int slot = Integer.parseInt(matcher.group(1)) - 1;
					lines.set(index, "plugin" + (slot + 1) + " = " + values.get(slot));
					found[slot] = true;
				}
			}
			for (int slot = found.length - 1; slot >= 0; slot--)
			{
				if (!found[slot])
				{
					lines.add(end, "plugin" + (slot + 1) + " = " + values.get(slot));
				}
			}
			return new Document(String.join(lineSeparator, lines), lineSeparator, values);
		}

		private static int sectionStart(List<String> lines)
		{
			for (int index = 0; index < lines.size(); index++)
			{
				Matcher matcher = SECTION.matcher(lines.get(index));
				if (matcher.matches() && "plugins".equals(matcher.group(1).toLowerCase(Locale.ROOT)))
				{
					return index;
				}
			}
			return -1;
		}

		private static int sectionEnd(List<String> lines, int start)
		{
			if (start < 0)
			{
				return lines.size();
			}
			for (int index = start + 1; index < lines.size(); index++)
			{
				if (SECTION.matcher(lines.get(index)).matches())
				{
					return index;
				}
			}
			return lines.size();
		}
	}
}
