package openrtm.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DnDConstants;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

abstract class FileDropPanel extends JPanel
{
	private static final Color VEIL = new Color(20, 22, 25, 172);
	private static final float[] BLUR = {
		0.04f, 0.04f, 0.04f, 0.04f, 0.04f,
		0.04f, 0.04f, 0.04f, 0.04f, 0.04f,
		0.04f, 0.04f, 0.04f, 0.04f, 0.04f,
		0.04f, 0.04f, 0.04f, 0.04f, 0.04f,
		0.04f, 0.04f, 0.04f, 0.04f, 0.04f
	};
	private final FlatSVGIcon dropIcon = new FlatSVGIcon("openrtm/images/drag-drop.svg", 112, 112);
	private boolean dropActive;
	private String dropPrompt = "Drop a file here to use!";
	private BufferedImage blurredControls;
	private Predicate<List<Path>> dropAction;

	protected FileDropPanel()
	{
	}

	protected FileDropPanel(LayoutManager layout)
	{
		super(layout);
	}

	protected final void enableFileDrop(String prompt, Predicate<List<Path>> action)
	{
		dropPrompt = prompt;
		dropAction = action;
		if (GraphicsEnvironment.isHeadless())
		{
			return;
		}
		installDropTargets(this, action);
	}

	@Override
	public void updateUI()
	{
		super.updateUI();
		if (dropAction != null && !GraphicsEnvironment.isHeadless())
		{
			SwingUtilities.invokeLater(() -> installDropTargets(this, dropAction));
		}
	}

	protected final JLabel fileDropHint(String text)
	{
		JLabel label = new JLabel(text);
		label.setEnabled(false);
		return label;
	}

	private void installDropTarget(Component target, Predicate<List<Path>> action)
	{
		new DropTarget(target, DnDConstants.ACTION_COPY, new DropTargetAdapter()
		{
			@Override
			public void dragEnter(DropTargetDragEvent event)
			{
				updateDrag(event);
			}

			@Override
			public void dragOver(DropTargetDragEvent event)
			{
				updateDrag(event);
			}

			@Override
			public void dragExit(DropTargetEvent event)
			{
				setDropActive(false);
			}

			@Override
			public void drop(DropTargetDropEvent event)
			{
				setDropActive(false);
				if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
				{
					event.rejectDrop();
					return;
				}
				try
				{
					event.acceptDrop(DnDConstants.ACTION_COPY);
					List<?> values = (List<?>) event.getTransferable()
						.getTransferData(DataFlavor.javaFileListFlavor);
					List<Path> paths = new ArrayList<>();
					for (Object value : values)
					{
						if (value instanceof File file)
						{
							paths.add(file.toPath().toAbsolutePath().normalize());
						}
					}
					event.dropComplete(!paths.isEmpty() && action.test(List.copyOf(paths)));
				}
				catch (Exception failure)
				{
					event.dropComplete(false);
				}
			}

			private void updateDrag(DropTargetDragEvent event)
			{
				boolean supported = event.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
				if (supported)
				{
					event.acceptDrag(DnDConstants.ACTION_COPY);
				}
				else
				{
					event.rejectDrag();
				}
				setDropActive(supported);
			}
		}, true);
	}

	private void installDropTargets(Component target, Predicate<List<Path>> action)
	{
		installDropTarget(target, action);
		if (target instanceof Container container)
		{
			for (Component child : container.getComponents())
			{
				installDropTargets(child, action);
			}
		}
	}

	private void setDropActive(boolean active)
	{
		if (dropActive == active)
		{
			return;
		}
		dropActive = active;
		blurredControls = null;
		repaint();
	}

	@Override
	protected void paintChildren(Graphics graphics)
	{
		if (!dropActive)
		{
			super.paintChildren(graphics);
			return;
		}
		int width = getWidth();
		int height = getHeight();
		if (width <= 0 || height <= 0)
		{
			return;
		}
		if (blurredControls == null || blurredControls.getWidth() != width
			|| blurredControls.getHeight() != height)
		{
			BufferedImage controls = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D offscreen = controls.createGraphics();
			super.paintChildren(offscreen);
			offscreen.dispose();
			blurredControls = new ConvolveOp(new Kernel(5, 5, BLUR), ConvolveOp.EDGE_NO_OP, null)
				.filter(controls, null);
		}

		Graphics2D output = (Graphics2D) graphics.create();
		output.drawImage(blurredControls, 0, 0, null);
		output.setColor(VEIL);
		output.fillRect(0, 0, width, height);
		int iconY = Math.max(18, height / 2 - 100);
		dropIcon.paintIcon(this, output, Math.max(0, (width - dropIcon.getIconWidth()) / 2), iconY);
		output.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		output.setColor(Color.WHITE);
		output.setFont(getFont().deriveFont(Font.BOLD, 20.0f));
		drawPrompt(output, width, iconY + dropIcon.getIconHeight() + 18);
		output.dispose();
	}

	private void drawPrompt(Graphics2D graphics, int width, int startY)
	{
		FontMetrics metrics = graphics.getFontMetrics();
		int available = Math.max(80, width - 48);
		List<String> lines = new ArrayList<>();
		String line = "";
		for (String word : dropPrompt.split(" "))
		{
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && metrics.stringWidth(candidate) > available)
			{
				lines.add(line);
				line = word;
			}
			else
			{
				line = candidate;
			}
		}
		if (!line.isEmpty())
		{
			lines.add(line);
		}
		int y = startY + metrics.getAscent();
		for (String value : lines)
		{
			graphics.drawString(value, Math.max(24, (width - metrics.stringWidth(value)) / 2), y);
			y += metrics.getHeight();
		}
	}
}
