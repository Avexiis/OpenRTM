package openrtm.titleids;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class TitleIds
{
	private static final String RESOURCE = "/openrtm/titleids/TitleIDs.json";
	private static final Pattern TITLE_ID = Pattern.compile("[0-9A-F]{8}");
	private static final Map<String, Title> TITLES = loadTitlesSafely();

	private TitleIds()
	{
	}

	public static Optional<Title> find(String value)
	{
		String id = normalize(value);
		return id == null ? Optional.empty() : Optional.ofNullable(TITLES.get(id));
	}

	public static String displayName(String value)
	{
		return find(value).map(Title::name).orElse(value);
	}

	public record Title(String id, String name)
	{
	}

	private static Map<String, Title> loadTitlesSafely()
	{
		try
		{
			return loadTitles();
		}
		catch (RuntimeException failure)
		{
			System.err.println("Could not load Xbox 360 title IDs: " + failure.getMessage());
			return Map.of();
		}
	}

	private static Map<String, Title> loadTitles()
	{
		InputStream stream = TitleIds.class.getResourceAsStream(RESOURCE);
		if (stream == null)
		{
			throw new IllegalStateException("Missing title ID resource: " + RESOURCE);
		}

		try (stream; InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
		{
			JsonElement root = JsonParser.parseReader(reader);
			if (!root.isJsonArray())
			{
				throw new IllegalStateException("Title ID resource must contain an array");
			}

			Map<String, Title> titles = new LinkedHashMap<>();
			for (JsonElement element : root.getAsJsonArray())
			{
				JsonObject object = element.getAsJsonObject();
				String id = normalize(requiredString(object, "TitleID"));
				String name = requiredString(object, "Title").trim();
				if (id == null || name.isEmpty())
				{
					throw new IllegalStateException("Invalid title ID entry in " + RESOURCE);
				}
				if (titles.putIfAbsent(id, new Title(id, name)) != null)
				{
					throw new IllegalStateException("Duplicate title ID in " + RESOURCE + ": " + id);
				}
			}
			return Map.copyOf(titles);
		}
		catch (IOException failure)
		{
			throw new IllegalStateException("Could not load title IDs from " + RESOURCE, failure);
		}
	}

	private static String requiredString(JsonObject object, String property)
	{
		JsonElement value = object.get(property);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
		{
			throw new IllegalStateException("Missing string property: " + property);
		}
		return value.getAsString();
	}

	private static String normalize(String value)
	{
		if (value == null)
		{
			return null;
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		if (normalized.startsWith("0X"))
		{
			normalized = normalized.substring(2);
		}
		return TITLE_ID.matcher(normalized).matches() ? normalized : null;
	}
}
