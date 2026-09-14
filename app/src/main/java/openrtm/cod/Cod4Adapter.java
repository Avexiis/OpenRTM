package openrtm.cod;

import openrtm.console.ConsoleService;

import java.util.List;
import java.util.Map;

final class Cod4Adapter extends AbstractCodAdapter
{
	private static final long COMMAND = 0x82224910L;
	private static final long SERVER_COMMAND = 0x821F5B30L;
	private static final long DVAR_GET_INT = 0x821C7A30L;
	private static final long CLIENT_STATE = 2191248752L;
	private static final long CLIENT_STATE_SIZE = 12724L;
	private static final String UNLOCK_COMMAND = "statset 3000 9 statset 3001 9 statset 3002 9 statset 3003 1 statset 3004 1 statset 3010 7951 statset 3011 7951 statset 3012 16143 statset 3013 7951 statset 3014 7951 statset 3020 16175 statset 3021 7983 statset 3022 7937 statset 3023 7983 statset 3024 7983 statset 3025 7983 statset 3026 7983 statset 3060 16131 statset 3061 7939 statset 3062 7939 statset 3064 7939 statset 3065 7939 statset 3070 16149 statset 3071 7957 statset 3080 7959 statset 3081 7959 statset 3082 16151 uploadstats";
	private static final Map<String, Integer> STAT_IDS = Map.ofEntries(
		Map.entry("prestige", 2326), Map.entry("rank", 2350), Map.entry("xp", 2301),
		Map.entry("score", 2302), Map.entry("kills", 2303), Map.entry("deaths", 2305),
		Map.entry("wins", 2316), Map.entry("losses", 2317), Map.entry("headshots", 2308),
		Map.entry("assists", 2307), Map.entry("hits", 2322), Map.entry("misses", 2323),
		Map.entry("killstreak", 2304), Map.entry("winstreak", 2319));
	private static final List<StatGroup> STATS = List.of(new StatGroup("multiplayer", "Multiplayer Statistics", List.of(
		stat("prestige", "Prestige", 10), stat("rank", "Rank", 55), stat("xp", "XP"),
		stat("score", "Score"), stat("kills", "Kills"), stat("deaths", "Deaths"),
		stat("wins", "Wins"), stat("losses", "Losses"), stat("headshots", "Headshots"),
		stat("assists", "Assists"), stat("hits", "Hits"), stat("misses", "Misses"),
		stat("killstreak", "Best Killstreak"), stat("winstreak", "Best Win Streak")), false));
	private static final List<Action> ACTIONS = List.of(
		new Action("start", "Start Match", "Match", false),
		new Action("restart", "Restart Match", "Match", false),
		new Action("end", "End Match", "Match", false),
		new Action("leave", "Leave Match", "Match", false));
	private static final List<Toggle> TOGGLES = List.of(
		new Toggle("force_host", "Force Host", "Match", false),
		new Toggle("max_ammo", "Unlimited Ammo", "Host Settings", false));
	private static final List<ChoiceOption> CHOICES = List.of(
		new ChoiceOption("jump", "Jump Height", "Host Settings", CodPresets.MULTIPLIERS, false),
		new ChoiceOption("gravity", "Gravity", "Host Settings", CodPresets.GRAVITY, false),
		new ChoiceOption("time", "Game Speed", "Host Settings", CodPresets.GAME_SPEED, false),
		new ChoiceOption("vision", "Vision", "Host Settings", List.of("Default", "Chrome", "Cartoon", "Rainbow", "No Fog"), false),
		new ChoiceOption("team", "Team", "Selected Player", List.of("Allies", "Axis", "Spectator"), true));
	private static final List<TextOption> TEXT = List.of(
		new TextOption("clan", "Clan Tag", "Profile", 4, false),
		new TextOption("client_name", "In-Game Name", "Selected Player", 15, true),
		new TextOption("message", "Message", "Selected Player", 80, true));

	Cod4Adapter(ConsoleService console)
	{
		super(console, CodGame.COD4);
	}

	@Override
	public List<StatGroup> statGroups()
	{
		return STATS;
	}

	@Override
	protected void onWriteStats(String group, Map<String, Long> values)
	{
		if (!"multiplayer".equals(group))
		{
			throw new IllegalArgumentException("Unknown statistics group");
		}
		StringBuilder command = new StringBuilder();
		STAT_IDS.forEach((key, id) -> {
			Long value = values.get(key);
			if (value != null)
			{
				command.append("statset ").append(id).append(' ').append(value).append(';');
			}
		});
		command.append("uploadstats");
		command(COMMAND, command.toString());
	}

	@Override
	protected void onUnlockAll()
	{
		command(COMMAND, UNLOCK_COMMAND);
	}

	@Override
	protected void onDerankSignedInProfile()
	{
		command(COMMAND, "resetstats;updategamerprofile;uploadstats");
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
			default -> throw new IllegalArgumentException("Unknown match action");
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
				? "party_connectToOthers 0;partyMigrate_disabled 1;party_minplayers 1;set allowAllNAT 1"
				: "party_connectToOthers 1;partyMigrate_disabled 0;set allowAllNAT 0");
			case "max_ammo" -> command(COMMAND, "player_sustainAmmo " + (enabled ? 1 : 0));
			default -> throw new IllegalArgumentException("Unknown toggle");
		}
	}

	@Override
	public boolean clientNamesReadable()
	{
		return true;
	}

	@Override
	protected List<ClientInfo> onReadClients()
	{
		return readClientTable(CLIENT_STATE, CLIENT_STATE_SIZE, 12380, 15);
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
		if ("team".equals(key))
		{
			serverCommand(SERVER_COMMAND, client, "v team " + value.toLowerCase());
			return;
		}
		if (!"vision".equals(key))
		{
			throw new IllegalArgumentException("Unknown choice");
		}
		String setting = switch (value)
		{
			case "Chrome" -> "r_specularmap 2";
			case "Cartoon" -> "r_fullbright 1";
			case "Rainbow" -> "r_debugShader 1";
			case "No Fog" -> "r_fog 0;scr_fog_disable 1";
			default -> "scr_art_tweak 0;r_glowUseTweaks 0;r_filmUseTweaks 0;r_specularmap 1;r_fullbright 0;r_debugShader 0;r_fog 1;scr_fog_disable 0";
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
		switch (key)
		{
			case "clan" -> command(COMMAND, "clanName " + text + ";updategamerprofile");
			case "client_name" -> serverCommand(SERVER_COMMAND, client, "v name \"" + text + "\"");
			case "message" -> serverCommand(SERVER_COMMAND, client, "c \"" + text + "\"");
			default -> throw new IllegalArgumentException("Unknown text option");
		}
	}
}
