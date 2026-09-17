package openrtm.games;

import java.util.List;

public interface OtherGameAdapter
{
	record Section(String key, String label)
	{
	}

	record NumberControl(String key, String label, String section, long minimum, long maximum,
		long maximumAmount, long initialAmount)
	{
	}

	record SliderControl(String key, String label, String section, int minimum, int maximum, int initial,
		int divisor)
	{
	}

	record MeterControl(String key, String label, String section)
	{
	}

	record Toggle(String key, String label, String enabledLabel, String section, boolean initial,
		boolean readable)
	{
	}

	record Action(String key, String label, String section)
	{
	}

	OtherGame game();

	List<Section> sections();

	default boolean sectionTabs()
	{
		return true;
	}

	List<NumberControl> numberControls();

	List<SliderControl> sliderControls();

	List<MeterControl> meterControls();

	List<Toggle> toggles();

	List<Action> actions();

	long readNumber(String key);

	void adjustNumber(String key, long amount);

	void setNumber(String key, long value);

	void setSlider(String key, double value);

	double readMeter(String key);

	boolean readToggle(String key);

	void setToggle(String key, boolean enabled);

	void runAction(String key);
}
