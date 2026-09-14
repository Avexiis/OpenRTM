package openrtm.cod;

import openrtm.console.ConsoleService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class GhostsAdapter extends AbstractCodAdapter
{
	private static final long COMMAND = 2185545528L;
	private static final long SERVER_COMMAND = 2186122728L;
	private static final long STAT_BASE = 2203597004L;
	private static final long JUMP = 2181130408L;
	private static final long FALL_DAMAGE = 2183335380L;
	private static final long GRAVITY = 2185075102L;
	private static final long SPEED = 2185077458L;
	private static final long FRICTION = 2181145952L;
	private static final long CLIENT_STATE = 2200967552L;
	private static final long CLIENT_STATE_SIZE = 14080L;
	private static final byte[] UNLOCK_DATA = filled(855, 0xFF);
	private static final Map<String, Long> STAT_ADDRESSES = addresses(
		"deaths", STAT_BASE + 93, "games", STAT_BASE + 117, "hits", STAT_BASE + 125,
		"kills", STAT_BASE + 133, "killstreak", STAT_BASE + 137, "losses", STAT_BASE + 141,
		"misses", STAT_BASE + 146, "xp", STAT_BASE + 154, "score", STAT_BASE + 174,
		"time", STAT_BASE + 202, "wins", STAT_BASE + 214, "squad_points", STAT_BASE + 19504,
		"prestige", STAT_BASE + 20264);
	private static final Map<String, Long> EXTINCTION_ADDRESSES = addresses(
		"prestige", STAT_BASE + 30502, "revives", STAT_BASE + 30486, "teeth", STAT_BASE + 33522,
		"kills", STAT_BASE + 30518, "score", STAT_BASE + 30494);
	private static final List<StatGroup> STATS = List.of(
		new StatGroup("multiplayer", "Multiplayer Statistics", List.of(
			stat("prestige", "Prestige", 10), stat("xp", "Rank XP"), stat("squad_points", "Squad Points"),
			stat("score", "Score"), stat("kills", "Kills"), stat("deaths", "Deaths"),
			stat("killstreak", "Best Killstreak"), stat("hits", "Hits"), stat("misses", "Misses"),
			stat("wins", "Wins"), stat("losses", "Losses"), stat("games", "Games Played"),
			stat("time", "Time Played (seconds)")), true),
		new StatGroup("extinction", "Extinction Statistics", List.of(
			stat("prestige", "Prestige", 25), stat("teeth", "Teeth"), stat("revives", "Revives"),
			stat("kills", "Kills"), stat("score", "Score")), true));
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
		new ChoiceOption("time", "Game Speed", "Host Settings", CodPresets.GAME_SPEED, false));
	private static final List<TextOption> TEXT = List.of(
		new TextOption("message", "Message", "Selected Player", 80, true),
		new TextOption("client_name", "In-Game Name", "Selected Player", 15, true));

	GhostsAdapter(ConsoleService console)
	{
		super(console, CodGame.GHOSTS);
	}

	@Override
	public List<StatGroup> statGroups()
	{
		return STATS;
	}

	@Override
	protected Map<String, Long> onReadStats(String group)
	{
		return readDirect("extinction".equals(group) ? EXTINCTION_ADDRESSES : STAT_ADDRESSES);
	}

	@Override
	protected void onWriteStats(String group, Map<String, Long> values)
	{
		writeDirect("extinction".equals(group) ? EXTINCTION_ADDRESSES : STAT_ADDRESSES, values);
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
			names.add(readAscii(STAT_BASE + 4037 + i * 1380L, 32));
		}
		return names;
	}

	@Override
	protected void onWriteClassNames(List<String> names)
	{
		for (int i = 0; i < Math.min(classCount(), names.size()); i++)
		{
			writeAscii(STAT_BASE + 4037 + i * 1380L, names.get(i), 32);
		}
	}

	@Override
	protected void onUnlockAll()
	{
		write(STAT_BASE + 17124, UNLOCK_DATA);
		for (int i = 0; i < 10; i++)
		{
			requireTitle();
			writeByte(STAT_BASE + 4072 + i * 1380L, 0xFF);
		}
	}

	@Override
	protected void onDerankSignedInProfile()
	{
		writeIntLittle(STAT_BASE + 154, 0);
		writeIntLittle(STAT_BASE + 20264, 0);
	}

	@Override
	public boolean clientNamesReadable()
	{
		return true;
	}

	@Override
	public int maximumClients()
	{
		return 12;
	}

	@Override
	protected List<ClientInfo> onReadClients()
	{
		return readClientTable(CLIENT_STATE, CLIENT_STATE_SIZE, 12316, 32);
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
			case "start" -> command(COMMAND, "party_minplayers 1;xpartygo");
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
		if ("force_host".equals(key))
		{
			command(COMMAND, enabled
				? "party_connectToOthers 0;partyMigrate_disabled 1;sv_endGameIfISuck 0;badhost_endgameifisuck 0;set allowAllNAT 1"
				: "party_connectToOthers 1;partyMigrate_disabled 0;sv_endGameIfISuck 1;badhost_endgameifisuck 1;set allowAllNAT 0");
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
}
