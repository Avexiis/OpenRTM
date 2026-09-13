package openrtm.ui;

import openrtm.console.ConsoleService;
import openrtm.console.ConsoleService.ModuleInfo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class ModuleManagerPanel extends FileDropPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private static final Color ACCENT = new Color(91, 141, 239);
	private static final Color OK = new Color(92, 184, 117);
	private static final Color WARN = new Color(210, 157, 73);
	private static final Color DANGER = new Color(192, 85, 85);

	private final ConsoleService service;
	private final TaskRunner tasks;
	private final DefaultTableModel moduleModel = new DefaultTableModel(
		new Object[]{"Name", "Base Address", "Size"}, 0)
	{
		@Override
		public boolean isCellEditable(int row, int column)
		{
			return false;
		}
	};
	private final JTable moduleTable = new JTable(moduleModel);
	private final JButton refreshButton = new JButton("Refresh");
	private final JButton loadFromComputerButton = new JButton("Load from PC...");
	private final JButton injectButton = new JButton("Inject from Console...");
	private final JButton unloadButton = new JButton("Unload");
	private final JButton reloadButton = new JButton("Reload");
	private final JButton spoofButton = new JButton("Spoof Title ID");
	private final JButton undoSpoofButton = new JButton("Undo Spoof");
	private final JCheckBox spoofDashboard = new JCheckBox("Spoof as Dashboard (0xFFFE07D1)");
	private final JLabel statusLabel = new JLabel(" ");
	private final JTextField pathField = new JTextField("");
	private final Map<String, Path> computerModuleSources = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	private byte[] spoofOriginalBytes;
	private boolean isSpoofed;
	private boolean spoofActionPending;

	public ModuleManagerPanel(ConsoleService service, TaskRunner tasks)
	{
		this.service = service;
		this.tasks = tasks;

		setLayout(new BorderLayout(10, 10));
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));

		add(createToolbar(), BorderLayout.NORTH);
		add(createModuleTable(), BorderLayout.CENTER);
		add(createStatusBar(), BorderLayout.SOUTH);
		add(createSidePanel(), BorderLayout.EAST);
		enableFileDrop("Drop an Xbox 360 .xex module here to use!", this::dropModules);

		updateButtons();
	}

	private JPanel createToolbar()
	{
		JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));

		refreshButton.addActionListener(e -> refreshModules());
		loadFromComputerButton.addActionListener(e -> browseComputer());
		injectButton.addActionListener(e -> injectFromConsole());
		unloadButton.addActionListener(e -> unloadModule());
		reloadButton.addActionListener(e -> reloadModule());
		spoofButton.addActionListener(e -> spoofTitleId());
		undoSpoofButton.addActionListener(e -> undoSpoof());

		toolbar.add(refreshButton);
		toolbar.add(loadFromComputerButton);
		toolbar.add(injectButton);
		toolbar.add(unloadButton);
		toolbar.add(reloadButton);
		toolbar.add(spoofButton);
		toolbar.add(undoSpoofButton);

		return toolbar;
	}

	private JScrollPane createModuleTable()
	{
		moduleTable.setFillsViewportHeight(true);
		moduleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		moduleTable.setRowHeight(24);

		moduleTable.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting())
			{
				updateButtons();
			}
		});

		JScrollPane scroll = new JScrollPane(moduleTable);
		scroll.setBorder(BorderFactory.createLineBorder(LINE));

		return scroll;
	}

	private JPanel createSidePanel()
	{
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 1, 0, 0, LINE),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)));
		panel.setPreferredSize(new Dimension(280, 0));

		JPanel injectSection = section("Inject Module from Console");
		injectSection.setLayout(new BorderLayout(8, 8));

		JPanel pathRow = new JPanel(new BorderLayout(4, 4));
		pathRow.add(new JLabel("Console Path:"), BorderLayout.NORTH);
		pathRow.add(pathField, BorderLayout.CENTER);

		JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
		JButton browseButton = new JButton("Browse Console...");
		browseButton.addActionListener(e -> browseConsole());
		buttons.add(browseButton);
		buttons.add(injectButton);

		injectSection.add(pathRow, BorderLayout.NORTH);
		injectSection.add(buttons, BorderLayout.SOUTH);

		JPanel spoofSection = section("Title ID Spoof");
		spoofSection.setLayout(new GridLayout(0, 1, 4, 4));
		spoofSection.add(spoofDashboard);
		spoofSection.add(spoofButton);
		spoofSection.add(undoSpoofButton);

		panel.add(injectSection, BorderLayout.NORTH);
		panel.add(spoofSection, BorderLayout.CENTER);

		return panel;
	}

	private JPanel createStatusBar()
	{
		JPanel statusBar = new JPanel(new BorderLayout(8, 4));
		statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
		statusBar.add(statusLabel, BorderLayout.CENTER);
		statusBar.add(fileDropHint("Drag and drop .xex modules"), BorderLayout.EAST);
		return statusBar;
	}

	private JPanel section(String title)
	{
		JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
			BorderFactory.createEmptyBorder(12, 0, 14, 0)));
		JLabel label = new JLabel(title);
		label.setForeground(ACCENT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(label);
		return panel;
	}

	private void refreshModules()
	{
		tasks.run("list modules", () -> {
			List<ModuleInfo> modules = service.listModules();
			SwingUtilities.invokeLater(() -> {
				moduleModel.setRowCount(0);
				List<String> moduleNames = new ArrayList<>();
				for (ModuleInfo m : modules)
				{
					moduleNames.add(m.name());
					moduleModel.addRow(new Object[]{
						m.name(),
						String.format("0x%08X", m.base()),
						formatSize(m.size())
					});
				}
				computerModuleSources.keySet().removeIf(name -> moduleNames.stream()
					.noneMatch(moduleName -> moduleName.equalsIgnoreCase(name)));
				setStatus("Found " + modules.size() + " modules", OK);
				updateButtons();
			});
		});
	}

	private void browseComputer()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Load Xbox 360 Module");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setMultiSelectionEnabled(true);
		chooser.setFileFilter(new FileNameExtensionFilter("Xbox 360 modules (*.xex)", "xex"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		File[] files = chooser.getSelectedFiles();
		if (files.length == 0 && chooser.getSelectedFile() != null)
		{
			files = new File[]{chooser.getSelectedFile()};
		}
		List<Path> paths = new ArrayList<>();
		for (File file : files)
		{
			paths.add(file.toPath());
		}
		loadFromComputer(paths);
	}

	private boolean dropModules(List<Path> paths)
	{
		if (paths.isEmpty() || paths.stream().anyMatch(path -> !validModulePath(path)))
		{
			JOptionPane.showMessageDialog(this, "Drop one or more .xex files", "Load Module",
				JOptionPane.WARNING_MESSAGE);
			return false;
		}
		loadFromComputer(paths);
		return true;
	}

	private static boolean validModulePath(Path path)
	{
		return Files.isRegularFile(path)
			&& path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xex");
	}

	private void loadFromComputer(List<Path> paths)
	{
		if (paths.isEmpty())
		{
			return;
		}
		setStatus(paths.size() == 1 ? "Loading module from PC..." : "Loading modules from PC...", WARN);
		tasks.run("load module from PC", () -> {
			Map<String, Path> loadedSources = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
			try
			{
				for (Path path : paths)
				{
					String remotePath = service.loadModuleFromComputer(path);
					loadedSources.put(remotePath, path.toAbsolutePath().normalize());
				}
			}
			finally
			{
				if (!loadedSources.isEmpty())
				{
					SwingUtilities.invokeLater(() -> {
						computerModuleSources.putAll(loadedSources);
						setStatus(loadedSources.size() == 1 ? "Loaded module from PC"
							: "Loaded " + loadedSources.size() + " modules from PC", OK);
						refreshModules();
					});
				}
			}
		});
	}

	private void browseConsole()
	{
		String initialPath = pathField.getText().trim();
		if (initialPath.isEmpty())
		{
			initialPath = "Hdd:\\";
		}

		ConsoleFilePicker picker = new ConsoleFilePicker(
			(JFrame) SwingUtilities.getWindowAncestor(this), service, initialPath);
		picker.setVisible(true);
		String selected = picker.getSelectedPath();
		if (selected != null)
		{
			pathField.setText(selected);
		}
	}

	private void injectFromConsole()
	{
		String consolePath = pathField.getText().trim();
		if (consolePath.isEmpty())
		{
			JOptionPane.showMessageDialog(this, "Enter or browse to the console path of the XEX file",
				"Inject Module", JOptionPane.WARNING_MESSAGE);
			return;
		}

		tasks.run("inject module from console", () -> {
			service.injectModuleFromConsole(consolePath);
			SwingUtilities.invokeLater(() -> {
				setStatus("Injected: " + consolePath, OK);
				refreshModules();
			});
		});
	}

	private void unloadModule()
	{
		int row = moduleTable.getSelectedRow();
		if (row < 0)
		{
			JOptionPane.showMessageDialog(this, "Select a module to unload", "Unload Module",
				JOptionPane.WARNING_MESSAGE);
			return;
		}

		String name = (String) moduleModel.getValueAt(row, 0);
		tasks.run("unload module", () -> {
			service.unloadModule(name);
			SwingUtilities.invokeLater(() -> {
				computerModuleSources.remove(name);
				setStatus("Unloaded: " + name, OK);
				refreshModules();
			});
		});
	}

	private void reloadModule()
	{
		int row = moduleTable.getSelectedRow();
		if (row < 0)
		{
			JOptionPane.showMessageDialog(this, "Select a module to reload", "Reload Module",
				JOptionPane.WARNING_MESSAGE);
			return;
		}

		String name = (String) moduleModel.getValueAt(row, 0);
		Path computerSource = computerModuleSources.get(name);
		if (computerSource != null)
		{
			tasks.run("reload module", () -> {
				service.unloadModule(name);
				String remotePath = service.loadModuleFromComputer(computerSource);
				SwingUtilities.invokeLater(() -> {
					computerModuleSources.remove(name);
					computerModuleSources.put(remotePath, computerSource);
					setStatus("Reloaded: " + computerSource.getFileName(), OK);
					refreshModules();
				});
			});
			return;
		}
		if (!name.startsWith("Hdd:\\") && !name.startsWith("Usb:\\"))
		{
			JOptionPane.showMessageDialog(this, "Can only reload modules from Hdd or Usb", "Reload Module",
				JOptionPane.WARNING_MESSAGE);
			return;
		}

		tasks.run("reload module", () -> {
			service.reloadModule(name);
			SwingUtilities.invokeLater(() -> {
				setStatus("Reloaded: " + name, OK);
				refreshModules();
			});
		});
	}

	private void spoofTitleId()
	{
		if (isSpoofed || spoofActionPending)
		{
			return;
		}
		long titleId;
		if (spoofDashboard.isSelected())
		{
			titleId = 0xFFFE07D1L;
		}
		else
		{
			String input = JOptionPane.showInputDialog(this, "Enter title ID (hex, e.g. FFFE07D1):");
			if (input == null || input.isBlank())
			{
				return;
			}
			try
			{
				titleId = Long.parseLong(input.replace("0x", "").trim(), 16);
			}
			catch (NumberFormatException e)
			{
				JOptionPane.showMessageDialog(this, "Invalid hex number", "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
		}

		spoofActionPending = true;
		updateButtons();
		tasks.run("spoof title id", () ->
		{
			try
			{
				byte[] original = service.spoofTitleId(titleId);
				SwingUtilities.invokeLater(() ->
				{
					spoofOriginalBytes = original;
					isSpoofed = true;
					spoofActionPending = false;
					setStatus("Spoofed title ID to: 0x" + Long.toHexString(titleId).toUpperCase(), OK);
					updateButtons();
				});
			}
			catch (Exception failure)
			{
				SwingUtilities.invokeLater(() ->
				{
					spoofActionPending = false;
					updateButtons();
				});
				throw failure;
			}
		});
	}

	private void undoSpoof()
	{
		if (!isSpoofed || spoofOriginalBytes == null || spoofActionPending)
		{
			return;
		}

		byte[] original = spoofOriginalBytes;
		spoofActionPending = true;
		updateButtons();
		tasks.run("undo spoof", () ->
		{
			try
			{
				service.undoSpoofTitleId(original);
				SwingUtilities.invokeLater(() ->
				{
					isSpoofed = false;
					spoofOriginalBytes = null;
					spoofActionPending = false;
					setStatus("Title ID spoof undone", OK);
					updateButtons();
				});
			}
			catch (Exception failure)
			{
				SwingUtilities.invokeLater(() ->
				{
					spoofActionPending = false;
					updateButtons();
				});
				throw failure;
			}
		});
	}

	private void updateButtons()
	{
		boolean hasSelection = moduleTable.getSelectedRow() >= 0;
		unloadButton.setEnabled(hasSelection);
		reloadButton.setEnabled(hasSelection);
		spoofButton.setEnabled(!isSpoofed && !spoofActionPending);
		undoSpoofButton.setEnabled(isSpoofed && !spoofActionPending);
	}

	private void setStatus(String text, Color color)
	{
		statusLabel.setText(text);
		statusLabel.setForeground(color);
	}

	private static String formatSize(long size)
	{
		if (size < 1024)
		{
			return size + " B";
		}
		if (size < 1024 * 1024)
		{
			return String.format("%.1f KB", size / 1024.0);
		}
		return String.format("%.1f MB", size / (1024.0 * 1024));
	}
}
