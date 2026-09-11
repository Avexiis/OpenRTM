package openrtm.ui;

import openrtm.config.VideoSettings;
import openrtm.video.AudioDevice;
import openrtm.video.CaptureDeviceDiscovery;
import openrtm.video.VideoCaptureConfig;
import openrtm.video.VideoCaptureConfig.Decoder;
import openrtm.video.VideoCaptureConfig.FrameRate;
import openrtm.video.VideoCaptureConfig.Performance;
import openrtm.video.VideoCaptureConfig.Resolution;
import openrtm.video.VideoCaptureService;
import openrtm.video.VideoDevice;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class VideoCapturePanel extends JPanel implements VideoCaptureService.Listener
{
	private static final Color LINE = new Color(55, 60, 66);
	private static final Color ACCENT = new Color(91, 141, 239);
	private static final Color OK = new Color(92, 184, 117);
	private static final Color WARN = new Color(210, 157, 73);
	private static final Color RECORDING = new Color(214, 78, 78);
	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

	private final VideoSettings settings = new VideoSettings();
	private final VideoCaptureService capture = new VideoCaptureService(this);
	private final ExecutorService tasks = Executors.newSingleThreadExecutor(r ->
	{
		Thread thread = new Thread(r, "openrtm-video-task");
		thread.setDaemon(true);
		return thread;
	});
	private final JComboBox<VideoDevice> videoDevices = new JComboBox<>();
	private final JComboBox<AudioDevice> audioDevices = new JComboBox<>();
	private final JComboBox<Decoder> decoders = new JComboBox<>(Decoder.values());
	private final JComboBox<Resolution> resolutions = new JComboBox<>(Resolution.values());
	private final JComboBox<FrameRate> frameRates = new JComboBox<>(FrameRate.values());
	private final JComboBox<Performance> performanceModes = new JComboBox<>(Performance.values());
	private final JCheckBox monitorAudio = new JCheckBox("Monitor audio");
	private final JCheckBox detachedViewer = new JCheckBox("Detached viewer");
	private final JCheckBox lockViewerSize = new JCheckBox("Lock viewer size");
	private final JTextField captureDirectory = new JTextField(38);
	private final JButton record = new JButton("Record");
	private final JButton screenshot = new JButton("Screenshot");
	private final JButton refresh = new JButton("Refresh devices");
	private final JLabel status = new JLabel("No input source detected");
	private final JLabel audioStatus = new JLabel("Audio off");
	private final VideoDisplayPanel display = new VideoDisplayPanel();
	private final Object frameLock = new Object();
	private final AtomicBoolean frameUpdatePending = new AtomicBoolean();
	private BufferedImage pendingImage;
	private BufferedImage displayedImage;
	private BufferedImage reusableImage;
	private double pendingFrameRate;
	private Decoder pendingDecoder;
	private volatile boolean captureRequested;
	private boolean recordActionPending;
	private boolean screenshotActionPending;
	private boolean shuttingDown;
	private boolean loading = true;
	private VideoViewerWindow viewer;

	public VideoCapturePanel()
	{
		super(new BorderLayout(10, 10));
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
		add(settingsPanel(), BorderLayout.WEST);
		JPanel viewerPanel = new JPanel(new BorderLayout(0, 8));
		viewerPanel.add(new AspectRatioPanel(display, 16, 9), BorderLayout.CENTER);
		viewerPanel.add(actionPanel(), BorderLayout.SOUTH);
		add(viewerPanel, BorderLayout.CENTER);
		loadSettings();
		bindActions();
		loading = false;
		refreshDevices();
		if (detachedViewer.isSelected())
		{
			SwingUtilities.invokeLater(this::openViewer);
		}
	}

	public void shutdown()
	{
		shuttingDown = true;
		capture.close();
		tasks.shutdownNow();
		VideoViewerWindow current = viewer;
		viewer = null;
		if (current != null)
		{
			current.dispose();
		}
	}

	@Override
	public void onFrame(BufferedImage image, double framesPerSecond, Decoder decoder)
	{
		synchronized (frameLock)
		{
			BufferedImage target = writableImage(image);
			copyImage(image, target);
			BufferedImage replaced = pendingImage;
			pendingImage = target;
			if (replaced != null && replaced != target)
			{
				reusableImage = replaced;
			}
			pendingFrameRate = framesPerSecond;
			pendingDecoder = decoder;
		}
		queueFrameUpdate();
	}

	@Override
	public void onState(VideoCaptureService.State state, String message)
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText(message);
			status.setForeground(state == VideoCaptureService.State.LIVE ? OK : WARN);
			if (state == VideoCaptureService.State.NO_INPUT || state == VideoCaptureService.State.STOPPED)
			{
				record.setEnabled(capture.isRecording() && !recordActionPending);
				screenshot.setEnabled(false);
				clearFrameBuffers();
				display.clear();
				if (viewer != null)
				{
					viewer.clear();
				}
			}
		});
	}

	@Override
	public void onAudioState(String message)
	{
		SwingUtilities.invokeLater(() -> audioStatus.setText(message));
	}

	@Override
	public void onRecordingChanged(boolean active, Path destination, boolean hasAudio)
	{
		SwingUtilities.invokeLater(() ->
		{
			recordActionPending = false;
			record.setEnabled(captureRequested && capture.latestImage() != null);
			record.setText(active ? "Stop recording" : "Record");
			if (!active)
			{
				status.setText("Recording saved to " + destination.getFileName());
				status.setForeground(OK);
			}
		});
	}

	@Override
	public void onRecordingFailure(String message)
	{
		tasks.submit(() ->
		{
			capture.stopRecording();
			SwingUtilities.invokeLater(() ->
			{
				recordActionPending = false;
				record.setEnabled(captureRequested);
				status.setText(message);
				status.setForeground(WARN);
				JOptionPane.showMessageDialog(this, message, "Recording", JOptionPane.ERROR_MESSAGE);
			});
		});
	}

	private void showPendingFrame()
	{
		if (!captureRequested)
		{
			synchronized (frameLock)
			{
				pendingImage = null;
			}
			frameUpdatePending.set(false);
			return;
		}
		BufferedImage image;
		BufferedImage previous;
		double framesPerSecond;
		Decoder decoder;
		synchronized (frameLock)
		{
			image = pendingImage;
			pendingImage = null;
			previous = displayedImage;
			displayedImage = image;
			framesPerSecond = pendingFrameRate;
			decoder = pendingDecoder;
		}
		if (image != null)
		{
			display.showFrame(image);
			if (viewer != null)
			{
				viewer.showFrame(image);
			}
			String rate = framesPerSecond > 0
				? String.format(Locale.ROOT, "%.1f FPS", framesPerSecond)
				: "Measuring FPS";
			status.setText(image.getWidth() + " x " + image.getHeight() + " | " + rate + " | " + decoder);
			status.setForeground(OK);
			record.setEnabled(!recordActionPending);
			screenshot.setEnabled(!screenshotActionPending);
		}
		synchronized (frameLock)
		{
			if (reusableImage == null)
			{
				reusableImage = previous;
			}
		}
		frameUpdatePending.set(false);
		synchronized (frameLock)
		{
			if (pendingImage != null)
			{
				queueFrameUpdate();
			}
		}
	}

	private void queueFrameUpdate()
	{
		if (frameUpdatePending.compareAndSet(false, true))
		{
			SwingUtilities.invokeLater(this::showPendingFrame);
		}
	}

	private BufferedImage writableImage(BufferedImage source)
	{
		BufferedImage target = reusableImage;
		reusableImage = null;
		if (!compatibleImage(target, source))
		{
			target = null;
		}
		if (target == null && compatibleImage(pendingImage, source))
		{
			target = pendingImage;
		}
		if (target == null)
		{
			target = new BufferedImage(source.getWidth(), source.getHeight(), imageType(source));
		}
		return target;
	}

	private void copyImage(BufferedImage source, BufferedImage target)
	{
		DataBuffer sourceBuffer = source.getRaster().getDataBuffer();
		DataBuffer targetBuffer = target.getRaster().getDataBuffer();
		if (source.getType() == target.getType()
			&& sourceBuffer instanceof DataBufferByte && targetBuffer instanceof DataBufferByte)
		{
			byte[] sourceData = ((DataBufferByte) sourceBuffer).getData();
			byte[] targetData = ((DataBufferByte) targetBuffer).getData();
			if (sourceData.length == targetData.length)
			{
				System.arraycopy(sourceData, 0, targetData, 0, sourceData.length);
				return;
			}
		}
		Graphics2D graphics = target.createGraphics();
		try
		{
			graphics.setComposite(AlphaComposite.Src);
			graphics.drawImage(source, 0, 0, null);
		}
		finally
		{
			graphics.dispose();
		}
	}

	private void clearFrameBuffers()
	{
		synchronized (frameLock)
		{
			pendingImage = null;
			displayedImage = null;
			reusableImage = null;
		}
	}

	private static boolean compatibleImage(BufferedImage target, BufferedImage source)
	{
		return target != null && target.getWidth() == source.getWidth()
			&& target.getHeight() == source.getHeight() && target.getType() == imageType(source);
	}

	private static int imageType(BufferedImage source)
	{
		return source.getType() == BufferedImage.TYPE_CUSTOM
			? BufferedImage.TYPE_3BYTE_BGR : source.getType();
	}

	private JComponent settingsPanel()
	{
		videoDevices.setPrototypeDisplayValue(new VideoDevice("prototype",
			"USB HDMI capture (/dev/video0)", "", 0));
		audioDevices.setPrototypeDisplayValue(new AudioDevice("prototype", "USB HDMI capture audio", null));
		JPanel settings = new JPanel();
		settings.setLayout(new BoxLayout(settings, BoxLayout.Y_AXIS));
		settings.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 12));
		settings.add(sourceSettings());
		settings.add(videoSettings());
		settings.add(viewerSettings());
		settings.add(captureSettings());

		JPanel sidebar = new JPanel(new BorderLayout());
		sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, LINE));
		sidebar.setPreferredSize(new Dimension(340, 0));
		sidebar.add(settings, BorderLayout.NORTH);
		return sidebar;
	}

	private JPanel sourceSettings()
	{
		JPanel panel = settingsSection("Source");
		panel.add(labeledControl("Video input", videoDevices));
		panel.add(Box.createVerticalStrut(6));
		JPanel refreshRow = optionRow();
		refreshRow.add(refresh);
		panel.add(refreshRow);
		panel.add(Box.createVerticalStrut(8));
		panel.add(labeledControl("Audio input", audioDevices));
		JPanel monitorRow = optionRow();
		monitorRow.add(monitorAudio);
		panel.add(monitorRow);
		return panel;
	}

	private JPanel videoSettings()
	{
		JPanel panel = settingsSection("Video");
		JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.add(labeledControl("Resolution", resolutions));
		grid.add(labeledControl("Frame rate", frameRates));
		grid.add(labeledControl("Decoder", decoders));
		grid.add(labeledControl("Performance", performanceModes));
		panel.add(grid);
		return panel;
	}

	private JPanel viewerSettings()
	{
		JPanel panel = settingsSection("Viewer");
		JPanel detachedRow = optionRow();
		detachedRow.add(detachedViewer);
		panel.add(detachedRow);
		JPanel lockRow = optionRow();
		lockRow.add(lockViewerSize);
		panel.add(lockRow);
		return panel;
	}

	private JPanel captureSettings()
	{
		JPanel panel = settingsSection("Capture");
		JButton browse = new JButton("Browse");
		browse.addActionListener(e -> chooseCaptureDirectory());
		JPanel folder = new JPanel(new BorderLayout(6, 0));
		folder.add(captureDirectory, BorderLayout.CENTER);
		folder.add(browse, BorderLayout.EAST);
		panel.add(labeledControl("Capture folder", folder));
		return panel;
	}

	private JPanel settingsSection(String title)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
			BorderFactory.createEmptyBorder(10, 0, 12, 0)));
		JLabel heading = new JLabel(title);
		heading.setForeground(ACCENT);
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(heading);
		panel.add(Box.createVerticalStrut(8));
		return panel;
	}

	private JPanel labeledControl(String label, JComponent control)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(new JLabel(label), BorderLayout.NORTH);
		panel.add(control, BorderLayout.CENTER);
		return panel;
	}

	private JPanel optionRow()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private JPanel actionPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 2));
		panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
		JPanel toolbar = new JPanel(new BorderLayout());
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
		actions.add(record);
		actions.add(screenshot);
		record.setBackground(RECORDING);
		record.setForeground(Color.WHITE);
		record.setOpaque(true);
		record.setPreferredSize(new Dimension(132, 32));
		screenshot.setPreferredSize(new Dimension(108, 32));
		record.setEnabled(false);
		screenshot.setEnabled(false);
		toolbar.add(actions, BorderLayout.WEST);
		JPanel audioState = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 14));
		audioState.add(audioStatus);
		toolbar.add(audioState, BorderLayout.EAST);
		panel.add(toolbar, BorderLayout.CENTER);
		status.setForeground(WARN);
		status.setBorder(BorderFactory.createEmptyBorder(0, 8, 6, 8));
		panel.add(status, BorderLayout.SOUTH);
		return panel;
	}

	private void loadSettings()
	{
		decoders.setSelectedItem(settings.decoder());
		resolutions.setSelectedItem(settings.resolution());
		frameRates.setSelectedItem(settings.frameRate());
		performanceModes.setSelectedItem(settings.performance());
		monitorAudio.setSelected(settings.monitorAudio());
		detachedViewer.setSelected(settings.detachedViewer());
		lockViewerSize.setSelected(settings.lockViewerSize());
		captureDirectory.setText(settings.captureDirectory().toString());
	}

	private void bindActions()
	{
		record.addActionListener(e -> toggleRecording());
		screenshot.addActionListener(e -> saveScreenshot());
		refresh.addActionListener(e -> refreshDevices());
		videoDevices.addActionListener(e ->
		{
			VideoDevice selected = selectedVideoDevice();
			if (!loading && selected != null)
			{
				settings.videoDeviceId(selected.id());
				restartCapture();
			}
		});
		audioDevices.addActionListener(e ->
		{
			AudioDevice selected = selectedAudioDevice();
			if (!loading && selected != null)
			{
				settings.audioDeviceId(selected.id());
				restartCapture();
			}
		});
		decoders.addActionListener(e ->
		{
			if (!loading)
			{
				settings.decoder((Decoder) decoders.getSelectedItem());
				restartCapture();
			}
		});
		resolutions.addActionListener(e ->
		{
			if (!loading)
			{
				Resolution selected = (Resolution) resolutions.getSelectedItem();
				settings.resolution(selected);
				if (viewer != null)
				{
					viewer.resolution(selected);
				}
				restartCapture();
			}
		});
		frameRates.addActionListener(e ->
		{
			if (!loading)
			{
				settings.frameRate((FrameRate) frameRates.getSelectedItem());
				restartCapture();
			}
		});
		performanceModes.addActionListener(e ->
		{
			if (!loading)
			{
				settings.performance((Performance) performanceModes.getSelectedItem());
				restartCapture();
			}
		});
		monitorAudio.addActionListener(e ->
		{
			if (!loading)
			{
				settings.monitorAudio(monitorAudio.isSelected());
				restartCapture();
			}
		});
		detachedViewer.addActionListener(e ->
		{
			settings.detachedViewer(detachedViewer.isSelected());
			if (detachedViewer.isSelected())
			{
				openViewer();
			}
			else
			{
				closeViewer();
			}
		});
		lockViewerSize.addActionListener(e ->
		{
			settings.lockViewerSize(lockViewerSize.isSelected());
			if (viewer != null)
			{
				viewer.locked(lockViewerSize.isSelected());
			}
		});
		captureDirectory.addActionListener(e -> saveCaptureDirectory());
		captureDirectory.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusLost(FocusEvent event)
			{
				saveCaptureDirectory();
			}
		});
	}

	private void refreshDevices()
	{
		refresh.setEnabled(false);
		status.setText("Searching for capture devices");
		tasks.submit(() ->
		{
			List<VideoDevice> foundVideo = CaptureDeviceDiscovery.videoDevices();
			List<AudioDevice> foundAudio = CaptureDeviceDiscovery.audioDevices();
			SwingUtilities.invokeLater(() -> showDevices(foundVideo, foundAudio));
		});
	}

	private void showDevices(List<VideoDevice> foundVideo, List<AudioDevice> foundAudio)
	{
		loading = true;
		String videoId = selectedVideoId();
		String audioId = selectedAudioId();
		videoDevices.removeAllItems();
		foundVideo.forEach(videoDevices::addItem);
		audioDevices.removeAllItems();
		foundAudio.forEach(audioDevices::addItem);
		selectVideoDevice(videoId);
		boolean restoredAudio = selectAudioDevice(audioId);
		if (!restoredAudio)
		{
			AudioDevice preferred = CaptureDeviceDiscovery.preferredAudioDevice(
				selectedVideoDevice(), foundAudio);
			if (preferred != null)
			{
				audioDevices.setSelectedItem(preferred);
			}
		}
		loading = false;
		refresh.setEnabled(true);
		if (foundVideo.isEmpty())
		{
			if (captureRequested)
			{
				captureRequested = false;
				tasks.submit(capture::stopCapture);
			}
			status.setText("No capture devices found");
			status.setForeground(WARN);
		}
		else
		{
			VideoDevice selectedVideo = selectedVideoDevice();
			AudioDevice selectedAudio = selectedAudioDevice();
			settings.videoDeviceId(selectedVideo.id());
			settings.audioDeviceId(selectedAudio == null ? "" : selectedAudio.id());
			captureRequested = true;
			record.setEnabled(false);
			screenshot.setEnabled(false);
			VideoCaptureConfig config = currentConfig();
			tasks.submit(() -> capture.start(config));
		}
	}

	private void restartCapture()
	{
		if (captureRequested && selectedVideoDevice() != null)
		{
			VideoCaptureConfig config = currentConfig();
			tasks.submit(() -> capture.start(config));
		}
	}

	private VideoCaptureConfig currentConfig()
	{
		return new VideoCaptureConfig(selectedVideoDevice(), selectedAudioDevice(),
			(Decoder) decoders.getSelectedItem(), (Resolution) resolutions.getSelectedItem(),
			(FrameRate) frameRates.getSelectedItem(), (Performance) performanceModes.getSelectedItem(),
			monitorAudio.isSelected());
	}

	private void toggleRecording()
	{
		if (recordActionPending)
		{
			return;
		}
		recordActionPending = true;
		record.setEnabled(false);
		if (capture.isRecording())
		{
			tasks.submit(capture::stopRecording);
			return;
		}
		Path destination = capturePath("OpenRTM_", ".mp4");
		tasks.submit(() ->
		{
			try
			{
				capture.startRecording(destination);
			}
			catch (Exception failure)
			{
				showFailure("Recording", failure);
				SwingUtilities.invokeLater(() ->
				{
					recordActionPending = false;
					record.setEnabled(captureRequested && capture.latestImage() != null);
				});
			}
		});
	}

	private void saveScreenshot()
	{
		if (screenshotActionPending)
		{
			return;
		}
		screenshotActionPending = true;
		screenshot.setEnabled(false);
		Path destination = capturePath("OpenRTM_", ".png");
		tasks.submit(() ->
		{
			try
			{
				Path saved = capture.saveScreenshot(destination);
				SwingUtilities.invokeLater(() ->
				{
					status.setText("Screenshot saved to " + saved.getFileName());
					status.setForeground(OK);
				});
			}
			catch (Exception failure)
			{
				showFailure("Screenshot", failure);
			}
			finally
			{
				SwingUtilities.invokeLater(() ->
				{
					screenshotActionPending = false;
					screenshot.setEnabled(captureRequested && capture.latestImage() != null);
				});
			}
		});
	}

	private Path capturePath(String prefix, String extension)
	{
		saveCaptureDirectory();
		return settings.captureDirectory().resolve(prefix + FILE_TIME.format(LocalDateTime.now()) + extension);
	}

	private void chooseCaptureDirectory()
	{
		JFileChooser chooser = new JFileChooser(settings.captureDirectory().toFile());
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
			captureDirectory.setText(selected.toString());
			settings.captureDirectory(selected);
		}
	}

	private void saveCaptureDirectory()
	{
		try
		{
			String entered = captureDirectory.getText().trim();
			if (entered.isEmpty())
			{
				throw new IllegalArgumentException("A capture folder is required");
			}
			Path selected = Path.of(entered).toAbsolutePath().normalize();
			settings.captureDirectory(selected);
			captureDirectory.setText(selected.toString());
		}
		catch (RuntimeException failure)
		{
			captureDirectory.setText(settings.captureDirectory().toString());
		}
	}

	private void openViewer()
	{
		if (shuttingDown)
		{
			return;
		}
		if (viewer != null)
		{
			viewer.toFront();
			return;
		}
		Window owner = SwingUtilities.getWindowAncestor(this);
		viewer = new VideoViewerWindow(owner, (Resolution) resolutions.getSelectedItem(),
			lockViewerSize.isSelected(), this::viewerClosed);
		BufferedImage image;
		synchronized (frameLock)
		{
			image = displayedImage;
		}
		if (image != null)
		{
			viewer.showFrame(image);
		}
		viewer.setVisible(true);
	}

	private void closeViewer()
	{
		VideoViewerWindow current = viewer;
		viewer = null;
		if (current != null)
		{
			current.dispose();
		}
	}

	private void viewerClosed()
	{
		viewer = null;
		if (!shuttingDown && detachedViewer.isSelected())
		{
			detachedViewer.setSelected(false);
			settings.detachedViewer(false);
		}
	}

	private VideoDevice selectedVideoDevice()
	{
		return (VideoDevice) videoDevices.getSelectedItem();
	}

	private AudioDevice selectedAudioDevice()
	{
		return (AudioDevice) audioDevices.getSelectedItem();
	}

	private String selectedVideoId()
	{
		VideoDevice selected = selectedVideoDevice();
		return selected == null ? settings.videoDeviceId() : selected.id();
	}

	private String selectedAudioId()
	{
		AudioDevice selected = selectedAudioDevice();
		return selected == null ? settings.audioDeviceId() : selected.id();
	}

	private void selectVideoDevice(String id)
	{
		String primaryId = CaptureDeviceDiscovery.primaryVideoDeviceId(id);
		for (int index = 0; index < videoDevices.getItemCount(); index++)
		{
			if (videoDevices.getItemAt(index).id().equals(primaryId))
			{
				videoDevices.setSelectedIndex(index);
				return;
			}
		}
		if (videoDevices.getItemCount() > 0)
		{
			videoDevices.setSelectedIndex(0);
		}
	}

	private boolean selectAudioDevice(String id)
	{
		for (int index = 0; index < audioDevices.getItemCount(); index++)
		{
			if (audioDevices.getItemAt(index).id().equals(id))
			{
				audioDevices.setSelectedIndex(index);
				return true;
			}
		}
		if (audioDevices.getItemCount() > 0)
		{
			audioDevices.setSelectedIndex(0);
		}
		return false;
	}

	private void showFailure(String title, Exception failure)
	{
		String message = failure.getMessage() == null ? title + " failed" : failure.getMessage();
		SwingUtilities.invokeLater(() ->
		{
			status.setText(message);
			status.setForeground(WARN);
			JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
		});
	}

}
