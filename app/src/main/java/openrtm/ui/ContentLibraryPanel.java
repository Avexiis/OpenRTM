package openrtm.ui;

import openrtm.console.ConsoleService;
import openrtm.console.ContentLibraryService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ContentLibraryPanel extends JPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private final TaskRunner tasks;
	private final ContentLibraryService library;
	private final DefaultTableModel model = new DefaultTableModel(
		new Object[]{"Owner", "Title", "Type", "File", "Size"}, 0)
	{
		@Override
		public boolean isCellEditable(int row, int column)
		{
			return false;
		}
	};
	private final JTable table = new JTable(model);
	private final JLabel status = new JLabel("Connect to a console, then refresh the library");
	private final JProgressBar progress = new JProgressBar();
	private List<ContentLibraryService.Item> items = List.of();

	public ContentLibraryPanel(ConsoleService console, TaskRunner tasks)
	{
		super(new BorderLayout(10, 10));
		this.tasks = tasks;
		library = new ContentLibraryService(console);
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));

		JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
		JButton refresh = new JButton("Refresh Library");
		JButton install = new JButton("Install Packages");
		JButton details = new JButton("Show Selected Path");
		refresh.addActionListener(event -> refresh());
		install.addActionListener(event -> choosePackages());
		details.addActionListener(event -> showSelectedPath());
		toolbar.add(refresh);
		toolbar.add(install);
		toolbar.add(details);
		toolbar.add(status);
		add(toolbar, BorderLayout.NORTH);

		table.setAutoCreateRowSorter(true);
		table.setFillsViewportHeight(true);
		add(new JScrollPane(table), BorderLayout.CENTER);
		progress.setStringPainted(true);
		progress.setVisible(false);
		add(progress, BorderLayout.SOUTH);
	}

	private void refresh()
	{
		status.setText("Reading content folders...");
		tasks.run("refresh content library", () -> {
			List<ContentLibraryService.Item> found = library.scan(
				message -> SwingUtilities.invokeLater(() -> status.setText(message)));
			SwingUtilities.invokeLater(() -> {
				items = found;
				model.setRowCount(0);
				for (ContentLibraryService.Item item : found)
				{
					String title = item.title().equals(item.titleId())
						? item.titleId() : item.title() + " [" + item.titleId() + "]";
					model.addRow(new Object[]{item.ownerName(), title, item.contentTypeName(),
						item.fileName(), formatSize(item.size())});
				}
				status.setText(found.size() + " package(s)");
			});
		});
	}

	private void choosePackages()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setMultiSelectionEnabled(true);
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		List<Path> selected = Arrays.stream(chooser.getSelectedFiles())
			.map(file -> file.toPath().toAbsolutePath().normalize()).toList();
		progress.setVisible(true);
		progress.setMaximum(Math.max(1, selected.size()));
		progress.setValue(0);
		tasks.run("install content packages", () -> {
			int installed = 0;
			int existing = 0;
			for (int index = 0; index < selected.size(); index++)
			{
				Path packageFile = selected.get(index);
				int position = index;
				ContentLibraryService.InstallResult result = library.install(packageFile,
					(done, total, message) -> SwingUtilities.invokeLater(() -> {
						progress.setString(packageFile.getFileName() + ": " + message);
						status.setText("Installing " + packageFile.getFileName());
					}));
				if (result.existingDestination())
				{
					existing++;
				}
				else
				{
					installed++;
				}
				SwingUtilities.invokeLater(() -> progress.setValue(position + 1));
			}
			int added = installed;
			int verified = existing;
			SwingUtilities.invokeLater(() -> status.setText(
				added + " installed, " + verified + " existing verified"));
		});
	}

	private void showSelectedPath()
	{
		int viewRow = table.getSelectedRow();
		if (viewRow < 0)
		{
			return;
		}
		int row = table.convertRowIndexToModel(viewRow);
		ContentLibraryService.Item item = items.get(row);
		JOptionPane.showMessageDialog(this,
			"Owner folder: " + item.ownerId() + "\nTitle folder: " + item.titleId()
				+ "\nContent type folder: " + String.format(Locale.ROOT, "%08X", item.contentType())
				+ "\n\n" + item.remotePath(),
			"Actual Console Path", JOptionPane.INFORMATION_MESSAGE);
	}

	private static String formatSize(long bytes)
	{
		if (bytes < 1_024)
		{
			return String.format(Locale.ROOT, "%,d bytes", bytes);
		}
		String[] units = {"KB", "MB", "GB"};
		double value = bytes;
		int unit = -1;
		do
		{
			value /= 1_024.0;
			unit++;
		}
		while (value >= 1_024.0 && unit < units.length - 1);
		return String.format(Locale.ROOT, value >= 100 ? "%,.0f %s" : "%,.1f %s", value, units[unit]);
	}
}
