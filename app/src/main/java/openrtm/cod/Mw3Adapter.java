package openrtm.cod;

import openrtm.console.ConsoleService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class Mw3Adapter extends AbstractCodAdapter
{
	private static final long COMMAND = 0x82287F68L;
	private static final long SERVER_COMMAND = 0x822C9340L;
	private static final long STAT_BASE = 0x830A60C0L;
	private static final long SERVER_ID = 0x826B81E8L;
	private static final long JUMP = 0x82001D6CL;
	private static final long FALL_DAMAGE = 0x82000C04L;
	private static final long GAME_SPEED = 0x82000CBCL;
	private static final long GRAVITY = 0x8222EDAEL;
	private static final long SPEED = 0x8222E61EL;
	private static final long FRICTION = 0x82005A5CL;
	private static final long FORCE_HOST_ONE = 0x823C8B4CL;
	private static final long FORCE_HOST_TWO = 0x8219DB40L;
	private static final long ENTITY = 2195508352L;
	private static final long ENTITY_SIZE = 640L;
	private static final byte[] FORCE_HOST_ONE_ON = {0x48, 0, 0, (byte) 0xC0};
	private static final byte[] FORCE_HOST_TWO_ON = {0x48, 0, 0, 0x68};
	private static final byte[] FORCE_HOST_ONE_OFF = {0x40, (byte) 0x99, 0, (byte) 0xC0};
	private static final byte[] FORCE_HOST_TWO_OFF = {0x41, (byte) 0x9A, 0, 0x68};
	private static final byte[] UNLOCK_CHALLENGES = unlockChallenges();
	private static final byte[] UNLOCK_PROGRESS = unlockProgress();
	private static final byte[] UNLOCK_FLAGS = filled(256, 0xFF);
	private static final Map<String, Long> STAT_ADDRESSES = addresses(
		"xp", STAT_BASE + 2716, "prestige", STAT_BASE + 3244, "score", STAT_BASE + 3252,
		"kills", STAT_BASE + 3292, "killstreak", STAT_BASE + 3296, "deaths", STAT_BASE + 3300,
		"assists", STAT_BASE + 3308, "headshots", STAT_BASE + 3312, "time", STAT_BASE + 3328,
		"kd", STAT_BASE + 3340, "wins", STAT_BASE + 3344, "losses", STAT_BASE + 3348,
		"ties", STAT_BASE + 3352, "winstreak", STAT_BASE + 3356, "hits", STAT_BASE + 3368,
		"misses", STAT_BASE + 3372, "accuracy", STAT_BASE + 3380, "tokens", STAT_BASE + 11019,
		"double_xp", STAT_BASE + 11105, "weapon_xp", STAT_BASE + 11113);
	private static final Map<String, Long> SPEC_OPS_ADDRESSES = addresses(
		"rank_xp", 2205207865L, "kills", 2205208025L, "juggernauts", 2205208029L,
		"headshots", 2205208033L, "misses", 2205208037L, "hits", 2205208041L, "waves", 2205208045L);
	private static final List<StatGroup> STATS = List.of(
		new StatGroup("multiplayer", "Multiplayer Statistics", List.of(
			stat("prestige", "Prestige", 20), stat("xp", "XP"), stat("score", "Score"),
			stat("kills", "Kills"), stat("deaths", "Deaths"), stat("assists", "Assists"),
			stat("headshots", "Headshots"), stat("killstreak", "Best Killstreak"),
			stat("kd", "K/D Ratio (x1000)"), stat("wins", "Wins"), stat("losses", "Losses"),
			stat("ties", "Ties"), stat("winstreak", "Best Win Streak"), stat("hits", "Hits"),
			stat("misses", "Misses"), stat("accuracy", "Accuracy (x100)"), stat("tokens", "Prestige Tokens"),
			stat("time", "Time Played (seconds)"), stat("double_xp", "Double XP Time (seconds)"),
			stat("weapon_xp", "Double Weapon XP Time (seconds)")), true),
		new StatGroup("spec_ops", "Special Ops & Survival", List.of(
			stat("rank_xp", "Rank XP"), stat("kills", "Kills"), stat("juggernauts", "Juggernaut Kills"),
			stat("headshots", "Headshots"), stat("hits", "Hits"), stat("misses", "Misses"),
			stat("waves", "Waves Survived")), true));
	private static final List<Action> ACTIONS = List.of(
		new Action("start", "Start Match", "Match", false), new Action("restart", "Restart Match", "Match", false),
		new Action("end", "End Match", "Match", false), new Action("leave", "Leave Match", "Match", false));
	private static final List<Toggle> TOGGLES = List.of(
		new Toggle("force_host", "Force Host", "Match", false), new Toggle("max_ammo", "Unlimited Ammo", "Host Settings", false));
	private static final List<ChoiceOption> CHOICES = List.of(
		new ChoiceOption("jump", "Jump Height", "Host Settings", CodPresets.MULTIPLIERS, false),
		new ChoiceOption("fall", "Fall Damage", "Host Settings", CodPresets.FALL_DAMAGE, false),
		new ChoiceOption("gravity", "Gravity", "Host Settings", CodPresets.GRAVITY, false),
		new ChoiceOption("friction", "Friction", "Host Settings", CodPresets.FRICTION, false),
		new ChoiceOption("speed", "Movement Speed", "Host Settings", CodPresets.MULTIPLIERS, false),
		new ChoiceOption("time", "Game Speed", "Host Settings", CodPresets.GAME_SPEED, false),
		new ChoiceOption("team", "Team", "Selected Player", List.of("Allies", "Axis", "Spectator"), true));
	private static final List<TextOption> TEXT = List.of(
		new TextOption("message", "Message", "Selected Player", 80, true),
		new TextOption("client_name", "In-Game Name", "Selected Player", 15, true));

	Mw3Adapter(ConsoleService console)
	{
		super(console, CodGame.MW3);
	}

	@Override
	public List<StatGroup> statGroups()
	{
		return STATS;
	}

	@Override
	protected Map<String, Long> onReadStats(String group)
	{
		return readDirect("spec_ops".equals(group) ? SPEC_OPS_ADDRESSES : STAT_ADDRESSES);
	}

	@Override
	protected void onWriteStats(String group, Map<String, Long> values)
	{
		writeDirect("spec_ops".equals(group) ? SPEC_OPS_ADDRESSES : STAT_ADDRESSES, values);
	}

	@Override
	public int classCount()
	{
		return 15;
	}

	@Override
	public boolean classNamesReadable()
	{
		return true;
	}

	@Override
	protected List<String> onReadClassNames()
	{
		List<String> names = new ArrayList<>();
		for (int i = 0; i < classCount(); i++)
		{
			names.add(readAscii(STAT_BASE + 4148 + i * 98L + 40, 20));
		}
		return names;
	}

	@Override
	protected void onWriteClassNames(List<String> names)
	{
		for (int i = 0; i < Math.min(classCount(), names.size()); i++)
		{
			writeAscii(STAT_BASE + 4148 + i * 98L + 40, names.get(i), 20);
		}
	}

	@Override
	protected void onUnlockAll()
	{
		write(STAT_BASE + 2276, UNLOCK_CHALLENGES);
		write(STAT_BASE + 6186, UNLOCK_PROGRESS);
		write(STAT_BASE + 10448, UNLOCK_FLAGS);
	}

	@Override
	protected void onDerankSignedInProfile()
	{
		writeIntLittle(STAT_BASE + 2716, 0);
		writeIntLittle(STAT_BASE + 3244, 0);
	}

	@Override
	public boolean clientNamesReadable()
	{
		return true;
	}

	@Override
	protected List<ClientInfo> onReadClients()
	{
		return readPointerClientTable(ENTITY, ENTITY_SIZE, 344, 13332, 20);
	}

	@Override
	public List<Action> actions()
	{
		return ACTIONS;
	}

	@Override
	protected void onRunAction(String key, int client)
	{
		switch (key)
		{
			case "start" -> command(COMMAND, "cl_timeout 0.01;partymigrate_pingtest_timeout 0.01;party_matchedPlayerCount 0;party_minplayers 1;xpartygo");
			case "restart" -> command(COMMAND, "fast_restart");
			case "end" -> command(COMMAND, "cmd mr " + Integer.toUnsignedLong(readIntBig(SERVER_ID)) + " -1 endround");
			case "leave" -> command(COMMAND, "disconnect");
			default -> throw new IllegalArgumentException("Unknown action");
		}
	}

	@Override
	public List<Toggle> toggles()
	{
		return TOGGLES;
	}

	@Override
	protected void onSetToggle(String key, boolean enabled, int client)
	{
		if ("force_host".equals(key))
		{
			write(FORCE_HOST_ONE, enabled ? FORCE_HOST_ONE_ON : FORCE_HOST_ONE_OFF);
			write(FORCE_HOST_TWO, enabled ? FORCE_HOST_TWO_ON : FORCE_HOST_TWO_OFF);
		}
		else if ("max_ammo".equals(key))
		{
			command(COMMAND, "player_sustainAmmo " + (enabled ? 1 : 0));
		}
		else
		{
			throw new IllegalArgumentException("Unknown toggle");
		}
	}

	@Override
	public List<ChoiceOption> choiceOptions()
	{
		return CHOICES;
	}

	@Override
	protected void onSetChoice(String key, String value, int client)
	{
		switch (key)
		{
			case "jump" -> writeFloatBig(JUMP, CodPresets.multiply(value, 39));
			case "fall" -> writeFloatBig(FALL_DAMAGE, CodPresets.percentage(value, 128));
			case "gravity" -> writeShortBig(GRAVITY, CodPresets.percentage(value, 800));
			case "friction" -> writeFloatBig(FRICTION, CodPresets.percentage(value, 550) / 100.0F);
			case "speed" -> writeShortBig(SPEED, CodPresets.multiply(value, 190));
			case "time" -> writeFloatBig(GAME_SPEED, CodPresets.percent(value) / 100.0F);
			case "team" -> serverCommand(SERVER_COMMAND, client, "v team " + value.toLowerCase());
			default -> throw new IllegalArgumentException("Unknown choice");
		}
	}

	@Override
	public List<TextOption> textOptions()
	{
		return TEXT;
	}

	@Override
	protected void onSetText(String key, String value, int client)
	{
		String text = cleanText(value);
		if ("message".equals(key))
		{
			serverCommand(SERVER_COMMAND, client, "c \"" + text + "\"");
		}
		else if ("client_name".equals(key))
		{
			serverCommand(SERVER_COMMAND, client, "v name \"" + text + "\"");
		}
		else
		{
			throw new IllegalArgumentException("Unknown text option");
		}
	}

	private static byte[] unlockChallenges()
	{
		ByteBuffer buffer = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < 512; i++)
		{
			buffer.putShort((short) 7864);
		}
		return buffer.array();
	}

	private static byte[] unlockProgress()
	{
		ByteBuffer buffer = ByteBuffer.allocate(1047 * 4).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < 1047; i++)
		{
			buffer.putInt(2_000_000);
		}
		return buffer.array();
	}
}
