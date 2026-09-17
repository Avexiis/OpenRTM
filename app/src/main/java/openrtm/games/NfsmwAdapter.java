package openrtm.games;

import openrtm.console.ConsoleService;

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
	//private static final long VISUAL_TREATMENT = 0x828F48B2L; //TODO: Fix yellow filter toggle, this disables the wrong filter. unused for now.
	private static final long PLAYER_MANAGER_SLOT_POINTER = 0x82C74BD4L;
	private static final long PLAYER_MANAGER_READY = 0x82C74BDCL;
	private static final long PLAYER_VTABLE = 0x82092948L;
	private static final long USER_PROFILE_OFFSET = 0x10L;
	private static final long CAREER_SETTINGS_OFFSET = 0xB0L;
	private static final long CASH_OFFSET = 0x0CL;
	private static final long GAME_BREAKER_CHARGE_OFFSET = 0x38L;
	private static final long MINIMUM_POINTER = 0x80000000L;
	private static final long MAXIMUM_POINTER = 0xD0000000L;
	private static final long MAXIMUM_VALUE = 2_000_000_000L;
	private static final long MAXIMUM_ADJUSTMENT = MAXIMUM_VALUE;
	private static final List<Section> SECTIONS = List.of(
		new Section("career", "Career"),
		new Section("pursuit", "Pursuit"),
		new Section("driving", "Driving"),
		//new Section("visual", "Visual"), //TODO: Fix yellow filter toggle
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
		new Toggle("infinite_nitrous", "Infinite Nitrous", "Enabled", "driving", false, true)/*,*/
		/*new Toggle("hide_yellow_filter", "Yellow Filter", "Hidden", "visual", false, true)*/); //TODO: Fix yellow filter toggle
	private static final List<Action> ACTIONS = List.of(
		new Action("start_pursuit", "Start Pursuit", "pursuit"),
		new Action("prevent_bust", "Prevent Bust", "pursuit"),
//		new Action("refill_speedbreaker", "Refill", "driving"), //deprecated by infinite toggle
//		new Action("empty_speedbreaker", "Empty", "driving"), //deprecated by infinite toggle
//		new Action("toggle_speedbreaker", "Toggle", "driving"), //deprecated by infinite toggle
		new Action("safe_house", "Safe House", "travel"),
		new Action("car_lot", "Car Lot", "travel"));

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
			//case "hide_yellow_filter" -> readToggleByte(VISUAL_TREATMENT, "yellow filter") == 0; //TODO: Fix yellow filter toggle
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
			//case "hide_yellow_filter" -> writeToggleByte(VISUAL_TREATMENT, enabled ? 0 : 1, "yellow filter"); //TODO: Fix yellow filter toggle
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
