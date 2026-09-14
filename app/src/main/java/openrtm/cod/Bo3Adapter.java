package openrtm.cod;

import openrtm.console.ConsoleService;

import java.util.List;
import java.util.Map;

final class Bo3Adapter extends AbstractCodAdapter
{
	private static final long MP_COMMAND = 2187568800L;
	private static final long ZOMBIES_COMMAND = 2187531824L;
	private static final long MP_SERVER_COMMAND = 2187783856L;
	private static final long ZOMBIES_SERVER_COMMAND = 2187745640L;
	private static final long MP_PLAYER_STATE = 2220299808L;
	private static final long ZOMBIES_PLAYER_STATE = 2219925712L;
	private static final long MP_PLAYER_SIZE = 25048L;
	private static final long ZOMBIES_PLAYER_SIZE = 25016L;
	private static final long MP_JUMP = 2181678948L;
	private static final long ZOMBIES_JUMP = 2181678708L;
	private static final long SERVER_ID = 2198239968L;
	private static final byte[] MAX_AMMO = maxAmmoPayload();
	private static final List<String> GAME_MODES = List.of(
		"tdm", "hctdm", "dm", "hcsd", "sd", "hcdem", "dom", "hcdom", "dem", "hckoth",
		"koth", "hcconf", "gun", "ball", "ctf", "sr", "conf", "fr", "oic", "escort");
	private static final List<StatGroup> STATS = List.of(
		new StatGroup("multiplayer", "Multiplayer Statistics", List.of(
			stat("PLEVEL", "Prestige", 11), stat("RANKXP", "Rank XP"), stat("SCORE", "Score"),
			stat("KILLS", "Kills"), stat("DEATHS", "Deaths"), stat("ASSISTS", "Assists"),
			stat("HEADSHOTS", "Headshots"), stat("TEAMKILLS", "Team Kills"), stat("SUICIDES", "Suicides"),
			stat("WINS", "Wins"), stat("LOSSES", "Losses"), stat("TIES", "Ties"),
			stat("CUR_WIN_STREAK", "Current Win Streak"), stat("HITS", "Hits"), stat("MISSES", "Misses"),
			stat("TOTAL_SHOTS", "Total Shots"), stat("TIME_PLAYED_TOTAL", "Time Played (seconds)")), false),
		new StatGroup("zombies", "Zombies Statistics", List.of(
			stat("PLEVEL", "Prestige", 11), stat("RANK", "Rank", 35), stat("RANKXP", "Rank XP"),
			stat("SCORE", "Score"), stat("KILLS", "Kills"), stat("DEATHS", "Deaths"),
			stat("TOTAL_ROUNDS_SURVIVED", "Rounds Survived"), stat("PERKS_DRANK", "Perks Used"),
			stat("TIME_PLAYED_TOTAL", "Time Played (seconds)")), false));
	private static final List<Action> ACTIONS = List.of(
		new Action("start", "Start Match", "Match", false), new Action("restart", "Restart Match", "Match", false),
		new Action("end", "End Match", "Match", false), new Action("leave", "Leave Match", "Match", false),
		new Action("zombies_restart", "Restart Match", "Zombies", false),
		new Action("mp_max_ammo", "Refill Multiplayer Ammo", "Selected Player", true),
		new Action("zombies_max_ammo", "Refill Zombies Ammo", "Selected Player", true));
	private static final List<Toggle> TOGGLES = List.of(
		new Toggle("force_host", "Force Host", "Match", false));
	private static final List<ChoiceOption> CHOICES = List.of(
		new ChoiceOption("mp_jump", "Jump Height", "Host Settings", CodPresets.MULTIPLIERS, false),
		new ChoiceOption("zombies_jump", "Jump Height", "Zombies", CodPresets.MULTIPLIERS, false));
	private static final List<TextOption> TEXT = List.of(
		new TextOption("mp_message", "Multiplayer Message", "Selected Player", 80, true),
		new TextOption("zombies_message", "Zombies Message", "Selected Player", 80, true));

	Bo3Adapter(ConsoleService console)
	{
		super(console, CodGame.BO3);
	}

	@Override
	public List<StatGroup> statGroups()
	{
		return STATS;
	}

	@Override
	protected void onWriteStats(String group, Map<String, Long> values)
	{
		long commandAddress = switch (group)
		{
			case "multiplayer" -> MP_COMMAND;
			case "zombies" -> ZOMBIES_COMMAND;
			default -> throw new IllegalArgumentException("Unknown statistics group");
		};
		values.forEach((key, value) -> {
			requireTitle();
			command(commandAddress, "statsetbyname " + key + " " + value);
		});
		command(commandAddress, "updategamerprofile;uploadstats");
	}

	@Override
	public int classCount()
	{
		return 10;
	}

	@Override
	protected void onWriteClassNames(List<String> names)
	{
		for (int i = 0; i < Math.min(classCount(), names.size()); i++)
		{
			requireTitle();
			String name = cleanText(names.get(i));
			command(MP_COMMAND, "resetLoadoutMPPublic " + i + " class_custom_assault \"" + name + "\"");
			command(MP_COMMAND, "resetLoadoutMPCustom " + i + " class_custom_assault \"" + name + "\"");
		}
		command(MP_COMMAND, "updategamerprofile");
	}

	@Override
	protected void onUnlockAll()
	{
		command(MP_COMMAND, "statwriteddl playerstatslist 28 challengevalue 999;statwriteddl playerstatslist 29 challengevalue 999");
		command(MP_COMMAND, "statwriteddl playerstatslist 30 challengevalue 999;statwriteddl playerstatslist 31 challengevalue 999");
		for (int item = 0; item < 100; item++)
		{
			requireTitle();
			command(MP_COMMAND, "statwriteddl itemstats " + item + " stats headshots challengevalue 999;statwriteddl itemstats " + item + " stats challenge1 challengevalue 999;statwriteddl itemstats " + item + " stats challenge2 challengevalue 999");
			command(MP_COMMAND, "statwriteddl itemstats " + item + " stats challenge3 challengevalue 999;statwriteddl itemstats " + item + " stats challenge4 challengevalue 999;statwriteddl itemstats " + item + " stats challenge5 challengevalue 999");
			command(MP_COMMAND, "statwriteddl itemstats " + item + " stats challenge6 challengevalue 21;statwriteddl itemstats " + item + " stats kills challengevalue 21;statwriteddl itemstats " + item + " stats challenge7 challengevalue 21");
			command(MP_COMMAND, "statwriteddl itemstats " + item + " plevel 2;statwriteddl itemstats " + item + " xp 133337");
			command(MP_COMMAND, "statwriteddl itemstats " + item + " stats kills statvalue 21;statwriteddl itemstats " + item + " stats challenges challengevalue 999");
		}
		command(MP_COMMAND, "statwriteddl playerstatslist 32 challengevalue 999;statwriteddl playerstatslist 33 challengevalue 999;statwriteddl playerstatslist 34 challengevalue 999");
		command(MP_COMMAND, "statwriteddl playerstatslist 35 challengevalue 999;statwriteddl playerstatslist 36 challengevalue 999;statwriteddl playerstatslist 37 challengevalue 999");
		for (int i = 0; i + 1 < CodPayloads.BO3_CHALLENGES.length; i += 2)
		{
			requireTitle();
			command(MP_COMMAND, "statwriteddl playerstatslist " + CodPayloads.BO3_CHALLENGES[i]
				+ " challengevalue 21;statwriteddl playerstatslist " + CodPayloads.BO3_CHALLENGES[i + 1]
				+ " challengevalue 21");
		}
		for (String mode : GAME_MODES)
		{
			requireTitle();
			command(MP_COMMAND, "statwriteddl playerstatsbygametype " + mode + " wins challengevalue 500");
		}
		command(MP_COMMAND, "updategamerprofile;uploadstats");
	}

	@Override
	protected void onDerankSignedInProfile()
	{
		command(MP_COMMAND, "statsetbyname PLEVEL 0;statsetbyname RANK 0;statsetbyname RANKXP 0;updategamerprofile;uploadstats");
	}

	@Override
	public boolean clientNamesReadable()
	{
		return true;
	}

	@Override
	protected List<ClientInfo> onReadClients()
	{
		List<ClientInfo> multiplayer = readClientTable(MP_PLAYER_STATE, MP_PLAYER_SIZE, 23984, 15);
		return multiplayer.isEmpty()
			? readClientTable(ZOMBIES_PLAYER_STATE, ZOMBIES_PLAYER_SIZE, 23984, 15)
			: multiplayer;
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
			case "start" -> command(MP_COMMAND, "party_minplayers 1;xpartygo");
			case "restart" -> command(MP_COMMAND, "fast_restart");
			case "end" -> command(MP_COMMAND, "cmd mr " + readIntBig(SERVER_ID) + " 7 endround");
			case "leave" -> command(MP_COMMAND, "disconnect");
			case "zombies_restart" -> command(ZOMBIES_COMMAND, "fast_restart");
			case "mp_max_ammo" -> write(MP_PLAYER_STATE + client * MP_PLAYER_SIZE + 1340, MAX_AMMO);
			case "zombies_max_ammo" -> write(ZOMBIES_PLAYER_STATE + client * ZOMBIES_PLAYER_SIZE + 1340, MAX_AMMO);
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
		if (!"force_host".equals(key))
		{
			throw new IllegalArgumentException("Unknown toggle");
		}
		command(MP_COMMAND, enabled
			? "party_connectToOthers 0;partyMigrate_disabled 1;party_minplayers 1;set allowAllNAT 1"
			: "party_connectToOthers 1;partyMigrate_disabled 0;set allowAllNAT 0");
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
			case "mp_jump" -> writeFloatBig(MP_JUMP, CodPresets.multiply(value, 39));
			case "zombies_jump" -> writeFloatBig(ZOMBIES_JUMP, CodPresets.multiply(value, 39));
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
			case "mp_message" -> serverCommand(MP_SERVER_COMMAND, client, "c \"" + text + "\"");
			case "zombies_message" -> serverCommand(ZOMBIES_SERVER_COMMAND, client, "c \"" + text + "\"");
			default -> throw new IllegalArgumentException("Unknown text option");
		}
	}

	private static byte[] maxAmmoPayload()
	{
		byte[] data = new byte[72];
		for (int i = 0; i < data.length; i += 4)
		{
			data[i + 1] = (byte) 0xFF;
			data[i + 2] = (byte) 0xFF;
			data[i + 3] = (byte) 0xFF;
		}
		return data;
	}
}
