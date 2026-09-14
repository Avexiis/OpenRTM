package openrtm.cod;

import openrtm.console.ConsoleService;

import java.util.List;
import java.util.Map;

final class Bo2Adapter extends AbstractCodAdapter
{
	private static final long COMMAND = 2185237984L;
	private static final long SERVER_COMMAND = 2185427824L;
	private static final long STAT_BASE = 2218035656L;
	private static final long UNLOCK_ADDRESS = 2218045086L;
	private static final long CLIENT_STATE = 0xC403C3A8L;
	private static final long CLIENT_STATE_SIZE = 328L;
	private static final long[] PRIMARY_WEAPONS = {
		2218080842L, 2218080894L, 2218080947L, 2218080999L, 2218081052L,
		2218081104L, 2218081157L, 2218081209L, 2218081262L, 2218081314L
	};
	private static final byte[] UNLOCK_HEADER = filled(196, 0xFF);
	private static final byte[] ZOMBIES_RANK_PROGRESS = zombiesRankProgress();
	private static final Map<String, Long> STAT_ADDRESSES = addresses(
		"prestige", 2218037668L, "tokens", 2218037692L, "score", 2218037728L,
		"kills", 2218036480L, "deaths", 2218035922L, "wins", 2218037986L,
		"losses", 2218036594L, "xp", 2218037686L, "time", 2218037914L,
		"headshots", 2218036180L, "assists", 2218037224L);
	private static final Map<String, Long> ZOMBIES_ADDRESSES = addresses(
		"kills", STAT_BASE + 212, "deaths", STAT_BASE + 272, "downs", STAT_BASE + 252,
		"hits", STAT_BASE + 268, "perks", STAT_BASE + 264, "revives", STAT_BASE + 544,
		"gibs", STAT_BASE + 228, "xp", STAT_BASE + 568, "time", STAT_BASE + 292);
	private static final List<StatGroup> STATS = List.of(
		new StatGroup("multiplayer", "Multiplayer Statistics", List.of(
			stat("prestige", "Prestige", 15), stat("xp", "XP"), stat("tokens", "Unlock Tokens"),
			stat("score", "Score"), stat("kills", "Kills"), stat("deaths", "Deaths"),
			stat("assists", "Assists"), stat("headshots", "Headshots"), stat("wins", "Wins"),
			stat("losses", "Losses"), stat("time", "Time Played (seconds)")), true),
		new StatGroup("zombies", "Zombies Statistics", List.of(
			stat("kills", "Kills"), stat("deaths", "Deaths"), stat("downs", "Downs"),
			stat("revives", "Revives"), stat("hits", "Hits"), stat("perks", "Perks Used"),
			stat("gibs", "Gibs"), stat("xp", "XP"), stat("time", "Time Played (seconds)")), true));
	private static final List<Action> ACTIONS = List.of(
		new Action("start", "Start Match", "Match", false), new Action("restart", "Restart Match", "Match", false),
		new Action("end", "End Match", "Match", false), new Action("leave", "Leave Match", "Match", false));
	private static final List<Toggle> TOGGLES = List.of(
		new Toggle("force_host", "Force Host", "Match", false), new Toggle("max_ammo", "Unlimited Ammo", "Host Settings", false));
	private static final List<ChoiceOption> CHOICES = List.of(
		new ChoiceOption("camo", "Primary Weapon Camouflage", "Classes", List.of(
			"None", "DEVGRU", "A-TACS AU", "ERDL", "Siberia", "Choco", "Blue Tiger", "Bloodshot",
			"Ghostex: Delta 6", "Kryptek: Typhon", "Carbon Fiber", "Cherry Blossom", "Art of War", "Ronin",
			"Skulls", "Gold", "Diamond"), false),
		new ChoiceOption("zombies_rank", "Rank Emblem", "Zombies", List.of(
			"Bones", "Crossed Bones", "Skull", "Skull with Knife", "Shotguns"), false));
	private static final List<TextOption> TEXT = List.of(
		new TextOption("message", "Message", "Selected Player", 80, true));

	Bo2Adapter(ConsoleService console)
	{
		super(console, CodGame.BO2);
	}

	@Override
	public List<StatGroup> statGroups()
	{
		return STATS;
	}

	@Override
	protected Map<String, Long> onReadStats(String group)
	{
		return readDirect("zombies".equals(group) ? ZOMBIES_ADDRESSES : STAT_ADDRESSES);
	}

	@Override
	protected void onWriteStats(String group, Map<String, Long> values)
	{
		writeDirect("zombies".equals(group) ? ZOMBIES_ADDRESSES : STAT_ADDRESSES, values);
		command(COMMAND, "updategamerprofile;uploadstats");
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
			command(COMMAND, "setStatFromLocString cacLoadouts customClassName " + i + " " + cleanText(names.get(i)));
		}
		command(COMMAND, "updategamerprofile");
	}

	@Override
	protected void onUnlockAll()
	{
		write(2218082975L, UNLOCK_HEADER);
		writeByte(2218083244L, 0xFF);
		writeByte(2218083241L, 0xFF);
		requireTitle();
		write(UNLOCK_ADDRESS, CodPayloads.BO2_UNLOCK);
	}

	@Override
	protected void onDerankSignedInProfile()
	{
		writeIntLittle(2218037668L, 0);
		writeIntLittle(2218037686L, 0);
		writeIntLittle(2218037692L, 0);
		command(COMMAND, "updategamerprofile;uploadstats");
	}

	@Override
	public boolean clientNamesReadable()
	{
		return true;
	}

	@Override
	protected List<ClientInfo> onReadClients()
	{
		return readClientTable(CLIENT_STATE, CLIENT_STATE_SIZE, 0, 15);
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
		if ("camo".equals(key))
		{
			int camo = CHOICES.get(0).choices().indexOf(value);
			for (long primary : PRIMARY_WEAPONS)
			{
				writeByte(primary + 6, camo);
			}
		}
		else if ("zombies_rank".equals(key))
		{
			write(STAT_BASE + 591, ZOMBIES_RANK_PROGRESS);
			byte[] emblem = switch (value)
			{
				case "Crossed Bones" -> new byte[]{0, 0x20, 0, 0};
				case "Skull" -> new byte[]{0, (byte) 0x80, 0, 0};
				case "Skull with Knife" -> new byte[]{0, 0x10, 1, 0};
				case "Shotguns" -> new byte[]{0, 0, 0, 0x5F};
				default -> new byte[4];
			};
			write(STAT_BASE + 92, emblem);
		}
		else
		{
			throw new IllegalArgumentException("Unknown choice");
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
		serverCommand(SERVER_COMMAND, client, "c \"" + cleanText(value) + "\"");
	}

	private static byte[] zombiesRankProgress()
	{
		byte[] result = new byte[32];
		for (int i = 0; i < result.length; i += 4)
		{
			result[i] = 0x7F;
			result[i + 1] = (byte) 0xFF;
			result[i + 2] = (byte) 0xFF;
			result[i + 3] = (byte) 0xFF;
		}
		return result;
	}
}
