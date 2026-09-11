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
	private final List<SavedAssignment> assignments = new ArrayList<>();

	public GameSaveAssignmentStore()
	{
		this(ConfigManager.shared());
	}

	GameSaveAssignmentStore(Path directory)
	{
		this(new ConfigManager(directory));
	}

	GameSaveAssignmentStore(ConfigManager config)
	{
		this.config = config;
		load();
	}

	public synchronized List<SavedAssignment> all()
	{
		List<SavedAssignment> sorted = new ArrayList<>(assignments);
		sorted.sort(Comparator.comparing(SavedAssignment::label, String.CASE_INSENSITIVE_ORDER));
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
		return assignments.stream().filter(saved -> saved.profileId().equals(normalized)).findFirst();
	}

	public synchronized SavedAssignment save(String label, GameSaveService.Assignment assignment)
	{
		if (assignment == null)
		{
			throw new IllegalArgumentException("Assignment IDs are required");
		}
		String name = label == null ? "" : label.trim();
		if (name.isBlank())
		{
			throw new IllegalArgumentException("Enter a label for this profile");
		}
		if (name.length() > 64)
		{
			throw new IllegalArgumentException("Profile label must be 64 characters or fewer");
		}
		SavedAssignment saved = new SavedAssignment(name,
			normalizeId(assignment.profileId(), 16, "Profile ID"),
			normalizeId(assignment.consoleId(), 10, "Console ID"),
			normalizeId(assignment.deviceId(), 40, "Device ID"));
		assignments.removeIf(existing -> existing.profileId().equals(saved.profileId())
			|| existing.label().equalsIgnoreCase(saved.label()));
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
				assignments.add(new SavedAssignment(assignment.get("label").getAsString().trim(),
					normalizeId(assignment.get("profileId").getAsString(), 16, "Profile ID"),
					normalizeId(assignment.get("consoleId").getAsString(), 10, "Console ID"),
					normalizeId(assignment.get("deviceId").getAsString(), 40, "Device ID")));
			}
			catch (RuntimeException ignored)
			{
			}
		}
		assignments.removeIf(savedAssignment -> savedAssignment.label().isBlank());
	}

	private void write()
	{
		JsonArray values = new JsonArray();
		for (SavedAssignment assignment : all())
		{
			JsonObject value = new JsonObject();
			value.addProperty("label", assignment.label());
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

	public record SavedAssignment(String label, String profileId, String consoleId, String deviceId)
	{
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
			return label;
		}
	}
}
