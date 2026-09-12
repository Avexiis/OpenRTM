package openrtm.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BioPresetCatalog
{
	private static final String RESOURCE = "/openrtm/profile/bio-presets.json";

	private BioPresetCatalog()
	{
	}

	public static List<BioPreset> load() throws IOException
	{
		InputStream input = BioPresetCatalog.class.getResourceAsStream(RESOURCE);
		if (input == null)
		{
			throw new IOException("The bio preset list is unavailable");
		}
		try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8))
		{
			return parse(reader);
		}
	}

	static List<BioPreset> parse(Reader reader) throws IOException
	{
		try
		{
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonObject())
			{
				throw new IOException("The bio preset list has an invalid format");
			}
			JsonObject document = root.getAsJsonObject();
			JsonElement version = document.get("version");
			JsonElement presets = document.get("presets");
			if (version == null || !version.isJsonPrimitive()
				|| !version.getAsJsonPrimitive().isNumber() || version.getAsInt() != 1
				|| presets == null || !presets.isJsonArray())
			{
				throw new IOException("The bio preset list uses an unsupported format");
			}
			return parsePresets(presets.getAsJsonArray());
		}
		catch (RuntimeException failure)
		{
			throw new IOException("The bio preset list has an invalid format", failure);
		}
	}

	private static List<BioPreset> parsePresets(JsonArray entries) throws IOException
	{
		List<BioPreset> presets = new ArrayList<>();
		for (JsonElement entry : entries)
		{
			if (!entry.isJsonObject())
			{
				throw new IOException("The bio preset list has an invalid entry");
			}
			JsonObject object = entry.getAsJsonObject();
			JsonElement nameValue = object.get("name");
			JsonElement linesValue = object.get("lines");
			if (nameValue == null || !nameValue.isJsonPrimitive()
				|| !nameValue.getAsJsonPrimitive().isString() || linesValue == null
				|| !linesValue.isJsonArray())
			{
				throw new IOException("The bio preset list has an invalid entry");
			}
			String name = nameValue.getAsString();
			if (name.isBlank())
			{
				throw new IOException("A bio preset is missing its name");
			}
			List<String> lines = new ArrayList<>();
			for (JsonElement line : linesValue.getAsJsonArray())
			{
				if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString())
				{
					throw new IOException("A bio preset contains an invalid line");
				}
				lines.add(line.getAsString());
			}
			if (lines.isEmpty())
			{
				throw new IOException("A bio preset is empty");
			}
			presets.add(new BioPreset(name, String.join("\n", lines)));
		}
		return Collections.unmodifiableList(presets);
	}

	public static final class BioPreset
	{
		private final String name;
		private final String content;

		private BioPreset(String name, String content)
		{
			this.name = name;
			this.content = content;
		}

		public String name()
		{
			return name;
		}

		public String content()
		{
			return content;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
