package openrtm.ui;

import openrtm.profile.ProfileService;
import openrtm.profile.ProfileWorkspace;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProfileGameAdderPanel extends JPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private static final Color ACCENT = new Color(91, 141, 239);
	private static final Pattern TITLE_FILE = Pattern.compile("(?i)([0-9a-f]{8})\\.gpd$");
	private final ProfileWorkspace workspace;
	private final TaskRunner tasks;
	private final JLabel profile = new JLabel("Open a gamer profile on the Gamer Profile page");
	private final JTextField gameFile = new JTextField(38);
	private final JTextField titleId = new JTextField(10);
	private final JTextField titleName = new JTextField(28);
	private final DefaultTableModel games = new DefaultTableModel(
		new Object[]{"Game", "Title ID", "Achievements", "Gamerscore"}, 0)
	{
		@Override
		public boolean isCellEditable(int row, int column)
		{
			return false;
		}
	};

	public ProfileGameAdderPanel(ProfileWorkspace workspace, TaskRunner tasks)
	{
		super(new BorderLayout(10, 10));
		this.workspace = workspace;
		this.tasks = tasks;
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
		add(header(), BorderLayout.NORTH);
		add(new JScrollPane(new JTable(games)), BorderLayout.CENTER);
		add(addSection(), BorderLayout.SOUTH);
		workspace.addListener(() -> SwingUtilities.invokeLater(this::workspaceChanged));
	}

	private JPanel header()
	{
		JPanel section = section("Profile Games");
		profile.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.add(profile);
		return section;
	}

	private JPanel addSection()
	{
		JPanel section = section("Add Game");
		JPanel sourceRow = row();
		sourceRow.add(new JLabel("Game database"));
		sourceRow.add(gameFile);
		JButton browse = new JButton("Browse");
		browse.addActionListener(event -> chooseGame());
		sourceRow.add(browse);
		section.add(sourceRow);

		JPanel details = row();
		details.add(new JLabel("Title ID"));
		details.add(titleId);
		details.add(new JLabel("Game name"));
		details.add(titleName);
		JButton add = new JButton("Add Game");
		add.addActionListener(event -> addGame());
		details.add(add);
		section.add(details);
		return section;
	}

	private void chooseGame()
	{
		JFileChooser chooser = new JFileChooser();
		if (!gameFile.getText().isBlank())
		{
			chooser.setSelectedFile(Path.of(gameFile.getText()).toFile());
		}
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			Path selected = chooser.getSelectedFile().toPath();
			gameFile.setText(selected.toAbsolutePath().toString());
			Matcher matcher = TITLE_FILE.matcher(selected.getFileName().toString());
			if (matcher.find())
			{
				titleId.setText(matcher.group(1).toUpperCase());
			}
		}
	}

	private void addGame()
	{
		Path profilePath = workspace.path();
		if (profilePath == null)
		{
			showWarning("Open a gamer profile first");
			return;
		}
		if (gameFile.getText().isBlank())
		{
			showWarning("Choose a game database");
			return;
		}
		int answer = JOptionPane.showConfirmDialog(this,
			"Add this game to the open profile?", "Add Game", JOptionPane.YES_NO_OPTION);
		if (answer != JOptionPane.YES_OPTION)
		{
			return;
		}
		tasks.run("add profile game", () -> {
			workspace.profiles().addGame(profilePath, profilePath, Path.of(gameFile.getText().trim()),
				titleId.getText(), titleName.getText(), true);
			workspace.refresh();
			SwingUtilities.invokeLater(() -> {
				gameFile.setText("");
				titleId.setText("");
				titleName.setText("");
			});
		});
	}

	private void workspaceChanged()
	{
		ProfileService.Profile loaded = workspace.profile();
		games.setRowCount(0);
		if (loaded == null)
		{
			profile.setText("Open a gamer profile on the Gamer Profile page");
			return;
		}
		profile.setText(loaded.displayName() + "  |  " + loaded.profileId());
		for (ProfileService.Game game : loaded.games())
		{
			games.addRow(new Object[]{game.name(), game.titleId(),
				game.earnedAchievements() + " / " + game.possibleAchievements(),
				game.earnedCredit() + " / " + game.possibleCredit()});
		}
	}

	private static JPanel section(String title)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
			BorderFactory.createEmptyBorder(12, 0, 14, 0)));
		JLabel label = new JLabel(title);
		label.setForeground(ACCENT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(label);
		return panel;
	}

	private static JPanel row()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private void showWarning(String message)
	{
		JOptionPane.showMessageDialog(this, message, "Profile Games", JOptionPane.WARNING_MESSAGE);
	}
}
