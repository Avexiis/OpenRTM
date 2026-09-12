package openrtm.ui;

import openrtm.profile.ProfileService;
import openrtm.profile.ProfileWorkspace;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AchievementUnlockerPanel extends JPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private static final Color ACCENT = new Color(91, 141, 239);
	private static final DateTimeFormatter UNLOCK_TIME = DateTimeFormatter
		.ofPattern("MMM d, yyyy h:mm:ss a").withZone(ZoneId.systemDefault());
	private final ProfileWorkspace workspace;
	private final TaskRunner tasks;
	private final JComboBox<ProfileService.Game> game = new JComboBox<>();
	private final JCheckBox online = new JCheckBox("Online unlock", true);
	private final JSpinner dateTime = new JSpinner(
		new SpinnerDateModel(new Date(), null, null, Calendar.SECOND));
	private final JLabel summary = new JLabel("Open a gamer profile to load achievements");
	private final List<ProfileService.Achievement> loaded = new ArrayList<>();
	private final DefaultTableModel model = new DefaultTableModel(
		new Object[]{"Select", "Achievement", "Score", "Status", "Unlock Time", "Description"}, 0)
	{
		@Override
		public Class<?> getColumnClass(int columnIndex)
		{
			return columnIndex == 0 ? Boolean.class : Object.class;
		}

		@Override
		public boolean isCellEditable(int row, int column)
		{
			return column == 0 && row >= 0 && row < loaded.size();
		}
	};
	private final JTable table = new JTable(model);

	public AchievementUnlockerPanel(ProfileWorkspace workspace, TaskRunner tasks)
	{
		super(new BorderLayout(10, 10));
		this.workspace = workspace;
		this.tasks = tasks;
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
		add(header(), BorderLayout.NORTH);
		configureTable();
		add(new JScrollPane(table), BorderLayout.CENTER);
		add(actions(), BorderLayout.SOUTH);
		game.addActionListener(event -> loadAchievements());
		online.addActionListener(event -> dateTime.setEnabled(online.isSelected()));
		workspace.addListener(() -> SwingUtilities.invokeLater(this::workspaceChanged));
	}

	private void configureTable()
	{
		table.setFillsViewportHeight(true);
		table.getColumnModel().getColumn(0).setMaxWidth(62);
		table.getColumnModel().getColumn(2).setMaxWidth(70);
		table.getColumnModel().getColumn(3).setMaxWidth(78);
		table.getColumnModel().getColumn(4).setPreferredWidth(176);
		table.getColumnModel().getColumn(4).setMaxWidth(190);
		table.getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting())
			{
				loadSelectedUnlock();
			}
		});
	}

	private JPanel header()
	{
		JPanel section = section("Achievements");
		JPanel row = row();
		row.add(new JLabel("Game"));
		game.setPrototypeDisplayValue(new ProfileService.Game("00000000",
			"A Long Xbox 360 Game Title", 0, 0, 0, 0));
		row.add(game);
		JButton refresh = new JButton("Refresh");
		refresh.addActionListener(event -> loadAchievements());
		row.add(refresh);
		row.add(summary);
		section.add(row);
		return section;
	}

	private JPanel actions()
	{
		JPanel section = section("Unlock Details");
		JPanel timeRow = row();
		dateTime.setEditor(new JSpinner.DateEditor(dateTime, "MMM d, yyyy h:mm:ss a"));
		JButton now = new JButton("Use Current Time");
		now.addActionListener(event -> dateTime.setValue(new Date()));
		timeRow.add(online);
		timeRow.add(new JLabel("Date and time"));
		timeRow.add(dateTime);
		timeRow.add(now);
		section.add(timeRow);
		section.add(Box.createVerticalStrut(4));
		JPanel actionRow = row();
		JButton selectAll = new JButton("Select All Locked");
		JButton clear = new JButton("Clear Selection");
		JButton apply = new JButton("Apply Changes");
		selectAll.addActionListener(event -> selectAllLocked());
		clear.addActionListener(event -> setSelection(false));
		apply.addActionListener(event -> applySelected());
		actionRow.add(selectAll);
		actionRow.add(clear);
		actionRow.add(apply);
		section.add(actionRow);
		return section;
	}

	private void workspaceChanged()
	{
		ProfileService.Game selected = (ProfileService.Game) game.getSelectedItem();
		String selectedId = selected == null ? null : selected.titleId();
		game.removeAllItems();
		ProfileService.Profile profile = workspace.profile();
		if (profile == null)
		{
			model.setRowCount(0);
			loaded.clear();
			return;
		}
		for (ProfileService.Game item : profile.games())
		{
			game.addItem(item);
			if (item.titleId().equals(selectedId))
			{
				game.setSelectedItem(item);
			}
		}
		if (game.getSelectedIndex() < 0 && game.getItemCount() > 0)
		{
			game.setSelectedIndex(0);
		}
	}

	private void loadAchievements()
	{
		PathSelection selection = selection();
		if (selection == null)
		{
			model.setRowCount(0);
			loaded.clear();
			return;
		}
		tasks.run("load achievements", () -> {
			List<ProfileService.Achievement> achievements = workspace.profiles()
				.achievements(selection.path, selection.game.titleId());
			SwingUtilities.invokeLater(() -> showAchievements(selection.game, achievements));
		});
	}

	private void showAchievements(ProfileService.Game selected, List<ProfileService.Achievement> achievements)
	{
		loaded.clear();
		loaded.addAll(achievements);
		model.setRowCount(0);
		int unlocked = 0;
		for (ProfileService.Achievement achievement : loaded)
		{
			if (achievement.unlocked())
			{
				unlocked++;
			}
			String description = achievement.unlocked() || achievement.lockedDescription().isEmpty()
				? achievement.description() : achievement.lockedDescription();
			Instant achievedAt = achievement.achievedInstant();
			String status = achievement.unlocked() ? achievement.online() ? "Online" : "Offline" : "Locked";
			model.addRow(new Object[]{false, achievement.name(), achievement.credit(), status,
				achievedAt == null ? "" : UNLOCK_TIME.format(achievedAt), description});
		}
		summary.setText(unlocked + " / " + loaded.size() + " unlocked  |  "
			+ selected.earnedCredit() + " / " + selected.possibleCredit() + " gamerscore");
	}

	private void loadSelectedUnlock()
	{
		int row = table.getSelectedRow();
		if (row < 0 || row >= loaded.size())
		{
			return;
		}
		ProfileService.Achievement achievement = loaded.get(row);
		if (achievement.unlocked())
		{
			online.setSelected(achievement.online());
			Instant achievedAt = achievement.achievedInstant();
			if (achievedAt != null)
			{
				dateTime.setValue(Date.from(achievedAt));
			}
		}
		else
		{
			online.setSelected(true);
			dateTime.setValue(new Date());
		}
		dateTime.setEnabled(online.isSelected());
	}

	private void selectAllLocked()
	{
		for (int row = 0; row < loaded.size(); row++)
		{
			if (!loaded.get(row).unlocked())
			{
				model.setValueAt(true, row, 0);
			}
		}
	}

	private void setSelection(boolean selected)
	{
		for (int row = 0; row < loaded.size(); row++)
		{
			model.setValueAt(selected, row, 0);
		}
	}

	private void applySelected()
	{
		PathSelection selection = selection();
		if (selection == null)
		{
			showWarning("Open a profile and choose a game first");
			return;
		}
		Set<Long> selected = new LinkedHashSet<>();
		for (int row = 0; row < loaded.size(); row++)
		{
			if (Boolean.TRUE.equals(model.getValueAt(row, 0)))
			{
				selected.add(loaded.get(row).id());
			}
		}
		if (selected.isEmpty())
		{
			showWarning("Select at least one achievement");
			return;
		}
		int answer = JOptionPane.showConfirmDialog(this,
			"Apply these unlock details to " + selected.size() + " achievement(s) in "
				+ selection.game.name() + "?", "Edit Achievements", JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE);
		if (answer != JOptionPane.YES_OPTION)
		{
			return;
		}
		try
		{
			dateTime.commitEdit();
		}
		catch (ParseException failure)
		{
			showWarning("Enter a valid unlock date and time");
			return;
		}
		boolean onlineUnlock = online.isSelected();
		Instant achievedAt = ((Date) dateTime.getValue()).toInstant();
		tasks.run("edit achievements", () -> {
			ProfileService.AchievementEditResult result = workspace.profiles().editAchievements(
				selection.path, selection.path, selection.game.titleId(), selected,
				onlineUnlock, achievedAt, true);
			workspace.refresh();
			SwingUtilities.invokeLater(() -> showResult(result));
		});
	}

	private void showResult(ProfileService.AchievementEditResult result)
	{
		String message = result.changedCount() + " achievement(s) updated.";
		if (result.unlockedCount() > 0)
		{
			message += " " + result.unlockedCount() + " newly unlocked for "
				+ result.addedCredit() + " gamerscore.";
		}
		JOptionPane.showMessageDialog(this, message, "Achievements", JOptionPane.INFORMATION_MESSAGE);
	}

	private PathSelection selection()
	{
		ProfileService.Game selected = (ProfileService.Game) game.getSelectedItem();
		return workspace.path() == null || selected == null ? null : new PathSelection(workspace.path(), selected);
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
		JOptionPane.showMessageDialog(this, message, "Achievements", JOptionPane.WARNING_MESSAGE);
	}

	private static final class PathSelection
	{
		private final Path path;
		private final ProfileService.Game game;

		private PathSelection(Path path, ProfileService.Game game)
		{
			this.path = path;
			this.game = game;
		}
	}
}
