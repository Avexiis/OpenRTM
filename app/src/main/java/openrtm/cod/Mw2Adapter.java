package openrtm.cod;

import openrtm.console.ConsoleService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

final class Mw2Adapter extends AbstractCodAdapter
{
	private static final long COMMAND = 0x82224990L;
	private static final long SERVER_COMMAND = 0x822548D8L;
	private static final long STAT_BASE = 0x831A05C0L;
	private static final long END_GAME_ID = 0x826237E0L;
	private static final long FORCE_HOST_ONE = 0x8231ECE4L;
	private static final long FORCE_HOST_TWO = 0x8215FC78L;
	private static final long JUMP = 0x82001A34L;
	private static final long FALL_DAMAGE = 0x82019C48L;
	private static final long GRAVITY = 0x821D264EL;
	private static final long FRICTION = 0x82006FE8L;
	private static final long SPEED = 0x821D1DE2L;
	private static final long ENTITY = 2196780544L;
	private static final long ENTITY_SIZE = 640L;
	private static final byte[] FORCE_HOST_ONE_ON = {(byte) 0x48, 0, 1, 0x20};
	private static final byte[] FORCE_HOST_TWO_ON = {(byte) 0x48, 0, 0, 0x3C};
	private static final byte[] FORCE_HOST_ONE_OFF = {0x40, (byte) 0x99, 1, 0x20};
	private static final byte[] FORCE_HOST_TWO_OFF = {0x41, (byte) 0x9A, 0, 0x3C};
	private static final String[] UNLOCK_COMMANDS = unlockCommands();
	private static final String[] REPAIR_CLASS_NAMES = repairClassNames();
	private static final int[] REMOTE_CLASS_FIELDS = {3040, 3104, 3168, 3232, 3296, 3360, 3424, 3488, 3552, 3616};
	private static final Map<String, Long> STAT_ADDRESSES = addresses(
		"prestige", STAT_BASE + 2068, "xp", STAT_BASE + 2060, "score", STAT_BASE + 2076,
		"kills", STAT_BASE + 2080, "killstreak", STAT_BASE + 2084, "deaths", STAT_BASE + 2088,
		"assists", STAT_BASE + 2096, "headshots", STAT_BASE + 2100, "wins", STAT_BASE + 2132,
		"losses", STAT_BASE + 2136, "ties", STAT_BASE + 2140, "winstreak", STAT_BASE + 2144);
	private static final long[] CLASS_NAMES = {
		0x831A31A8L, 0x831A31E8L, 0x831A3228L, 0x831A3268L, 0x831A32A8L,
		0x831A32E8L, 0x831A3328L, 0x831A3368L, 0x831A33A8L, 0x831A33E8L
	};
	private static final List<StatGroup> STATS = List.of(new StatGroup("multiplayer", "Multiplayer Statistics", List.of(
		stat("prestige", "Prestige", 10), stat("xp", "XP"), stat("score", "Score"),
		stat("kills", "Kills"), stat("deaths", "Deaths"), stat("assists", "Assists"),
		stat("headshots", "Headshots"), stat("killstreak", "Best Killstreak"),
		stat("wins", "Wins"), stat("losses", "Losses"), stat("ties", "Ties"),
		stat("winstreak", "Best Win Streak")), true));
	private static final List<Action> ACTIONS = List.of(
		new Action("start", "Start Match", "Match", false), new Action("restart", "Restart Match", "Match", false),
		new Action("end", "End Match", "Match", false), new Action("leave", "Leave Match", "Match", false),
		new Action("unfreeze_host_classes", "Unfreeze Host Classes", "Host Settings", false),
		new Action("unfreeze_player_classes", "Unfreeze Selected Player Classes", "Selected Player", true));
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
		new TextOption("clan", "Clan Tag", "Profile", 4, false),
		new TextOption("client_name", "In-Game Name", "Selected Player", 15, true),
		new TextOption("message", "Message", "Selected Player", 80, true));

	Mw2Adapter(ConsoleService console)
	{
		super(console, CodGame.MW2);
	}

	@Override
	public List<StatGroup> statGroups()
	{
		return STATS;
	}

	@Override
	protected Map<String, Long> onReadStats(String group)
	{
		return readDirect(STAT_ADDRESSES);
	}

	@Override
	protected void onWriteStats(String group, Map<String, Long> values)
	{
		writeDirect(STAT_ADDRESSES, values);
	}

	@Override
	public int classCount()
	{
		return CLASS_NAMES.length;
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
		for (long address : CLASS_NAMES)
		{
			names.add(readAscii(address, 19));
		}
		return names;
	}

	@Override
	protected void onWriteClassNames(List<String> names)
	{
		for (int i = 0; i < Math.min(names.size(), CLASS_NAMES.length); i++)
		{
			writeAscii(CLASS_NAMES[i], names.get(i), 19);
		}
	}

	@Override
	protected void onUnlockAll()
	{
		throw new UnsupportedOperationException("MW2 challenge unlocks require a selected client");
	}

	@Override
	public boolean unlockTargetsClient()
	{
		return true;
	}

	@Override
	protected void onUnlockClient(int client)
	{
		serverCommand(SERVER_COMMAND, client, "J 2056 206426 6525 7F");
		for (String payload : UNLOCK_COMMANDS)
		{
			requireTitle();
			serverCommand(SERVER_COMMAND, client, payload);
		}
	}

	@Override
	protected void onDerankSignedInProfile()
	{
		command(COMMAND, "resetStats;defaultStatsInit");
	}

	@Override
	public boolean clientNamesReadable()
	{
		return true;
	}

	@Override
	protected List<ClientInfo> onReadClients()
	{
		return readPointerClientTable(ENTITY, ENTITY_SIZE, 344, 12944, 32);
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
			case "start" -> command(COMMAND, "party_minplayers 1;party_maxTeamDiff 6;xpartygo");
			case "restart" -> command(COMMAND, "fast_restart");
			case "end" -> command(COMMAND, "mr " + Integer.toUnsignedLong(readIntBig(END_GAME_ID)) + " -1 endround");
			case "leave" -> command(COMMAND, "disconnect");
			case "unfreeze_host_classes" -> repairHostClasses();
			case "unfreeze_player_classes" -> repairPlayerClasses(client);
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
			case "time" -> command(COMMAND, "timescale " + CodPresets.percent(value) / 100.0);
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
		switch (key)
		{
			case "clan" -> command(COMMAND, "clanName " + text + ";updategamerprofile");
			case "client_name" -> serverCommand(SERVER_COMMAND, client, "v name \"" + text + "\"");
			case "message" -> serverCommand(SERVER_COMMAND, client, "c \"" + text + "\"");
			default -> throw new IllegalArgumentException("Unknown text option");
		}
	}

	private static String[] unlockCommands()
	{
		List<String> commands = new ArrayList<>();
		for (int start = 3500; start < 5000; start += 50)
		{
			StringBuilder command = new StringBuilder("J");
			for (int index = start; index <= start + 50; index++)
			{
				command.append(' ').append(index).append(" 99");
			}
			commands.add(command.toString());
		}
		return commands.toArray(String[]::new);
	}

	private static String[] repairClassNames()
	{
		String[] names = new String[10];
		for (int index = 0; index < names.length; index++)
		{
			names[index] = "^" + (index % 7 + 1) + "OpenRTM";
		}
		return names;
	}

	private void repairHostClasses()
	{
		for (int index = 0; index < CLASS_NAMES.length; index++)
		{
			requireTitle();
			writeAscii(CLASS_NAMES[index], REPAIR_CLASS_NAMES[index], 19);
		}
	}

	private void repairPlayerClasses(int client)
	{
		HexFormat hex = HexFormat.of().withUpperCase();
		for (int index = 0; index < REMOTE_CLASS_FIELDS.length; index++)
		{
			requireTitle();
			String value = hex.formatHex(REPAIR_CLASS_NAMES[index].getBytes(StandardCharsets.US_ASCII));
			call(SERVER_COMMAND, client, 1, "J " + REMOTE_CLASS_FIELDS[index] + " " + value + "00");
		}
	}

}
