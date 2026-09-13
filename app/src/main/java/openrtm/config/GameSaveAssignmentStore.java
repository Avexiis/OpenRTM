package openrtm.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import openrtm.stfs.GameSaveService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class GameSaveAssignmentStore
{
	private static final int MAX_ASSIGNMENTS = 64;

	private final ConfigManager config;
	private final ProfileIdentityStore identities;
	private final List<SavedAssignment> assignments = new ArrayList<>();

	public GameSaveAssignmentStore()
	{
		this(ConfigManager.shared(), new ProfileIdentityStore());
	}

	public GameSaveAssignmentStore(ProfileIdentityStore identities)
	{
		this(ConfigManager.shared(), identities);
	}

	GameSaveAssignmentStore(Path directory)
	{
		this(new ConfigManager(directory));
	}

	private GameSaveAssignmentStore(ConfigManager config)
	{
		this(config, new ProfileIdentityStore(config));
	}

	private GameSaveAssignmentStore(ConfigManager config, ProfileIdentityStore identities)
	{
		this.config = config;
		this.identities = identities;
		load();
		write();
	}

	public synchronized List<SavedAssignment> all()
	{
		List<SavedAssignment> sorted = new ArrayList<>();
		for (SavedAssignment assignment : assignments)
		{
			sorted.add(assignment.withGamertag(identities.displayName(assignment.profileId())));
		}
		sorted.sort(Comparator.comparing(SavedAssignment::gamertag, String.CASE_INSENSITIVE_ORDER));
		return sorted;
	}

	public synchronized Optional<SavedAssignment> find(GameSaveService.Assignment assignment)
	{
		if (assignment == null)
		{
			return Optional.empty();
		}
		return assignments.stream().filter(saved -> saved.matches(assignment)).findFirst();
	}

	public synchronized Optional<SavedAssignment> findByProfileId(String profileId)
	{
		String normalized = normalizeId(profileId, 16, "Profile ID");
		return assignments.stream().filter(saved -> saved.profileId().equals(normalized)).findFirst()
			.map(saved -> saved.withGamertag(identities.displayName(saved.profileId())));
	}

	public synchronized SavedAssignment save(GameSaveService.Assignment assignment)
	{
		if (assignment == null)
		{
			throw new IllegalArgumentException("Assignment IDs are required");
		}
		String profileId = normalizeId(assignment.profileId(), 16, "Profile ID");
		String gamertag = identities.find(profileId).orElseThrow(() ->
			new IllegalArgumentException("Open this gamer profile first so its gamertag can be detected"));
		SavedAssignment saved = new SavedAssignment(gamertag, profileId,
			normalizeId(assignment.consoleId(), 10, "Console ID"),
			normalizeId(assignment.deviceId(), 40, "Device ID"));
		assignments.removeIf(existing -> existing.profileId().equals(saved.profileId()));
		assignments.add(saved);
		if (assignments.size() > MAX_ASSIGNMENTS)
		{
			assignments.remove(0);
		}
		write();
		return saved;
	}

	private void load()
	{
		for (JsonElement entry : config.array("gameSaves.assignments"))
		{
			if (assignments.size() >= MAX_ASSIGNMENTS)
			{
				break;
			}
			try
			{
				JsonObject assignment = entry.getAsJsonObject();
				String profileId = normalizeId(assignment.get("profileId").getAsString(), 16, "Profile ID");
				assignments.add(new SavedAssignment(identities.displayName(profileId), profileId,
					normalizeId(assignment.get("consoleId").getAsString(), 10, "Console ID"),
					normalizeId(assignment.get("deviceId").getAsString(), 40, "Device ID")));
			}
			catch (RuntimeException ignored)
			{
			}
		}
	}

	private void write()
	{
		JsonArray values = new JsonArray();
		for (SavedAssignment assignment : all())
		{
			JsonObject value = new JsonObject();
			value.addProperty("profileId", assignment.profileId());
			value.addProperty("consoleId", assignment.consoleId());
			value.addProperty("deviceId", assignment.deviceId());
			values.add(value);
		}
		config.put("gameSaves.assignments", values);
	}

	private static String normalizeId(String value, int length, String label)
	{
		String normalized = value == null ? "" : value.trim().replaceAll("[^0-9A-Fa-f]", "")
			.toUpperCase(Locale.ROOT);
		if (normalized.length() != length)
		{
			throw new IllegalArgumentException(label + " must contain exactly " + length + " hexadecimal digits");
		}
		return normalized;
	}

	public record SavedAssignment(String gamertag, String profileId, String consoleId, String deviceId)
	{
		private SavedAssignment withGamertag(String value)
		{
			return new SavedAssignment(value, profileId, consoleId, deviceId);
		}

		public GameSaveService.Assignment assignment()
		{
			return new GameSaveService.Assignment(profileId, consoleId, deviceId);
		}

		private boolean matches(GameSaveService.Assignment other)
		{
			return profileId.equalsIgnoreCase(other.profileId())
				&& consoleId.equalsIgnoreCase(other.consoleId())
				&& deviceId.equalsIgnoreCase(other.deviceId());
		}

		@Override
		public String toString()
		{
			return gamertag;
		}
	}
}
