package openrtm.video;

import org.bytedeco.ffmpeg.avdevice.AVDeviceInfo;
import org.bytedeco.ffmpeg.avdevice.AVDeviceInfoList;
import org.bytedeco.ffmpeg.avformat.AVInputFormat;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacv.FFmpegFrameGrabber;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bytedeco.ffmpeg.global.avdevice.avdevice_free_list_devices;
import static org.bytedeco.ffmpeg.global.avdevice.avdevice_list_input_sources;
import static org.bytedeco.ffmpeg.global.avdevice.avdevice_register_all;
import static org.bytedeco.ffmpeg.global.avformat.av_find_input_format;

public final class CaptureDeviceDiscovery
{
	private static final Pattern ALSA_MIXER = Pattern.compile("\\[plughw:(\\d+),\\d+]$");
	private static final AudioFormat[] AUDIO_FORMATS = {
		new AudioFormat(48_000, 16, 2, true, false),
		new AudioFormat(48_000, 16, 1, true, false),
		new AudioFormat(44_100, 16, 2, true, false),
		new AudioFormat(44_100, 16, 1, true, false)
	};

	private CaptureDeviceDiscovery()
	{
	}

	public static List<VideoDevice> videoDevices()
	{
		if (isLinux())
		{
			return linuxVideoDevices();
		}
		List<VideoDevice> devices = new ArrayList<>();
		for (int index = 0; index < 8; index++)
		{
			String name = "Capture device " + index;
			devices.add(new VideoDevice("index:" + index, name, Integer.toString(index), index));
		}
		return devices;
	}

	public static List<AudioDevice> audioDevices()
	{
		List<AudioDevice> devices = new ArrayList<>();
		devices.add(AudioDevice.disabled());
		if (isLinux())
		{
			List<AudioDevice> pulseDevices = pulseAudioDevices();
			if (!pulseDevices.isEmpty())
			{
				devices.addAll(pulseDevices);
				return devices;
			}
		}
		for (Mixer.Info info : AudioSystem.getMixerInfo())
		{
			Mixer mixer = AudioSystem.getMixer(info);
			if (supportsCapture(mixer) && !isPlaybackWrapper(info))
			{
				String id = String.join("|", info.getName(), info.getVendor(), info.getVersion(),
					info.getDescription());
				devices.add(new AudioDevice(id, info.getName(), info));
			}
		}
		return devices;
	}

	public static AudioDevice preferredAudioDevice(VideoDevice videoDevice, List<AudioDevice> audioDevices)
	{
		if (!isLinux() || videoDevice == null)
		{
			return null;
		}
		Path videoHardware = videoHardwareParent(videoDevice);
		if (videoHardware == null)
		{
			return null;
		}
		for (AudioDevice audioDevice : audioDevices)
		{
			if (videoHardware.equals(audioHardwareParent(audioDevice)))
			{
				return audioDevice;
			}
		}
		String manufacturer = normalized(readText(videoHardware.resolve("manufacturer")));
		String product = normalized(readText(videoHardware.resolve("product")));
		AudioDevice preferred = null;
		int preferredScore = 0;
		for (AudioDevice audioDevice : audioDevices)
		{
			if (!audioDevice.enabled())
			{
				continue;
			}
			String candidate = normalized(audioDevice.id() + audioDevice.name());
			int score = 0;
			if (manufacturer.length() >= 4 && candidate.contains(manufacturer))
			{
				score += 2;
			}
			if (product.length() >= 4 && candidate.contains(product))
			{
				score += 3;
			}
			if (score > preferredScore)
			{
				preferred = audioDevice;
				preferredScore = score;
			}
		}
		return preferred;
	}

	public static String primaryVideoDeviceId(String id)
	{
		if (!isLinux() || id == null || id.isEmpty())
		{
			return id;
		}
		try
		{
			Path requestedName = Path.of(id).getFileName();
			if (requestedName == null || !requestedName.toString().matches("video\\d+"))
			{
				return id;
			}
			Path requestedDevice = Path.of("/sys/class/video4linux", requestedName.toString(), "device");
			try (DirectoryStream<Path> entries = Files.newDirectoryStream(
				Path.of("/sys/class/video4linux"), "video*"))
			{
				for (Path entry : entries)
				{
					if (readHardwareIndex(entry.getFileName().toString()) == 0
						&& Files.isSameFile(requestedDevice, entry.resolve("device")))
					{
						return Path.of("/dev", entry.getFileName().toString()).toString();
					}
				}
			}
		}
		catch (IOException | RuntimeException ignored)
		{
		}
		return id;
	}

	public static AudioFormat supportedAudioFormat(AudioDevice device)
	{
		if (device == null || !device.enabled())
		{
			return null;
		}
		Mixer mixer = AudioSystem.getMixer(device.mixerInfo());
		for (AudioFormat format : AUDIO_FORMATS)
		{
			DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, format);
			if (mixer.isLineSupported(lineInfo))
			{
				return format;
			}
		}
		return null;
	}

	private static List<AudioDevice> pulseAudioDevices()
	{
		List<AudioDevice> devices = new ArrayList<>();
		AVDeviceInfoList sourceList = new AVDeviceInfoList();
		boolean listed = false;
		try
		{
			FFmpegFrameGrabber.tryLoad();
			avdevice_register_all();
			AVInputFormat pulse = av_find_input_format("pulse");
			if (pulse == null || pulse.isNull())
			{
				return devices;
			}
			int result = avdevice_list_input_sources(pulse, (String) null, null, sourceList);
			if (result < 0)
			{
				return devices;
			}
			listed = true;
			for (int index = 0; index < sourceList.nb_devices(); index++)
			{
				AVDeviceInfo info = sourceList.devices(index);
				String source = pointerText(info.device_name());
				String description = pointerText(info.device_description());
				if (source.isEmpty() || source.endsWith(".monitor")
					|| description.toLowerCase(Locale.ROOT).startsWith("monitor of "))
				{
					continue;
				}
				devices.add(AudioDevice.pulse(source, description.isEmpty() ? source : description));
			}
		}
		catch (Throwable ignored)
		{
			devices.clear();
		}
		finally
		{
			if (listed)
			{
				avdevice_free_list_devices(sourceList);
			}
		}
		return devices;
	}

	private static List<VideoDevice> linuxVideoDevices()
	{
		List<VideoDevice> devices = new ArrayList<>();
		Path deviceDirectory = Path.of("/dev");
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(deviceDirectory, "video*"))
		{
			for (Path entry : entries)
			{
				String fileName = entry.getFileName().toString();
				if (!fileName.matches("video\\d+") || readHardwareIndex(fileName) > 0)
				{
					continue;
				}
				int index = Integer.parseInt(fileName.substring("video".length()));
				String locator = entry.toAbsolutePath().normalize().toString();
				String hardwareName = readHardwareName(fileName);
				String name = hardwareName.isEmpty() ? locator : hardwareName + " (" + locator + ")";
				devices.add(new VideoDevice(locator, name, locator, index));
			}
		}
		catch (IOException ignored)
		{
		}
		devices.sort(Comparator.comparingInt(VideoDevice::index));
		return devices;
	}

	private static int readHardwareIndex(String deviceName)
	{
		Path indexFile = Path.of("/sys/class/video4linux", deviceName, "index");
		try
		{
			return Integer.parseInt(Files.readString(indexFile).trim());
		}
		catch (IOException | NumberFormatException ignored)
		{
			return 0;
		}
	}

	private static String readHardwareName(String deviceName)
	{
		Path nameFile = Path.of("/sys/class/video4linux", deviceName, "name");
		try
		{
			return Files.readString(nameFile).trim();
		}
		catch (IOException ignored)
		{
			return "";
		}
	}

	private static Path videoHardwareParent(VideoDevice videoDevice)
	{
		try
		{
			Path fileName = Path.of(videoDevice.locator()).getFileName();
			if (fileName == null || !fileName.toString().matches("video\\d+"))
			{
				return null;
			}
			return Path.of("/sys/class/video4linux", fileName.toString(), "device")
				.toRealPath().getParent();
		}
		catch (IOException | RuntimeException ignored)
		{
			return null;
		}
	}

	private static Path audioHardwareParent(AudioDevice audioDevice)
	{
		if (audioDevice == null || audioDevice.mixerInfo() == null)
		{
			return null;
		}
		Matcher matcher = ALSA_MIXER.matcher(audioDevice.mixerInfo().getName());
		if (!matcher.find())
		{
			return null;
		}
		try
		{
			return Path.of("/sys/class/sound", "card" + matcher.group(1), "device")
				.toRealPath().getParent();
		}
		catch (IOException | RuntimeException ignored)
		{
			return null;
		}
	}

	private static String readText(Path path)
	{
		try
		{
			return Files.readString(path).trim();
		}
		catch (IOException ignored)
		{
			return "";
		}
	}

	private static String normalized(String value)
	{
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

	private static String pointerText(BytePointer pointer)
	{
		return pointer == null || pointer.isNull() ? "" : pointer.getString();
	}

	private static boolean supportsCapture(Mixer mixer)
	{
		for (AudioFormat format : AUDIO_FORMATS)
		{
			if (mixer.isLineSupported(new DataLine.Info(TargetDataLine.class, format)))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isPlaybackWrapper(Mixer.Info info)
	{
		return info.getName().toLowerCase(Locale.ROOT).contains("playback");
	}

	private static boolean isLinux()
	{
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
	}
}
