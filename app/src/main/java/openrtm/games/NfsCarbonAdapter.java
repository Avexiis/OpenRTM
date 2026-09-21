package openrtm.games;

import openrtm.console.ConsoleService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

final class NfsCarbonAdapter extends AbstractOtherGameAdapter
{
	private static final long CASH_GETTER = 0x821CE620L;
	private static final long CASH_SETTER = 0x821CE628L;
//	private static final long BOUNTY_HELPER = 0x82408028L; //carbon doesn't directly use bounty.
//	private static final long BOUNTY_GETTER = 0x824081F8L; //I wasted my time getting these
	private static final long SET_COPS_ENABLED = 0x82406E20L;
	private static final long START_PURSUIT = 0x82406EE0L;
	private static final long SET_WORLD_HEAT = 0x82408F50L;
	private static final long PREVENT_BUST = 0x824094C8L;
	private static final long FRONTEND_MANAGER_POINTER = 0x82C64AD0L;
	private static final long INTRO_SKIP = 0x82C6503DL;
	private static final long INFINITE_SPEEDBREAKER = 0x82C6541DL;
	private static final long NITROUS_DRAIN_STORE = 0x824C2C88L;
	private static final long NITROUS_DRAIN_CLAMP_STORE = 0x824C2CBCL;
	private static final long RACECAR_DRIVE_SPEED_SLOT = 0x8206794CL;
	private static final long PLAYER_MANAGER_SLOT_POINTER = 0x830079C8L;
	private static final long PLAYER_MANAGER_READY = 0x830079D0L;
	private static final long PLAYER_VTABLE = 0x8209E870L;
	private static final long PROFILE_SLOTS_OFFSET = 0xD4L;
	private static final long CAREER_SETTINGS_OFFSET = 0x234L;
	private static final long CASH_OBJECT_OFFSET = 0x242C0L;
	private static final long PROFILE_VTABLE = 0x8206FF84L;
	private static final long CASH_OBJECT_VTABLE = 0x8206FCB8L;
	private static final long MINIMUM_POINTER = 0x80000000L;
	private static final long MAXIMUM_POINTER = 0xD0000000L;
	private static final long MAXIMUM_VALUE = 2_000_000_000L;
	private static final long MAXIMUM_ADJUSTMENT = MAXIMUM_VALUE;
	private static final int NITROUS_DRAIN_INSTRUCTION = 0xD1BF00FC;
	private static final int RACECAR_DRIVE_SPEED_SETTER = 0x8211AFA0;
	private static final int RACECAR_ZERO_DRIVE_SPEED_SETTER = 0x822823C0;
	private static final int NOP_INSTRUCTION = 0x60000000;
	private static final List<Section> SECTIONS = List.of(
		new Section("career", "Career"),
		new Section("pursuit", "Pursuit"),
		new Section("driving", "Driving"));
	private static final List<NumberControl> NUMBERS = List.of(
		new NumberControl("cash", "Cash", "career", 0, MAXIMUM_VALUE, MAXIMUM_ADJUSTMENT, 10_000));
		//new NumberControl("bounty", "Bounty", "career", 0, MAXIMUM_VALUE, MAXIMUM_ADJUSTMENT, 10_000)); //carbon doesn't directly use bounty.
	private static final List<SliderControl> SLIDERS = List.of(
		new SliderControl("heat", "Heat", "pursuit", 10, 50, 10, 10));
	private static final List<Toggle> TOGGLES = List.of(
		new Toggle("cops", "Cops", "Enabled", "pursuit", true, false),
		new Toggle("skip_intro", "Career Intro", "Skip New Career Intro", "career", false, true),
		new Toggle("infinite_speedbreaker", "Infinite Speedbreaker", "Enabled", "driving", false, true),
		new Toggle("infinite_nitrous", "Infinite Nitrous", "Enabled", "driving", false, true),
		new Toggle("freeze_opponents", "Freeze Opponents", "Enabled", "driving", false, true));
	private static final List<Action> ACTIONS = List.of(
		new Action("start_pursuit", "Start Pursuit", "pursuit"),
		new Action("prevent_bust", "Prevent Bust", "pursuit"));

	NfsCarbonAdapter(ConsoleService console)
	{
		super(console, OtherGame.NFS_CARBON);
	}

	@Override
	public List<Section> sections()
	{
		return SECTIONS;
	}

	@Override
	public boolean sectionTabs()
	{
		return false;
	}

	@Override
	public List<NumberControl> numberControls()
	{
		return NUMBERS;
	}

	@Override
	public List<SliderControl> sliderControls()
	{
		return SLIDERS;
	}

	@Override
	public List<Toggle> toggles()
	{
		return TOGGLES;
	}

	@Override
	public List<Action> actions()
	{
		return ACTIONS;
	}

	@Override
	protected long onReadNumber(String key)
	{
		return switch (key)
		{
			case "cash" -> readCash();
			//case "bounty" -> readBounty(); //carbon doesn't directly use bounty.
			default -> throw unknown(key);
		};
	}

	@Override
	protected void onAdjustNumber(String key, long amount)
	{
		long current = onReadNumber(key);
		long target = Math.addExact(current, amount);
		if (target < 0 || target > MAXIMUM_VALUE)
		{
			throw new IllegalArgumentException("The resulting value must be between 0 and 2,000,000,000");
		}
		applyAdjustment(key, amount, target);
	}

	@Override
	protected void onSetNumber(String key, long value)
	{
		long current = onReadNumber(key);
		applyAdjustment(key, value - current, value);
	}

	@Override
	protected void onSetSlider(String key, double value)
	{
		if (!"heat".equals(key))
		{
			throw unknown(key);
		}
		currentPlayer();
		callVoid(SET_WORLD_HEAT, (float) value);
	}

	@Override
	protected boolean onReadToggle(String key)
	{
		return switch (key)
		{
			case "skip_intro" -> readToggleByte(INTRO_SKIP, "career intro") != 0;
			case "infinite_speedbreaker" -> readToggleByte(INFINITE_SPEEDBREAKER, "Speedbreaker") != 0;
			case "infinite_nitrous" -> nitrousInfinite();
			case "freeze_opponents" -> opponentsFrozen();
			default -> throw unknown(key);
		};
	}

	@Override
	protected void onSetToggle(String key, boolean enabled)
	{
		switch (key)
		{
			case "cops" -> {
				currentPlayer();
				callVoid(SET_COPS_ENABLED, enabled);
			}
			case "skip_intro" -> writeToggleByte(INTRO_SKIP, enabled ? 1 : 0, "career intro");
			case "infinite_speedbreaker" ->
				writeToggleByte(INFINITE_SPEEDBREAKER, enabled ? 1 : 0, "Speedbreaker");
			case "infinite_nitrous" -> setNitrousInfinite(enabled);
			case "freeze_opponents" -> setOpponentsFrozen(enabled);
			default -> throw unknown(key);
		}
	}

	@Override
	protected void onRunAction(String key)
	{
		switch (key)
		{
			case "start_pursuit" -> {
				currentPlayer();
				callVoid(START_PURSUIT, 0);
			}
			case "prevent_bust" -> {
				currentPlayer();
				callVoid(PREVENT_BUST);
			}
			default -> throw unknown(key);
		}
	}

	private long readCash()
	{
		long cash = (int) call(CASH_GETTER, cashObject());
		return requireStoredValue(cash, "cash");
	}

//	private long readBounty() //carbon doesn't directly use bounty.
//	{
//		currentPlayer();
//		long bounty = (int) call(BOUNTY_GETTER);
//		return requireStoredValue(bounty, "bounty");
//	}

	private long requireStoredValue(long value, String label)
	{
		if (value < 0 || value > MAXIMUM_VALUE)
		{
			throw new IllegalStateException("The current " + label + " value is outside the supported range");
		}
		return value;
	}

	private void applyAdjustment(String key, long amount, long target)
	{
//		int adjustment = Math.toIntExact(amount);
		switch (key)
		{
			case "cash" -> setCash(target);
//			case "bounty" -> { //carbon doesn't directly use bounty.
//				currentPlayer();
//				callVoid(BOUNTY_HELPER, adjustment);
//			}
			default -> throw unknown(key);
		}
	}

	private void setCash(long value)
	{
		long object = cashObject();
		callVoid(CASH_SETTER, object, Math.toIntExact(value));
		long retained = (int) call(CASH_GETTER, object);
		if (retained != value)
		{
			throw new IllegalStateException("The game did not retain the cash value");
		}
	}

	private long careerSettings()
	{
		return requirePointer(activeProfile() + CAREER_SETTINGS_OFFSET, "career settings");
	}

	private long cashObject()
	{
		long object = requirePointer(activeProfile() + CASH_OBJECT_OFFSET, "cash data");
		long vtable = readPointer(object, "cash data vtable");
		if (vtable != CASH_OBJECT_VTABLE)
		{
			throw new IllegalStateException("The cash data did not pass its game-state check");
		}
		return object;
	}

	private long activeProfile()
	{
		long manager = readPointer(FRONTEND_MANAGER_POINTER, "front-end manager");
		long profileSlots = readPointer(manager + PROFILE_SLOTS_OFFSET, "user profile slots");
		long profile = readPointer(profileSlots, "active user profile");
		long vtable = readPointer(profile, "user profile vtable");
		if (vtable != PROFILE_VTABLE)
		{
			throw new IllegalStateException("The active user profile did not pass its game-state check");
		}
		return profile;
	}

	private long currentPlayer()
	{
		if (readIntBig(PLAYER_MANAGER_READY) == 0)
		{
			throw new IllegalStateException("Enter free roam before using this control");
		}
		long slot = readPointer(PLAYER_MANAGER_SLOT_POINTER, "player manager slot");
		long current = readPointer(slot, "current player object");
		long vtable = readPointer(current, "player interface vtable");
		if (vtable != PLAYER_VTABLE)
		{
			throw new IllegalStateException("The current player object did not pass its game-state check");
		}
		return current;
	}

	private boolean nitrousInfinite()
	{
		int drain = readIntBig(NITROUS_DRAIN_STORE);
		int clamp = readIntBig(NITROUS_DRAIN_CLAMP_STORE);
		if (drain == NOP_INSTRUCTION && clamp == NOP_INSTRUCTION)
		{
			return true;
		}
		if (drain == NITROUS_DRAIN_INSTRUCTION && clamp == NITROUS_DRAIN_INSTRUCTION)
		{
			return false;
		}
		throw new IllegalStateException("The nitrous code did not pass its game-state check");
	}

	private void setNitrousInfinite(boolean enabled)
	{
		boolean previous = nitrousInfinite();
		if (previous == enabled)
		{
			return;
		}
		int instruction = enabled ? NOP_INSTRUCTION : NITROUS_DRAIN_INSTRUCTION;
		try
		{
			writeInstruction(NITROUS_DRAIN_STORE, instruction);
			writeInstruction(NITROUS_DRAIN_CLAMP_STORE, instruction);
			if (nitrousInfinite() != enabled)
			{
				throw new IllegalStateException("The game did not retain the infinite nitrous setting");
			}
		}
		catch (RuntimeException failure)
		{
			try
			{
				int restore = previous ? NOP_INSTRUCTION : NITROUS_DRAIN_INSTRUCTION;
				writeInstruction(NITROUS_DRAIN_STORE, restore);
				writeInstruction(NITROUS_DRAIN_CLAMP_STORE, restore);
			}
			catch (RuntimeException rollbackFailure)
			{
				failure.addSuppressed(rollbackFailure);
			}
			throw failure;
		}
	}

	private boolean opponentsFrozen()
	{
		int setter = readIntBig(RACECAR_DRIVE_SPEED_SLOT);
		if (setter == RACECAR_ZERO_DRIVE_SPEED_SETTER)
		{
			return true;
		}
		if (setter == RACECAR_DRIVE_SPEED_SETTER)
		{
			return false;
		}
		throw new IllegalStateException("The opponent racecar code did not pass its game-state check");
	}

	private void setOpponentsFrozen(boolean enabled)
	{
		boolean previous = opponentsFrozen();
		if (previous == enabled)
		{
			return;
		}
		int setter = enabled ? RACECAR_ZERO_DRIVE_SPEED_SETTER : RACECAR_DRIVE_SPEED_SETTER;
		try
		{
			writeIntBig(RACECAR_DRIVE_SPEED_SLOT, setter);
			if (opponentsFrozen() != enabled)
			{
				throw new IllegalStateException("The game did not retain the opponent freeze setting");
			}
		}
		catch (RuntimeException failure)
		{
			try
			{
				writeIntBig(RACECAR_DRIVE_SPEED_SLOT,
					previous ? RACECAR_ZERO_DRIVE_SPEED_SETTER : RACECAR_DRIVE_SPEED_SETTER);
			}
			catch (RuntimeException rollbackFailure)
			{
				failure.addSuppressed(rollbackFailure);
			}
			throw failure;
		}
	}

	private void writeInstruction(long address, int instruction)
	{
		writeIntBig(address, instruction);
	}

	private void writeIntBig(long address, int value)
	{
		write(address, ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(value).array());
	}

	private long readPointer(long address, String label)
	{
		return requirePointer(Integer.toUnsignedLong(readIntBig(address)), label);
	}

	private int readToggleByte(long address, String label)
	{
		int value = readUnsignedByte(address);
		if (value > 1)
		{
			throw new IllegalStateException("The current " + label + " setting is not valid");
		}
		return value;
	}

	private void writeToggleByte(long address, int value, String label)
	{
		readToggleByte(address, label);
		writeByte(address, value);
		if (readToggleByte(address, label) != value)
		{
			throw new IllegalStateException("The game did not retain the " + label + " setting");
		}
	}

	private long requirePointer(long pointer, String label)
	{
		if (pointer < MINIMUM_POINTER || pointer >= MAXIMUM_POINTER || (pointer & 3L) != 0)
		{
			throw new IllegalStateException("The " + label + " is not available in the current game state");
		}
		return pointer;
	}
}
