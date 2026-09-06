package openrtm.ui;

import openrtm.stfs.PackageService;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class PackageManagerPanel extends JPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private static final Color ACCENT = new Color(91, 141, 239);
	private static final Color OK = new Color(92, 184, 117);
	private static final Color WARN = new Color(210, 157, 73);

	private final TaskRunner tasks;
	private final PackageService packages = new PackageService();
	private final JTextField source = new JTextField(48);
	private final JTextField displayName = new JTextField(34);
	private final JTextField titleName = new JTextField(34);
	private final JTextArea description = new JTextArea(3, 34);
	private final JTextField publisher = new JTextField(34);
	private final JTextField titleId = new JTextField(12);
	private final JTextField profileId = new JTextField(20);
	private final JTextField consoleId = new JTextField(14);
	private final JTextField deviceId = new JTextField(42);
	private final JLabel signatureType = new JLabel("-");
	private final JLabel contentType = new JLabel("-");
	private final JLabel headerStatus = new JLabel("-");
	private final JLabel signatureStatus = new JLabel("-");
	private final JLabel destination = new JLabel("-");
	private final JLabel status = new JLabel("No package loaded");
	private final JLabel thumbnail = imageLabel();
	private final JLabel titleThumbnail = imageLabel();
	private final DefaultTableModel contentModel = new DefaultTableModel(new Object[]{"Internal Path", "Kind", "Size"}, 0)
	{
		@Override
		public boolean isCellEditable(int row, int column)
		{
			return false;
		}
	};
	private final JTable contents = new JTable(contentModel);
	private final JCheckBox backup = new JCheckBox("Create .bak backup", true);
	private final JButton save = new JButton("Save, Rehash & Resign");
	private final JButton saveAs = new JButton("Save As");
	private byte[] pendingThumbnail;
	private byte[] pendingTitleThumbnail;
	private Path loadedFile;
	private PackageService.Info loadedInfo;
	private List<PackageService.InternalEntry> internalEntries = List.of();

	public PackageManagerPanel(TaskRunner tasks)
	{
		super(new BorderLayout(10, 10));
		this.tasks = tasks;
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
		add(fileBar(), BorderLayout.NORTH);

		JSplitPane upper = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, metadataPanel(), thumbnailsPanel());
		upper.setResizeWeight(0.75);
		upper.setContinuousLayout(true);
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, upper, contentsPanel());
		split.setResizeWeight(0.62);
		split.setContinuousLayout(true);
		add(split, BorderLayout.CENTER);
		add(actionBar(), BorderLayout.SOUTH);
		setEditable(false);
	}

	private JPanel fileBar()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
		JButton browse = new JButton("Browse");
		JButton open = new JButton("Open");
		browse.addActionListener(event -> choosePackage());
		open.addActionListener(event -> open());
		panel.add(source);
		panel.add(browse);
		panel.add(open);
		return panel;
	}

	private JPanel metadataPanel()
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 8));
		JPanel fields = new JPanel(new GridBagLayout());
		int row = 0;
		addField(fields, row++, "Signature", signatureType);
		addField(fields, row++, "Content type", contentType);
		addField(fields, row++, "Display name", displayName);
		addField(fields, row++, "Title name", titleName);
		addField(fields, row++, "Description", new JScrollPane(description));
		addField(fields, row++, "Publisher", publisher);
		addField(fields, row++, "Title ID", titleId);
		addField(fields, row++, "Profile ID", profileId);
		addField(fields, row++, "Console ID", consoleId);
		addField(fields, row++, "Device ID", deviceId);
		addField(fields, row++, "Header hash", headerStatus);
		addField(fields, row++, "Package signature", signatureStatus);
		addField(fields, row, "Suggested console folder", destination);
		panel.add(fields, BorderLayout.NORTH);
		return panel;
	}

	private JPanel thumbnailsPanel()
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(4, 4, 4, 4);
		c.gridx = 0;
		c.gridy = 0;
		panel.add(new JLabel("Package Image"), c);
		c.gridx = 1;
		panel.add(new JLabel("Title Image"), c);
		c.gridy = 1;
		c.gridx = 0;
		panel.add(thumbnail, c);
		c.gridx = 1;
		panel.add(titleThumbnail, c);
		c.gridy = 2;
		c.gridx = 0;
		JButton replaceThumbnail = new JButton("Replace");
		replaceThumbnail.addActionListener(event -> replaceImage(false));
		panel.add(replaceThumbnail, c);
		c.gridx = 1;
		JButton replaceTitleThumbnail = new JButton("Replace");
		replaceTitleThumbnail.addActionListener(event -> replaceImage(true));
		panel.add(replaceTitleThumbnail, c);
		c.gridy = 3;
		c.gridx = 0;
		JButton exportThumbnail = new JButton("Export");
		exportThumbnail.addActionListener(event -> exportImage(false));
		panel.add(exportThumbnail, c);
		c.gridx = 1;
		JButton exportTitleThumbnail = new JButton("Export");
		exportTitleThumbnail.addActionListener(event -> exportImage(true));
		panel.add(exportTitleThumbnail, c);
		return panel;
	}

	private JPanel contentsPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(8, 6));
		JLabel label = new JLabel("Package Files");
		label.setForeground(ACCENT);
		panel.add(label, BorderLayout.NORTH);
		contents.setAutoCreateRowSorter(true);
		panel.add(new JScrollPane(contents), BorderLayout.CENTER);
		JButton extract = new JButton("Extract Selected File");
		extract.addActionListener(event -> extractSelected());
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		row.add(extract);
		panel.add(row, BorderLayout.SOUTH);
		return panel;
	}

	private JPanel actionBar()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 7));
		panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
		save.addActionListener(event -> save(loadedFile, true));
		saveAs.addActionListener(event -> chooseSaveAs());
		panel.add(backup);
		panel.add(save);
		panel.add(saveAs);
		panel.add(status);
		return panel;
	}

	private void choosePackage()
	{
		JFileChooser chooser = GameSaveEditorPanel.chooserFor(source.getText());
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			source.setText(chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString());
			open();
		}
	}

	private void open()
	{
		String value = source.getText().trim();
		if (value.isBlank())
		{
			JOptionPane.showMessageDialog(this, "Choose an Xbox 360 package", "Open Package",
				JOptionPane.WARNING_MESSAGE);
			return;
		}
		Path requested;
		try
		{
			requested = Path.of(value).toAbsolutePath().normalize();
		}
		catch (java.nio.file.InvalidPathException failure)
		{
			JOptionPane.showMessageDialog(this, "Package path is invalid", "Open Package",
				JOptionPane.WARNING_MESSAGE);
			return;
		}
		status.setText("Opening...");
		tasks.run("open package", () -> {
			PackageService.Info info = packages.inspect(requested);
			List<PackageService.InternalEntry> entries;
			String contentError = null;
			try
			{
				entries = info.stfs() ? packages.contents(requested) : List.of();
			}
			catch (Exception failure)
			{
				entries = List.of();
				contentError = failure.getMessage();
			}
			List<PackageService.InternalEntry> found = entries;
			String error = contentError;
			SwingUtilities.invokeLater(() -> showInfo(info, found, error));
		});
	}

	private void showInfo(PackageService.Info info, List<PackageService.InternalEntry> entries, String contentError)
	{
		loadedFile = info.path();
		loadedInfo = info;
		internalEntries = entries;
		source.setText(info.path().toString());
		signatureType.setText(info.signatureType().name());
		contentType.setText(info.contentTypeName());
		displayName.setText(info.displayName());
		titleName.setText(info.titleName());
		description.setText(info.description());
		publisher.setText(info.publisher());
		titleId.setText(info.titleId());
		titleId.setToolTipText(info.titleNameFromId().equals(info.titleId()) ? null : info.titleNameFromId());
		profileId.setText(info.creatorId());
		consoleId.setText(info.consoleId());
		deviceId.setText(info.deviceId());
		setValidation(headerStatus, info.headerHashValid());
		signatureStatus.setText(info.signatureValid() == null ? "Not checked" : info.signatureValid() ? "Valid" : "Invalid");
		signatureStatus.setForeground(Boolean.FALSE.equals(info.signatureValid()) ? WARN : OK);
		destination.setText(PackageService.recommendedDirectory(info, null));
		pendingThumbnail = info.thumbnail();
		pendingTitleThumbnail = info.titleThumbnail();
		showImage(thumbnail, pendingThumbnail);
		showImage(titleThumbnail, pendingTitleThumbnail);
		contentModel.setRowCount(0);
		for (PackageService.InternalEntry entry : entries)
		{
			contentModel.addRow(new Object[]{entry.path(), entry.directory() ? "Folder" : "File",
				entry.directory() ? "" : String.format(Locale.ROOT, "%,d", entry.size())});
		}
		status.setText(contentError == null ? "Ready" : "Metadata ready; package files unavailable: " + contentError);
		setEditable(info.signatureType() == PackageService.SignatureType.CON);
	}

	private void chooseSaveAs()
	{
		if (loadedFile == null)
		{
			return;
		}
		JFileChooser chooser = GameSaveEditorPanel.chooserFor(loadedFile.toString());
		chooser.setSelectedFile(loadedFile.resolveSibling(loadedFile.getFileName() + ".edited").toFile());
		if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			Path output = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
			if (!Files.exists(output) || JOptionPane.showConfirmDialog(this, "Replace the existing file?", "Save As",
				JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
			{
				save(output, false);
			}
		}
	}

	private void save(Path output, boolean inPlace)
	{
		if (loadedFile == null || output == null)
		{
			return;
		}
		PackageService.Edits edits = new PackageService.Edits(displayName.getText(), titleName.getText(),
			description.getText(), publisher.getText(), titleId.getText(), profileId.getText(), consoleId.getText(),
			deviceId.getText(), changedImage(pendingThumbnail, loadedInfo.thumbnail()),
			changedImage(pendingTitleThumbnail, loadedInfo.titleThumbnail()));
		Path input = loadedFile;
		status.setText("Saving...");
		tasks.run("save package", () -> {
			PackageService.SaveResult result = packages.save(input, output, edits, inPlace && backup.isSelected());
			SwingUtilities.invokeLater(() -> showInfo(result.info(), internalEntries, null));
		});
	}

	private static byte[] changedImage(byte[] current, byte[] original)
	{
		return java.util.Arrays.equals(current, original) ? null : current;
	}

	private void replaceImage(boolean titleImage)
	{
		if (loadedInfo == null || loadedInfo.signatureType() != PackageService.SignatureType.CON)
		{
			return;
		}
		JFileChooser chooser = new JFileChooser();
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			try
			{
				byte[] data = Files.readAllBytes(chooser.getSelectedFile().toPath());
				if (titleImage)
				{
					pendingTitleThumbnail = data;
					showImage(titleThumbnail, data);
				}
				else
				{
					pendingThumbnail = data;
					showImage(thumbnail, data);
				}
			}
			catch (Exception failure)
			{
				JOptionPane.showMessageDialog(this, failure.getMessage(), "Replace Image", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void exportImage(boolean titleImage)
	{
		byte[] data = titleImage ? pendingTitleThumbnail : pendingThumbnail;
		if (data == null || data.length == 0)
		{
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(Path.of(titleImage ? "title-thumbnail.png" : "thumbnail.png").toFile());
		if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			try
			{
				Files.write(chooser.getSelectedFile().toPath(), data);
			}
			catch (Exception failure)
			{
				JOptionPane.showMessageDialog(this, failure.getMessage(), "Export Image", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void extractSelected()
	{
		int viewRow = contents.getSelectedRow();
		if (loadedFile == null || viewRow < 0)
		{
			return;
		}
		PackageService.InternalEntry entry = internalEntries.get(contents.convertRowIndexToModel(viewRow));
		if (entry.directory())
		{
			return;
		}
		JFileChooser chooser = new JFileChooser();
		String name = entry.path().substring(entry.path().lastIndexOf('/') + 1);
		chooser.setSelectedFile(Path.of(name).toFile());
		if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			Path output = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
			tasks.run("extract package file", () -> packages.extract(loadedFile, entry.path(), output));
		}
	}

	private void setEditable(boolean editable)
	{
		displayName.setEditable(editable);
		titleName.setEditable(editable);
		description.setEditable(editable);
		publisher.setEditable(editable);
		titleId.setEditable(editable);
		profileId.setEditable(editable);
		consoleId.setEditable(editable);
		deviceId.setEditable(editable);
		backup.setEnabled(editable);
		save.setEnabled(editable && loadedFile != null);
		saveAs.setEnabled(editable && loadedFile != null);
	}

	private static JLabel imageLabel()
	{
		JLabel label = new JLabel("No image", SwingConstants.CENTER);
		label.setPreferredSize(new Dimension(112, 112));
		label.setMinimumSize(new Dimension(112, 112));
		label.setBorder(BorderFactory.createLineBorder(LINE));
		return label;
	}

	private static void showImage(JLabel label, byte[] data)
	{
		try
		{
			java.awt.image.BufferedImage image = data == null || data.length == 0 ? null
				: ImageIO.read(new ByteArrayInputStream(data));
			if (image == null)
			{
				label.setIcon(null);
				label.setText("No image");
			}
			else
			{
				label.setText("");
				label.setIcon(new ImageIcon(image.getScaledInstance(104, 104, Image.SCALE_SMOOTH)));
			}
		}
		catch (Exception ignored)
		{
			label.setIcon(null);
			label.setText("Unreadable");
		}
	}

	private static void addField(JPanel panel, int row, String label, java.awt.Component value)
	{
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(3, 5, 3, 5);
		c.gridy = row;
		c.gridx = 0;
		c.anchor = GridBagConstraints.WEST;
		panel.add(new JLabel(label), c);
		c.gridx = 1;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		panel.add(value, c);
	}

	private static void setValidation(JLabel label, boolean valid)
	{
		label.setText(valid ? "Valid" : "Invalid");
		label.setForeground(valid ? OK : WARN);
	}
}
