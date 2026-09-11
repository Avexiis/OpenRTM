package openrtm.video;

public final class VideoCaptureConfig
{
	private final VideoDevice videoDevice;
	private final AudioDevice audioDevice;
	private final Decoder decoder;
	private final Resolution resolution;
	private final FrameRate frameRate;
	private final Performance performance;
	private final boolean monitorAudio;

	public VideoCaptureConfig(VideoDevice videoDevice, AudioDevice audioDevice, Decoder decoder,
		Resolution resolution, FrameRate frameRate, Performance performance, boolean monitorAudio)
	{
		this.videoDevice = videoDevice;
		this.audioDevice = audioDevice;
		this.decoder = decoder;
		this.resolution = resolution;
		this.frameRate = frameRate;
		this.performance = performance;
		this.monitorAudio = monitorAudio;
	}

	public VideoDevice videoDevice()
	{
		return videoDevice;
	}

	public AudioDevice audioDevice()
	{
		return audioDevice;
	}

	public Decoder decoder()
	{
		return decoder;
	}

	public Resolution resolution()
	{
		return resolution;
	}

	public FrameRate frameRate()
	{
		return frameRate;
	}

	public Performance performance()
	{
		return performance;
	}

	public boolean monitorAudio()
	{
		return monitorAudio;
	}

	public enum Decoder
	{
		AUTOMATIC("Automatic"),
		FFMPEG("FFmpeg"),
		OPENCV("OpenCV");

		private final String label;

		Decoder(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public enum Resolution
	{
		SOURCE("Source resolution", 0, 0),
		HD_1080("1080p", 1920, 1080),
		HD_720("720p", 1280, 720);

		private final String label;
		private final int width;
		private final int height;

		Resolution(String label, int width, int height)
		{
			this.label = label;
			this.width = width;
			this.height = height;
		}

		public int width()
		{
			return width;
		}

		public int height()
		{
			return height;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public enum FrameRate
	{
		SOURCE("Source FPS", 0),
		FPS_60("60 FPS", 60),
		FPS_30("30 FPS", 30);

		private final String label;
		private final int framesPerSecond;

		FrameRate(String label, int framesPerSecond)
		{
			this.label = label;
			this.framesPerSecond = framesPerSecond;
		}

		public int framesPerSecond()
		{
			return framesPerSecond;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public enum Performance
	{
		LOWEST_LATENCY("Lowest latency"),
		HIGH_FRAME_RATE("High frame rate"),
		COMPATIBILITY("Compatibility");

		private final String label;

		Performance(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
