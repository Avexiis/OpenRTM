package openrtm.ui;

import openrtm.console.ConsoleService;
import openrtm.console.ContentLibraryService;
import openrtm.profile.ProfileIdentityResolver;

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
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ContentLibraryPanel extends FileDropPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private final TaskRunner tasks;
	private final ContentLibraryService library;
	private final DefaultTableModel model = new DefaultTableModel(
		new Object[]{"Owner", "ID", "Title", "Type", "File", "Size"}, 0)
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

	public ContentLibraryPanel(ConsoleService console, TaskRunner tasks, ProfileIdentityResolver identities)
	{
		super(new BorderLayout(10, 10));
		this.tasks = tasks;
		library = new ContentLibraryService(console, identities);
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
		toolbar.add(fileDropHint("Drag and drop content packages"));
		toolbar.add(status);
		add(toolbar, BorderLayout.NORTH);

		table.setAutoCreateRowSorter(true);
		table.setFillsViewportHeight(true);
		table.getColumnModel().getColumn(1).setCellRenderer(new IdCellRenderer());
		table.getColumnModel().getColumn(1).setMinWidth(42);
		table.getColumnModel().getColumn(1).setMaxWidth(42);
		table.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				showOwnerId(event);
			}
		});
		add(new JScrollPane(table), BorderLayout.CENTER);
		progress.setStringPainted(true);
		progress.setVisible(false);
		add(progress, BorderLayout.SOUTH);
		enableFileDrop("Drop content packages here to use!", this::dropPackages);
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
					model.addRow(new Object[]{item.ownerName(), "ID", title, item.contentTypeName(),
						item.fileName(), formatSize(item.size())});
				}
				status.setText(found.size() + " package(s)");
			});
		});
	}

	private void showOwnerId(MouseEvent event)
	{
		int viewRow = table.rowAtPoint(event.getPoint());
		int viewColumn = table.columnAtPoint(event.getPoint());
		if (viewRow < 0 || table.convertColumnIndexToModel(viewColumn) != 1)
		{
			return;
		}
		ContentLibraryService.Item item = items.get(table.convertRowIndexToModel(viewRow));
		JOptionPane.showMessageDialog(this, item.ownerId(), "Profile ID", JOptionPane.INFORMATION_MESSAGE);
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
		installPackages(selected);
	}

	private void installPackages(List<Path> selected)
	{
		progress.setVisible(true);
		progress.setMaximum(Math.max(1, selected.size()));
		progress.setValue(0);
		tasks.run("install content packages", () -> {
			for (Path packageFile : selected)
			{
				library.validate(packageFile);
			}
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

	private boolean dropPackages(List<Path> paths)
	{
		if (paths.isEmpty() || paths.stream().anyMatch(path -> !Files.isRegularFile(path)))
		{
			JOptionPane.showMessageDialog(this, "Drop one or more content package files",
				"Install Packages", JOptionPane.WARNING_MESSAGE);
			return false;
		}
		installPackages(paths);
		return true;
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

	private static final class IdCellRenderer extends JButton implements TableCellRenderer
	{
		private IdCellRenderer()
		{
			super("ID");
			setFocusable(false);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
		                                               boolean hasFocus, int row, int column)
		{
			setBackground(selected ? table.getSelectionBackground() : table.getBackground());
			setForeground(selected ? table.getSelectionForeground() : table.getForeground());
			return this;
		}
	}
}
