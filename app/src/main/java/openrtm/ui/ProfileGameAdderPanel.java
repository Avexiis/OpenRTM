package openrtm.ui;

import openrtm.profile.ProfileGameCatalog;
import openrtm.profile.ProfileService;
import openrtm.profile.ProfileWorkspace;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public final class ProfileGameAdderPanel extends JPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private static final Color ACCENT = new Color(91, 141, 239);
	private final ProfileWorkspace workspace;
	private final TaskRunner tasks;
	private final ProfileGameCatalog catalog = new ProfileGameCatalog();
	private final JLabel profile = new JLabel("Open a gamer profile on the Gamer Profile page");
	private final JLabel catalogStatus = new JLabel("Loading bundled games...");
	private final JTextField search = new JTextField(32);
	private final JButton add = new JButton("Add Selected Game");
	private final List<ProfileGameCatalog.Entry> catalogEntries = new ArrayList<>();
	private final DefaultTableModel games = readOnlyModel(
		new Object[]{"Game", "Title ID", "Achievements", "Gamerscore"});
	private final DefaultTableModel available = readOnlyModel(
		new Object[]{"Game", "Title ID", "Status"});
	private final TableRowSorter<DefaultTableModel> availableSorter = new TableRowSorter<>(available);
	private final JTable availableTable;

	public ProfileGameAdderPanel(ProfileWorkspace workspace, TaskRunner tasks)
	{
		super(new BorderLayout(10, 10));
		this.workspace = workspace;
		this.tasks = tasks;
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
		add(header(), BorderLayout.NORTH);
		availableTable = availableTable();
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, currentGamesPanel(), catalogPanel());
		split.setBorder(null);
		split.setResizeWeight(0.35);
		split.setContinuousLayout(true);
		add(split, BorderLayout.CENTER);
		bindSearch();
		workspace.addListener(() -> SwingUtilities.invokeLater(this::workspaceChanged));
		loadCatalog();
	}

	private JPanel header()
	{
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
		JLabel heading = new JLabel("Profile Games");
		heading.setForeground(ACCENT);
		panel.add(heading, BorderLayout.WEST);
		panel.add(profile, BorderLayout.EAST);
		return panel;
	}

	private JPanel currentGamesPanel()
	{
		JTable table = new JTable(games);
		table.setFillsViewportHeight(true);
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
		panel.add(new JLabel("Games in Profile"), BorderLayout.NORTH);
		panel.add(new JScrollPane(table), BorderLayout.CENTER);
		return panel;
	}

	private JPanel catalogPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		JPanel toolbar = new JPanel(new BorderLayout(8, 0));
		JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
		searchRow.add(new JLabel("Find a game"));
		searchRow.add(search);
		toolbar.add(searchRow, BorderLayout.WEST);
		toolbar.add(catalogStatus, BorderLayout.EAST);
		panel.add(toolbar, BorderLayout.NORTH);
		panel.add(new JScrollPane(availableTable), BorderLayout.CENTER);
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
		add.setEnabled(false);
		add.addActionListener(event -> addGame());
		actions.add(add);
		panel.add(actions, BorderLayout.SOUTH);
		return panel;
	}

	private JTable availableTable()
	{
		JTable table = new JTable(available);
		table.setRowSorter(availableSorter);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setFillsViewportHeight(true);
		table.getSelectionModel().addListSelectionListener(event -> updateAddEnabled());
		table.getColumnModel().getColumn(1).setPreferredWidth(90);
		table.getColumnModel().getColumn(1).setMaxWidth(110);
		table.getColumnModel().getColumn(2).setPreferredWidth(100);
		table.getColumnModel().getColumn(2).setMaxWidth(120);
		return table;
	}

	private void bindSearch()
	{
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent event)
			{
				filterCatalog();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				filterCatalog();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				filterCatalog();
			}
		});
	}

	private void loadCatalog()
	{
		new SwingWorker<List<ProfileGameCatalog.Entry>, Void>()
		{
			@Override
			protected List<ProfileGameCatalog.Entry> doInBackground() throws Exception
			{
				return catalog.load();
			}

			@Override
			protected void done()
			{
				try
				{
					catalogEntries.clear();
					catalogEntries.addAll(get());
					rebuildCatalog();
				}
				catch (InterruptedException failure)
				{
					Thread.currentThread().interrupt();
					catalogStatus.setText("Game catalog unavailable");
				}
				catch (ExecutionException failure)
				{
					catalogStatus.setText("Game catalog unavailable");
					System.err.println("Game catalog could not be loaded: " + failure.getCause());
				}
			}
		}.execute();
	}

	private void filterCatalog()
	{
		String query = search.getText().trim().toLowerCase(Locale.ROOT);
		if (query.isEmpty())
		{
			availableSorter.setRowFilter(null);
		}
		else
		{
			availableSorter.setRowFilter(new RowFilter<>()
			{
				@Override
				public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry)
				{
					int row = entry.getIdentifier();
					if (row < 0 || row >= catalogEntries.size())
					{
						return false;
					}
					ProfileGameCatalog.Entry game = catalogEntries.get(row);
					return game.name().toLowerCase(Locale.ROOT).contains(query)
						|| game.titleId().toLowerCase(Locale.ROOT).contains(query);
				}
			});
		}
		updateCatalogStatus();
		updateAddEnabled();
	}

	private void addGame()
	{
		Path profilePath = workspace.path();
		ProfileGameCatalog.Entry selected = selectedCatalogEntry();
		if (profilePath == null)
		{
			showWarning("Open a gamer profile first");
			return;
		}
		if (selected == null)
		{
			showWarning("Choose a game from the title browser");
			return;
		}
		if (profileTitleIds().contains(selected.titleId()))
		{
			showWarning("This game is already present in the profile");
			return;
		}
		int answer = JOptionPane.showConfirmDialog(this,
			"Add " + selected.name() + " to the open profile?", "Add Game", JOptionPane.YES_NO_OPTION);
		if (answer != JOptionPane.YES_OPTION)
		{
			return;
		}
		tasks.run("add profile game", () -> {
			byte[] database = catalog.read(selected);
			workspace.profiles().addGame(profilePath, profilePath, database,
				selected.titleId(), selected.name(), true);
			workspace.refresh();
		});
	}

	private void workspaceChanged()
	{
		ProfileService.Profile loaded = workspace.profile();
		games.setRowCount(0);
		if (loaded == null)
		{
			profile.setText("Open a gamer profile on the Gamer Profile page");
		}
		else
		{
			profile.setText(loaded.displayName() + "  |  " + loaded.profileId());
			for (ProfileService.Game game : loaded.games())
			{
				games.addRow(new Object[]{game.name(), game.titleId(),
					game.earnedAchievements() + " / " + game.possibleAchievements(),
					game.earnedCredit() + " / " + game.possibleCredit()});
			}
		}
		rebuildCatalog();
	}

	private void rebuildCatalog()
	{
		Set<String> present = profileTitleIds();
		available.setRowCount(0);
		for (ProfileGameCatalog.Entry entry : catalogEntries)
		{
			available.addRow(new Object[]{entry.name(), entry.titleId(),
				present.contains(entry.titleId()) ? "In profile" : "Available"});
		}
		filterCatalog();
	}

	private Set<String> profileTitleIds()
	{
		Set<String> ids = new HashSet<>();
		ProfileService.Profile loaded = workspace.profile();
		if (loaded != null)
		{
			for (ProfileService.Game game : loaded.games())
			{
				ids.add(game.titleId());
			}
		}
		return ids;
	}

	private ProfileGameCatalog.Entry selectedCatalogEntry()
	{
		int selected = availableTable.getSelectedRow();
		if (selected < 0)
		{
			return null;
		}
		int modelRow = availableTable.convertRowIndexToModel(selected);
		return modelRow >= 0 && modelRow < catalogEntries.size() ? catalogEntries.get(modelRow) : null;
	}

	private void updateCatalogStatus()
	{
		if (catalogEntries.isEmpty())
		{
			catalogStatus.setText("Loading bundled games...");
		}
		else
		{
			catalogStatus.setText(availableTable.getRowCount() + " of " + catalogEntries.size() + " titles");
		}
	}

	private void updateAddEnabled()
	{
		ProfileGameCatalog.Entry selected = selectedCatalogEntry();
		add.setEnabled(workspace.path() != null && selected != null
			&& !profileTitleIds().contains(selected.titleId()));
	}

	private static DefaultTableModel readOnlyModel(Object[] columns)
	{
		return new DefaultTableModel(columns, 0)
		{
			@Override
			public boolean isCellEditable(int row, int column)
			{
				return false;
			}
		};
	}

	private void showWarning(String message)
	{
		JOptionPane.showMessageDialog(this, message, "Profile Games", JOptionPane.WARNING_MESSAGE);
	}
}
