package openrtm.ui;

import openrtm.kvchecker.KvCheckerService;
import openrtm.kvchecker.KvFileOrganizer;
import openrtm.kvchecker.kerberosauthentication.KeyVaultFile.InvalidKeyVaultException;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public final class KvCheckerPanel extends JPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private static final Color ACCENT = new Color(91, 141, 239);
	private static final Color OK = new Color(92, 184, 117);
	private static final Color WARN = new Color(210, 157, 73);
	private static final Color DANGER = new Color(214, 78, 78);
	private final KvCheckerService checker = new KvCheckerService();
	private final KvFileOrganizer organizer = new KvFileOrganizer();
	private final List<Item> items = new ArrayList<>();
	private final ItemTableModel model = new ItemTableModel();
	private final JTable table = new JTable(model);
	private final JButton browse = new JButton("Add Files");
	private final JButton addFolder = new JButton("Add Folder");
	private final JButton checkAll = new JButton("Check All");
	private final JButton checkSelected = new JButton("Check Selected");
	private final JButton sortResults = new JButton("Sort Results");
	private final JButton remove = new JButton("Remove Selected");
	private final JButton clear = new JButton("Clear");
	private final JButton cancel = new JButton("Cancel");
	private final JProgressBar progress = new JProgressBar();
	private final JLabel summary = new JLabel("0 files");
	private SwingWorker<?, ?> worker;

	public KvCheckerPanel()
	{
		super(new BorderLayout(10, 10));
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
		add(toolbar(), BorderLayout.NORTH);
		configureTable();
		JScrollPane scroll = new JScrollPane(table);
		add(scroll, BorderLayout.CENTER);
		add(footer(), BorderLayout.SOUTH);
		TransferHandler transfers = fileTransferHandler();
		setTransferHandler(transfers);
		table.setTransferHandler(transfers);
		scroll.setTransferHandler(transfers);
		bindActions();
		updateControls();
	}

	public void shutdown()
	{
		SwingWorker<?, ?> active = worker;
		if (active != null)
		{
			active.cancel(true);
		}
	}

	private JPanel toolbar()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
		JPanel sourceActions = new JPanel(new BorderLayout());
		JLabel heading = new JLabel("KV Checker");
		heading.setForeground(ACCENT);
		sourceActions.add(heading, BorderLayout.WEST);
		JPanel importActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
		importActions.add(browse);
		importActions.add(addFolder);
		sourceActions.add(importActions, BorderLayout.EAST);
		panel.add(sourceActions);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
		actions.add(checkSelected);
		actions.add(checkAll);
		actions.add(sortResults);
		actions.add(remove);
		actions.add(clear);
		actions.add(cancel);
		panel.add(actions);
		return panel;
	}

	private JPanel footer()
	{
		JPanel panel = new JPanel(new BorderLayout(10, 0));
		panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));
		progress.setStringPainted(true);
		progress.setString("Idle");
		panel.add(progress, BorderLayout.CENTER);
		JPanel counts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		counts.add(summary);
		panel.add(counts, BorderLayout.EAST);
		return panel;
	}

	private void configureTable()
	{
		table.setFillsViewportHeight(true);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.getColumnModel().getColumn(0).setPreferredWidth(170);
		table.getColumnModel().getColumn(1).setPreferredWidth(105);
		table.getColumnModel().getColumn(1).setMaxWidth(130);
		table.getColumnModel().getColumn(2).setPreferredWidth(320);
		table.getColumnModel().getColumn(3).setPreferredWidth(90);
		table.getColumnModel().getColumn(3).setMaxWidth(110);
		table.getColumnModel().getColumn(4).setPreferredWidth(240);
		table.getColumnModel().getColumn(3).setCellRenderer(new StatusRenderer());
		table.getSelectionModel().addListSelectionListener(event -> updateControls());
	}

	private void bindActions()
	{
		browse.addActionListener(event -> browse());
		addFolder.addActionListener(event -> browseFolder());
		checkAll.addActionListener(event -> startCheck(allValidRows()));
		checkSelected.addActionListener(event -> startCheck(selectedValidRows()));
		sortResults.addActionListener(event -> chooseSortFolder());
		remove.addActionListener(event -> removeSelected());
		clear.addActionListener(event -> clear());
		cancel.addActionListener(event -> {
			SwingWorker<?, ?> active = worker;
			if (active != null)
			{
				active.cancel(true);
			}
		});
	}

	private void browse()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose Key Vault Files");
		chooser.setMultiSelectionEnabled(true);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter("Key vault files (*.bin)", "bin"));
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			List<Path> selected = new ArrayList<>();
			for (File file : chooser.getSelectedFiles())
			{
				selected.add(file.toPath());
			}
			if (selected.isEmpty() && chooser.getSelectedFile() != null)
			{
				selected.add(chooser.getSelectedFile().toPath());
			}
			startImport(selected);
		}
	}

	private void browseFolder()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose a Folder to Search");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			startImport(Collections.singletonList(chooser.getSelectedFile().toPath()));
		}
	}

	private void startImport(List<Path> sources)
	{
		if (worker != null || sources.isEmpty())
		{
			return;
		}
		Set<Path> existing = new LinkedHashSet<>();
		for (Item item : items)
		{
			existing.add(item.path);
		}
		progress.setIndeterminate(true);
		progress.setString("Finding key vaults...");
		SwingWorker<ImportResult, Void> importing = new SwingWorker<>()
		{
			@Override
			protected ImportResult doInBackground()
			{
				ImportResult result = new ImportResult();
				Set<Path> candidates = new LinkedHashSet<>();
				for (Path source : sources)
				{
					if (isCancelled())
					{
						break;
					}
					Path path = source.toAbsolutePath().normalize();
					if (Files.isDirectory(path))
					{
						try
						{
							KvFileOrganizer.Discovery discovery = organizer.discover(path);
							candidates.addAll(discovery.files());
							result.unreadable += discovery.unreadableEntries();
						}
						catch (IOException failure)
						{
							result.unreadable++;
						}
					}
					else if (isBin(path))
					{
						candidates.add(path);
					}
					else
					{
						result.rejected++;
					}
				}
				for (Path path : candidates)
				{
					if (isCancelled())
					{
						break;
					}
					if (existing.contains(path))
					{
						result.duplicates++;
						continue;
					}
					try
					{
						String consoleSerial = checker.validate(path);
						result.items.add(new Item(path, consoleSerial, true, Status.READY, ""));
					}
					catch (InvalidKeyVaultException failure)
					{
						result.items.add(new Item(path, "", false, Status.INVALID,
							failure.getMessage()));
					}
					catch (IOException failure)
					{
						result.items.add(new Item(path, "", false, Status.ERROR,
							readableMessage(failure)));
					}
				}
				result.candidates = candidates.size();
				return result;
			}

			@Override
			protected void done()
			{
				progress.setIndeterminate(false);
				worker = null;
				try
				{
					ImportResult result = get();
					items.addAll(result.items);
					model.fireTableDataChanged();
					progress.setValue(0);
					progress.setString(result.items.size() + " file(s) added");
					showImportNotice(result);
				}
				catch (CancellationException failure)
				{
					progress.setString("Canceled");
				}
				catch (InterruptedException failure)
				{
					Thread.currentThread().interrupt();
					progress.setString("Canceled");
				}
				catch (ExecutionException failure)
				{
					progress.setString("Import failed");
					JOptionPane.showMessageDialog(KvCheckerPanel.this,
						readableMessage(failure.getCause()), "KV Checker",
						JOptionPane.ERROR_MESSAGE);
				}
				updateSummary();
				updateControls();
			}
		};
		worker = importing;
		updateControls();
		importing.execute();
	}

	private void showImportNotice(ImportResult result)
	{
		List<String> messages = new ArrayList<>();
		if (result.candidates == 0)
		{
			messages.add("No KV.bin files were found.");
		}
		else if (result.items.isEmpty() && result.duplicates > 0)
		{
			messages.add("All discovered key vaults are already in the list.");
		}
		if (result.rejected > 0)
		{
			messages.add(result.rejected
				+ " file(s) were skipped because only .bin files are accepted.");
		}
		if (result.unreadable > 0)
		{
			messages.add(result.unreadable + " folder entry or entries could not be read.");
		}
		if (!messages.isEmpty())
		{
			JOptionPane.showMessageDialog(this,
				String.join(System.lineSeparator(), messages),
				"KV Checker", JOptionPane.WARNING_MESSAGE);
		}
	}

	private void startCheck(List<Integer> rows)
	{
		if (worker != null)
		{
			return;
		}
		if (rows.isEmpty())
		{
			JOptionPane.showMessageDialog(this, "Choose at least one valid key vault.",
				"KV Checker", JOptionPane.WARNING_MESSAGE);
			return;
		}
		progress.setMinimum(0);
		progress.setMaximum(rows.size());
		progress.setValue(0);
		progress.setString("0 / " + rows.size());
		SwingWorker<Void, RowUpdate> checking = new SwingWorker<>()
		{
			@Override
			protected Void doInBackground()
			{
				int completed = 0;
				for (int row : rows)
				{
					if (isCancelled())
					{
						break;
					}
					publish(new RowUpdate(row, true, Status.CHECKING, ""));
					try
					{
						KvCheckerService.Result result = checker.check(items.get(row).path, this::isCancelled);
						if (result == KvCheckerService.Result.BANNED)
						{
							publish(new RowUpdate(row, true, Status.BANNED, "Key vault is banned"));
						}
						else
						{
							publish(new RowUpdate(row, true, Status.UNBANNED, "Key vault is unbanned"));
						}
					}
					catch (InvalidKeyVaultException failure)
					{
						publish(new RowUpdate(row, false, Status.INVALID, failure.getMessage()));
					}
					catch (InterruptedIOException failure)
					{
						publish(new RowUpdate(row, true, Status.READY, "Canceled"));
						break;
					}
					catch (GeneralSecurityException failure)
					{
						publish(new RowUpdate(row, true, Status.ERROR, "Authentication failed"));
					}
					catch (IOException | RuntimeException failure)
					{
						publish(new RowUpdate(row, true, Status.ERROR, readableMessage(failure)));
					}
					completed++;
					setProgressValue(completed, rows.size());
				}
				return null;
			}

			@Override
			protected void process(List<RowUpdate> updates)
			{
				for (RowUpdate update : updates)
				{
					Item item = items.get(update.row);
					item.valid = update.valid;
					item.status = update.status;
					item.detail = update.detail;
					model.fireTableRowsUpdated(update.row, update.row);
				}
				updateSummary();
			}

			@Override
			protected void done()
			{
				for (int row : rows)
				{
					Item item = items.get(row);
					if (item.status == Status.CHECKING)
					{
						item.status = Status.READY;
						item.detail = "Canceled";
						model.fireTableRowsUpdated(row, row);
					}
				}
				progress.setString(isCancelled() ? "Canceled" : "Complete");
				worker = null;
				updateSummary();
				updateControls();
			}

			private void setProgressValue(int completed, int total)
			{
				SwingUtilities.invokeLater(() -> {
					progress.setValue(completed);
					progress.setString(completed + " / " + total);
				});
			}
		};
		worker = checking;
		updateControls();
		checking.execute();
	}

	private void chooseSortFolder()
	{
		List<Integer> rows = resultRows();
		if (rows.isEmpty())
		{
			JOptionPane.showMessageDialog(this,
				"Check at least one key vault before sorting results.",
				"KV Checker", JOptionPane.WARNING_MESSAGE);
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Choose a Folder for Sorted Key Vaults");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		Path destination = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
		String message = "Move " + rows.size() + " checked key vault(s) into:"
			+ System.lineSeparator() + destination.resolve("banned")
			+ System.lineSeparator() + destination.resolve("unbanned")
			+ System.lineSeparator() + System.lineSeparator()
			+ "Existing KV.bin files will not be overwritten.";
		if (JOptionPane.showConfirmDialog(this, message, "Sort KV Results",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION)
		{
			startSort(rows, destination);
		}
	}

	private void startSort(List<Integer> rows, Path destination)
	{
		progress.setIndeterminate(false);
		progress.setMinimum(0);
		progress.setMaximum(rows.size());
		progress.setValue(0);
		progress.setString("0 / " + rows.size());
		SwingWorker<SortOutcome, SortUpdate> sorting = new SwingWorker<>()
		{
			@Override
			protected SortOutcome doInBackground()
			{
				SortOutcome outcome = new SortOutcome();
				int completed = 0;
				for (int row : rows)
				{
					if (isCancelled())
					{
						break;
					}
					Item item = items.get(row);
					try
					{
						String currentSerial = checker.validate(item.path);
						if (!item.consoleSerial.equals(currentSerial))
						{
							throw new IOException("The key vault changed after it was checked");
						}
						KvCheckerService.Result result = item.status == Status.BANNED
							? KvCheckerService.Result.BANNED : KvCheckerService.Result.UNBANNED;
						Path source = item.path;
						Path moved = organizer.move(source, destination, currentSerial, result);
						String detail = source.equals(moved) ? "Already sorted"
							: "Moved to " + moved.getParent();
						publish(new SortUpdate(row, moved, detail));
						outcome.moved++;
					}
					catch (IOException | RuntimeException failure)
					{
						publish(new SortUpdate(row, null,
							"Not moved: " + readableMessage(failure)));
						outcome.failed++;
					}
					completed++;
					setProgressValue(completed, rows.size());
				}
				return outcome;
			}

			@Override
			protected void process(List<SortUpdate> updates)
			{
				for (SortUpdate update : updates)
				{
					Item item = items.get(update.row);
					if (update.path != null)
					{
						item.path = update.path;
					}
					item.detail = update.detail;
					model.fireTableRowsUpdated(update.row, update.row);
				}
			}

			@Override
			protected void done()
			{
				worker = null;
				try
				{
					SortOutcome outcome = get();
					progress.setString(outcome.moved + " sorted, " + outcome.failed + " not moved");
					if (outcome.failed > 0)
					{
						JOptionPane.showMessageDialog(KvCheckerPanel.this,
							outcome.failed + " key vault(s) could not be moved. Review the Result column.",
							"Sort KV Results", JOptionPane.WARNING_MESSAGE);
					}
				}
				catch (CancellationException failure)
				{
					progress.setString("Canceled");
				}
				catch (InterruptedException failure)
				{
					Thread.currentThread().interrupt();
					progress.setString("Canceled");
				}
				catch (ExecutionException failure)
				{
					progress.setString("Sort failed");
					JOptionPane.showMessageDialog(KvCheckerPanel.this,
						readableMessage(failure.getCause()), "Sort KV Results",
						JOptionPane.ERROR_MESSAGE);
				}
				updateControls();
			}

			private void setProgressValue(int completed, int total)
			{
				SwingUtilities.invokeLater(() -> {
					progress.setValue(completed);
					progress.setString(completed + " / " + total);
				});
			}
		};
		worker = sorting;
		updateControls();
		sorting.execute();
	}

	private List<Integer> resultRows()
	{
		List<Integer> rows = new ArrayList<>();
		for (int row = 0; row < items.size(); row++)
		{
			Status status = items.get(row).status;
			if (status == Status.BANNED || status == Status.UNBANNED)
			{
				rows.add(row);
			}
		}
		return rows;
	}

	private List<Integer> allValidRows()
	{
		List<Integer> rows = new ArrayList<>();
		for (int row = 0; row < items.size(); row++)
		{
			if (items.get(row).valid)
			{
				rows.add(row);
			}
		}
		return rows;
	}

	private List<Integer> selectedValidRows()
	{
		Set<Integer> rows = new LinkedHashSet<>();
		for (int viewRow : table.getSelectedRows())
		{
			int row = table.convertRowIndexToModel(viewRow);
			if (items.get(row).valid)
			{
				rows.add(row);
			}
		}
		return new ArrayList<>(rows);
	}

	private void removeSelected()
	{
		List<Integer> rows = new ArrayList<>();
		for (int viewRow : table.getSelectedRows())
		{
			rows.add(table.convertRowIndexToModel(viewRow));
		}
		rows.sort(Collections.reverseOrder());
		for (int row : rows)
		{
			items.remove(row);
		}
		model.fireTableDataChanged();
		updateSummary();
		updateControls();
	}

	private void clear()
	{
		items.clear();
		model.fireTableDataChanged();
		progress.setValue(0);
		progress.setString("Idle");
		updateSummary();
		updateControls();
	}

	private void updateSummary()
	{
		int banned = 0;
		int unbanned = 0;
		int problems = 0;
		for (Item item : items)
		{
			if (item.status == Status.BANNED)
			{
				banned++;
			}
			else if (item.status == Status.UNBANNED)
			{
				unbanned++;
			}
			else if (item.status == Status.INVALID || item.status == Status.ERROR)
			{
				problems++;
			}
		}
		summary.setText(items.size() + " files  |  " + unbanned + " unbanned  |  "
			+ banned + " banned  |  " + problems + " errors");
	}

	private void updateControls()
	{
		boolean busy = worker != null;
		boolean selected = table.getSelectedRowCount() > 0;
		browse.setEnabled(!busy);
		addFolder.setEnabled(!busy);
		checkAll.setEnabled(!busy && !allValidRows().isEmpty());
		checkSelected.setEnabled(!busy && selected && !selectedValidRows().isEmpty());
		sortResults.setEnabled(!busy && !resultRows().isEmpty());
		remove.setEnabled(!busy && selected);
		clear.setEnabled(!busy && !items.isEmpty());
		cancel.setEnabled(busy);
	}

	private TransferHandler fileTransferHandler()
	{
		return new TransferHandler()
		{
			@Override
			public boolean canImport(TransferSupport support)
			{
				return worker == null && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
			}

			@Override
			public boolean importData(TransferSupport support)
			{
				if (!canImport(support))
				{
					return false;
				}
				try
				{
					List<?> values = (List<?>) support.getTransferable()
						.getTransferData(DataFlavor.javaFileListFlavor);
					List<Path> paths = new ArrayList<>();
					for (Object value : values)
					{
						if (value instanceof File)
						{
							paths.add(((File) value).toPath());
						}
					}
					startImport(paths);
					return !paths.isEmpty();
				}
				catch (UnsupportedFlavorException | IOException failure)
				{
					return false;
				}
			}
		};
	}

	private static String readableMessage(Throwable failure)
	{
		String message = failure.getMessage();
		return message == null || message.isBlank() ? "Check failed" : message;
	}

	private static boolean isBin(Path path)
	{
		return path.getFileName() != null
			&& path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".bin");
	}

	private final class ItemTableModel extends AbstractTableModel
	{
		private final String[] columns = {"File", "Console Serial", "Folder", "Status", "Result"};

		@Override
		public int getRowCount()
		{
			return items.size();
		}

		@Override
		public int getColumnCount()
		{
			return columns.length;
		}

		@Override
		public String getColumnName(int column)
		{
			return columns[column];
		}

		@Override
		public Object getValueAt(int row, int column)
		{
			Item item = items.get(row);
			switch (column)
			{
				case 0:
					return item.path.getFileName();
				case 1:
					return item.consoleSerial;
				case 2:
					return item.path.getParent();
				case 3:
					return item.status.label;
				case 4:
					return item.detail;
				default:
					return "";
			}
		}
	}

	private static final class Item
	{
		private Path path;
		private final String consoleSerial;
		private boolean valid;
		private Status status;
		private String detail;

		private Item(Path path, String consoleSerial, boolean valid, Status status, String detail)
		{
			this.path = path;
			this.consoleSerial = consoleSerial;
			this.valid = valid;
			this.status = status;
			this.detail = detail;
		}
	}

	private static final class ImportResult
	{
		private final List<Item> items = new ArrayList<>();
		private int candidates;
		private int duplicates;
		private int rejected;
		private int unreadable;
	}

	private static final class SortOutcome
	{
		private int moved;
		private int failed;
	}

	private static final class SortUpdate
	{
		private final int row;
		private final Path path;
		private final String detail;

		private SortUpdate(int row, Path path, String detail)
		{
			this.row = row;
			this.path = path;
			this.detail = detail;
		}
	}

	private static final class RowUpdate
	{
		private final int row;
		private final boolean valid;
		private final Status status;
		private final String detail;

		private RowUpdate(int row, boolean valid, Status status, String detail)
		{
			this.row = row;
			this.valid = valid;
			this.status = status;
			this.detail = detail;
		}
	}

	private enum Status
	{
		READY("Ready"),
		CHECKING("Checking"),
		UNBANNED("Unbanned"),
		BANNED("Banned"),
		INVALID("Invalid"),
		ERROR("Error");

		private final String label;

		Status(String label)
		{
			this.label = label;
		}
	}

	private final class StatusRenderer extends DefaultTableCellRenderer
	{
		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
		                                               boolean focused, int row, int column)
		{
			Component component = super.getTableCellRendererComponent(table, value, selected,
				focused, row, column);
			if (!selected)
			{
				Status status = items.get(table.convertRowIndexToModel(row)).status;
				if (status == Status.UNBANNED)
				{
					component.setForeground(OK);
				}
				else if (status == Status.BANNED)
				{
					component.setForeground(DANGER);
				}
				else if (status == Status.INVALID || status == Status.ERROR)
				{
					component.setForeground(WARN);
				}
				else
				{
					component.setForeground(UIManager.getColor("Table.foreground"));
				}
			}
			return component;
		}
	}
}
