package openrtm.cod;

import openrtm.console.ConsoleService;

import java.util.List;
import java.util.Map;

final class Bo1Adapter extends AbstractCodAdapter
{
	private static final long COMMAND = 2184440024L;
	private static final long ZOMBIES_COMMAND = 2184248664L;
	private static final long SERVER_COMMAND = 2184597016L;
	private static final long STAT_BASE = 2215139104L;
	private static final long SERVER_ID = 2191255076L;
	private static final long FORCE_HOST_ONE = 2186137092L;
	private static final long FORCE_HOST_TWO = 2183550324L;
	private static final long ZOMBIES_MAX_AMMO = 2182364812L;
	private static final long ENTITY = 2196628800L;
	private static final long ENTITY_SIZE = 760L;
	private static final long[] ZOMBIES_POINTS = {2195469236L, 2195476700L, 2195484164L, 2195491628L};
	private static final byte[] FORCE_HOST_ONE_ON = {0x48, 0, 0, (byte) 0x8C};
	private static final byte[] FORCE_HOST_TWO_ON = {0x60, 0, 0, 0};
	private static final byte[] FORCE_HOST_ONE_OFF = {0x40, (byte) 0x99, 0, (byte) 0x8C};
	private static final byte[] FORCE_HOST_TWO_OFF = {0x40, (byte) 0x9A, 1, 0x38};
	private static final byte[] MAX_AMMO_ON = {0x60, 0, 0, 0};
	private static final byte[] MAX_AMMO_OFF = {(byte) 0x90, (byte) 0xE3, 0, 4};
	private static final byte[] UNLOCK_79 = filled(79, 0xFF);
	private static final byte[] UNLOCK_187 = filled(187, 0xFF);
	private static final byte[] UNLOCK_3212 = filled(3212, 0xFF);
	private static final long[] WEAPON_UNLOCKS = {
		2215148128L, 2215148380L, 2215148967L, 2215149051L, 2215149303L, 2215149135L,
		2215149386L, 2215149470L, 2215149554L, 2215149638L, 2215149647L, 2215150141L,
		2215150225L, 2215150309L, 2215150393L, 2215150477L, 2215150561L, 2215150645L,
		2215150728L, 2215150812L, 2215151148L, 2215151232L, 2215151316L, 2215151567L,
		2215151651L, 2215151735L, 2215151987L, 2215151903L, 2215152154L, 2215152490L,
		2215152574L, 2215152658L, 2215152741L, 2215153161L, 2215153329L, 2215153412L,
		2215153664L, 2215153748L, 2215153832L, 2215153916L, 2215154083L, 2215154167L,
		2215154251L, 2215154335L, 2215154419L, 2215154503L
	};
	private static final Map<String, Long> STAT_ADDRESSES = addresses(
		"prestige", STAT_BASE + 37085, "xp", STAT_BASE + 37093, "score", STAT_BASE + 37113,
		"kills", STAT_BASE + 36393, "deaths", STAT_BASE + 36085, "assists", STAT_BASE + 35985,
		"headshots", STAT_BASE + 36365, "wins", STAT_BASE + 37213, "losses", STAT_BASE + 36573,
		"cod_points", STAT_BASE + 36049, "time", STAT_BASE + 37157);
	private static final List<StatGroup> STATS = List.of(new StatGroup("multiplayer", "Multiplayer Statistics", List.of(
		stat("prestige", "Prestige", 15), stat("xp", "XP"), stat("score", "Score"),
		stat("kills", "Kills"), stat("deaths", "Deaths"), stat("assists", "Assists"),
		stat("headshots", "Headshots"), stat("wins", "Wins"), stat("losses", "Losses"),
		stat("cod_points", "COD Points"), stat("time", "Time Played (seconds)")), true));
	private static final List<Action> ACTIONS = List.of(
		new Action("start", "Start Match", "Match", false), new Action("restart", "Restart Match", "Match", false),
		new Action("end", "End Match", "Match", false), new Action("leave", "Leave Match", "Match", false),
		new Action("next_round", "Skip Current Round", "Zombies", false));
	private static final List<Toggle> TOGGLES = List.of(
		new Toggle("force_host", "Force Host", "Match", false),
		new Toggle("zombies_force_host", "Force Host", "Zombies", false),
		new Toggle("zombies_max_ammo", "Unlimited Ammo", "Zombies", false));
	private static final List<NumberOption> NUMBERS = List.of(
		new NumberOption("zombies_points", "Points", "Zombies", 0, Integer.MAX_VALUE, 50000, true),
		new NumberOption("zombies_round", "Round", "Zombies", 1, 255, 1, false));
	private static final List<TextOption> TEXT = List.of(
		new TextOption("message", "Message", "Selected Player", 80, true));

	Bo1Adapter(ConsoleService console)
	{
		super(console, CodGame.BO1);
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
		return 10;
	}

	@Override
	protected void onWriteClassNames(List<String> names)
	{
		for (int i = 0; i < Math.min(names.size(), classCount()); i++)
		{
			command(COMMAND, "customclass" + (i + 1) + " \"" + cleanText(names.get(i)) + "\"");
		}
		command(COMMAND, "updategamerprofile");
	}

	@Override
	protected void onUnlockAll()
	{
		writeIntBig(2215141031L, 4294967055L);
		write(2215145697L, UNLOCK_79);
		write(2215145793L, UNLOCK_187);
		writeByte(2215146178L, 192);
		for (long address : WEAPON_UNLOCKS)
		{
			requireTitle();
			writeByte(address, 0xFF);
		}
		write(2215156935L, UNLOCK_3212);
	}

	@Override
	protected void onDerankSignedInProfile()
	{
		writeByte(STAT_BASE + 37085, 0);
		write(STAT_BASE + 37093, new byte[3]);
	}

	@Override
	public boolean clientNamesReadable()
	{
		return true;
	}

	@Override
	protected List<ClientInfo> onReadClients()
	{
		return readPointerClientTable(ENTITY, ENTITY_SIZE, 324, 10232, 15);
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
			case "end" -> command(COMMAND, "cmd mr " + Integer.toUnsignedLong(readIntBig(SERVER_ID)) + " -1 endround");
			case "leave" -> command(COMMAND, "disconnect");
			case "next_round" -> command(ZOMBIES_COMMAND, "ai axis delete");
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
			case "force_host" -> {
				write(FORCE_HOST_ONE, enabled ? FORCE_HOST_ONE_ON : FORCE_HOST_ONE_OFF);
				write(FORCE_HOST_TWO, enabled ? FORCE_HOST_TWO_ON : FORCE_HOST_TWO_OFF);
			}
			case "zombies_force_host" -> command(ZOMBIES_COMMAND, enabled
				? "party_connectTimeout 1;party_hostmigration 0"
				: "party_connectTimeout 0;party_hostmigration 1");
			case "zombies_max_ammo" -> write(ZOMBIES_MAX_AMMO, enabled ? MAX_AMMO_ON : MAX_AMMO_OFF);
			default -> throw new IllegalArgumentException("Unknown toggle");
		}
	}

	@Override
	public List<NumberOption> numberOptions()
	{
		return NUMBERS;
	}

	@Override
	protected void onSetNumber(String key, long value, int client)
	{
		if ("zombies_points".equals(key))
		{
			if (client > 3)
			{
				throw new IllegalArgumentException("Zombies client slots are 0 through 3");
			}
			writeIntBig(ZOMBIES_POINTS[client], value);
		}
		else if ("zombies_round".equals(key))
		{
			command(ZOMBIES_COMMAND, "scr_zombie_round " + value + ";ai axis delete");
		}
		else
		{
			throw new IllegalArgumentException("Unknown numeric option");
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
}
