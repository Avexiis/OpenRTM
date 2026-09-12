package openrtm.profile;

import openrtm.stfs.PackageService;
import openrtm.titleids.TitleIds;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ProfileService
{
	private static final String DASHBOARD_FILE = "FFFE07D1.gpd";
	private static final long TITLES_PLAYED = 0x10040012L;
	private static final long ACHIEVEMENTS_EARNED = 0x10040013L;
	private static final long CREDIT_EARNED = 0x10040006L;
	private static final long MOTTO = 0x402C0011L;
	private static final long USER_NAME = 0x41040040L;
	private static final long LOCATION = 0x40520041L;
	private static final long BIO = 0x43E80043L;
	private static final long TITLE_ACHIEVEMENTS_EARNED = 0x10040039L;
	private static final long TITLE_CREDIT_EARNED = 0x10040038L;
	private static final long WINDOWS_EPOCH_OFFSET = 116444736000000000L;
	private static final int ACHIEVED_ONLINE = 0x00010000;
	private static final int ACHIEVED_OFFLINE = 0x00020000;
	private static final int PLATFORM_MASK = 0x00700000;
	private static final int XBOX_360_PLATFORM = 0x00100000;

	private final PackageService packages = new PackageService();

	public Profile inspect(Path profile) throws IOException
	{
		PackageService.Info packageInfo = requireProfile(profile);
		ProfileDatabase dashboard = dashboard(profile);
		List<Game> games = games(profile, dashboard);
		return new Profile(profile.toAbsolutePath().normalize(), packageInfo.creatorId(), packageInfo.displayName(),
			readUnicodeSetting(dashboard, MOTTO), readUnicodeSetting(dashboard, USER_NAME),
			readUnicodeSetting(dashboard, LOCATION), readUnicodeSetting(dashboard, BIO), games,
			readIntSetting(dashboard, ACHIEVEMENTS_EARNED, sumAchievements(games)),
			readIntSetting(dashboard, CREDIT_EARNED, sumCredit(games)));
	}

	public List<Achievement> achievements(Path profile, String titleId) throws IOException
	{
		requireProfile(profile);
		String id = normalizeTitleId(titleId);
		ProfileDatabase game = new ProfileDatabase(packages.readInternalFile(profile, id + ".gpd"));
		List<Achievement> achievements = new ArrayList<>();
		for (ProfileDatabase.Record record : game.records(ProfileDatabase.ACHIEVEMENTS))
		{
			achievements.add(parseAchievement(record.data()));
		}
		achievements.sort((left, right) -> Long.compareUnsigned(left.id(), right.id()));
		return Collections.unmodifiableList(achievements);
	}

	public PackageService.SaveResult saveDetails(Path source, Path destination, Details details,
	                                             boolean createBackup) throws IOException
	{
		requireProfile(source);
		if (details == null)
		{
			throw new IllegalArgumentException("Profile details are required");
		}
		ProfileDatabase dashboard = dashboard(source);
		putUnicodeSetting(dashboard, MOTTO, details.motto());
		putUnicodeSetting(dashboard, USER_NAME, details.userName());
		putUnicodeSetting(dashboard, LOCATION, details.location());
		putUnicodeSetting(dashboard, BIO, details.bio());
		return packages.saveInternalFiles(source, destination,
			Collections.singletonMap(DASHBOARD_FILE, dashboard.toByteArray()), createBackup);
	}

	public PackageService.SaveResult addGame(Path source, Path destination, Path gameDatabase,
	                                         String titleId, String requestedName,
	                                         boolean createBackup) throws IOException
	{
		requireProfile(source);
		if (gameDatabase == null || !Files.isRegularFile(gameDatabase))
		{
			throw new IOException("Choose a game profile database");
		}
		String id = normalizeTitleId(titleId);
		byte[] gameBytes = Files.readAllBytes(gameDatabase);
		ProfileDatabase game = new ProfileDatabase(gameBytes);
		ProfileDatabase dashboard = dashboard(source);
		long numericId = Long.parseUnsignedLong(id, 16);
		if (dashboard.contains(ProfileDatabase.TITLES, numericId))
		{
			throw new IOException("This game is already present in the profile");
		}
		String name = requestedName == null ? "" : requestedName.trim();
		if (name.isEmpty())
		{
			name = readGameName(game);
		}
		if (name.isEmpty())
		{
			name = TitleIds.displayName(id);
		}
		if (name.equalsIgnoreCase(id))
		{
			name = id;
		}
		int possibleAchievements = 0;
		int possibleCredit = 0;
		for (ProfileDatabase.Record record : game.records(ProfileDatabase.ACHIEVEMENTS))
		{
			Achievement achievement = parseAchievement(record.data());
			possibleAchievements++;
			possibleCredit = Math.addExact(possibleCredit, achievement.credit());
			byte[] locked = record.data();
			ByteBuffer values = ByteBuffer.wrap(locked).order(ByteOrder.BIG_ENDIAN);
			int flags = values.getInt(16) & ~(ACHIEVED_ONLINE | ACHIEVED_OFFLINE);
			values.putInt(16, flags);
			values.putLong(20, 0);
			game.put(ProfileDatabase.ACHIEVEMENTS, record.id(), locked, true);
			if (achievement.imageId() != 0)
			{
				game.remove(ProfileDatabase.IMAGES, achievement.imageId());
			}
		}
		putIntSetting(game, TITLE_ACHIEVEMENTS_EARNED, 0, false);
		putIntSetting(game, TITLE_CREDIT_EARNED, 0, false);
		byte[] titleRecord = createTitleRecord(numericId, name, possibleAchievements, possibleCredit);
		dashboard.put(ProfileDatabase.TITLES, numericId, titleRecord, true);
		incrementIntSetting(dashboard, TITLES_PLAYED, 1, true);
		Map<String, byte[]> updates = new LinkedHashMap<>();
		updates.put(DASHBOARD_FILE, dashboard.toByteArray());
		updates.put(id + ".gpd", game.toByteArray());
		return packages.saveInternalFiles(source, destination, updates, createBackup);
	}

	public UnlockResult unlock(Path source, Path destination, String titleId, Set<Long> achievementIds,
	                           boolean online, Instant achievedAt, boolean createBackup) throws IOException
	{
		requireProfile(source);
		if (achievementIds == null || achievementIds.isEmpty())
		{
			throw new IllegalArgumentException("Select at least one achievement");
		}
		String id = normalizeTitleId(titleId);
		long numericId = Long.parseUnsignedLong(id, 16);
		ProfileDatabase dashboard = dashboard(source);
		ProfileDatabase game = new ProfileDatabase(packages.readInternalFile(source, id + ".gpd"));
		Set<Long> requested = new LinkedHashSet<>(achievementIds);
		int unlockedCount = 0;
		int addedCredit = 0;
		long fileTime = online ? fileTime(achievedAt == null ? Instant.now() : achievedAt) : 0;
		for (ProfileDatabase.Record record : game.records(ProfileDatabase.ACHIEVEMENTS))
		{
			if (!requested.contains(record.id()))
			{
				continue;
			}
			Achievement achievement = parseAchievement(record.data());
			if (achievement.unlocked())
			{
				continue;
			}
			byte[] changed = record.data();
			ByteBuffer values = ByteBuffer.wrap(changed).order(ByteOrder.BIG_ENDIAN);
			int flags = values.getInt(16);
			flags &= ~(ACHIEVED_ONLINE | ACHIEVED_OFFLINE | PLATFORM_MASK);
			flags |= XBOX_360_PLATFORM | (online ? ACHIEVED_ONLINE : ACHIEVED_OFFLINE);
			values.putInt(16, flags);
			values.putLong(20, fileTime);
			game.put(ProfileDatabase.ACHIEVEMENTS, record.id(), changed, true);
			unlockedCount++;
			addedCredit = Math.addExact(addedCredit, achievement.credit());
		}
		if (unlockedCount == 0)
		{
			throw new IOException("The selected achievements are already unlocked");
		}
		byte[] title = dashboard.data(ProfileDatabase.TITLES, numericId);
		ByteBuffer titleValues = ByteBuffer.wrap(title).order(ByteOrder.BIG_ENDIAN);
		int earned = Math.addExact(titleValues.getInt(8), unlockedCount);
		int credit = Math.addExact(titleValues.getInt(16), addedCredit);
		if (earned > titleValues.getInt(4) || credit > titleValues.getInt(12))
		{
			throw new IOException("Achievement totals would exceed the game totals");
		}
		titleValues.putInt(8, earned);
		titleValues.putInt(16, credit);
		dashboard.put(ProfileDatabase.TITLES, numericId, title, true);
		incrementIntSetting(dashboard, ACHIEVEMENTS_EARNED, unlockedCount, false);
		incrementIntSetting(dashboard, CREDIT_EARNED, addedCredit, false);
		incrementIntSetting(game, TITLE_ACHIEVEMENTS_EARNED, unlockedCount, false);
		incrementIntSetting(game, TITLE_CREDIT_EARNED, addedCredit, false);
		Map<String, byte[]> updates = new LinkedHashMap<>();
		updates.put(DASHBOARD_FILE, dashboard.toByteArray());
		updates.put(id + ".gpd", game.toByteArray());
		PackageService.SaveResult saved = packages.saveInternalFiles(source, destination, updates, createBackup);
		return new UnlockResult(saved, unlockedCount, addedCredit);
	}

	private PackageService.Info requireProfile(Path profile) throws IOException
	{
		PackageService.Info info = packages.inspect(profile);
		if (info.contentType() != 0x00010000 || !info.stfs()
			|| info.signatureType() != PackageService.SignatureType.CON)
		{
			throw new IOException("Choose an editable Xbox 360 gamer profile");
		}
		return info;
	}

	private ProfileDatabase dashboard(Path profile) throws IOException
	{
		return new ProfileDatabase(packages.readInternalFile(profile, DASHBOARD_FILE));
	}

	private List<Game> games(Path profile, ProfileDatabase dashboard) throws IOException
	{
		Set<String> files = new LinkedHashSet<>();
		for (PackageService.InternalEntry entry : packages.contents(profile))
		{
			if (!entry.directory())
			{
				files.add(entry.path().toUpperCase(Locale.ROOT));
			}
		}
		List<Game> games = new ArrayList<>();
		for (ProfileDatabase.Record record : dashboard.records(ProfileDatabase.TITLES))
		{
			Game game = parseGame(record.data());
			if (files.contains(game.titleId() + ".GPD"))
			{
				games.add(game);
			}
		}
		games.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
		return Collections.unmodifiableList(games);
	}

	private static Game parseGame(byte[] data) throws IOException
	{
		if (data.length < 40)
		{
			throw new IOException("Invalid game record in profile");
		}
		ByteBuffer input = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
		String titleId = String.format(Locale.ROOT, "%08X", input.getInt());
		int possibleAchievements = input.getInt();
		int earnedAchievements = input.getInt();
		int possibleCredit = input.getInt();
		int earnedCredit = input.getInt();
		input.position(40);
		String name = readNullTerminatedUnicode(input);
		if (name.isEmpty())
		{
			name = TitleIds.displayName(titleId);
		}
		return new Game(titleId, name, possibleAchievements, earnedAchievements, possibleCredit, earnedCredit);
	}

	private static Achievement parseAchievement(byte[] data) throws IOException
	{
		if (data.length < 28)
		{
			throw new IOException("Invalid achievement record in profile");
		}
		ByteBuffer input = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
		int fixedSize = input.getInt();
		if (fixedSize < 28 || fixedSize > data.length)
		{
			throw new IOException("Invalid achievement record size");
		}
		long id = Integer.toUnsignedLong(input.getInt());
		long imageId = Integer.toUnsignedLong(input.getInt());
		int credit = input.getInt();
		int flags = input.getInt();
		long achievedAt = input.getLong();
		input.position(fixedSize);
		String name = readNullTerminatedUnicode(input);
		String description = readNullTerminatedUnicode(input);
		String lockedDescription = readNullTerminatedUnicode(input);
		return new Achievement(id, imageId, credit, flags, achievedAt, name, description, lockedDescription);
	}

	private static byte[] createTitleRecord(long titleId, String name, int achievements, int credit)
	{
		byte[] titleName = unicodeValue(name);
		ByteBuffer output = ByteBuffer.allocate(40 + titleName.length).order(ByteOrder.BIG_ENDIAN);
		output.putInt((int) titleId);
		output.putInt(achievements);
		output.putInt(0);
		output.putInt(credit);
		output.putInt(0);
		output.putShort((short) 0);
		output.put(new byte[6]);
		output.putInt(0);
		output.putLong(0);
		output.put(titleName);
		return output.array();
	}

	private static String readGameName(ProfileDatabase game) throws IOException
	{
		if (!game.contains(ProfileDatabase.STRINGS, 0x8000L))
		{
			return "";
		}
		return readNullTerminatedUnicode(ByteBuffer.wrap(game.data(ProfileDatabase.STRINGS, 0x8000L))
			.order(ByteOrder.BIG_ENDIAN));
	}

	private static String readUnicodeSetting(ProfileDatabase database, long id) throws IOException
	{
		if (!database.contains(ProfileDatabase.SETTINGS, id))
		{
			return "";
		}
		byte[] data = database.data(ProfileDatabase.SETTINGS, id);
		if (data.length < 24 || (data[8] & 0xFF) != 4)
		{
			return "";
		}
		int length = ByteBuffer.wrap(data, 16, 4).order(ByteOrder.BIG_ENDIAN).getInt();
		if (length < 0 || 24L + length > data.length)
		{
			throw new IOException("Invalid text field in profile");
		}
		return readNullTerminatedUnicode(ByteBuffer.wrap(data, 24, length).order(ByteOrder.BIG_ENDIAN));
	}

	private static void putUnicodeSetting(ProfileDatabase database, long id, String value) throws IOException
	{
		String normalized = value == null ? "" : value;
		byte[] text = unicodeValue(normalized);
		int maximumBytes = (int) ((id >>> 16) & 0xFFF);
		if (maximumBytes > 0 && text.length > maximumBytes)
		{
			throw new IOException("Profile text is too long; shorten the highlighted field");
		}
		byte[] data;
		if (database.contains(ProfileDatabase.SETTINGS, id))
		{
			byte[] current = database.data(ProfileDatabase.SETTINGS, id);
			if (current.length < 24 || (current[8] & 0xFF) != 4)
			{
				throw new IOException("Profile field has an unsupported data type");
			}
			data = new byte[24 + text.length];
			System.arraycopy(current, 0, data, 0, 24);
		}
		else
		{
			data = new byte[24 + text.length];
			ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).putInt((int) id);
			data[8] = 4;
		}
		ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).putInt(16, text.length);
		System.arraycopy(text, 0, data, 24, text.length);
		database.put(ProfileDatabase.SETTINGS, id, data, true);
	}

	private static int readIntSetting(ProfileDatabase database, long id, int fallback) throws IOException
	{
		if (!database.contains(ProfileDatabase.SETTINGS, id))
		{
			return fallback;
		}
		byte[] data = database.data(ProfileDatabase.SETTINGS, id);
		if (data.length < 20 || (data[8] & 0xFF) != 1)
		{
			return fallback;
		}
		return ByteBuffer.wrap(data, 16, 4).order(ByteOrder.BIG_ENDIAN).getInt();
	}

	private static void incrementIntSetting(ProfileDatabase database, long id, int increment,
	                                        boolean pendingSync) throws IOException
	{
		putIntSetting(database, id, Math.addExact(readIntSetting(database, id, 0), increment), pendingSync);
	}

	private static void putIntSetting(ProfileDatabase database, long id, int value,
	                                  boolean pendingSync) throws IOException
	{
		byte[] data;
		if (database.contains(ProfileDatabase.SETTINGS, id))
		{
			data = database.data(ProfileDatabase.SETTINGS, id);
			if (data.length < 20 || (data[8] & 0xFF) != 1)
			{
				throw new IOException("Profile counter has an unsupported data type");
			}
		}
		else
		{
			data = new byte[24];
			ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).putInt((int) id);
			data[8] = 1;
		}
		ByteBuffer values = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
		values.putInt(16, value);
		database.put(ProfileDatabase.SETTINGS, id, data, pendingSync);
	}

	private static byte[] unicodeValue(String value)
	{
		byte[] text = value.getBytes(StandardCharsets.UTF_16BE);
		byte[] terminated = new byte[text.length + 2];
		System.arraycopy(text, 0, terminated, 0, text.length);
		return terminated;
	}

	private static String readNullTerminatedUnicode(ByteBuffer input)
	{
		StringBuilder value = new StringBuilder();
		while (input.remaining() >= 2)
		{
			char character = input.getChar();
			if (character == 0)
			{
				break;
			}
			value.append(character);
		}
		return value.toString();
	}

	private static String normalizeTitleId(String titleId)
	{
		String value = titleId == null ? "" : titleId.trim().toUpperCase(Locale.ROOT);
		if (value.startsWith("0X"))
		{
			value = value.substring(2);
		}
		if (!value.matches("[0-9A-F]{8}"))
		{
			throw new IllegalArgumentException("Title ID must contain 8 hexadecimal digits");
		}
		return value;
	}

	private static long fileTime(Instant instant)
	{
		long seconds = Math.multiplyExact(instant.getEpochSecond(), 10_000_000L);
		return Math.addExact(WINDOWS_EPOCH_OFFSET, Math.addExact(seconds, instant.getNano() / 100));
	}

	private static int sumAchievements(List<Game> games)
	{
		int total = 0;
		for (Game game : games)
		{
			total += game.earnedAchievements();
		}
		return total;
	}

	private static int sumCredit(List<Game> games)
	{
		int total = 0;
		for (Game game : games)
		{
			total += game.earnedCredit();
		}
		return total;
	}

	public static final class Profile
	{
		private final Path path;
		private final String profileId;
		private final String displayName;
		private final String motto;
		private final String userName;
		private final String location;
		private final String bio;
		private final List<Game> games;
		private final int achievements;
		private final int gamerscore;

		private Profile(Path path, String profileId, String displayName, String motto, String userName,
		                String location, String bio, List<Game> games, int achievements, int gamerscore)
		{
			this.path = path;
			this.profileId = profileId;
			this.displayName = displayName;
			this.motto = motto;
			this.userName = userName;
			this.location = location;
			this.bio = bio;
			this.games = games;
			this.achievements = achievements;
			this.gamerscore = gamerscore;
		}

		public Path path()
		{
			return path;
		}

		public String profileId()
		{
			return profileId;
		}

		public String displayName()
		{
			return displayName;
		}

		public String motto()
		{
			return motto;
		}

		public String userName()
		{
			return userName;
		}

		public String location()
		{
			return location;
		}

		public String bio()
		{
			return bio;
		}

		public List<Game> games()
		{
			return games;
		}

		public int achievements()
		{
			return achievements;
		}

		public int gamerscore()
		{
			return gamerscore;
		}
	}

	public static final class Game
	{
		private final String titleId;
		private final String name;
		private final int possibleAchievements;
		private final int earnedAchievements;
		private final int possibleCredit;
		private final int earnedCredit;

		public Game(String titleId, String name, int possibleAchievements, int earnedAchievements,
		            int possibleCredit, int earnedCredit)
		{
			this.titleId = titleId;
			this.name = name;
			this.possibleAchievements = possibleAchievements;
			this.earnedAchievements = earnedAchievements;
			this.possibleCredit = possibleCredit;
			this.earnedCredit = earnedCredit;
		}

		public String titleId()
		{
			return titleId;
		}

		public String name()
		{
			return name;
		}

		public int possibleAchievements()
		{
			return possibleAchievements;
		}

		public int earnedAchievements()
		{
			return earnedAchievements;
		}

		public int possibleCredit()
		{
			return possibleCredit;
		}

		public int earnedCredit()
		{
			return earnedCredit;
		}

		@Override
		public String toString()
		{
			return name + " (" + titleId + ")";
		}
	}

	public static final class Achievement
	{
		private final long id;
		private final long imageId;
		private final int credit;
		private final int flags;
		private final long achievedAt;
		private final String name;
		private final String description;
		private final String lockedDescription;

		private Achievement(long id, long imageId, int credit, int flags, long achievedAt, String name,
		                    String description, String lockedDescription)
		{
			this.id = id;
			this.imageId = imageId;
			this.credit = credit;
			this.flags = flags;
			this.achievedAt = achievedAt;
			this.name = name;
			this.description = description;
			this.lockedDescription = lockedDescription;
		}

		public long id()
		{
			return id;
		}

		public long imageId()
		{
			return imageId;
		}

		public int credit()
		{
			return credit;
		}

		public int flags()
		{
			return flags;
		}

		public long achievedAt()
		{
			return achievedAt;
		}

		public String name()
		{
			return name;
		}

		public String description()
		{
			return description;
		}

		public String lockedDescription()
		{
			return lockedDescription;
		}

		public boolean unlocked()
		{
			return (flags & (ACHIEVED_ONLINE | ACHIEVED_OFFLINE)) != 0;
		}
	}

	public static final class Details
	{
		private final String motto;
		private final String userName;
		private final String location;
		private final String bio;

		public Details(String motto, String userName, String location, String bio)
		{
			this.motto = motto;
			this.userName = userName;
			this.location = location;
			this.bio = bio;
		}

		public String motto()
		{
			return motto;
		}

		public String userName()
		{
			return userName;
		}

		public String location()
		{
			return location;
		}

		public String bio()
		{
			return bio;
		}
	}

	public static final class UnlockResult
	{
		private final PackageService.SaveResult saveResult;
		private final int unlockedCount;
		private final int addedCredit;

		private UnlockResult(PackageService.SaveResult saveResult, int unlockedCount, int addedCredit)
		{
			this.saveResult = saveResult;
			this.unlockedCount = unlockedCount;
			this.addedCredit = addedCredit;
		}

		public PackageService.SaveResult saveResult()
		{
			return saveResult;
		}

		public int unlockedCount()
		{
			return unlockedCount;
		}

		public int addedCredit()
		{
			return addedCredit;
		}
	}
}
