package openrtm.ui;

import openrtm.video.VideoCaptureConfig.Resolution;

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

public final class VideoViewerWindow extends JFrame
{
	private final VideoDisplayPanel display = new VideoDisplayPanel();
	private Resolution resolution;
	private boolean locked;
	private Dimension sourceSize;

	public VideoViewerWindow(Window owner, Resolution resolution, boolean locked, Runnable closed)
	{
		super("Xbox 360 Capture");
		this.resolution = resolution;
		this.locked = locked;
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		add(display, BorderLayout.CENTER);
		setMinimumSize(new Dimension(640, 360));
		applySize();
		setLocationRelativeTo(owner);
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent event)
			{
				closed.run();
			}
		});
	}

	public void showFrame(BufferedImage image)
	{
		display.showFrame(image);
		Dimension incomingSize = new Dimension(image.getWidth(), image.getHeight());
		if (!incomingSize.equals(sourceSize))
		{
			sourceSize = incomingSize;
			if (locked && resolution == Resolution.SOURCE)
			{
				applySize();
			}
		}
	}

	public void clear()
	{
		display.clear();
	}

	public void resolution(Resolution value)
	{
		resolution = value;
		applySize();
	}

	public void locked(boolean value)
	{
		locked = value;
		applySize();
	}

	private void applySize()
	{
		setResizable(!locked);
		if (!locked)
		{
			return;
		}
		Dimension displaySize;
		if (resolution == Resolution.SOURCE)
		{
			displaySize = sourceSize == null ? new Dimension(1280, 720) : sourceSize;
		}
		else
		{
			displaySize = new Dimension(resolution.width(), resolution.height());
		}
		display.setPreferredSize(displaySize);
		pack();
	}
}
