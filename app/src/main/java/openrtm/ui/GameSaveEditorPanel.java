package openrtm.ui;

import openrtm.stfs.GameSaveService;
import openrtm.titleids.TitleIds;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

public final class GameSaveEditorPanel extends JPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private static final Color ACCENT = new Color(91, 141, 239);
	private static final Color OK = new Color(92, 184, 117);
	private static final Color WARN = new Color(210, 157, 73);

	private final TaskRunner tasks;
	private final GameSaveService service = new GameSaveService();
	private final JTextField source = new JTextField(52);
	private final JTextField profileId = hexField(16, 20);
	private final JTextField consoleId = hexField(10, 14);
	private final JTextField deviceId = hexField(40, 42);
	private final JLabel titleId = new JLabel("-");
	private final JLabel contentType = new JLabel("-");
	private final JLabel packageSize = new JLabel("-");
	private final JLabel headerStatus = new JLabel("-");
	private final JLabel signatureStatus = new JLabel("-");
	private final JLabel operationStatus = new JLabel("No game save loaded");
	private final JCheckBox createBackup = new JCheckBox("Create .bak backup", true);
	private final JButton open = new JButton("Open");
	private final JButton save = new JButton("Save, Rehash & Resign");
	private final JButton saveAs = new JButton("Save As");
	private Path loadedFile;

	public GameSaveEditorPanel(TaskRunner tasks)
	{
		super(new BorderLayout(10, 10));
		this.tasks = tasks;
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));

		JPanel content = new JPanel();
		content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
		content.add(fileSection());
		content.add(packageSection());
		content.add(assignmentSection());
		add(content, BorderLayout.NORTH);

		open.addActionListener(e -> openFromField());
		save.addActionListener(e -> save(loadedFile, true));
		saveAs.addActionListener(e -> chooseSaveAs());
		setEditorEnabled(false);
	}

	private JPanel fileSection()
	{
		JPanel panel = section("Game Save");
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		row.setAlignmentX(LEFT_ALIGNMENT);
		JButton browse = new JButton("Browse");
		browse.addActionListener(e -> chooseSource());
		row.add(source);
		row.add(browse);
		row.add(open);
		panel.add(row);
		return panel;
	}

	private JPanel packageSection()
	{
		JPanel panel = section("Package");
		JPanel fields = fields();
		addField(fields, 0, "Content type", contentType, true);
		addField(fields, 1, "Title", titleId, true);
		addField(fields, 2, "Package size", packageSize, true);
		addField(fields, 3, "Header hash", headerStatus, true);
		addField(fields, 4, "Signature", signatureStatus, true);
		panel.add(fields);
		return panel;
	}

	private JPanel assignmentSection()
	{
		JPanel panel = section("Assignment");
		JPanel fields = fields();
		addField(fields, 0, "Profile ID", profileId, false);
		addField(fields, 1, "Console ID", consoleId, false);
		addField(fields, 2, "Device ID", deviceId, false);
		lockPreferredWidth(fields);
		panel.add(fields);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
		actions.setAlignmentX(LEFT_ALIGNMENT);
		actions.add(createBackup);
		actions.add(save);
		actions.add(saveAs);
		operationStatus.setForeground(WARN);
		actions.add(operationStatus);
		panel.add(actions);
		return panel;
	}

	private void chooseSource()
	{
		JFileChooser chooser = chooserFor(source.getText());
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setFileFilter(packageFilter());
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			source.setText(chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString());
			openFromField();
		}
	}

	private void openFromField()
	{
		String requestedPath = source.getText();
		loadedFile = null;
		setEditorEnabled(false);
		setBusy(true, "Opening...");
		tasks.run("open game save", () -> {
			try
			{
				GameSaveService.Info info;
				try
				{
					info = service.inspect(path(requestedPath));
				}
				catch (InvalidPathException invalidPath)
				{
					throw new IllegalArgumentException("Invalid game save path", invalidPath);
				}
				SwingUtilities.invokeLater(() -> showInfo(info, "Ready"));
			}
			catch (Exception failure)
			{
				SwingUtilities.invokeLater(() -> showFailure("Open failed"));
				throw failure;
			}
			finally
			{
				SwingUtilities.invokeLater(() -> setBusy(false, null));
			}
		});
	}

	private void chooseSaveAs()
	{
		if (loadedFile == null)
		{
			return;
		}
		JFileChooser chooser = chooserFor(loadedFile.toString());
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setFileFilter(packageFilter());
		chooser.setSelectedFile(loadedFile.resolveSibling(loadedFile.getFileName() + ".resigned").toFile());
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		Path destination = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
		if (Files.exists(destination))
		{
			int choice = javax.swing.JOptionPane.showConfirmDialog(
				this, "Replace the existing file?", "Save As",
				javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE);
			if (choice != javax.swing.JOptionPane.YES_OPTION)
			{
				return;
			}
		}
		save(destination, false);
	}

	private void save(Path destination, boolean backup)
	{
		if (loadedFile == null || destination == null)
		{
			return;
		}
		Path input = loadedFile;
		GameSaveService.Assignment assignment = new GameSaveService.Assignment(
			profileId.getText(), consoleId.getText(), deviceId.getText());
		boolean requestedBackup = backup && createBackup.isSelected();
		setBusy(true, "Saving...");
		tasks.run("rehash and resign game save", () -> {
			try
			{
				GameSaveService.SaveResult result =
					service.save(input, destination, assignment, requestedBackup);
				String message = result.backup() == null
					? "Saved and verified"
					: "Saved and verified; backup: " + result.backup().getFileName();
				SwingUtilities.invokeLater(() -> showInfo(result.info(), message));
			}
			catch (Exception failure)
			{
				SwingUtilities.invokeLater(() -> showFailure("Save failed"));
				throw failure;
			}
			finally
			{
				SwingUtilities.invokeLater(() -> setBusy(false, null));
			}
		});
	}

	void showInfo(GameSaveService.Info info, String message)
	{
		loadedFile = info.path();
		source.setText(info.path().toString());
		profileId.setText(info.profileId());
		consoleId.setText(info.consoleId());
		deviceId.setText(info.deviceId());
		TitleIds.find(info.titleId()).ifPresentOrElse(title -> {
			titleId.setText(title.name());
			titleId.setToolTipText("Title ID: " + title.id());
		}, () -> {
			titleId.setText(info.titleId());
			titleId.setToolTipText(null);
		});
		contentType.setText(info.contentTypeName());
		packageSize.setText(String.format(Locale.ROOT, "%,d bytes", info.packageSize()));
		setValidation(headerStatus, info.headerHashValid());
		setValidation(signatureStatus, info.signatureValid());
		operationStatus.setText(message);
		operationStatus.setForeground(OK);
		setEditorEnabled(true);
	}

	private void showFailure(String message)
	{
		operationStatus.setText(message);
		operationStatus.setForeground(WARN);
	}

	private void setBusy(boolean busy, String message)
	{
		open.setEnabled(!busy);
		save.setEnabled(!busy && loadedFile != null);
		saveAs.setEnabled(!busy && loadedFile != null);
		profileId.setEnabled(!busy && loadedFile != null);
		consoleId.setEnabled(!busy && loadedFile != null);
		deviceId.setEnabled(!busy && loadedFile != null);
		createBackup.setEnabled(!busy && loadedFile != null);
		if (message != null)
		{
			operationStatus.setText(message);
			operationStatus.setForeground(WARN);
		}
	}

	private void setEditorEnabled(boolean enabled)
	{
		save.setEnabled(enabled);
		saveAs.setEnabled(enabled);
		profileId.setEnabled(enabled);
		consoleId.setEnabled(enabled);
		deviceId.setEnabled(enabled);
		createBackup.setEnabled(enabled);
	}

	private static void setValidation(JLabel label, boolean valid)
	{
		label.setText(valid ? "Valid" : "Invalid");
		label.setForeground(valid ? OK : WARN);
	}

	private static JPanel section(String title)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
		panel.setAlignmentX(LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
			BorderFactory.createEmptyBorder(12, 0, 14, 0)));
		JLabel label = new JLabel(title);
		label.setForeground(ACCENT);
		label.setAlignmentX(LEFT_ALIGNMENT);
		panel.add(label);
		return panel;
	}

	private static JPanel fields()
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setAlignmentX(LEFT_ALIGNMENT);
		return panel;
	}

	private static void addField(JPanel panel, int row, String label, java.awt.Component value, boolean flexible)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 6, 4, 6);
		c.gridy = row;
		c.gridx = 0;
		c.anchor = GridBagConstraints.WEST;
		panel.add(new JLabel(label), c);
		c.gridx = 1;
		c.anchor = GridBagConstraints.WEST;
		c.weightx = flexible ? 1 : 0;
		c.fill = flexible ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
		panel.add(value, c);
	}

	private static void lockPreferredWidth(JPanel panel)
	{
		panel.setMaximumSize(panel.getPreferredSize());
	}

	private static JTextField hexField(int digits, int columns)
	{
		JTextField field = new JTextField(columns);
		((AbstractDocument) field.getDocument()).setDocumentFilter(new HexFilter(digits));
		return field;
	}

	static JFileChooser chooserFor(String current)
	{
		if (current == null || current.isBlank())
		{
			return new JFileChooser();
		}

		Path value;
		try
		{
			value = Path.of(current.trim()).toAbsolutePath().normalize();
		}
		catch (InvalidPathException invalidPath)
		{
			value = null;
		}
		if (value == null)
		{
			return new JFileChooser();
		}
		Path initial = Files.isDirectory(value) ? value : value.getParent();
		return initial == null ? new JFileChooser() : new JFileChooser(initial.toFile());
	}

	private static FileFilter packageFilter()
	{
		return new FileFilter()
		{
			@Override
			public boolean accept(java.io.File file)
			{
				return file.isDirectory() || !file.getName().startsWith(".");
			}

			@Override
			public String getDescription()
			{
				return "Xbox 360 game saves";
			}
		};
	}

	private static Path path(String text)
	{
		if (text == null || text.isBlank())
		{
			throw new IllegalArgumentException("Choose a game save");
		}
		return Path.of(text.trim()).toAbsolutePath().normalize();
	}

	private static final class HexFilter extends DocumentFilter
	{
		private final int maximumLength;

		private HexFilter(int maximumLength)
		{
			this.maximumLength = maximumLength;
		}

		@Override
		public void insertString(FilterBypass bypass, int offset, String text, AttributeSet attributes)
			throws BadLocationException
		{
			replace(bypass, offset, 0, text, attributes);
		}

		@Override
		public void replace(FilterBypass bypass, int offset, int length, String text, AttributeSet attributes)
			throws BadLocationException
		{
			String value = text == null ? "" : text.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
			int available = maximumLength - (bypass.getDocument().getLength() - length);
			if (available <= 0)
			{
				return;
			}
			if (value.length() > available)
			{
				value = value.substring(0, available);
			}
			bypass.replace(offset, length, value, attributes);
		}
	}
}
