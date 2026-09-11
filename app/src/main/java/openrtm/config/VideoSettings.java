package openrtm.config;

import openrtm.video.VideoCaptureConfig.Decoder;
import openrtm.video.VideoCaptureConfig.FrameRate;
import openrtm.video.VideoCaptureConfig.Performance;
import openrtm.video.VideoCaptureConfig.Resolution;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

public final class VideoSettings
{
	private final ConfigManager config;

	public VideoSettings()
	{
		config = ConfigManager.shared();
	}

	public String videoDeviceId()
	{
		return config.string("video.device").orElse("");
	}

	public void videoDeviceId(String value)
	{
		config.put("video.device", value);
	}

	public String audioDeviceId()
	{
		return config.string("video.audioDevice").orElse("");
	}

	public void audioDeviceId(String value)
	{
		config.put("video.audioDevice", value);
	}

	public Decoder decoder()
	{
		return enumValue("video.decoder", Decoder.class, Decoder.AUTOMATIC);
	}

	public void decoder(Decoder value)
	{
		config.put("video.decoder", value.name());
	}

	public Resolution resolution()
	{
		return enumValue("video.resolution", Resolution.class, Resolution.SOURCE);
	}

	public void resolution(Resolution value)
	{
		config.put("video.resolution", value.name());
	}

	public FrameRate frameRate()
	{
		return enumValue("video.frameRate", FrameRate.class, FrameRate.SOURCE);
	}

	public void frameRate(FrameRate value)
	{
		config.put("video.frameRate", value.name());
	}

	public Performance performance()
	{
		return enumValue("video.performance", Performance.class, Performance.LOWEST_LATENCY);
	}

	public void performance(Performance value)
	{
		config.put("video.performance", value.name());
	}

	public boolean monitorAudio()
	{
		return config.bool("video.monitorAudio", true);
	}

	public void monitorAudio(boolean value)
	{
		config.put("video.monitorAudio", value);
	}

	public boolean detachedViewer()
	{
		return config.bool("video.detachedViewer", false);
	}

	public void detachedViewer(boolean value)
	{
		config.put("video.detachedViewer", value);
	}

	public boolean lockViewerSize()
	{
		return config.bool("video.lockViewerSize", true);
	}

	public void lockViewerSize(boolean value)
	{
		config.put("video.lockViewerSize", value);
	}

	public Path captureDirectory()
	{
		Path defaultDirectory = Path.of(System.getProperty("user.home"), "Videos", "OpenRTM");
		String saved = config.string("video.captureDirectory")
			.orElse(defaultDirectory.toString());
		try
		{
			return Path.of(saved).toAbsolutePath().normalize();
		}
		catch (InvalidPathException ignored)
		{
			return defaultDirectory.toAbsolutePath().normalize();
		}
	}

	public void captureDirectory(Path value)
	{
		config.put("video.captureDirectory", value.toAbsolutePath().normalize().toString());
	}

	private <T extends Enum<T>> T enumValue(String key, Class<T> type, T fallback)
	{
		String saved = config.string(key).orElse("");
		try
		{
			return Enum.valueOf(type, saved);
		}
		catch (IllegalArgumentException ignored)
		{
			return fallback;
		}
	}
}
