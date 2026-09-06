package openrtm.config;

import openrtm.stfs.GameSaveService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

public final class GameSaveAssignmentStore
{
	private static final int MAX_ASSIGNMENTS = 64;

	private final Path directory;
	private final Path file;
	private final List<SavedAssignment> assignments = new ArrayList<>();

	public GameSaveAssignmentStore()
	{
		this(Path.of(System.getProperty("user.home"), ".openrtm"));
	}

	GameSaveAssignmentStore(Path directory)
	{
		this.directory = directory.toAbsolutePath().normalize();
		file = this.directory.resolve("game-save-assignments.properties");
		load();
	}

	public synchronized List<SavedAssignment> all()
	{
		return assignments.stream()
			.sorted(Comparator.comparing(SavedAssignment::label, String.CASE_INSENSITIVE_ORDER))
			.toList();
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
		if (!Files.isRegularFile(file))
		{
			return;
		}
		Properties values = new Properties();
		try (InputStream input = Files.newInputStream(file))
		{
			values.load(input);
			int count = Math.min(MAX_ASSIGNMENTS, Integer.parseInt(values.getProperty("count", "0")));
			for (int index = 0; index < count; index++)
			{
				String prefix = "assignment." + index + ".";
				try
				{
					assignments.add(new SavedAssignment(
						values.getProperty(prefix + "label",
							values.getProperty(prefix + "gamertag", "")).trim(),
						normalizeId(values.getProperty(prefix + "profileId"), 16, "Profile ID"),
						normalizeId(values.getProperty(prefix + "consoleId"), 10, "Console ID"),
						normalizeId(values.getProperty(prefix + "deviceId"), 40, "Device ID")));
				}
				catch (RuntimeException ignored)
				{
					// Ignore a damaged saved entry without discarding the other assignments.
				}
			}
			assignments.removeIf(saved -> saved.label().isBlank());
		}
		catch (IOException | NumberFormatException ignored)
		{
			assignments.clear();
		}
	}

	private void write()
	{
		Properties values = new Properties();
		List<SavedAssignment> saved = all();
		values.setProperty("count", Integer.toString(saved.size()));
		for (int index = 0; index < saved.size(); index++)
		{
			SavedAssignment assignment = saved.get(index);
			String prefix = "assignment." + index + ".";
			values.setProperty(prefix + "label", assignment.label());
			values.setProperty(prefix + "profileId", assignment.profileId());
			values.setProperty(prefix + "consoleId", assignment.consoleId());
			values.setProperty(prefix + "deviceId", assignment.deviceId());
		}
		try
		{
			Files.createDirectories(directory);
			Path temporary = Files.createTempFile(directory, "game-save-assignments-", ".tmp");
			try
			{
				try (OutputStream output = Files.newOutputStream(temporary))
				{
					values.store(output, "OpenRTM saved game profiles");
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
			throw new IllegalStateException("Could not save game assignment profiles", failure);
		}
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
