package openrtm.games;

import openrtm.console.ConsoleService;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;

abstract class AbstractOtherGameAdapter implements OtherGameAdapter
{
	private final ConsoleService console;
	private final OtherGame game;

	AbstractOtherGameAdapter(ConsoleService console, OtherGame game)
	{
		this.console = console;
		this.game = game;
	}

	@Override
	public final OtherGame game()
	{
		return game;
	}

	@Override
	public List<NumberControl> numberControls()
	{
		return List.of();
	}

	@Override
	public List<SliderControl> sliderControls()
	{
		return List.of();
	}

	@Override
	public List<MeterControl> meterControls()
	{
		return List.of();
	}

	@Override
	public List<Toggle> toggles()
	{
		return List.of();
	}

	@Override
	public List<Action> actions()
	{
		return List.of();
	}

	@Override
	public final long readNumber(String key)
	{
		requireTitle();
		requireNumberControl(key);
		return onReadNumber(key);
	}

	@Override
	public final void adjustNumber(String key, long amount)
	{
		requireTitle();
		NumberControl control = requireNumberControl(key);
		if (amount == 0 || amount < -control.maximumAmount() || amount > control.maximumAmount())
		{
			throw new IllegalArgumentException(control.label() + " adjustment must be between 1 and "
				+ String.format(Locale.US, "%,d", control.maximumAmount()));
		}
		onAdjustNumber(key, amount);
	}

	@Override
	public final void setNumber(String key, long value)
	{
		requireTitle();
		NumberControl control = requireNumberControl(key);
		if (value < control.minimum() || value > control.maximum())
		{
			throw new IllegalArgumentException(control.label() + " must be between "
				+ String.format(Locale.US, "%,d", control.minimum()) + " and "
				+ String.format(Locale.US, "%,d", control.maximum()));
		}
		onSetNumber(key, value);
	}

	@Override
	public final void setSlider(String key, double value)
	{
		requireTitle();
		SliderControl control = requireSliderControl(key);
		double minimum = (double) control.minimum() / control.divisor();
		double maximum = (double) control.maximum() / control.divisor();
		if (!Double.isFinite(value) || value < minimum || value > maximum)
		{
			throw new IllegalArgumentException(control.label() + " must be between " + minimum + " and " + maximum);
		}
		onSetSlider(key, value);
	}

	@Override
	public final double readMeter(String key)
	{
		requireTitle();
		requireMeterControl(key);
		return onReadMeter(key);
	}

	@Override
	public final boolean readToggle(String key)
	{
		requireTitle();
		Toggle toggle = requireToggle(key);
		if (!toggle.readable())
		{
			throw new UnsupportedOperationException(toggle.label() + " cannot be read from the game");
		}
		return onReadToggle(key);
	}

	@Override
	public final void setToggle(String key, boolean enabled)
	{
		requireTitle();
		requireToggle(key);
		onSetToggle(key, enabled);
	}

	@Override
	public final void runAction(String key)
	{
		requireTitle();
		requireAction(key);
		onRunAction(key);
	}

	protected long onReadNumber(String key)
	{
		throw unknown(key);
	}

	protected void onAdjustNumber(String key, long amount)
	{
		throw unknown(key);
	}

	protected void onSetNumber(String key, long value)
	{
		throw unknown(key);
	}

	protected void onSetSlider(String key, double value)
	{
		throw unknown(key);
	}

	protected double onReadMeter(String key)
	{
		throw unknown(key);
	}

	protected boolean onReadToggle(String key)
	{
		throw unknown(key);
	}

	protected void onSetToggle(String key, boolean enabled)
	{
		throw unknown(key);
	}

	protected void onRunAction(String key)
	{
		throw unknown(key);
	}

	protected final byte[] read(long address, int length)
	{
		return console.readMemory(address, length);
	}

	protected final int readIntBig(long address)
	{
		return ByteBuffer.wrap(read(address, 4)).order(ByteOrder.BIG_ENDIAN).getInt();
	}

	protected final float readFloatBig(long address)
	{
		return ByteBuffer.wrap(read(address, 4)).order(ByteOrder.BIG_ENDIAN).getFloat();
	}

	protected final int readUnsignedByte(long address)
	{
		return Byte.toUnsignedInt(read(address, 1)[0]);
	}

	protected final long call(long address, Object... arguments)
	{
		return console.callTitle(address, arguments);
	}

	protected final void callVoid(long address, Object... arguments)
	{
		console.callTitleVoid(address, arguments);
	}

	protected final void writeByte(long address, int value)
	{
		console.writeByte(address, value);
	}

	protected final void write(long address, byte[] value)
	{
		console.writeMemory(address, value);
	}

	protected final IllegalArgumentException unknown(String key)
	{
		return new IllegalArgumentException("Unknown option for " + game.tabName() + ": " + key);
	}

	private NumberControl requireNumberControl(String key)
	{
		return numberControls().stream().filter(control -> control.key().equals(key)).findFirst().orElseThrow(() -> unknown(key));
	}

	private SliderControl requireSliderControl(String key)
	{
		return sliderControls().stream().filter(control -> control.key().equals(key)).findFirst().orElseThrow(() -> unknown(key));
	}

	private MeterControl requireMeterControl(String key)
	{
		return meterControls().stream().filter(control -> control.key().equals(key)).findFirst().orElseThrow(() -> unknown(key));
	}

	private Toggle requireToggle(String key)
	{
		return toggles().stream().filter(toggle -> toggle.key().equals(key)).findFirst().orElseThrow(() -> unknown(key));
	}

	private Action requireAction(String key)
	{
		return actions().stream().filter(action -> action.key().equals(key)).findFirst().orElseThrow(() -> unknown(key));
	}

	private void requireTitle()
	{
		String active = console.currentTitleId().toUpperCase(Locale.ROOT);
		if (!game.titleId().equals(active))
		{
			throw new IllegalStateException("Open " + game.displayName()
				+ " on the console before using this tab. Active title: " + active);
		}
	}
}
