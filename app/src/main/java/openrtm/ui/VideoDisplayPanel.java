package openrtm.ui;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

public final class VideoDisplayPanel extends JPanel
{
	private static final long SIGNAL_TIMEOUT_NANOS = TimeUnit.MILLISECONDS.toNanos(1_500);
	private static final Color BACKGROUND = new Color(8, 9, 11);
	private static final Color MESSAGE = new Color(190, 194, 201);
	private static final String NO_INPUT_MESSAGE = "No input source detected";

	private volatile BufferedImage image;
	private volatile long frameReceivedAt;
	private volatile String message = NO_INPUT_MESSAGE;
	private final Timer signalTimer;

	public VideoDisplayPanel()
	{
		setBackground(BACKGROUND);
		setOpaque(true);
		setPreferredSize(new Dimension(640, 360));
		signalTimer = new Timer(250, e -> repaint());
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		signalTimer.start();
	}

	@Override
	public void removeNotify()
	{
		signalTimer.stop();
		super.removeNotify();
	}

	public void showFrame(BufferedImage frame)
	{
		image = frame;
		frameReceivedAt = System.nanoTime();
		message = NO_INPUT_MESSAGE;
		repaint();
	}

	public void clear()
	{
		showMessage(NO_INPUT_MESSAGE);
	}

	public void showMessage(String value)
	{
		image = null;
		frameReceivedAt = 0;
		message = value;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		Graphics2D canvas = (Graphics2D) graphics.create();
		try
		{
			BufferedImage frame = image;
			boolean signalPresent = frame != null
				&& System.nanoTime() - frameReceivedAt <= SIGNAL_TIMEOUT_NANOS;
			if (signalPresent)
			{
				drawFrame(canvas, frame);
			}
			else
			{
				drawMessage(canvas);
			}
		}
		finally
		{
			canvas.dispose();
		}
	}

	private void drawFrame(Graphics2D canvas, BufferedImage frame)
	{
		int availableWidth = getWidth();
		int availableHeight = getHeight();
		double scale = Math.min((double) availableWidth / frame.getWidth(),
			(double) availableHeight / frame.getHeight());
		int width = Math.max(1, (int) Math.round(frame.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(frame.getHeight() * scale));
		int x = (availableWidth - width) / 2;
		int y = (availableHeight - height) / 2;
		canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		canvas.drawImage(frame, x, y, width, height, null);
	}

	private void drawMessage(Graphics2D canvas)
	{
		FontMetrics metrics = canvas.getFontMetrics();
		int x = Math.max(12, (getWidth() - metrics.stringWidth(message)) / 2);
		int y = Math.max(metrics.getAscent() + 12,
			(getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
		canvas.setColor(MESSAGE);
		canvas.drawString(message, x, y);
	}
}
