package openrtm.games;

import openrtm.console.ConsoleService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class NfsmwAdapter extends AbstractOtherGameAdapter
{
	private static final long CASH_HELPER = 0x822974A8L;
	private static final long BOUNTY_HELPER = 0x82366B08L;
	private static final long BOUNTY_GETTER = 0x82366C90L;
	private static final long SET_COPS_ENABLED = 0x82365E08L;
	private static final long START_PURSUIT = 0x82365E88L;
	private static final long JUMP_TO_CAR_LOT = 0x82366F60L;
	private static final long JUMP_TO_SAFE_HOUSE = 0x82366FA8L;
	private static final long SET_WORLD_HEAT = 0x82367A48L;
	private static final long PREVENT_BUST = 0x82367FE0L;
	private static final long TOGGLE_GAME_BREAKER = 0x8246C0A0L;
	private static final long RESET_GAME_BREAKER = 0x8246C150L;
	private static final long FRONTEND_DATABASE_POINTER = 0x82A2C684L;
	private static final long INTRO_SKIP = 0x82A2CE01L;
	private static final long INFINITE_NITROUS = 0x82A2CE71L;
	private static final long INFINITE_SPEEDBREAKER = 0x82A2D1C6L;
	private static final long VISUAL_TREATMENT_POINTER = 0x82A2C4D4L;
	private static final long PLAYER_MANAGER_SLOT_POINTER = 0x82C74BD4L;
	private static final long PLAYER_MANAGER_READY = 0x82C74BDCL;
	private static final long PLAYER_VTABLE = 0x82092948L;
	private static final long USER_PROFILE_OFFSET = 0x10L;
	private static final long CAREER_SETTINGS_OFFSET = 0xB0L;
	private static final long CASH_OFFSET = 0x0CL;
	private static final long GAME_BREAKER_CHARGE_OFFSET = 0x38L;
	private static final long[] VISUAL_LOOK_POINTER_OFFSETS = {0x18CL, 0x1A0L, 0x1B4L};
	private static final long VISUAL_LOOK_TINT_OFFSET = 0xC0L;
	private static final long MINIMUM_POINTER = 0x80000000L;
	private static final long MAXIMUM_POINTER = 0xD0000000L;
	private static final long MAXIMUM_VALUE = 2_000_000_000L;
	private static final long MAXIMUM_ADJUSTMENT = MAXIMUM_VALUE;
	private static final Tint NEUTRAL_TINT = new Tint(1.0F, 1.0F, 1.0F);
	private static final List<Tint> STOCK_TINTS = List.of(
		new Tint(0.85F, 0.75F, 0.25F),
		new Tint(0.56F, 0.78F, 0.93F),
		new Tint(0.05F, 0.05F, 0.80F));
	private static final List<Section> SECTIONS = List.of(
		new Section("career", "Career"),
		new Section("pursuit", "Pursuit"),
		new Section("driving", "Driving"),
		new Section("visual", "Visual"),
		new Section("travel", "Fast Travel"));
	private static final List<NumberControl> NUMBERS = List.of(
		new NumberControl("cash", "Cash", "career", 0, MAXIMUM_VALUE, MAXIMUM_ADJUSTMENT, 10_000),
		new NumberControl("bounty", "Bounty", "career", 0, MAXIMUM_VALUE, MAXIMUM_ADJUSTMENT, 10_000));
	private static final List<SliderControl> SLIDERS = List.of(
		new SliderControl("heat", "Heat", "pursuit", 10, 50, 10, 10));
//	private static final List<MeterControl> METERS = List.of(
//		new MeterControl("speedbreaker", "Speedbreaker", "driving")); //nobody cares for this, and it takes up too much space
	private static final List<Toggle> TOGGLES = List.of(
		new Toggle("cops", "Cops", "Enabled", "pursuit", true, false),
		new Toggle("skip_intro", "Career Intro", "Skip New Career Intro", "career", false, true),
		new Toggle("infinite_speedbreaker", "Infinite Speedbreaker", "Enabled", "driving", false, true),
		new Toggle("infinite_nitrous", "Infinite Nitrous", "Enabled", "driving", false, true),
		new Toggle("hide_yellow_filter", "Yellow Filter", "Hidden", "visual", false, true));
	private static final List<Action> ACTIONS = List.of(
		new Action("start_pursuit", "Start Pursuit", "pursuit"),
		new Action("prevent_bust", "Prevent Bust", "pursuit"),
//		new Action("refill_speedbreaker", "Refill", "driving"), //deprecated by infinite toggle
//		new Action("empty_speedbreaker", "Empty", "driving"), //deprecated by infinite toggle
//		new Action("toggle_speedbreaker", "Toggle", "driving"), //deprecated by infinite toggle
		new Action("safe_house", "Safe House", "travel"),
		new Action("car_lot", "Car Lot", "travel"));
	private List<TintBackup> tintBackups = List.of();

	NfsmwAdapter(ConsoleService console)
	{
		super(console, OtherGame.NFSMW);
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

//	@Override
//	public List<MeterControl> meterControls()
//	{
//		return METERS; //disabled along with speedbreaker meter
//	}

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
			case "bounty" -> readBounty();
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
		applyAdjustment(key, amount);
	}

	@Override
	protected void onSetNumber(String key, long value)
	{
		long current = onReadNumber(key);
		applyAdjustment(key, value - current);
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
	protected double onReadMeter(String key)
	{
		if (!"speedbreaker".equals(key))
		{
			throw unknown(key);
		}
		float charge = readFloatBig(currentPlayer() + GAME_BREAKER_CHARGE_OFFSET);
		if (!Float.isFinite(charge) || charge < -0.01F || charge > 1.01F)
		{
			throw new IllegalStateException("The current Speedbreaker value is not valid");
		}
		return Math.max(0.0, Math.min(1.0, charge));
	}

	@Override
	protected boolean onReadToggle(String key)
	{
		return switch (key)
		{
			case "skip_intro" -> readToggleByte(INTRO_SKIP, "career intro") != 0;
			case "infinite_speedbreaker" -> readToggleByte(INFINITE_SPEEDBREAKER, "Speedbreaker") != 0;
			case "infinite_nitrous" -> readToggleByte(INFINITE_NITROUS, "nitrous") != 0;
			case "hide_yellow_filter" -> yellowFilterHidden(visualLookAttributes());
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
			case "infinite_nitrous" -> writeToggleByte(INFINITE_NITROUS, enabled ? 1 : 0, "nitrous");
			case "hide_yellow_filter" -> setYellowFilterHidden(enabled);
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
			case "refill_speedbreaker" -> callVoid(RESET_GAME_BREAKER, currentPlayer(), true);
			case "empty_speedbreaker" -> callVoid(RESET_GAME_BREAKER, currentPlayer(), false);
			case "toggle_speedbreaker" -> callVoid(TOGGLE_GAME_BREAKER, currentPlayer());
			case "safe_house" -> {
				currentPlayer();
				callVoid(JUMP_TO_SAFE_HOUSE);
			}
			case "car_lot" -> {
				currentPlayer();
				callVoid(JUMP_TO_CAR_LOT);
			}
			default -> throw unknown(key);
		}
	}

	private long readCash()
	{
		long cash = readIntBig(careerSettings() + CASH_OFFSET);
		return requireStoredValue(cash, "cash");
	}

	private long readBounty()
	{
		currentPlayer();
		long bounty = (int) call(BOUNTY_GETTER);
		return requireStoredValue(bounty, "bounty");
	}

	private long requireStoredValue(long value, String label)
	{
		if (value < 0 || value > MAXIMUM_VALUE)
		{
			throw new IllegalStateException("The current " + label + " value is outside the supported range");
		}
		return value;
	}

	private void applyAdjustment(String key, long amount)
	{
		int adjustment = Math.toIntExact(amount);
		switch (key)
		{
			case "cash" -> callVoid(CASH_HELPER, careerSettings(), adjustment);
			case "bounty" -> {
				currentPlayer();
				callVoid(BOUNTY_HELPER, adjustment);
			}
			default -> throw unknown(key);
		}
	}

	private long careerSettings()
	{
		long database = readPointer(FRONTEND_DATABASE_POINTER, "front-end database");
		long profile = readPointer(database + USER_PROFILE_OFFSET, "active user profile");
		return requirePointer(profile + CAREER_SETTINGS_OFFSET, "career settings");
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

	private long[] visualLookAttributes()
	{
		long treatment = readPointer(VISUAL_TREATMENT_POINTER, "visual treatment");
		long[] attributes = new long[VISUAL_LOOK_POINTER_OFFSETS.length];
		for (int index = 0; index < VISUAL_LOOK_POINTER_OFFSETS.length; index++)
		{
			attributes[index] = readPointer(treatment + VISUAL_LOOK_POINTER_OFFSETS[index], "visual look");
		}
		return attributes;
	}

	private boolean yellowFilterHidden(long[] attributes)
	{
		byte[] neutral = encodeTint(NEUTRAL_TINT);
		for (long attribute : attributes)
		{
			if (!Arrays.equals(read(attribute + VISUAL_LOOK_TINT_OFFSET, neutral.length), neutral))
			{
				return false;
			}
		}
		return true;
	}

	private void setYellowFilterHidden(boolean hidden)
	{
		long[] attributes = visualLookAttributes();
		if (hidden)
		{
			hideYellowFilter(attributes);
		}
		else
		{
			restoreYellowFilter(attributes);
		}
	}

	private void hideYellowFilter(long[] attributes)
	{
		if (yellowFilterHidden(attributes))
		{
			return;
		}
		List<TintBackup> backups = new ArrayList<>();
		for (long attribute : attributes)
		{
			byte[] value = read(attribute + VISUAL_LOOK_TINT_OFFSET, 12);
			requireValidTint(value);
			backups.add(new TintBackup(attribute, value));
		}
		byte[] neutral = encodeTint(NEUTRAL_TINT);
		try
		{
			for (long attribute : attributes)
			{
				write(attribute + VISUAL_LOOK_TINT_OFFSET, neutral);
			}
			verifyTints(attributes, List.of(NEUTRAL_TINT, NEUTRAL_TINT, NEUTRAL_TINT));
			tintBackups = List.copyOf(backups);
		}
		catch (RuntimeException failure)
		{
			for (TintBackup backup : backups)
			{
				write(backup.address() + VISUAL_LOOK_TINT_OFFSET, backup.value());
			}
			throw failure;
		}
	}

	private void restoreYellowFilter(long[] attributes)
	{
		List<TintBackup> backups = matchingBackups(attributes);
		if (!backups.isEmpty())
		{
			for (TintBackup backup : backups)
			{
				write(backup.address() + VISUAL_LOOK_TINT_OFFSET, backup.value());
			}
			for (TintBackup backup : backups)
			{
				if (!Arrays.equals(read(backup.address() + VISUAL_LOOK_TINT_OFFSET, backup.value().length), backup.value()))
				{
					throw new IllegalStateException("The game did not restore the yellow filter");
				}
			}
		}
		else
		{
			for (int index = 0; index < attributes.length; index++)
			{
				write(attributes[index] + VISUAL_LOOK_TINT_OFFSET, encodeTint(STOCK_TINTS.get(index)));
			}
			verifyTints(attributes, STOCK_TINTS);
		}
		tintBackups = List.of();
	}

	private List<TintBackup> matchingBackups(long[] attributes)
	{
		if (tintBackups.size() != attributes.length)
		{
			return List.of();
		}
		for (int index = 0; index < attributes.length; index++)
		{
			if (tintBackups.get(index).address() != attributes[index])
			{
				return List.of();
			}
		}
		return tintBackups;
	}

	private void verifyTints(long[] attributes, List<Tint> expected)
	{
		for (int index = 0; index < attributes.length; index++)
		{
			byte[] value = encodeTint(expected.get(index));
			if (!Arrays.equals(read(attributes[index] + VISUAL_LOOK_TINT_OFFSET, value.length), value))
			{
				throw new IllegalStateException("The game did not retain the yellow filter setting");
			}
		}
	}

	private void requireValidTint(byte[] value)
	{
		ByteBuffer buffer = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN);
		while (buffer.hasRemaining())
		{
			float component = buffer.getFloat();
			if (!Float.isFinite(component) || component < -4.0F || component > 4.0F)
			{
				throw new IllegalStateException("The current visual treatment is not valid");
			}
		}
	}

	private static byte[] encodeTint(Tint tint)
	{
		return ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
			.putFloat(tint.red()).putFloat(tint.green()).putFloat(tint.blue()).array();
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

	private record Tint(float red, float green, float blue)
	{
	}

	private record TintBackup(long address, byte[] value)
	{
	}
}
