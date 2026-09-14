package openrtm.cod;

import openrtm.console.ConsoleService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class AwAdapter extends AbstractCodAdapter
{
	private static final long COMMAND = 2185702272L;
	private static final long SERVER_COMMAND = 2185124856L;
	private static final long GAME_START = 2187004984L;
	private static final long STAT_BASE = 2208743748L;
	private static final long JUMP = 2181453472L;
	private static final long PLAYER_STATE = 2206236544L;
	private static final long PLAYER_STATE_SIZE = 14720L;
	private static final long CLIENT_POINTER = 2215605904L;
	private static final long CLIENT_SIZE = 290432L;
	private static final byte[] UNLOCK_DATA = filled(6000, 0xFE);
	private static final byte[] MAX_AMMO_ON = {(byte) 0x3B, (byte) 0x80, 0, 0};
	private static final byte[] MAX_AMMO_OFF = {(byte) 0x7C, (byte) 0xDC, (byte) 0x33, (byte) 0x78};
	private static final long MAX_AMMO = 2188986752L;
	private static final Map<String, Long> STAT_ADDRESSES = addresses(
		"prestige", STAT_BASE + 13, "accuracy", STAT_BASE + 81, "assists", STAT_BASE + 85,
		"captures", STAT_BASE + 137, "confirms", STAT_BASE + 141, "deaths", STAT_BASE + 149,
		"xp", STAT_BASE + 169, "games", STAT_BASE + 173, "kills", STAT_BASE + 189,
		"killstreak", STAT_BASE + 193, "losses", STAT_BASE + 197, "score", STAT_BASE + 230,
		"time", STAT_BASE + 258, "wins", STAT_BASE + 274, "suicides", STAT_BASE + 564);
	private static final List<StatGroup> STATS = List.of(new StatGroup("multiplayer", "Multiplayer Statistics", List.of(
		stat("prestige", "Prestige", 30), stat("xp", "Rank XP"), stat("score", "Score"),
		stat("kills", "Kills"), stat("deaths", "Deaths"), stat("assists", "Assists"),
		stat("accuracy", "Accuracy"), stat("wins", "Wins"), stat("losses", "Losses"),
		stat("captures", "Captures"), stat("confirms", "Confirms"),
		stat("killstreak", "Best Killstreak"), stat("games", "Games Played"),
		stat("time", "Time Played (seconds)"), stat("suicides", "Suicides")), true));
	private static final List<Action> ACTIONS = List.of(
		new Action("start", "Start Match", "Match", false), new Action("restart", "Restart Match", "Match", false),
		new Action("end", "End Match", "Match", false), new Action("leave", "Leave Match", "Match", false));
	private static final List<Toggle> TOGGLES = List.of(
		new Toggle("force_host", "Force Host", "Match", false),
		new Toggle("max_ammo", "Unlimited Ammo", "Host Settings", false),
		new Toggle("friction", "No Friction", "Selected Player", true),
		new Toggle("ragdoll_gravity", "Ragdoll Gravity", "Exo Zombies", false));
	private static final List<ChoiceOption> CHOICES = List.of(
		new ChoiceOption("jump", "Jump Height", "Host Settings", CodPresets.MULTIPLIERS, false),
		new ChoiceOption("speed", "Movement Speed", "Host Settings", CodPresets.MULTIPLIERS, false),
		new ChoiceOption("time", "Game Speed", "Host Settings", CodPresets.GAME_SPEED, false),
		new ChoiceOption("exo_jump", "Jump Height", "Exo Zombies", CodPresets.MULTIPLIERS, false),
		new ChoiceOption("exo_speed", "Movement Speed", "Exo Zombies", CodPresets.MULTIPLIERS, false),
		new ChoiceOption("exo_time", "Game Speed", "Exo Zombies", CodPresets.GAME_SPEED, false));
	private static final List<TextOption> TEXT = List.of(
		new TextOption("message", "Message", "Selected Player", 80, true),
		new TextOption("client_name", "In-Game Name", "Selected Player", 15, true));

	AwAdapter(ConsoleService console)
	{
		super(console, CodGame.AW);
	}

	@Override
	public List<StatGroup> statGroups()
	{
		return STATS;
	}

	@Override
	protected Map<String, Long> onReadStats(String group)
	{
		if (!"multiplayer".equals(group))
		{
			throw new IllegalArgumentException("Unknown statistics group");
		}
		return readDirect(STAT_ADDRESSES);
	}

	@Override
	protected void onWriteStats(String group, Map<String, Long> values)
	{
		if (!"multiplayer".equals(group))
		{
			throw new IllegalArgumentException("Unknown statistics group");
		}
		writeDirect(STAT_ADDRESSES, values);
	}

	@Override
	public int classCount()
	{
		return 10;
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
			names.add(readAscii(STAT_BASE + 19945 + i * 128L, 19));
		}
		return names;
	}

	@Override
	protected void onWriteClassNames(List<String> names)
	{
		for (int i = 0; i < Math.min(classCount(), names.size()); i++)
		{
			writeAscii(STAT_BASE + 19945 + i * 128L, names.get(i), 19);
		}
	}

	@Override
	protected void onUnlockAll()
	{
		write(STAT_BASE + 22336, UNLOCK_DATA);
	}

	@Override
	protected void onDerankSignedInProfile()
	{
		writeIntLittle(STAT_BASE + 13, 0);
		writeIntLittle(STAT_BASE + 169, 0);
	}

	@Override
	public boolean clientNamesReadable()
	{
		return true;
	}

	@Override
	protected List<ClientInfo> onReadClients()
	{
		long base = Integer.toUnsignedLong(readIntBig(CLIENT_POINTER));
		return base == 0 ? List.of() : readClientTable(base, CLIENT_SIZE, 23684, 32);
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
			case "start" -> {
				writeIntBig(GAME_START, 0x60000000L);
				command(COMMAND, "xpartygo");
			}
			case "restart" -> command(COMMAND, "fast_restart");
			case "end" -> command(COMMAND, "cmd endround");
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
		switch (key)
		{
			case "force_host" -> command(COMMAND, enabled
				? "party_connectToOthers 0;partyMigrate_disabled 1;sv_endGameIfISuck 0;badhost_endgameifisuck 0;set allowAllNAT 1"
				: "party_connectToOthers 1;partyMigrate_disabled 0;sv_endGameIfISuck 1;badhost_endgameifisuck 1;set allowAllNAT 0");
			case "max_ammo" -> write(MAX_AMMO, enabled ? MAX_AMMO_ON : MAX_AMMO_OFF);
			case "friction" -> writeByte(PLAYER_STATE + client * PLAYER_STATE_SIZE + 86, enabled ? 1 : 0);
			case "ragdoll_gravity" -> serverCommand(SERVER_COMMAND, -1, "phys_gravity_ragdoll " + (enabled ? 1 : 0));
			default -> throw new IllegalArgumentException("Unknown toggle");
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
			case "jump", "exo_jump" -> writeFloatBig(JUMP, CodPresets.multiply(value, 39));
			case "speed", "exo_speed" -> command(COMMAND, "g_speed " + CodPresets.multiply(value, 190));
			case "time", "exo_time" -> command(COMMAND, "timescale " + CodPresets.percent(value) / 100.0);
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
			case "message" -> serverCommand(SERVER_COMMAND, client, "c \"" + text + "\"");
			case "client_name" -> serverCommand(SERVER_COMMAND, client, "v name \"" + text + "\"");
			default -> throw new IllegalArgumentException("Unknown text option");
		}
	}
}
