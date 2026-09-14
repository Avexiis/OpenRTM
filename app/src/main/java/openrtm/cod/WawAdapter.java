package openrtm.cod;

import openrtm.console.ConsoleService;

import java.util.List;
import java.util.Map;

final class WawAdapter extends AbstractCodAdapter
{
	private static final long COMMAND = 0x8226FF08L;
	private static final long ZOMBIES_COMMAND = 0x8224D8E0L;
	private static final long SERVER_COMMAND = 0x82285FA8L;
	private static final long ZOMBIES_SERVER_COMMAND = 0x8225CB70L;
	private static final long DVAR_GET_INT = 0x822BE158L;
	private static final long CLIENT_STATE = 2194733592L;
	private static final long CLIENT_STATE_SIZE = 15468L;
	private static final String UNLOCK_COMMAND = "exec mp/unlock_menu.cfg;exec mp/unlock_allperks.cfg;exec mp/unlock_allweapon.cfg;exec mp/unlock_challenges.cfg;exec mp/unlock_init.cfg;updategamerprofile";
	private static final Map<String, Integer> STAT_IDS = Map.of(
		"prestige", 2326, "xp", 2301, "score", 2302, "kills", 2303,
		"deaths", 2305, "killstreak", 2304, "headshots", 2308, "time", 2310);
	private static final List<StatGroup> STATS = List.of(new StatGroup("multiplayer", "Multiplayer Statistics", List.of(
		stat("prestige", "Prestige", 10), stat("xp", "XP"), stat("score", "Score"),
		stat("kills", "Kills"), stat("deaths", "Deaths"), stat("killstreak", "Best Killstreak"),
		stat("headshots", "Headshots"), stat("time", "Time Played (seconds)")), false));
	private static final List<Action> ACTIONS = List.of(
		new Action("start", "Start Match", "Match", false), new Action("restart", "Restart Match", "Match", false),
		new Action("end", "End Match", "Match", false), new Action("leave", "Leave Match", "Match", false),
		new Action("round_next", "Next Round", "Zombies", false), new Action("round_previous", "Previous Round", "Zombies", false),
		new Action("power", "Turn On Power", "Zombies", false), new Action("double_points", "Double Points", "Zombies", false),
		new Action("revive", "Revive All", "Zombies", false), new Action("perks", "Give All Perks", "Zombies", true),
		new Action("points", "Give Points", "Zombies", true));
	private static final List<Toggle> TOGGLES = List.of(
		new Toggle("force_host", "Force Host", "Match", false), new Toggle("max_ammo", "Unlimited Ammo", "Host Settings", false));
	private static final List<NumberOption> NUMBERS = List.of(
		new NumberOption("round", "Round", "Zombies", 1, 255, 1, false),
		new NumberOption("zombies_prestige", "Prestige", "Zombies", 0, 10, 0, true));
	private static final List<ChoiceOption> CHOICES = List.of(
		new ChoiceOption("jump", "Jump Height", "Host Settings", CodPresets.MULTIPLIERS, false),
		new ChoiceOption("gravity", "Gravity", "Host Settings", CodPresets.GRAVITY, false),
		new ChoiceOption("time", "Game Speed", "Host Settings", CodPresets.GAME_SPEED, false),
		new ChoiceOption("vision", "Vision", "Host Settings", List.of("Default", "Chrome", "Cartoon", "Rainbow", "No Fog"), false));
	private static final List<TextOption> TEXT = List.of(
		new TextOption("message", "Message", "Selected Player", 80, true),
		new TextOption("zombies_name", "Zombies Name", "Zombies", 15, true));

	WawAdapter(ConsoleService console)
	{
		super(console, CodGame.WAW);
	}

	@Override
	public List<StatGroup> statGroups()
	{
		return STATS;
	}

	@Override
	protected void onWriteStats(String group, Map<String, Long> values)
	{
		StringBuilder output = new StringBuilder();
		STAT_IDS.forEach((key, id) -> {
			if (values.containsKey(key))
			{
				output.append("statset ").append(id).append(' ').append(values.get(key)).append(';');
			}
		});
		output.append("updategamerprofile");
		command(COMMAND, output.toString());
	}

	@Override
	public int classCount()
	{
		return 5;
	}

	@Override
	protected void onWriteClassNames(List<String> names)
	{
		for (int i = 0; i < Math.min(classCount(), names.size()); i++)
		{
			command(COMMAND, "prestigeclass" + (i + 1) + " \"" + cleanText(names.get(i)) + "\"");
		}
		command(COMMAND, "updategamerprofile");
	}

	@Override
	protected void onUnlockAll()
	{
		command(COMMAND, UNLOCK_COMMAND);
	}

	@Override
	protected void onDerankSignedInProfile()
	{
		command(COMMAND, "resetstats;statset 2326 0;statset 252 1;updategamerprofile");
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
			case "start" -> command(COMMAND, "xpartygo");
			case "restart" -> command(COMMAND, "fast_restart");
			case "end" -> command(COMMAND, "cmd mr " + call(DVAR_GET_INT, "sv_serverid") + " -1 endround");
			case "leave" -> command(COMMAND, "disconnect");
			case "round_next" -> command(ZOMBIES_COMMAND, "developeruser 1;developer 1;ai axis delete;zombie_devgui round_next");
			case "round_previous" -> command(ZOMBIES_COMMAND, "developeruser 1;developer 1;ai axis delete;zombie_devgui round_Prev");
			case "power" -> command(ZOMBIES_COMMAND, "developeruser 1;developer 1;zombie_devgui power");
			case "double_points" -> command(ZOMBIES_COMMAND, "zombie_devgui double_points");
			case "revive" -> command(ZOMBIES_COMMAND, "zombie_devgui revive_all");
			case "perks" -> {
				serverCommand(ZOMBIES_SERVER_COMMAND, client, "v zombie_devgui specialty_quickrevive");
				serverCommand(ZOMBIES_SERVER_COMMAND, client, "v zombie_devgui specialty_fastreload");
				serverCommand(ZOMBIES_SERVER_COMMAND, client, "v zombie_devgui specialty_rof");
				serverCommand(ZOMBIES_SERVER_COMMAND, client, "v zombie_devgui specialty_armorvest");
			}
			case "points" -> serverCommand(ZOMBIES_SERVER_COMMAND, client, "v zombie_devgui money");
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
	public List<NumberOption> numberOptions()
	{
		return NUMBERS;
	}

	@Override
	public boolean clientNamesReadable()
	{
		return true;
	}

	@Override
	protected List<ClientInfo> onReadClients()
	{
		return readClientTable(CLIENT_STATE, CLIENT_STATE_SIZE, 14904, 15);
	}

	@Override
	protected void onSetNumber(String key, long value, int client)
	{
		switch (key)
		{
			case "round" -> command(ZOMBIES_COMMAND, "ai axis delete;scr_zombie_round " + value + ";zombie_devgui round");
			case "zombies_prestige" -> serverCommand(ZOMBIES_SERVER_COMMAND, client, "v statset 2326 " + value + ";statset 65 55;statset 2302 999999");
			default -> throw new IllegalArgumentException("Unknown numeric option");
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
		if ("jump".equals(key))
		{
			command(COMMAND, "jump_height " + CodPresets.multiply(value, 39));
			return;
		}
		if ("gravity".equals(key))
		{
			command(COMMAND, "g_gravity " + CodPresets.percentage(value, 800));
			return;
		}
		if ("time".equals(key))
		{
			command(COMMAND, "timescale " + CodPresets.percent(value) / 100.0);
			return;
		}
		String setting = switch (value)
		{
			case "Chrome" -> "r_specularmap 2";
			case "Cartoon" -> "r_fullbright 1";
			case "Rainbow" -> "r_debugShader 1";
			case "No Fog" -> "r_fog 0;scr_fog_disable 1";
			default -> "r_specularmap 1;r_fullbright 0;r_debugShader 0;r_fog 1;scr_fog_disable 0";
		};
		command(COMMAND, setting);
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
		else if ("zombies_name".equals(key))
		{
			serverCommand(ZOMBIES_SERVER_COMMAND, client, "v name \"" + text + "\"");
		}
		else
		{
			throw new IllegalArgumentException("Unknown text option");
		}
	}
}
