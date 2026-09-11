package openrtm.ui;

import openrtm.console.ConsoleService;
import openrtm.console.ConsoleService.FileEntry;
import openrtm.console.ConsoleService.ModuleInfo;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DropMode;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.HeadlessException;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public final class ModuleManagerPanel extends JPanel
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
	private final JButton injectButton = new JButton("Inject from Console...");
	private final JButton unloadButton = new JButton("Unload");
	private final JButton reloadButton = new JButton("Reload");
	private final JButton spoofButton = new JButton("Spoof Title ID");
	private final JButton undoSpoofButton = new JButton("Undo Spoof");
	private final JCheckBox spoofDashboard = new JCheckBox("Spoof as Dashboard (0xFFFE07D1)");
	private final JLabel statusLabel = new JLabel(" ");
	private final JTextField pathField = new JTextField("");

	private byte[] spoofOriginalBytes;
	private boolean isSpoofed = false;

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

		updateButtons();
	}

	private JPanel createToolbar()
	{
		JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));

		refreshButton.addActionListener(e -> refreshModules());
		injectButton.addActionListener(e -> injectFromConsole());
		unloadButton.addActionListener(e -> unloadModule());
		reloadButton.addActionListener(e -> reloadModule());
		spoofButton.addActionListener(e -> spoofTitleId());
		undoSpoofButton.addActionListener(e -> undoSpoof());

		toolbar.add(refreshButton);
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
		JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		statusBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
		statusBar.add(statusLabel);
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
				for (ModuleInfo m : modules)
				{
					moduleModel.addRow(new Object[]{
						m.name(),
						String.format("0x%08X", m.base()),
						formatSize(m.size())
					});
				}
				setStatus("Found " + modules.size() + " modules", OK);
				updateButtons();
			});
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

		tasks.run("spoof title id", () -> {
			spoofOriginalBytes = service.spoofTitleId(titleId);
			isSpoofed = true;
			SwingUtilities.invokeLater(() -> {
				setStatus("Spoofed title ID to: 0x" + Long.toHexString(titleId).toUpperCase(), OK);
				updateButtons();
			});
		});
	}

	private void undoSpoof()
	{
		if (!isSpoofed || spoofOriginalBytes == null)
		{
			return;
		}

		tasks.run("undo spoof", () -> {
			service.undoSpoofTitleId(spoofOriginalBytes);
			isSpoofed = false;
			spoofOriginalBytes = null;
			SwingUtilities.invokeLater(() -> {
				setStatus("Title ID spoof undone", OK);
				updateButtons();
			});
		});
	}

	private void updateButtons()
	{
		boolean hasSelection = moduleTable.getSelectedRow() >= 0;
		unloadButton.setEnabled(hasSelection);
		reloadButton.setEnabled(hasSelection);
		undoSpoofButton.setEnabled(isSpoofed);
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
