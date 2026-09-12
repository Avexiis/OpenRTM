package openrtm.profile;

import openrtm.titleids.TitleIds;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ProfileGameCatalog
{
	private static final String DIRECTORY = "/openrtm/gpds/";
	private static final String INDEX = DIRECTORY + "index.txt";
	private static final Pattern FILE_NAME = Pattern.compile("[0-9A-Fa-f]{8}\\.gpd");

	public List<Entry> load() throws IOException
	{
		List<Entry> entries = new ArrayList<>();
		Set<String> titleIds = new LinkedHashSet<>();
		try (InputStream stream = ProfileGameCatalog.class.getResourceAsStream(INDEX))
		{
			if (stream == null)
			{
				throw new IOException("The bundled game catalog is unavailable");
			}
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
			{
				String fileName;
				while ((fileName = reader.readLine()) != null)
				{
					String value = fileName.trim();
					if (!FILE_NAME.matcher(value).matches())
					{
						continue;
					}
					String titleId = value.substring(0, 8).toUpperCase(Locale.ROOT);
					if (titleId.startsWith("FFFE") || !titleIds.add(titleId))
					{
						continue;
					}
					String name = TitleIds.displayName(titleId);
					if (name.equalsIgnoreCase(titleId))
					{
						name = databaseName(value);
					}
					if (name.isBlank())
					{
						name = titleId;
					}
					entries.add(new Entry(titleId, name, value));
				}
			}
		}
		entries.sort((left, right) -> {
			int comparison = left.name.compareToIgnoreCase(right.name);
			return comparison == 0 ? left.titleId.compareTo(right.titleId) : comparison;
		});
		return Collections.unmodifiableList(entries);
	}

	public byte[] read(Entry entry) throws IOException
	{
		if (entry == null)
		{
			throw new IllegalArgumentException("Choose a game first");
		}
		return readResource(entry.resourceName);
	}

	private String databaseName(String resourceName)
	{
		try
		{
			ProfileDatabase database = new ProfileDatabase(readResource(resourceName));
			if (!database.contains(ProfileDatabase.STRINGS, 0x8000L))
			{
				return "";
			}
			ByteBuffer input = ByteBuffer.wrap(database.data(ProfileDatabase.STRINGS, 0x8000L))
				.order(ByteOrder.BIG_ENDIAN);
			StringBuilder name = new StringBuilder();
			while (input.remaining() >= 2)
			{
				char character = input.getChar();
				if (character == 0)
				{
					break;
				}
				name.append(character);
			}
			return name.toString().trim();
		}
		catch (IOException failure)
		{
			return "";
		}
	}

	private byte[] readResource(String resourceName) throws IOException
	{
		try (InputStream stream = ProfileGameCatalog.class.getResourceAsStream(DIRECTORY + resourceName))
		{
			if (stream == null)
			{
				throw new IOException("The selected game database is unavailable");
			}
			return stream.readAllBytes();
		}
	}

	public static final class Entry
	{
		private final String titleId;
		private final String name;
		private final String resourceName;

		private Entry(String titleId, String name, String resourceName)
		{
			this.titleId = titleId;
			this.name = name;
			this.resourceName = resourceName;
		}

		public String titleId()
		{
			return titleId;
		}

		public String name()
		{
			return name;
		}
	}
}
