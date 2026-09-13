package openrtm.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ProfileIdentityStore
{
	private static final String KEY = "profiles.gamertags";
	private static final int MAX_IDENTITIES = 256;
	private final ConfigManager config;
	private final LinkedHashMap<String, String> identities = new LinkedHashMap<>();
	private final List<Runnable> listeners = new ArrayList<>();

	public ProfileIdentityStore()
	{
		this(ConfigManager.shared());
	}

	ProfileIdentityStore(ConfigManager config)
	{
		this.config = config;
		load();
	}

	public synchronized void remember(String profileId, String gamertag)
	{
		String id = normalize(profileId);
		String name = gamertag == null ? "" : gamertag.trim();
		if (id.isEmpty() || name.isEmpty() || name.equals("Unknown Profile"))
		{
			return;
		}
		if (name.equals(identities.get(id)))
		{
			return;
		}
		identities.remove(id);
		identities.put(id, name);
		while (identities.size() > MAX_IDENTITIES)
		{
			identities.remove(identities.keySet().iterator().next());
		}
		write();
		for (Runnable listener : listeners)
		{
			listener.run();
		}
	}

	public synchronized void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	public synchronized Optional<String> find(String profileId)
	{
		return Optional.ofNullable(identities.get(normalize(profileId)));
	}

	public synchronized String displayName(String profileId)
	{
		String id = normalize(profileId);
		if ("0000000000000000".equals(id))
		{
			return "Shared Content";
		}
		return Optional.ofNullable(identities.get(id)).orElse("Unknown Profile");
	}

	private void load()
	{
		for (JsonElement entry : config.array(KEY))
		{
			try
			{
				JsonObject value = entry.getAsJsonObject();
				String id = normalize(value.get("profileId").getAsString());
				String name = value.get("gamertag").getAsString().trim();
				if (!id.isEmpty() && !name.isEmpty() && !name.equals("Unknown Profile"))
				{
					identities.put(id, name);
				}
			}
			catch (RuntimeException ignored)
			{
			}
		}
	}

	private void write()
	{
		JsonArray values = new JsonArray();
		for (Map.Entry<String, String> identity : identities.entrySet())
		{
			JsonObject value = new JsonObject();
			value.addProperty("profileId", identity.getKey());
			value.addProperty("gamertag", identity.getValue());
			values.add(value);
		}
		config.put(KEY, values);
	}

	private static String normalize(String value)
	{
		String id = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
		return id.matches("[0-9A-F]{16}") ? id : "";
	}
}
