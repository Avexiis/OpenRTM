package openrtm.ui;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;

final class DetachablePage extends JPanel
{
	private static final Color BAR = new Color(55, 60, 66);
	private static final Color ACCENT = new Color(0x59, 0x87, 0xE1);
	private final String title;
	private final Component content;
	private final JPanel host = new JPanel(new BorderLayout());
	private DetachedWindow window;

	DetachablePage(String title, Component content)
	{
		super(new BorderLayout());
		this.title = title;
		this.content = content;
		JButton detach = new JButton(new PopOutIcon());
		detach.setToolTipText("Open this page in a separate window");
		detach.setFocusable(false);
		detach.addActionListener(event -> detach());
		JPanel toolbar = new JPanel(new BorderLayout());
		toolbar.setBorder(BorderFactory.createEmptyBorder(5, 6, 0, 6));
		toolbar.add(detach, BorderLayout.EAST);
		host.add(content, BorderLayout.CENTER);
		add(toolbar, BorderLayout.NORTH);
		add(host, BorderLayout.CENTER);
	}

	void closeDetached()
	{
		if (window != null)
		{
			window.disposeWithoutReturn();
			window = null;
		}
	}

	private void detach()
	{
		if (window != null)
		{
			window.toFront();
			return;
		}
		BufferedImage snapshot = snapshot();
		host.removeAll();
		host.add(new DetachedPlaceholder(snapshot), BorderLayout.CENTER);
		host.revalidate();
		host.repaint();
		Window owner = SwingUtilities.getWindowAncestor(this);
		window = new DetachedWindow(owner instanceof Frame ? (Frame) owner : null);
		window.open();
	}

	private void restore()
	{
		DetachedWindow detached = window;
		window = null;
		if (detached != null)
		{
			detached.remove(content);
			detached.disposeWithoutReturn();
		}
		host.removeAll();
		host.add(content, BorderLayout.CENTER);
		host.revalidate();
		host.repaint();
	}

	private BufferedImage snapshot()
	{
		int width = Math.max(1, content.getWidth());
		int height = Math.max(1, content.getHeight());
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		content.printAll(graphics);
		graphics.dispose();
		float[] values = new float[25];
		for (int index = 0; index < values.length; index++)
		{
			values[index] = 1.0f / values.length;
		}
		return new ConvolveOp(new Kernel(5, 5, values), ConvolveOp.EDGE_NO_OP, null).filter(image, null);
	}

	private final class DetachedWindow extends JFrame
	{
		private Rectangle normalBounds;

		private DetachedWindow(Frame owner)
		{
			super(title);
			setUndecorated(true);
			setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
			setMinimumSize(new Dimension(760, 520));
			setSize(Math.max(900, DetachablePage.this.getWidth()), Math.max(620, DetachablePage.this.getHeight()));
			setLocationRelativeTo(owner);
			setLayout(new BorderLayout());
			add(titleBar(), BorderLayout.NORTH);
			add(content, BorderLayout.CENTER);
			getRootPane().setBorder(BorderFactory.createLineBorder(BAR));
			addWindowListener(new WindowAdapter()
			{
				@Override
				public void windowClosing(WindowEvent event)
				{
					restore();
				}
			});
		}

		private JPanel titleBar()
		{
			JPanel bar = new JPanel(new BorderLayout());
			bar.setBackground(BAR);
			bar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 6));
			JLabel label = new JLabel(title);
			label.setForeground(Color.WHITE);
			bar.add(label, BorderLayout.WEST);
			JPanel controls = new JPanel();
			controls.setOpaque(false);
			WindowButton minimize = new WindowButton(WindowSymbol.MINIMIZE);
			WindowButton maximize = new WindowButton(WindowSymbol.MAXIMIZE);
			WindowButton close = new WindowButton(WindowSymbol.CLOSE);
			minimize.setToolTipText("Minimize");
			maximize.setToolTipText("Maximize");
			close.setToolTipText("Close");
			minimize.addActionListener(event -> setState(Frame.ICONIFIED));
			maximize.addActionListener(event -> toggleMaximized());
			close.addActionListener(event -> restore());
			controls.add(minimize);
			controls.add(maximize);
			controls.add(close);
			bar.add(controls, BorderLayout.EAST);
			DragHandler drag = new DragHandler();
			bar.addMouseListener(drag);
			bar.addMouseMotionListener(drag);
			label.addMouseListener(drag);
			label.addMouseMotionListener(drag);
			return bar;
		}

		private void open()
		{
			setVisible(true);
		}

		private void disposeWithoutReturn()
		{
			dispose();
		}

		private void toggleMaximized()
		{
			if (normalBounds == null)
			{
				normalBounds = getBounds();
				setBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
			}
			else
			{
				setBounds(normalBounds);
				normalBounds = null;
			}
		}

		private final class DragHandler extends MouseAdapter
		{
			private Point pressed;

			@Override
			public void mousePressed(MouseEvent event)
			{
				pressed = event.getLocationOnScreen();
			}

			@Override
			public void mouseDragged(MouseEvent event)
			{
				if (pressed == null || normalBounds != null)
				{
					return;
				}
				Point current = event.getLocationOnScreen();
				setLocation(getX() + current.x - pressed.x, getY() + current.y - pressed.y);
				pressed = current;
			}

			@Override
			public void mouseClicked(MouseEvent event)
			{
				if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event))
				{
					toggleMaximized();
				}
			}
		}
	}

	private static final class DetachedPlaceholder extends JPanel
	{
		private final BufferedImage snapshot;

		private DetachedPlaceholder(BufferedImage snapshot)
		{
			this.snapshot = snapshot;
			setOpaque(true);
			setBackground(new Color(35, 38, 42));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);
			Graphics2D copy = (Graphics2D) graphics.create();
			copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			copy.drawImage(snapshot, 0, 0, getWidth(), getHeight(), null);
			copy.setComposite(AlphaComposite.SrcOver.derive(0.72f));
			copy.setColor(new Color(25, 28, 32));
			copy.fillRect(0, 0, getWidth(), getHeight());
			copy.setComposite(AlphaComposite.SrcOver);
			copy.setColor(Color.WHITE);
			copy.setFont(getFont().deriveFont(Font.BOLD, 17.0f));
			String message = "Close the pop-out to return controls to this page.";
			int x = Math.max(16, (getWidth() - copy.getFontMetrics().stringWidth(message)) / 2);
			int y = (getHeight() + copy.getFontMetrics().getAscent()) / 2;
			copy.drawString(message, x, y);
			copy.dispose();
		}
	}

	private static final class PopOutIcon implements Icon
	{
		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y)
		{
			Graphics2D copy = (Graphics2D) graphics.create();
			copy.setColor(ACCENT);
			copy.setStroke(new BasicStroke(1.7f));
			copy.drawRoundRect(x + 1, y + 5, 11, 9, 2, 2);
			copy.drawLine(x + 7, y + 1, x + 15, y + 1);
			copy.drawLine(x + 15, y + 1, x + 15, y + 9);
			copy.drawLine(x + 8, y + 8, x + 15, y + 1);
			copy.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 17;
		}

		@Override
		public int getIconHeight()
		{
			return 16;
		}
	}

	private enum WindowSymbol
	{
		MINIMIZE,
		MAXIMIZE,
		CLOSE
	}

	private static final class WindowButton extends JButton
	{
		private final WindowSymbol symbol;

		private WindowButton(WindowSymbol symbol)
		{
			this.symbol = symbol;
			setPreferredSize(new Dimension(34, 25));
			setBorder(BorderFactory.createEmptyBorder());
			setContentAreaFilled(false);
			setFocusPainted(false);
			setRolloverEnabled(true);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D copy = (Graphics2D) graphics.create();
			copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			copy.setColor(getModel().isRollover() || getModel().isPressed() ? ACCENT : new Color(70, 76, 83));
			copy.fillRoundRect(0, 0, getWidth(), getHeight(), 9, 9);
			copy.setColor(Color.WHITE);
			copy.setStroke(new BasicStroke(1.6f));
			int centerX = getWidth() / 2;
			int centerY = getHeight() / 2;
			if (symbol == WindowSymbol.MINIMIZE)
			{
				copy.drawLine(centerX - 5, centerY + 3, centerX + 5, centerY + 3);
			}
			else if (symbol == WindowSymbol.MAXIMIZE)
			{
				copy.drawRoundRect(centerX - 5, centerY - 5, 10, 10, 2, 2);
			}
			else
			{
				copy.drawLine(centerX - 4, centerY - 4, centerX + 4, centerY + 4);
				copy.drawLine(centerX + 4, centerY - 4, centerX - 4, centerY + 4);
			}
			copy.dispose();
		}
	}
}
