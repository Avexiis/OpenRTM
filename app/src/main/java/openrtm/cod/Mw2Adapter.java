package openrtm.cod;

import openrtm.cod.gsc.Iw4GscCompiler;
import openrtm.cod.gsc.Iw4GscProgram;
import openrtm.console.ConsoleService;
import openrtm.console.ConsoleService.TransferProgress;
import openrtm.console.DebuggerService;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
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
	private static final long SCRIPT_IMAGE = 0x82500500L;
	private static final long SCRIPT_POINTER = 0x823D2DE8L;
	private static final long SCRIPT_LOCK = 0x838685ACL;
	private static final long SCRIPT_LOAD_HOOK = 0x82249D78L;
	private static final long SCRIPT_HOOK_CAVE = 0x823C76D0L;
	private static final long SCRIPT_HOOK_STATE = 0x823C7900L;
	private static final long SCRIPT_RESUME_PATCH = 0x8224FE64L;
	private static final long SCRIPT_REDIRECT_START = 0x82241AF0L;
	private static final int SCRIPT_POOL_SIZE = 0x38008;
	private static final int SCRIPT_ALLOCATION_SIZE = 0x40000;
	private static final int SCRIPT_REDIRECT_LENGTH = 0x1164;
	private static final int STOCK_SCRIPT_POINTER = 0x823A3510;
	private static final int SCRIPT_HIGH = 0x823D;
	private static final int SCRIPT_LOW = 0x2DE8;
	private static final long[] SCRIPT_HIGH_PATCHES = {
		0x82241AF2L, 0x82241B32L, 0x82241EB6L, 0x8224244EL, 0x82242762L, 0x82242B6AL
	};
	private static final long[] SCRIPT_LOW_PATCHES = {
		0x82241AF6L, 0x82241B42L, 0x82241BE2L, 0x82241ECAL, 0x82241F72L,
		0x8224202AL, 0x822420FAL, 0x8224212AL, 0x82242182L, 0x8224245EL,
		0x82242486L, 0x8224276EL, 0x82242B7EL, 0x82242C52L
	};
	private static final byte[] SCRIPT_LOAD_HOOK_STOCK = HexFormat.of().parseHex(
		"3D60835B394B7C58906A00104E800020");
	private static final byte[] SCRIPT_HOOK_HANDLER = HexFormat.of().parseHex(
		"7D8802A69181FFF8FBA1FFE0FBC1FFE8FBE1FFF09421FF707C7F1B783D60823C"
			+ "2C1F00004182008439400001914B790439400000914B790883CB790083BE0000"
			+ "3BDE00042C1D000041820040807E0004388000003D808224618C22507D8903A6"
			+ "4E8004212C03000041820030817E00007D4B1850B14B00003BDE00083BBDFFFF"
			+ "2C1D00004082FFC83D60823C39400002914B7904480000683D60823C3940FFFF"
			+ "914B790448000058814B79042C0A00024082004C39400003914B79043C600006"
			+ "6063A280388000003D808225618C02707D8903A64E8004213D60823C906B7908"
			+ "3D808224618CB2107D8903A64E8004213D60823C39400004914B79043D60835B"
			+ "93EB7C6838210090EBA1FFE0EBC1FFE8EBE1FFF08181FFF87D8803A64E800020");
	private static final byte[] SCRIPT_HOOK_CAVE_STOCK = new byte[SCRIPT_HOOK_HANDLER.length];
	private static final byte[] SCRIPT_RESUME_STOCK = HexFormat.of().parseHex("3D60835D396B8D7C");
	private static final byte[] SCRIPT_RESUME_ENABLED = HexFormat.of().parseHex("6000000060000000");
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
	private long scriptBuffer;
	private long scriptHookBuffer;
	private long scriptStringBuffer;
	private final DebuggerService debugger;

	Mw2Adapter(ConsoleService console)
	{
		super(console, CodGame.MW2);
		debugger = console.debugger();
	}

	@Override
	public List<StatGroup> statGroups()
	{
		return STATS;
	}

	@Override
	public boolean gscInjectionSupported()
	{
		return true;
	}

	@Override
	protected void onInjectGsc(Path source, TransferProgress progress)
	{
		Iw4GscProgram program = Iw4GscCompiler.compile(source,
			(percent, message) -> progress.update(1 + percent * 14 / 100, 100, message));
		progress.update(16, 100, "Checking the game for an existing menu");
		PatchState patchState = scriptPatchState();
		PatchState hookState = scriptHookState();
		if (patchState != hookState)
		{
			throw new IllegalStateException("The game script state is incomplete. Restart the game before injecting.");
		}
		if (patchState == PatchState.PATCHED && (scriptBuffer == 0 || scriptHookBuffer == 0))
		{
			throw new IllegalStateException("Another GSC injector is already active. Restart the game before injecting.");
		}
		if (patchState == PatchState.MIXED)
		{
			throw new IllegalStateException("The game script state is incomplete. Restart the game before injecting.");
		}
		if (patchState == PatchState.STOCK && readIntBig(SCRIPT_POINTER) != STOCK_SCRIPT_POINTER)
		{
			throw new IllegalStateException("Another GSC injector is already active. Restart the game before injecting.");
		}
		if (patchState == PatchState.PATCHED
			&& Integer.toUnsignedLong(readIntBig(SCRIPT_POINTER)) != scriptBuffer)
		{
			throw new IllegalStateException("The game script state changed. Restart the game before injecting.");
		}
		if (patchState == PatchState.STOCK)
		{
			scriptBuffer = 0;
			scriptHookBuffer = 0;
			scriptStringBuffer = 0;
		}
		byte[] bytecode = program.bytecode();
		int tableLength = program.stringTable(0, SCRIPT_IMAGE).length;
		long previousTable = scriptStringBuffer;
		progress.update(20, 100, "Reserving menu memory");
		long nextTable = allocate(tableLength);
		long nextPool = 0;
		boolean published = false;
		try
		{
			byte[] table = program.stringTable(nextTable, SCRIPT_IMAGE);
			writeVerified(nextTable, table, progress, 22, 36, "Uploading menu text");
			if (patchState == PatchState.STOCK)
			{
				nextPool = allocate(SCRIPT_ALLOCATION_SIZE);
			}
			published = true;
			installScript(patchState, nextPool, nextTable, bytecode, progress);
			if (patchState == PatchState.STOCK)
			{
				scriptBuffer = nextPool;
				scriptHookBuffer = SCRIPT_HOOK_CAVE;
			}
			scriptStringBuffer = nextTable;
		}
		catch (RuntimeException failure)
		{
			if (!published)
			{
				try
				{
					free(nextTable);
					if (nextPool != 0)
					{
						free(nextPool);
					}
				}
				catch (RuntimeException cleanupFailure)
				{
					failure.addSuppressed(cleanupFailure);
				}
			}
			throw failure;
		}
		if (previousTable != 0)
		{
			progress.update(98, 100, "Releasing the previous menu");
			free(previousTable);
		}
	}

	private void installScript(PatchState patchState, long poolAddress, long tableAddress, byte[] bytecode,
		TransferProgress progress)
	{
		boolean attachedHere = false;
		boolean paused = false;
		boolean canResume = true;
		Map<Long, byte[]> backup = new LinkedHashMap<>();
		try
		{
			if (!debugger.attached())
			{
				debugger.attach(false);
				attachedHere = true;
			}
			if (debugger.executionState() != DebuggerService.ExecutionState.RUNNING)
			{
				throw new IllegalStateException("Resume the game before injecting.");
			}
			progress.update(40, 100, "Pausing console for installation");
			debugger.pause();
			paused = true;
			progress.update(41, 100, "Console paused: Preparing installation");
			if (debugger.executionState() != DebuggerService.ExecutionState.STOPPED
				|| readIntBig(SCRIPT_LOCK + 0x14) != 0 || readIntBig(SCRIPT_LOCK + 0x18) != 0)
			{
				throw new IllegalStateException("The game is busy. Wait in the lobby and try injecting again.");
			}
			backup.put(SCRIPT_IMAGE, read(SCRIPT_IMAGE, patchState == PatchState.STOCK ? SCRIPT_POOL_SIZE : bytecode.length));
			backup.put(SCRIPT_POINTER, read(SCRIPT_POINTER, 4));
			backup.put(SCRIPT_HOOK_CAVE, read(SCRIPT_HOOK_CAVE, SCRIPT_HOOK_HANDLER.length));
			backup.put(SCRIPT_HOOK_STATE, read(SCRIPT_HOOK_STATE, 12));
			backup.put(SCRIPT_LOAD_HOOK, read(SCRIPT_LOAD_HOOK, SCRIPT_LOAD_HOOK_STOCK.length));
			backup.put(SCRIPT_RESUME_PATCH, read(SCRIPT_RESUME_PATCH, SCRIPT_RESUME_STOCK.length));
			for (long address : SCRIPT_HIGH_PATCHES)
			{
				backup.put(address, read(address, 2));
			}
			for (long address : SCRIPT_LOW_PATCHES)
			{
				backup.put(address, read(address, 2));
			}
			if (patchState == PatchState.STOCK)
			{
				byte[] pool = Arrays.copyOf(backup.get(SCRIPT_IMAGE), SCRIPT_POOL_SIZE);
				ByteBuffer poolData = ByteBuffer.wrap(pool).order(ByteOrder.BIG_ENDIAN);
				long marker = Integer.toUnsignedLong(poolData.getInt(0x38004));
				if (marker >= SCRIPT_IMAGE && marker < SCRIPT_IMAGE + 0x38000)
				{
					poolData.putInt(0x38004, (int) (poolAddress + marker - SCRIPT_IMAGE));
				}
				writeVerified(poolAddress, pool, progress, 45, 62, "Console paused: Preserving game text");
			}
			try
			{
				progress.update(63, 100, "Console paused: Preparing menu startup");
				writeVerified(SCRIPT_HOOK_STATE, ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
					.putInt((int) tableAddress).putInt(0).putInt(0).array());
				if (patchState == PatchState.STOCK)
				{
					writeVerified(SCRIPT_HOOK_CAVE, SCRIPT_HOOK_HANDLER);
					writeIntBig(SCRIPT_POINTER, poolAddress);
					for (long address : SCRIPT_HIGH_PATCHES)
					{
						writeShortBig(address, SCRIPT_HIGH);
					}
					for (long address : SCRIPT_LOW_PATCHES)
					{
						writeShortBig(address, SCRIPT_LOW);
					}
					writeVerified(SCRIPT_RESUME_PATCH, SCRIPT_RESUME_ENABLED);
					if (scriptPatchState() != PatchState.PATCHED
						|| Integer.toUnsignedLong(readIntBig(SCRIPT_POINTER)) != poolAddress)
					{
						throw new IllegalStateException("The GSC upload could not be verified.");
					}
				}
				writeVerified(SCRIPT_IMAGE, bytecode, progress, 68, 90, "Console paused: Installing menu");
				progress.update(92, 100, "Console paused: Verifying startup");
				writeVerified(SCRIPT_LOAD_HOOK, scriptLoadHook());
				progress.update(94, 100, "Console paused: Finishing installation");
				flushScriptCaches();
			}
			catch (RuntimeException failure)
			{
				progress.update(94, 100, "Console paused: Restoring previous state");
				try
				{
					for (Map.Entry<Long, byte[]> entry : backup.entrySet())
					{
						writeVerified(entry.getKey(), entry.getValue());
					}
					flushScriptCaches();
				}
				catch (RuntimeException restoreFailure)
				{
					canResume = false;
					failure.addSuppressed(restoreFailure);
					throw new IllegalStateException("The game could not be restored and remains paused. Restart the game.", failure);
				}
				throw failure;
			}
		}
		catch (IOException failure)
		{
			throw new IllegalStateException("Could not attach to the console debugger for injection.", failure);
		}
		finally
		{
			if (paused && canResume)
			{
				progress.update(96, 100, "Resuming console");
				debugger.resume();
			}
			if (attachedHere && canResume)
			{
				debugger.detach();
			}
		}
	}

	private void writeVerified(long address, byte[] data, TransferProgress progress,
		int start, int end, String message)
	{
		progress.update(start, 100, message);
		for (int offset = 0; offset < data.length; offset += 4096)
		{
			int length = Math.min(4096, data.length - offset);
			write(address + offset, Arrays.copyOfRange(data, offset, offset + length));
			progress.update(start + (long) (end - start) * (offset + length) / data.length, 100, message);
		}
		if (!Arrays.equals(read(address, data.length), data))
		{
			throw new IllegalStateException("The GSC upload could not be verified.");
		}
	}

	private void writeVerified(long address, byte[] data)
	{
		write(address, data);
		if (!Arrays.equals(read(address, data.length), data))
		{
			throw new IllegalStateException("The GSC upload could not be verified.");
		}
	}

	private void flushScriptCaches()
	{
		flushInstructionCache(SCRIPT_HOOK_CAVE, SCRIPT_HOOK_HANDLER.length);
		flushInstructionCache(SCRIPT_REDIRECT_START, SCRIPT_REDIRECT_LENGTH);
		flushInstructionCache(SCRIPT_RESUME_PATCH, SCRIPT_RESUME_ENABLED.length);
		flushInstructionCache(SCRIPT_LOAD_HOOK, SCRIPT_LOAD_HOOK_STOCK.length);
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

	private PatchState scriptPatchState()
	{
		byte[] resume = read(SCRIPT_RESUME_PATCH, SCRIPT_RESUME_STOCK.length);
		boolean stock = Arrays.equals(resume, SCRIPT_RESUME_STOCK);
		boolean patched = Arrays.equals(resume, SCRIPT_RESUME_ENABLED);
		for (long address : SCRIPT_HIGH_PATCHES)
		{
			int value = readUnsignedShortBig(address);
			stock &= value == 0x8250;
			patched &= value == SCRIPT_HIGH;
		}
		for (long address : SCRIPT_LOW_PATCHES)
		{
			int value = readUnsignedShortBig(address);
			stock &= value == 0xE71C;
			patched &= value == SCRIPT_LOW;
		}
		if (stock)
		{
			return PatchState.STOCK;
		}
		return patched ? PatchState.PATCHED : PatchState.MIXED;
	}

	private PatchState scriptHookState()
	{
		byte[] hook = read(SCRIPT_LOAD_HOOK, SCRIPT_LOAD_HOOK_STOCK.length);
		byte[] cave = read(SCRIPT_HOOK_CAVE, SCRIPT_HOOK_CAVE_STOCK.length);
		if (Arrays.equals(hook, SCRIPT_LOAD_HOOK_STOCK) && Arrays.equals(cave, SCRIPT_HOOK_CAVE_STOCK))
		{
			return PatchState.STOCK;
		}
		if (scriptHookBuffer == SCRIPT_HOOK_CAVE && Arrays.equals(hook, scriptLoadHook())
			&& Arrays.equals(cave, SCRIPT_HOOK_HANDLER))
		{
			return PatchState.PATCHED;
		}
		return PatchState.MIXED;
	}

	private byte[] scriptLoadHook()
	{
		int address = (int) SCRIPT_HOOK_CAVE;
		return ByteBuffer.allocate(SCRIPT_LOAD_HOOK_STOCK.length).order(ByteOrder.BIG_ENDIAN)
			.putInt(0x3D600000 | address >>> 16 & 0xFFFF)
			.putInt(0x616B0000 | address & 0xFFFF)
			.putInt(0x7D6903A6)
			.putInt(0x4E800420)
			.array();
	}

	private int readUnsignedShortBig(long address)
	{
		byte[] value = read(address, 2);
		return (value[0] & 0xFF) << 8 | value[1] & 0xFF;
	}

	private enum PatchState
	{
		STOCK,
		PATCHED,
		MIXED
	}

}
