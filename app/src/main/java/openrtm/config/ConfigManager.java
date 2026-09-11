package openrtm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public final class ConfigManager
{
	private static final ConfigManager SHARED = new ConfigManager(
		Path.of(System.getProperty("user.home"), ".openrtm"));

	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private final Path directory;
	private final Path file;
	private JsonObject values = new JsonObject();

	public static ConfigManager shared()
	{
		return SHARED;
	}

	ConfigManager(Path directory)
	{
		this.directory = directory.toAbsolutePath().normalize();
		file = this.directory.resolve("settings.json");
		load();
		migrateLegacySettings();
	}

	public synchronized Path directory()
	{
		return directory;
	}

	public synchronized Path file()
	{
		return file;
	}

	public synchronized Optional<String> string(String key)
	{
		JsonElement value = value(key);
		try
		{
			return value == null || !value.isJsonPrimitive()
				? Optional.empty()
				: Optional.of(value.getAsString());
		}
		catch (RuntimeException ignored)
		{
			return Optional.empty();
		}
	}

	public synchronized boolean bool(String key, boolean fallback)
	{
		JsonElement value = value(key);
		try
		{
			return value == null || !value.isJsonPrimitive() ? fallback : value.getAsBoolean();
		}
		catch (RuntimeException ignored)
		{
			return fallback;
		}
	}

	public synchronized List<String> strings(String key)
	{
		JsonElement value = value(key);
		List<String> result = new ArrayList<>();
		if (value == null || !value.isJsonArray())
		{
			return result;
		}
		for (JsonElement entry : value.getAsJsonArray())
		{
			if (entry.isJsonPrimitive())
			{
				result.add(entry.getAsString());
			}
		}
		return result;
	}

	public synchronized JsonArray array(String key)
	{
		JsonElement value = value(key);
		return value == null || !value.isJsonArray() ? new JsonArray() : value.getAsJsonArray().deepCopy();
	}

	public synchronized void put(String key, String value)
	{
		requireKey(key);
		if (value == null)
		{
			values.remove(key);
		}
		else
		{
			values.addProperty(key, value);
		}
		write();
	}

	public synchronized void put(String key, boolean value)
	{
		requireKey(key);
		values.addProperty(key, value);
		write();
	}

	public synchronized void putStrings(String key, Iterable<String> entries)
	{
		requireKey(key);
		JsonArray array = new JsonArray();
		for (String entry : entries)
		{
			array.add(entry);
		}
		values.add(key, array);
		write();
	}

	public synchronized void put(String key, JsonArray value)
	{
		requireKey(key);
		values.add(key, value.deepCopy());
		write();
	}

	private JsonElement value(String key)
	{
		requireKey(key);
		JsonElement value = values.get(key);
		return value == null || value.isJsonNull() ? null : value;
	}

	private void load()
	{
		if (!Files.isRegularFile(file))
		{
			return;
		}
		try (Reader reader = Files.newBufferedReader(file))
		{
			JsonElement parsed = JsonParser.parseReader(reader);
			if (parsed.isJsonObject())
			{
				values = parsed.getAsJsonObject();
			}
		}
		catch (IOException | RuntimeException ignored)
		{
			values = new JsonObject();
		}
	}

	private void migrateLegacySettings()
	{
		boolean changed = migrateConsoleSettings();
		changed |= migrateGameSaveAssignments();
		if (changed)
		{
			write();
		}
	}

	private boolean migrateConsoleSettings()
	{
		Path legacyFile = directory.resolve("settings.properties");
		Properties legacy = loadProperties(legacyFile);
		if (legacy.isEmpty())
		{
			return false;
		}
		boolean changed = false;
		changed |= addLegacyString("console.lastHost", legacy.getProperty("lastHost"));
		changed |= addLegacyBoolean("console.autoConnect", legacy.getProperty("autoConnect"));
		if (!values.has("console.recentHosts"))
		{
			JsonArray hosts = new JsonArray();
			for (String host : legacy.getProperty("recentHosts", "").split(","))
			{
				String trimmed = host.trim();
				if (!trimmed.isEmpty())
				{
					hosts.add(trimmed);
				}
			}
			if (hosts.size() > 0)
			{
				values.add("console.recentHosts", hosts);
				changed = true;
			}
		}
		return changed;
	}

	private boolean migrateGameSaveAssignments()
	{
		if (values.has("gameSaves.assignments"))
		{
			return false;
		}
		Properties legacy = loadProperties(directory.resolve("game-save-assignments.properties"));
		int count;
		try
		{
			count = Integer.parseInt(legacy.getProperty("count", "0"));
		}
		catch (NumberFormatException ignored)
		{
			return false;
		}
		JsonArray assignments = new JsonArray();
		for (int index = 0; index < count; index++)
		{
			String prefix = "assignment." + index + ".";
			JsonObject assignment = new JsonObject();
			assignment.addProperty("label", legacy.getProperty(prefix + "label",
				legacy.getProperty(prefix + "gamertag", "")));
			assignment.addProperty("profileId", legacy.getProperty(prefix + "profileId", ""));
			assignment.addProperty("consoleId", legacy.getProperty(prefix + "consoleId", ""));
			assignment.addProperty("deviceId", legacy.getProperty(prefix + "deviceId", ""));
			assignments.add(assignment);
		}
		if (assignments.size() == 0)
		{
			return false;
		}
		values.add("gameSaves.assignments", assignments);
		return true;
	}

	private boolean addLegacyString(String key, String value)
	{
		if (values.has(key) || value == null || value.trim().isEmpty())
		{
			return false;
		}
		values.addProperty(key, value.trim());
		return true;
	}

	private boolean addLegacyBoolean(String key, String value)
	{
		if (values.has(key) || value == null)
		{
			return false;
		}
		values.addProperty(key, Boolean.parseBoolean(value));
		return true;
	}

	private Properties loadProperties(Path source)
	{
		Properties properties = new Properties();
		if (!Files.isRegularFile(source))
		{
			return properties;
		}
		try (Reader reader = Files.newBufferedReader(source))
		{
			properties.load(reader);
		}
		catch (IOException ignored)
		{
			properties.clear();
		}
		return properties;
	}

	private void write()
	{
		try
		{
			Files.createDirectories(directory);
			Path temporary = Files.createTempFile(directory, "settings-", ".tmp");
			try
			{
				try (Writer writer = Files.newBufferedWriter(temporary))
				{
					gson.toJson(values, writer);
				}
				try
				{
					Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
						StandardCopyOption.REPLACE_EXISTING);
				}
				catch (AtomicMoveNotSupportedException ignored)
				{
					Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			finally
			{
				Files.deleteIfExists(temporary);
			}
		}
		catch (IOException failure)
		{
			throw new IllegalStateException("Could not save settings to " + file, failure);
		}
	}

	private static void requireKey(String key)
	{
		if (key == null || key.trim().isEmpty())
		{
			throw new IllegalArgumentException("A settings key is required");
		}
	}
}
