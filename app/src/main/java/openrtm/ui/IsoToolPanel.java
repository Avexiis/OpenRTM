package openrtm.ui;

import openrtm.iso.ExtractXisoService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLayer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.LayerUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

public final class IsoToolPanel extends JPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private static final Color ACCENT = new Color(91, 141, 239);
	private static final boolean LINUX = System.getProperty("os.name", "")
		.toLowerCase(Locale.ROOT).contains("linux");

	private final TaskRunner tasks;
	private final ExtractXisoService tool = new ExtractXisoService();
	private final JComboBox<ExtractXisoService.Mode> mode = new JComboBox<>(ExtractXisoService.Mode.values());
	private final JLabel sourceLabel = new JLabel("ISO");
	private final JLabel outputLabel = new JLabel("Output directory");
	private final JTextField source = new JTextField(52);
	private final JTextField output = new JTextField(52);
	private final JButton browseOutput = new JButton("Browse");
	private final JCheckBox skipUpdate = new JCheckBox("Skip $SystemUpdate");
	private final JCheckBox disablePatch = new JCheckBox("Disable XBE media patch");
	private final JCheckBox deleteOriginal = new JCheckBox("Delete original after rewrite");
	private final JTextArea log = new JTextArea(22, 80);
	private final JButton run = new JButton("Run");
	private final JButton cancel = new JButton("Cancel");

	public IsoToolPanel(TaskRunner tasks)
	{
		super(new BorderLayout(10, 10));
		this.tasks = tasks;
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));

		JPanel content = new JPanel(new BorderLayout(10, 10));
		content.add(controls(), BorderLayout.NORTH);
		log.setEditable(false);
		log.setLineWrap(false);
		content.add(new JScrollPane(log), BorderLayout.CENTER);

		mode.addActionListener(e -> updateMode());
		run.addActionListener(e -> runTool());
		cancel.addActionListener(e -> {
			tool.cancel();
			append("Cancelling..." + System.lineSeparator());
		});
		cancel.setEnabled(false);
		updateMode();
		if (LINUX)
		{
			add(content, BorderLayout.CENTER);
		}
		else
		{
			setEnabledRecursively(content, false);
			add(new JLayer<>(content, new LinuxRequiredLayer()), BorderLayout.CENTER);
		}
	}

	private JPanel controls()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));

		JPanel fields = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 4, 3, 4);
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;

		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 0;
		fields.add(new JLabel("Operation"), c);
		c.gridx = 1;
		c.gridwidth = 2;
		c.weightx = 1;
		fields.add(mode, c);

		c.gridy = 1;
		c.gridx = 0;
		c.gridwidth = 1;
		c.weightx = 0;
		fields.add(sourceLabel, c);
		c.gridx = 1;
		c.weightx = 1;
		fields.add(source, c);
		JButton browseSource = new JButton("Browse");
		browseSource.addActionListener(e -> chooseSource());
		c.gridx = 2;
		c.weightx = 0;
		fields.add(browseSource, c);

		c.gridy = 2;
		c.gridx = 0;
		fields.add(outputLabel, c);
		c.gridx = 1;
		c.weightx = 1;
		fields.add(output, c);
		browseOutput.addActionListener(e -> chooseOutput());
		c.gridx = 2;
		c.weightx = 0;
		fields.add(browseOutput, c);

		JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
		options.add(skipUpdate);
		options.add(disablePatch);
		options.add(deleteOriginal);
		options.add(run);
		options.add(cancel);

		JLabel title = new JLabel("extract-xiso");
		title.setForeground(ACCENT);
		panel.add(title, BorderLayout.NORTH);
		panel.add(fields, BorderLayout.CENTER);
		panel.add(options, BorderLayout.SOUTH);
		return panel;
	}

	private void updateMode()
	{
		ExtractXisoService.Mode selected = selectedMode();
		boolean list = selected == ExtractXisoService.Mode.LIST;
		boolean create = selected == ExtractXisoService.Mode.CREATE;
		boolean rewrite = selected == ExtractXisoService.Mode.REWRITE;

		sourceLabel.setText(create ? "Input directory" : "ISO");
		outputLabel.setText(create ? "Output ISO" : "Output directory");
		output.setEnabled(!list);
		browseOutput.setEnabled(!list);
		skipUpdate.setEnabled(selected != ExtractXisoService.Mode.LIST);
		disablePatch.setEnabled(create || rewrite);
		deleteOriginal.setEnabled(rewrite);
		if (!rewrite)
		{
			deleteOriginal.setSelected(false);
		}
	}

	private void chooseSource()
	{
		JFileChooser chooser = chooserFor(source.getText());
		if (selectedMode() == ExtractXisoService.Mode.CREATE)
		{
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		}
		else
		{
			chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			chooser.setFileFilter(new FileNameExtensionFilter("Xbox ISO files", "iso", "xiso"));
		}
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			source.setText(chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString());
		}
	}

	private void chooseOutput()
	{
		JFileChooser chooser = chooserFor(output.getText());
		if (selectedMode() == ExtractXisoService.Mode.CREATE)
		{
			chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
			chooser.setFileFilter(new FileNameExtensionFilter("Xbox ISO files", "iso"));
			if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			{
				return;
			}
		}
		else
		{
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
			{
				return;
			}
		}
		output.setText(chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString());
	}

	private void runTool()
	{
		if (!LINUX)
		{
			return;
		}
		ExtractXisoService.Mode requestedMode = selectedMode();
		String requestedSource = source.getText();
		String requestedOutput = output.getText();
		boolean requestedSkipUpdate = skipUpdate.isSelected();
		boolean requestedDisablePatch = disablePatch.isSelected();
		boolean requestedDeleteOriginal = deleteOriginal.isSelected();
		log.setText("");
		run.setEnabled(false);
		cancel.setEnabled(true);
		tasks.run("extract-xiso", () -> {
			try
			{
				ExtractXisoService.Request request = new ExtractXisoService.Request(
					requestedMode, path(requestedSource),
					requestedMode == ExtractXisoService.Mode.LIST ? null : path(requestedOutput),
					requestedSkipUpdate, requestedDisablePatch, requestedDeleteOriginal);
				tool.run(request, this::append);
			}
			finally
			{
				SwingUtilities.invokeLater(() -> {
					run.setEnabled(true);
					cancel.setEnabled(false);
				});
			}
		});
	}

	private void append(String text)
	{
		SwingUtilities.invokeLater(() -> {
			log.append(text);
			log.setCaretPosition(log.getDocument().getLength());
		});
	}

	private ExtractXisoService.Mode selectedMode()
	{
		return (ExtractXisoService.Mode) mode.getSelectedItem();
	}

	private static JFileChooser chooserFor(String current)
	{
		Path path;
		try
		{
			path = path(current);
		}
		catch (InvalidPathException invalidPath)
		{
			path = null;
		}
		if (path == null)
		{
			return new JFileChooser();
		}
		Path initial = path.toFile().isDirectory() ? path : path.getParent();
		return initial == null ? new JFileChooser() : new JFileChooser(initial.toFile());
	}

	private static Path path(String value)
	{
		return value == null || value.isBlank() ? null : Path.of(value.trim()).toAbsolutePath().normalize();
	}

	private static void setEnabledRecursively(Component component, boolean enabled)
	{
		component.setEnabled(enabled);
		if (component instanceof Container container)
		{
			for (Component child : container.getComponents())
			{
				setEnabledRecursively(child, enabled);
			}
		}
	}

	private static final class LinuxRequiredLayer extends LayerUI<JComponent>
	{
		private static final float[] BLUR = {
			0.04f, 0.04f, 0.04f, 0.04f, 0.04f,
			0.04f, 0.04f, 0.04f, 0.04f, 0.04f,
			0.04f, 0.04f, 0.04f, 0.04f, 0.04f,
			0.04f, 0.04f, 0.04f, 0.04f, 0.04f,
			0.04f, 0.04f, 0.04f, 0.04f, 0.04f
		};

		@Override
		public void paint(Graphics graphics, JComponent component)
		{
			int width = component.getWidth();
			int height = component.getHeight();
			if (width <= 0 || height <= 0)
			{
				return;
			}
			BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D offscreen = source.createGraphics();
			offscreen.setClip(graphics.getClip());
			super.paint(offscreen, component);
			offscreen.dispose();

			BufferedImage blurred = new ConvolveOp(new Kernel(5, 5, BLUR), ConvolveOp.EDGE_NO_OP, null)
				.filter(source, null);
			Graphics2D output = (Graphics2D) graphics.create();
			output.drawImage(blurred, 0, 0, null);
			output.setColor(new Color(20, 22, 25, 150));
			output.fillRect(0, 0, width, height);
			output.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			output.setFont(component.getFont().deriveFont(Font.BOLD, 22f));
			output.setColor(Color.WHITE);
			String message = "This feature requires Linux!";
			int x = Math.max(12, (width - output.getFontMetrics().stringWidth(message)) / 2);
			int y = height / 2 + output.getFontMetrics().getAscent() / 2;
			output.drawString(message, x, y);
			output.dispose();
		}
	}
}
