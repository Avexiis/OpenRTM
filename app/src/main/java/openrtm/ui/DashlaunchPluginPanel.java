package openrtm.ui;

import openrtm.console.ConsoleService;
import openrtm.dashlaunch.DashlaunchPluginService;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class DashlaunchPluginPanel extends JPanel
{
	private static final String COMPUTER_PLUGIN_DIRECTORY = "Hdd:\\OpenRTM\\Plugins";
	private final JFrame parent;
	private final ConsoleService console;
	private final TaskRunner taskRunner;
	private final DashlaunchPluginService service;
	private final DefaultTableModel model = new DefaultTableModel(new Object[]{"Slot", "Console Path"}, 0)
	{
		@Override
		public boolean isCellEditable(int row, int column)
		{
			return column == 1;
		}
	};
	private final JTable table = new JTable(model);
	private final JButton save = new JButton("Save Plugins");
	private final JLabel state = new JLabel("Not loaded");
	private DashlaunchPluginService.Document document;

	DashlaunchPluginPanel(JFrame parent, ConsoleService console, TaskRunner taskRunner)
	{
		super(new BorderLayout(8, 8));
		this.parent = parent;
		this.console = console;
		this.taskRunner = taskRunner;
		service = new DashlaunchPluginService(console);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		setPreferredSize(new Dimension(700, 240));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		for (int slot = 1; slot <= 5; slot++)
		{
			model.addRow(new Object[]{"Plugin " + slot, ""});
		}
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setRowSelectionInterval(0, 0);
		table.setRowHeight(Math.max(24, table.getRowHeight()));
		table.getColumnModel().getColumn(0).setMaxWidth(100);
		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(700, 180));
		add(scroll, BorderLayout.CENTER);
		add(actions(), BorderLayout.SOUTH);
		save.setEnabled(false);
	}

	private JPanel actions()
	{
		JPanel panel = new JPanel(new BorderLayout());
		JPanel commands = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		JButton load = new JButton("Load Plugins");
		JButton browseConsole = new JButton("Browse Console");
		JButton browseComputer = new JButton("Browse PC");
		JButton clear = new JButton("Clear Slot");
		load.addActionListener(event -> load());
		browseConsole.addActionListener(event -> browseConsole());
		browseComputer.addActionListener(event -> browseComputer());
		clear.addActionListener(event -> clear());
		save.addActionListener(event -> save());
		commands.add(load);
		commands.add(browseConsole);
		commands.add(browseComputer);
		commands.add(clear);
		commands.add(save);
		panel.add(commands, BorderLayout.WEST);
		panel.add(state, BorderLayout.EAST);
		return panel;
	}

	private void load()
	{
		taskRunner.run("load Dashlaunch plugins", () -> {
			DashlaunchPluginService.Document loaded = service.load();
			SwingUtilities.invokeLater(() -> apply(loaded, "Loaded from console"));
		});
	}

	private void save()
	{
		if (table.isEditing())
		{
			table.getCellEditor().stopCellEditing();
		}
		DashlaunchPluginService.Document source = document;
		if (source == null)
		{
			return;
		}
		List<String> values = new ArrayList<>();
		for (int row = 0; row < model.getRowCount(); row++)
		{
			Object value = model.getValueAt(row, 1);
			values.add(value == null ? "" : value.toString());
		}
		taskRunner.run("save Dashlaunch plugins", () -> {
			DashlaunchPluginService.Document saved = service.save(source, values);
			SwingUtilities.invokeLater(() -> apply(saved, "Saved to console"));
		});
	}

	private void browseConsole()
	{
		int row = table.getSelectedRow();
		if (row < 0)
		{
			return;
		}
		String value = String.valueOf(model.getValueAt(row, 1));
		ConsoleFilePicker picker = ConsoleFilePicker.xexFiles(parent, console, parentPath(value));
		picker.setVisible(true);
		String selected = picker.getSelectedPath();
		if (selected == null)
		{
			return;
		}
		if (!selected.toLowerCase(Locale.ROOT).endsWith(".xex"))
		{
			JOptionPane.showMessageDialog(this, "Select an XEX plugin file", "Dashlaunch Plugins",
				JOptionPane.WARNING_MESSAGE);
			return;
		}
		model.setValueAt(selected, row, 1);
	}

	private void browseComputer()
	{
		int row = table.getSelectedRow();
		if (row < 0)
		{
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Select Dashlaunch Plugin");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter("Xbox 360 plugins (*.xex)", "xex"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
		if (!Files.isRegularFile(selected) || !selected.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xex"))
		{
			JOptionPane.showMessageDialog(this, "Select an XEX plugin file", "Dashlaunch Plugins",
				JOptionPane.WARNING_MESSAGE);
			return;
		}
		String remotePath = COMPUTER_PLUGIN_DIRECTORY + "\\" + consoleFileName(selected);
		taskRunner.run("upload Dashlaunch plugin", () -> {
			console.ensureDirectory(COMPUTER_PLUGIN_DIRECTORY);
			console.uploadFile(selected, remotePath);
			SwingUtilities.invokeLater(() -> {
				model.setValueAt(remotePath, row, 1);
				state.setText("Uploaded " + selected.getFileName());
			});
		});
	}

	private void clear()
	{
		int row = table.getSelectedRow();
		if (row >= 0)
		{
			model.setValueAt("", row, 1);
		}
	}

	private void apply(DashlaunchPluginService.Document value, String message)
	{
		document = value;
		List<String> plugins = value.plugins();
		for (int row = 0; row < plugins.size(); row++)
		{
			model.setValueAt(plugins.get(row), row, 1);
		}
		save.setEnabled(true);
		state.setText(message);
	}

	private static String parentPath(String value)
	{
		if (value == null || value.equals("null") || value.isBlank())
		{
			return "Hdd:\\";
		}
		String normalized = value.replace('/', '\\');
		int rootSeparator = normalized.indexOf('\\');
		int separator = normalized.lastIndexOf('\\');
		if (rootSeparator >= 0 && separator == rootSeparator)
		{
			return normalized.substring(0, rootSeparator + 1);
		}
		if (separator > rootSeparator)
		{
			return normalized.substring(0, separator);
		}
		int colon = normalized.indexOf(':');
		return colon > 0 ? normalized.substring(0, colon + 1) + "\\" : "Hdd:\\";
	}

	private static String consoleFileName(Path path)
	{
		return path.getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_");
	}
}
