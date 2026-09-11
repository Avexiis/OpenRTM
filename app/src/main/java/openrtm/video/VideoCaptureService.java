package openrtm.video;

import openrtm.video.VideoCaptureConfig.Decoder;
import openrtm.video.VideoCaptureConfig.Performance;
import openrtm.video.VideoCaptureConfig.Resolution;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import javax.imageio.ImageIO;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_MPEG4;
import static org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_PCM_S16LE;
import static org.bytedeco.ffmpeg.global.avutil.AV_LOG_FATAL;
import static org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P;
import static org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_PROP_BUFFERSIZE;
import static org.bytedeco.opencv.global.opencv_videoio.CAP_V4L2;

public final class VideoCaptureService implements AutoCloseable
{
	private static final long RETRY_DELAY_MS = 750;

	private final Listener listener;
	private final ExecutorService videoExecutor = Executors.newSingleThreadExecutor(r ->
	{
		Thread thread = new Thread(r, "openrtm-video-capture");
		thread.setDaemon(true);
		return thread;
	});
	private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor(r ->
	{
		Thread thread = new Thread(r, "openrtm-audio-capture");
		thread.setDaemon(true);
		return thread;
	});
	private final AtomicLong generation = new AtomicLong();
	private final Object imageLock = new Object();
	private volatile TargetDataLine activeAudioInput;
	private volatile SourceDataLine activeAudioOutput;
	private volatile AudioFormat activeAudioFormat;
	private volatile RecordingSession recording;
	private volatile BufferedImage latestImage;
	private volatile double latestFrameRate;
	private volatile boolean closed;

	public VideoCaptureService(Listener listener)
	{
		this.listener = listener;
		FFmpegLogCallback.setLevel(AV_LOG_FATAL);
	}

	public synchronized void start(VideoCaptureConfig config)
	{
		if (closed)
		{
			throw new IllegalStateException("Video capture is closed");
		}
		stopCapture();
		long token = generation.incrementAndGet();
		latestImage = null;
		latestFrameRate = 0;
		listener.onState(State.STARTING, "Starting video capture");
		videoExecutor.submit(() -> captureLoop(token, config));
		if (config.audioDevice() != null && config.audioDevice().enabled())
		{
			audioExecutor.submit(() -> audioLoop(token, config));
		}
		else
		{
			listener.onAudioState("Audio off");
		}
	}

	public synchronized void stopCapture()
	{
		generation.incrementAndGet();
		closeAudioLines();
		stopRecording();
		latestImage = null;
		latestFrameRate = 0;
		listener.onState(State.STOPPED, "Capture stopped");
	}

	public synchronized void startRecording(Path destination) throws IOException
	{
		if (recording != null)
		{
			throw new IllegalStateException("Recording is already active");
		}
		BufferedImage image = latestImage;
		if (image == null)
		{
			throw new IllegalStateException("No video frame is available to record");
		}
		Path output = destination.toAbsolutePath().normalize();
		Path parent = output.getParent();
		if (parent != null)
		{
			Files.createDirectories(parent);
		}
		AudioFormat audioFormat = activeAudioFormat;
		RecordingSession session = new RecordingSession(output, image.getWidth(), image.getHeight(),
			latestFrameRate, audioFormat, listener);
		recording = session;
		listener.onRecordingChanged(true, output, session.hasAudio());
	}

	public synchronized Path stopRecording()
	{
		RecordingSession session = recording;
		if (session == null)
		{
			return null;
		}
		recording = null;
		session.close();
		listener.onRecordingChanged(false, session.destination(), session.hasAudio());
		return session.destination();
	}

	public boolean isRecording()
	{
		return recording != null;
	}

	public Path saveScreenshot(Path destination) throws IOException
	{
		BufferedImage snapshot;
		synchronized (imageLock)
		{
			if (latestImage == null)
			{
				throw new IllegalStateException("No video frame is available");
			}
			snapshot = Java2DFrameConverter.cloneBufferedImage(latestImage);
		}
		Path output = destination.toAbsolutePath().normalize();
		Path parent = output.getParent();
		if (parent != null)
		{
			Files.createDirectories(parent);
		}
		if (!ImageIO.write(snapshot, "png", output.toFile()))
		{
			throw new IOException("PNG image support is unavailable");
		}
		return output;
	}

	public BufferedImage latestImage()
	{
		return latestImage;
	}

	@Override
	public synchronized void close()
	{
		if (closed)
		{
			return;
		}
		closed = true;
		stopCapture();
		videoExecutor.shutdownNow();
		audioExecutor.shutdownNow();
	}

	private void captureLoop(long token, VideoCaptureConfig config)
	{
		while (current(token))
		{
			for (Decoder decoder : decoderOrder(config.decoder()))
			{
				if (!current(token))
				{
					return;
				}
				FrameGrabber grabber = null;
				try
				{
					grabber = createGrabber(config, decoder);
					startGrabber(grabber, config.performance());
					readFrames(token, grabber, decoder);
				}
				catch (Throwable failure)
				{
					if (current(token) && config.decoder() != Decoder.AUTOMATIC)
					{
						listener.onState(State.NO_INPUT, "No input source detected");
					}
				}
				finally
				{
					closeGrabber(grabber);
				}
				if (!current(token))
				{
					return;
				}
				if (config.decoder() != Decoder.AUTOMATIC)
				{
					break;
				}
			}
			if (current(token))
			{
				synchronized (imageLock)
				{
					latestImage = null;
				}
				listener.onState(State.NO_INPUT, "No input source detected");
				sleepBeforeRetry();
			}
		}
	}

	private void readFrames(long token, FrameGrabber grabber, Decoder decoder) throws FrameGrabber.Exception
	{
		Java2DFrameConverter[] converters = {
			new Java2DFrameConverter(),
			new Java2DFrameConverter(),
			new Java2DFrameConverter()
		};
		int converterIndex = 0;
		boolean firstFrame = true;
		long sampleStarted = System.nanoTime();
		int sampleFrames = 0;
		double framesPerSecond = grabber.getFrameRate();
		if (!Double.isFinite(framesPerSecond) || framesPerSecond < 1 || framesPerSecond > 240)
		{
			framesPerSecond = 0;
		}
		latestFrameRate = framesPerSecond;
		while (current(token))
		{
			Frame frame = grabImage(grabber);
			if (frame == null || frame.image == null)
			{
				listener.onState(State.NO_INPUT, "No input source detected");
				throw new FrameGrabber.Exception("No video frame received");
			}
			sampleFrames++;
			long now = System.nanoTime();
			long elapsed = now - sampleStarted;
			if (elapsed >= TimeUnit.SECONDS.toNanos(1))
			{
				framesPerSecond = sampleFrames * 1_000_000_000.0 / elapsed;
				sampleFrames = 0;
				sampleStarted = now;
				latestFrameRate = framesPerSecond;
			}
			RecordingSession session = recording;
			if (session != null)
			{
				session.offerVideo(frame, now);
			}
			BufferedImage image;
			synchronized (imageLock)
			{
				image = converters[converterIndex].convert(frame);
				converterIndex = (converterIndex + 1) % converters.length;
				if (image != null)
				{
					latestImage = image;
				}
			}
			if (image != null)
			{
				if (firstFrame)
				{
					firstFrame = false;
					listener.onState(State.LIVE, "Video input detected using " + decoder);
				}
				listener.onFrame(image, framesPerSecond, decoder);
			}
		}
	}

	private Frame grabImage(FrameGrabber grabber) throws FrameGrabber.Exception
	{
		if (grabber instanceof FFmpegFrameGrabber)
		{
			return ((FFmpegFrameGrabber) grabber).grabImage();
		}
		return grabber.grab();
	}

	private FrameGrabber createGrabber(VideoCaptureConfig config, Decoder decoder)
	{
		FrameGrabber grabber;
		if (decoder == Decoder.FFMPEG)
		{
			FFmpegFrameGrabber ffmpeg = new FFmpegFrameGrabber(ffmpegLocator(config.videoDevice()));
			ffmpeg.setFormat(ffmpegFormat());
			if (config.performance() == Performance.LOWEST_LATENCY)
			{
				ffmpeg.setOption("fflags", "nobuffer+discardcorrupt");
				ffmpeg.setVideoOption("flags", "low_delay");
				ffmpeg.setOption("avioflags", "direct");
				ffmpeg.setOption("analyzeduration", "0");
				ffmpeg.setOption("probesize", "32");
			}
			else
			{
				ffmpeg.setOption("fflags", "discardcorrupt");
			}
			if (isLinux() && config.performance() != Performance.COMPATIBILITY)
			{
				ffmpeg.setOption("input_format", "mjpeg");
			}
			grabber = ffmpeg;
		}
		else
		{
			OpenCVFrameGrabber openCv = isLinux()
				? new OpenCVFrameGrabber(config.videoDevice().locator(), CAP_V4L2)
				: new OpenCVFrameGrabber(config.videoDevice().index());
			openCv.setOption(CAP_PROP_BUFFERSIZE, config.performance() == Performance.COMPATIBILITY ? 4 : 1);
			if (config.performance() != Performance.COMPATIBILITY)
			{
				openCv.setFormat("MJPG");
			}
			grabber = openCv;
		}
		if (config.resolution().width() > 0)
		{
			grabber.setImageWidth(config.resolution().width());
			grabber.setImageHeight(config.resolution().height());
		}
		int requestedFrameRate = config.frameRate().framesPerSecond();
		if (requestedFrameRate == 0)
		{
			requestedFrameRate = config.performance() == Performance.HIGH_FRAME_RATE
				|| config.resolution() == Resolution.HD_720 ? 60 : 30;
		}
		grabber.setFrameRate(requestedFrameRate);
		grabber.setNumBuffers(config.performance() == Performance.COMPATIBILITY ? 4 : 1);
		grabber.setTimeout(config.performance() == Performance.COMPATIBILITY ? 5_000 : 1_500);
		return grabber;
	}

	private void startGrabber(FrameGrabber grabber, Performance performance) throws FrameGrabber.Exception
	{
		if (grabber instanceof FFmpegFrameGrabber)
		{
			((FFmpegFrameGrabber) grabber).start(performance != Performance.LOWEST_LATENCY);
		}
		else
		{
			grabber.start();
		}
	}

	private void audioLoop(long token, VideoCaptureConfig config)
	{
		if (config.audioDevice().usesPulse())
		{
			pulseAudioLoop(token, config);
		}
		else
		{
			javaSoundAudioLoop(token, config);
		}
	}

	private void javaSoundAudioLoop(long token, VideoCaptureConfig config)
	{
		AudioFormat format = CaptureDeviceDiscovery.supportedAudioFormat(config.audioDevice());
		if (format == null)
		{
			listener.onAudioState("Audio input unavailable");
			return;
		}
		TargetDataLine input = null;
		SourceDataLine output = null;
		try
		{
			Mixer mixer = AudioSystem.getMixer(config.audioDevice().mixerInfo());
			DataLine.Info inputInfo = new DataLine.Info(TargetDataLine.class, format);
			input = (TargetDataLine) mixer.getLine(inputInfo);
			int bufferSize = Math.max(format.getFrameSize() * 256,
				(int) (format.getFrameRate() * format.getFrameSize() / 25));
			input.open(format, bufferSize);
			if (config.monitorAudio())
			{
				DataLine.Info outputInfo = new DataLine.Info(SourceDataLine.class, format);
				output = (SourceDataLine) AudioSystem.getLine(outputInfo);
				output.open(format, bufferSize);
				output.start();
			}
			activeAudioInput = input;
			activeAudioOutput = output;
			activeAudioFormat = format;
			input.start();
			listener.onAudioState(config.monitorAudio() ? "Audio live and monitored" : "Audio live");
			readAudio(token, input, output, format, bufferSize);
		}
		catch (LineUnavailableException | IllegalArgumentException failure)
		{
			if (current(token))
			{
				listener.onAudioState("Audio input unavailable");
			}
		}
		finally
		{
			if (activeAudioInput == input)
			{
				activeAudioInput = null;
			}
			if (activeAudioOutput == output)
			{
				activeAudioOutput = null;
			}
			activeAudioFormat = null;
			closeLine(input);
			closeLine(output);
		}
	}

	private void pulseAudioLoop(long token, VideoCaptureConfig config)
	{
		AudioFormat format = new AudioFormat(48_000, 16, 2, true, false);
		int bufferSize = (int) (format.getFrameRate() * format.getFrameSize() / 25);
		FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(config.audioDevice().pulseSource());
		FFmpegFrameRecorder monitor = null;
		try
		{
			grabber.setFormat("pulse");
			grabber.setSampleRate((int) format.getSampleRate());
			grabber.setAudioChannels(format.getChannels());
			grabber.setSampleFormat(AV_SAMPLE_FMT_S16);
			grabber.setTimeout(2_000);
			grabber.setOption("fragment_size", Integer.toString(bufferSize / 4));
			grabber.start();
			if (config.monitorAudio())
			{
				monitor = startPulseMonitor(format, bufferSize);
			}
			activeAudioFormat = format;
			listener.onAudioState(config.monitorAudio() ? "Audio live and monitored" : "Audio live");
			readPulseAudio(token, grabber, monitor, format);
		}
		catch (FrameGrabber.Exception | FrameRecorder.Exception | IllegalArgumentException failure)
		{
			if (current(token))
			{
				listener.onAudioState("Audio input unavailable");
			}
		}
		finally
		{
			activeAudioFormat = null;
			closeRecorder(monitor);
			closeGrabber(grabber);
		}
	}

	private FFmpegFrameRecorder startPulseMonitor(AudioFormat format, int bufferSize)
		throws FrameRecorder.Exception
	{
		FFmpegFrameRecorder monitor = new FFmpegFrameRecorder("default", format.getChannels());
		monitor.setFormat("pulse");
		monitor.setAudioCodec(AV_CODEC_ID_PCM_S16LE);
		monitor.setSampleRate((int) format.getSampleRate());
		monitor.setSampleFormat(AV_SAMPLE_FMT_S16);
		monitor.setOption("name", "OpenRTM");
		monitor.setOption("stream_name", "Xbox 360 Capture Monitor");
		monitor.setOption("device", "default");
		monitor.setOption("buffer_size", Integer.toString(bufferSize));
		monitor.setOption("prebuf", "0");
		monitor.setOption("minreq", Integer.toString(bufferSize / 4));
		try
		{
			monitor.start();
			return monitor;
		}
		catch (FrameRecorder.Exception failure)
		{
			closeRecorder(monitor);
			throw failure;
		}
	}

	private void readAudio(long token, TargetDataLine input, SourceDataLine output,
		AudioFormat format, int bufferSize)
	{
		byte[] buffer = new byte[Math.max(format.getFrameSize() * 128, bufferSize / 4)];
		while (current(token))
		{
			int read = input.read(buffer, 0, buffer.length);
			if (read <= 0)
			{
				continue;
			}
			if (output != null)
			{
				output.write(buffer, 0, read);
			}
			RecordingSession session = recording;
			if (session != null && session.hasAudio())
			{
				short[] samples = new short[read / 2];
				ByteBuffer.wrap(buffer, 0, read).order(ByteOrder.LITTLE_ENDIAN)
					.asShortBuffer().get(samples);
				session.offerAudio(samples, format, System.nanoTime());
			}
		}
	}

	private void readPulseAudio(long token, FFmpegFrameGrabber grabber, FFmpegFrameRecorder monitor,
		AudioFormat format) throws FrameGrabber.Exception, FrameRecorder.Exception
	{
		while (current(token))
		{
			Frame frame = grabber.grabSamples();
			short[] samples = shortSamples(frame, format.getChannels());
			if (samples.length == 0)
			{
				continue;
			}
			if (monitor != null)
			{
				monitor.recordSamples((int) format.getSampleRate(), format.getChannels(),
					ShortBuffer.wrap(samples));
			}
			RecordingSession session = recording;
			if (session != null && session.hasAudio())
			{
				session.offerAudio(samples, format, System.nanoTime());
			}
		}
	}

	private static short[] shortSamples(Frame frame, int channels)
	{
		if (frame == null || frame.samples == null || frame.samples.length == 0)
		{
			return new short[0];
		}
		if (frame.samples.length == 1 && frame.samples[0] instanceof ShortBuffer)
		{
			ShortBuffer source = ((ShortBuffer) frame.samples[0]).duplicate();
			short[] samples = new short[source.remaining()];
			source.get(samples);
			return samples;
		}
		if (frame.samples.length < channels)
		{
			return new short[0];
		}
		ShortBuffer[] sources = new ShortBuffer[channels];
		int frames = Integer.MAX_VALUE;
		for (int channel = 0; channel < channels; channel++)
		{
			Buffer buffer = frame.samples[channel];
			if (!(buffer instanceof ShortBuffer))
			{
				return new short[0];
			}
			sources[channel] = ((ShortBuffer) buffer).duplicate();
			frames = Math.min(frames, sources[channel].remaining());
		}
		short[] samples = new short[frames * channels];
		for (int frameIndex = 0; frameIndex < frames; frameIndex++)
		{
			for (int channel = 0; channel < channels; channel++)
			{
				samples[frameIndex * channels + channel] = sources[channel].get();
			}
		}
		return samples;
	}

	private static void closeRecorder(FFmpegFrameRecorder recorder)
	{
		if (recorder == null)
		{
			return;
		}
		try
		{
			recorder.close();
		}
		catch (FrameRecorder.Exception ignored)
		{
		}
	}

	private List<Decoder> decoderOrder(Decoder selected)
	{
		List<Decoder> decoders = new ArrayList<>();
		if (selected != Decoder.AUTOMATIC)
		{
			decoders.add(selected);
			return decoders;
		}
		if (isLinux())
		{
			decoders.add(Decoder.FFMPEG);
			decoders.add(Decoder.OPENCV);
		}
		else
		{
			decoders.add(Decoder.OPENCV);
			decoders.add(Decoder.FFMPEG);
		}
		return decoders;
	}

	private String ffmpegLocator(VideoDevice device)
	{
		if (isWindows())
		{
			return "video=" + device.locator();
		}
		if (isMac())
		{
			return device.index() + ":none";
		}
		return device.locator();
	}

	private String ffmpegFormat()
	{
		if (isWindows())
		{
			return "dshow";
		}
		if (isMac())
		{
			return "avfoundation";
		}
		return "v4l2";
	}

	private boolean current(long token)
	{
		return !closed && generation.get() == token && !Thread.currentThread().isInterrupted();
	}

	private void closeAudioLines()
	{
		TargetDataLine input = activeAudioInput;
		SourceDataLine output = activeAudioOutput;
		activeAudioInput = null;
		activeAudioOutput = null;
		activeAudioFormat = null;
		closeLine(input);
		closeLine(output);
	}

	private static void closeGrabber(FrameGrabber grabber)
	{
		if (grabber == null)
		{
			return;
		}
		try
		{
			grabber.close();
		}
		catch (FrameGrabber.Exception ignored)
		{
		}
	}

	private static void closeLine(DataLine line)
	{
		if (line == null)
		{
			return;
		}
		line.stop();
		line.flush();
		line.close();
	}

	private static void sleepBeforeRetry()
	{
		try
		{
			Thread.sleep(RETRY_DELAY_MS);
		}
		catch (InterruptedException ignored)
		{
			Thread.currentThread().interrupt();
		}
	}

	private static boolean isLinux()
	{
		return osName().contains("linux");
	}

	private static boolean isWindows()
	{
		return osName().contains("windows");
	}

	private static boolean isMac()
	{
		return osName().contains("mac");
	}

	private static String osName()
	{
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
	}

	public enum State
	{
		STARTING,
		LIVE,
		NO_INPUT,
		STOPPED
	}

	public interface Listener
	{
		void onFrame(BufferedImage image, double framesPerSecond, Decoder decoder);

		void onState(State state, String message);

		void onAudioState(String message);

		void onRecordingChanged(boolean active, Path destination, boolean hasAudio);

		void onRecordingFailure(String message);
	}

	private static final class RecordingSession implements AutoCloseable
	{
		private static final int QUEUE_CAPACITY = 12;

		private final Path destination;
		private final FFmpegFrameRecorder recorder;
		private final ArrayBlockingQueue<MediaSample> samples = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
		private final Thread worker;
		private final long startedAt = System.nanoTime();
		private final Listener listener;
		private final boolean audio;
		private volatile boolean accepting = true;

		private RecordingSession(Path destination, int width, int height, double measuredFrameRate,
			AudioFormat audioFormat, Listener listener) throws IOException
		{
			this.destination = destination;
			this.listener = listener;
			audio = audioFormat != null;
			recorder = new FFmpegFrameRecorder(destination.toFile(), width, height,
				audio ? audioFormat.getChannels() : 0);
			recorder.setFormat("mp4");
			recorder.setVideoCodec(AV_CODEC_ID_MPEG4);
			recorder.setPixelFormat(AV_PIX_FMT_YUV420P);
			double recordingFrameRate = measuredFrameRate >= 20 ? Math.rint(measuredFrameRate) : 60;
			recordingFrameRate = Math.max(20, Math.min(60, recordingFrameRate));
			recorder.setFrameRate(recordingFrameRate);
			recorder.setGopSize((int) Math.round(recordingFrameRate * 2));
			recorder.setVideoBitrate(width >= 1920 ? 20_000_000 : 10_000_000);
			recorder.setMaxBFrames(0);
			recorder.setInterleaved(true);
			if (audio)
			{
				recorder.setAudioCodec(AV_CODEC_ID_AAC);
				recorder.setSampleRate((int) audioFormat.getSampleRate());
				recorder.setAudioBitrate(192_000);
			}
			try
			{
				recorder.start();
			}
			catch (FrameRecorder.Exception failure)
			{
				throw new IOException("Could not start MP4 recording", failure);
			}
			worker = new Thread(this::writeLoop, "openrtm-video-recorder");
			worker.setDaemon(true);
			worker.start();
		}

		private Path destination()
		{
			return destination;
		}

		private boolean hasAudio()
		{
			return audio;
		}

		private void offerVideo(Frame frame, long capturedAt)
		{
			if (!accepting)
			{
				return;
			}
			VideoSample sample = new VideoSample(frame.clone(), timestamp(capturedAt));
			if (!samples.offer(sample))
			{
				sample.close();
			}
		}

		private void offerAudio(short[] data, AudioFormat format, long capturedAt)
		{
			if (!accepting || !audio)
			{
				return;
			}
			samples.offer(new AudioSample(data, (int) format.getSampleRate(), format.getChannels(),
				timestamp(capturedAt)));
		}

		private long timestamp(long capturedAt)
		{
			return Math.max(0, TimeUnit.NANOSECONDS.toMicros(capturedAt - startedAt));
		}

		private void writeLoop()
		{
			try
			{
				while (accepting || !samples.isEmpty())
				{
					MediaSample sample = samples.poll(100, TimeUnit.MILLISECONDS);
					if (sample != null)
					{
						try
						{
							sample.write(recorder);
						}
						finally
						{
							sample.close();
						}
					}
				}
			}
			catch (InterruptedException ignored)
			{
				Thread.currentThread().interrupt();
			}
			catch (FrameRecorder.Exception failure)
			{
				listener.onRecordingFailure("Recording stopped because the video could not be encoded");
			}
			finally
			{
				MediaSample remaining;
				while ((remaining = samples.poll()) != null)
				{
					remaining.close();
				}
				try
				{
					recorder.close();
				}
				catch (FrameRecorder.Exception ignored)
				{
				}
			}
		}

		@Override
		public void close()
		{
			accepting = false;
			try
			{
				worker.join(3_000);
			}
			catch (InterruptedException ignored)
			{
				Thread.currentThread().interrupt();
			}
			if (worker.isAlive())
			{
				worker.interrupt();
			}
		}
	}

	private abstract static class MediaSample implements AutoCloseable
	{
		private final long timestamp;

		private MediaSample(long timestamp)
		{
			this.timestamp = timestamp;
		}

		private void write(FFmpegFrameRecorder recorder)
			throws FrameRecorder.Exception
		{
			if (timestamp > recorder.getTimestamp())
			{
				recorder.setTimestamp(timestamp);
			}
			writeValue(recorder);
		}

		protected abstract void writeValue(FFmpegFrameRecorder recorder)
			throws FrameRecorder.Exception;

		@Override
		public abstract void close();
	}

	private static final class VideoSample extends MediaSample
	{
		private final Frame frame;

		private VideoSample(Frame frame, long timestamp)
		{
			super(timestamp);
			this.frame = frame;
		}

		@Override
		protected void writeValue(FFmpegFrameRecorder recorder)
			throws FrameRecorder.Exception
		{
			recorder.record(frame);
		}

		@Override
		public void close()
		{
			frame.close();
		}
	}

	private static final class AudioSample extends MediaSample
	{
		private final short[] data;
		private final int sampleRate;
		private final int channels;

		private AudioSample(short[] data, int sampleRate, int channels, long timestamp)
		{
			super(timestamp);
			this.data = data;
			this.sampleRate = sampleRate;
			this.channels = channels;
		}

		@Override
		protected void writeValue(FFmpegFrameRecorder recorder)
			throws FrameRecorder.Exception
		{
			ShortBuffer buffer = ShortBuffer.wrap(data);
			recorder.recordSamples(sampleRate, channels, buffer);
		}

		@Override
		public void close()
		{
		}
	}
}
