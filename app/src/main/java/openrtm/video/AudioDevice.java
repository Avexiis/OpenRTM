package openrtm.video;

import javax.sound.sampled.Mixer;
import java.util.Objects;

public final class AudioDevice
{
	private final String id;
	private final String name;
	private final Mixer.Info mixerInfo;
	private final String pulseSource;

	public AudioDevice(String id, String name, Mixer.Info mixerInfo)
	{
		this(id, name, mixerInfo, null);
	}

	private AudioDevice(String id, String name, Mixer.Info mixerInfo, String pulseSource)
	{
		this.id = id;
		this.name = name;
		this.mixerInfo = mixerInfo;
		this.pulseSource = pulseSource;
	}

	public static AudioDevice disabled()
	{
		return new AudioDevice("", "No audio", null);
	}

	public static AudioDevice pulse(String source, String name)
	{
		return new AudioDevice("pulse|" + source, name, null, source);
	}

	public String id()
	{
		return id;
	}

	public Mixer.Info mixerInfo()
	{
		return mixerInfo;
	}

	public String name()
	{
		return name;
	}

	public String pulseSource()
	{
		return pulseSource;
	}

	public boolean usesPulse()
	{
		return pulseSource != null;
	}

	public boolean enabled()
	{
		return mixerInfo != null || pulseSource != null;
	}

	@Override
	public boolean equals(Object other)
	{
		if (this == other)
		{
			return true;
		}
		if (!(other instanceof AudioDevice))
		{
			return false;
		}
		AudioDevice device = (AudioDevice) other;
		return id.equals(device.id);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id);
	}

	@Override
	public String toString()
	{
		return name;
	}
}
