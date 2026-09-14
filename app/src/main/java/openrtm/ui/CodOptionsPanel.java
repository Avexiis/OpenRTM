package openrtm.ui;

import openrtm.cod.CodAdapter;
import openrtm.cod.CodAdapters;
import openrtm.console.ConsoleService;

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
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CodOptionsPanel extends JPanel
{
	private static final Color DANGER = new Color(176, 53, 60);
	private static final Color DANGER_TEXT = new Color(255, 245, 245);
	private final TaskRunner taskRunner;

	public CodOptionsPanel(ConsoleService console, TaskRunner taskRunner)
	{
		super(new BorderLayout());
		this.taskRunner = taskRunner;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		JTabbedPane games = new JTabbedPane();
		games.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		for (CodAdapter adapter : CodAdapters.create(console))
		{
			games.addTab(adapter.game().tabName(), createGamePanel(adapter));
		}
		add(games, BorderLayout.CENTER);
	}

	private Component createGamePanel(CodAdapter adapter)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 10));
		panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		JLabel title = new JLabel(adapter.game().displayName());
		title.setFont(title.getFont().deriveFont(Font.BOLD, 18.0F));
		ClientSelection clients = new ClientSelection(adapter.maximumClients());
		JPanel heading = new JPanel(new BorderLayout(0, 8));
		heading.add(title, BorderLayout.NORTH);
		if (hasClientOptions(adapter))
		{
			heading.add(createClientBar(adapter, clients), BorderLayout.SOUTH);
		}
		panel.add(heading, BorderLayout.NORTH);

		JTabbedPane sections = new JTabbedPane();
		for (CodAdapter.StatGroup group : adapter.statGroups())
		{
			sections.addTab(group.label(), createStatPanel(adapter, group, clients));
		}
		if (adapter.classCount() > 0)
		{
			sections.addTab("Custom Classes", createClassPanel(adapter));
		}
		if (hasOptions(adapter))
		{
			sections.addTab("Game Options", createOptionsPanel(adapter, clients));
		}
		panel.add(sections, BorderLayout.CENTER);
		return panel;
	}

	private Component createStatPanel(CodAdapter adapter, CodAdapter.StatGroup group, ClientSelection clients)
	{
		JPanel content = verticalPanel();
		JPanel editor = formPanel();
		Map<String, JSpinner> inputs = new LinkedHashMap<>();
		int row = 0;
		for (CodAdapter.StatField field : group.fields())
		{
			JSpinner input = new JSpinner(new SpinnerNumberModel(0L, field.minimum(), field.maximum(), 1L));
			input.setPreferredSize(new Dimension(190, input.getPreferredSize().height));
			addFormRow(editor, row++, field.label(), input, null);
			inputs.put(field.key(), input);
		}
		content.add(section(group.label(), editor));

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		actions.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (group.readable())
		{
			JButton read = new JButton("Read From Console");
			read.addActionListener(event -> taskRunner.run(adapter.game().tabName() + " read statistics", () -> {
				Map<String, Long> values = adapter.readStats(group.key());
				SwingUtilities.invokeLater(() -> applyStatValues(group, inputs, values));
			}));
			actions.add(read);
		}
		JButton apply = new JButton("Apply Statistics");
		apply.addActionListener(event -> {
			Map<String, Long> values = new LinkedHashMap<>();
			inputs.forEach((key, input) -> values.put(key, ((Number) input.getValue()).longValue()));
			taskRunner.run(adapter.game().tabName() + " apply statistics", () -> adapter.writeStats(group.key(), values));
		});
		actions.add(apply);
		if ("multiplayer".equals(group.key()))
		{
			JButton unlock = new JButton(adapter.unlockTargetsClient() ? "Unlock Selected Player" : "Unlock All Challenges");
			unlock.addActionListener(event -> {
				String target = adapter.unlockTargetsClient() ? clients.description() : "the signed-in profile";
				if (confirm("Unlock all challenges for " + target + "?", "Unlock Challenges"))
				{
					taskRunner.run(adapter.game().tabName() + " unlock challenges", () -> {
						if (adapter.unlockTargetsClient())
						{
							adapter.unlockClient(clients.slot());
						}
						else
						{
							adapter.unlockAll();
						}
					});
				}
			});
			actions.add(unlock);
			JButton derank = new JButton("De-rank Signed-In Profile");
			derank.setBackground(DANGER);
			derank.setForeground(DANGER_TEXT);
			derank.setOpaque(true);
			derank.addActionListener(event -> {
				if (confirm("Reset rank and prestige for the signed-in profile?", "De-rank Profile"))
				{
					taskRunner.run(adapter.game().tabName() + " de-rank profile", adapter::derankSignedInProfile);
				}
			});
			actions.add(derank);
		}
		content.add(Box.createVerticalStrut(10));
		content.add(actions);
		content.add(Box.createVerticalGlue());
		return scroll(content);
	}

	private Component createClassPanel(CodAdapter adapter)
	{
		JPanel content = verticalPanel();
		JPanel editor = formPanel();
		List<JTextField> inputs = new ArrayList<>();
		for (int i = 0; i < adapter.classCount(); i++)
		{
			JTextField input = new JTextField(24);
			addFormRow(editor, i, "Class " + (i + 1), input, null);
			inputs.add(input);
		}
		content.add(section("Custom Class Names", editor));
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		actions.setAlignmentX(Component.LEFT_ALIGNMENT);
		if (adapter.classNamesReadable())
		{
			JButton read = new JButton("Read From Console");
			read.addActionListener(event -> taskRunner.run(adapter.game().tabName() + " read class names", () -> {
				List<String> names = adapter.readClassNames();
				SwingUtilities.invokeLater(() -> {
					for (int i = 0; i < Math.min(inputs.size(), names.size()); i++)
					{
						inputs.get(i).setText(names.get(i));
					}
				});
			}));
			actions.add(read);
		}
		JButton apply = new JButton("Apply Class Names");
		apply.addActionListener(event -> {
			List<String> names = inputs.stream().map(JTextField::getText).toList();
			taskRunner.run(adapter.game().tabName() + " apply class names", () -> adapter.writeClassNames(names));
		});
		actions.add(apply);
		content.add(Box.createVerticalStrut(10));
		content.add(actions);
		content.add(Box.createVerticalGlue());
		return scroll(content);
	}

	private Component createOptionsPanel(CodAdapter adapter, ClientSelection clients)
	{
		JPanel content = verticalPanel();
		for (String sectionName : optionSections(adapter))
		{
			JPanel sectionContent = verticalPanel();
			addImmediateOptions(adapter, sectionName, clients, sectionContent);
			addNumberOptions(adapter, sectionName, clients, sectionContent);
			addChoiceOptions(adapter, sectionName, clients, sectionContent);
			addTextOptions(adapter, sectionName, clients, sectionContent);
			content.add(section(sectionName, sectionContent));
			content.add(Box.createVerticalStrut(10));
		}
		content.add(Box.createVerticalGlue());
		return scroll(content);
	}

	private Component createClientBar(CodAdapter adapter, ClientSelection clients)
	{
		JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		bar.add(new JLabel("Target Player"));
		bar.add(clients.input());
		if (adapter.clientNamesReadable())
		{
			JButton refresh = new JButton("Refresh Players");
			refresh.addActionListener(event -> taskRunner.run(adapter.game().tabName() + " refresh players", () -> {
				List<CodAdapter.ClientInfo> discovered = adapter.readClients();
				SwingUtilities.invokeLater(() -> clients.replace(discovered));
			}));
			bar.add(refresh);
		}
		return bar;
	}

	private void addImmediateOptions(CodAdapter adapter, String sectionName, ClientSelection clients, JPanel parent)
	{
		List<CodAdapter.Action> actions = adapter.actions().stream().filter(option -> option.section().equals(sectionName)).toList();
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (CodAdapter.Action action : actions)
		{
			JButton button = new JButton(action.label());
			button.addActionListener(event -> {
				if (action.key().startsWith("unfreeze_")
					&& !confirm("Reset all custom class names to OpenRTM?", "Unfreeze Classes"))
				{
					return;
				}
				if (action.key().contains("unlock") && !confirm("Unlock all challenges for the selected client?", "Unlock Challenges"))
				{
					return;
				}
				int selectedClient = clients.slot();
				taskRunner.run(adapter.game().tabName() + " " + action.label(), () -> adapter.runAction(action.key(), selectedClient));
			});
			row.add(button);
		}
		for (CodAdapter.Toggle toggle : adapter.toggles())
		{
			if (!toggle.section().equals(sectionName))
			{
				continue;
			}
			JCheckBox input = new JCheckBox(toggle.label());
			input.addActionListener(event -> {
				int selectedClient = clients.slot();
				boolean enabled = input.isSelected();
				taskRunner.run(adapter.game().tabName() + " " + toggle.label(), () -> adapter.setToggle(toggle.key(), enabled, selectedClient));
			});
			row.add(input);
		}
		if (row.getComponentCount() > 0)
		{
			parent.add(row);
		}
	}

	private void addNumberOptions(CodAdapter adapter, String sectionName, ClientSelection clients, JPanel parent)
	{
		JPanel form = formPanel();
		int row = 0;
		for (CodAdapter.NumberOption option : adapter.numberOptions())
		{
			if (!option.section().equals(sectionName))
			{
				continue;
			}
			JSpinner input = new JSpinner(new SpinnerNumberModel(option.initial(), option.minimum(), option.maximum(), 1L));
			JButton apply = new JButton("Apply");
			apply.addActionListener(event -> {
				long value = ((Number) input.getValue()).longValue();
				int selectedClient = clients.slot();
				taskRunner.run(adapter.game().tabName() + " " + option.label(), () -> adapter.setNumber(option.key(), value, selectedClient));
			});
			addFormRow(form, row++, option.label(), input, apply);
		}
		if (row > 0)
		{
			parent.add(form);
		}
	}

	private void addChoiceOptions(CodAdapter adapter, String sectionName, ClientSelection clients, JPanel parent)
	{
		JPanel form = formPanel();
		int row = 0;
		for (CodAdapter.ChoiceOption option : adapter.choiceOptions())
		{
			if (!option.section().equals(sectionName))
			{
				continue;
			}
			JComboBox<String> input = new JComboBox<>(option.choices().toArray(String[]::new));
			JButton apply = new JButton("Apply");
			apply.addActionListener(event -> {
				String value = (String) input.getSelectedItem();
				int selectedClient = clients.slot();
				taskRunner.run(adapter.game().tabName() + " " + option.label(), () -> adapter.setChoice(option.key(), value, selectedClient));
			});
			addFormRow(form, row++, option.label(), input, apply);
		}
		if (row > 0)
		{
			parent.add(form);
		}
	}

	private void addTextOptions(CodAdapter adapter, String sectionName, ClientSelection clients, JPanel parent)
	{
		JPanel form = formPanel();
		int row = 0;
		for (CodAdapter.TextOption option : adapter.textOptions())
		{
			if (!option.section().equals(sectionName))
			{
				continue;
			}
			JTextField input = new JTextField(24);
			JButton apply = new JButton("Apply");
			apply.addActionListener(event -> {
				String value = input.getText();
				if (value.length() > option.maximumLength())
				{
					JOptionPane.showMessageDialog(this, option.label() + " is limited to " + option.maximumLength() + " characters.",
						option.label(), JOptionPane.WARNING_MESSAGE);
					return;
				}
				int selectedClient = clients.slot();
				taskRunner.run(adapter.game().tabName() + " " + option.label(), () -> adapter.setText(option.key(), value, selectedClient));
			});
			addFormRow(form, row++, option.label(), input, apply);
		}
		if (row > 0)
		{
			parent.add(form);
		}
	}

	private static Set<String> optionSections(CodAdapter adapter)
	{
		Set<String> sections = new LinkedHashSet<>();
		adapter.actions().forEach(option -> sections.add(option.section()));
		adapter.toggles().forEach(option -> sections.add(option.section()));
		adapter.numberOptions().forEach(option -> sections.add(option.section()));
		adapter.choiceOptions().forEach(option -> sections.add(option.section()));
		adapter.textOptions().forEach(option -> sections.add(option.section()));
		Set<String> ordered = new LinkedHashSet<>();
		List.of("Match", "Host Settings", "Selected Player", "Zombies", "Exo Zombies", "Profile", "Classes")
			.forEach(section -> {
				if (sections.contains(section))
				{
					ordered.add(section);
				}
			});
		ordered.addAll(sections);
		return ordered;
	}

	private static boolean hasOptions(CodAdapter adapter)
	{
		return !adapter.actions().isEmpty() || !adapter.toggles().isEmpty() || !adapter.numberOptions().isEmpty()
			|| !adapter.choiceOptions().isEmpty() || !adapter.textOptions().isEmpty();
	}

	private static boolean hasClientOptions(CodAdapter adapter)
	{
		return adapter.unlockTargetsClient()
			|| adapter.actions().stream().anyMatch(CodAdapter.Action::clientTargeted)
			|| adapter.toggles().stream().anyMatch(CodAdapter.Toggle::clientTargeted)
			|| adapter.numberOptions().stream().anyMatch(CodAdapter.NumberOption::clientTargeted)
			|| adapter.choiceOptions().stream().anyMatch(CodAdapter.ChoiceOption::clientTargeted)
			|| adapter.textOptions().stream().anyMatch(CodAdapter.TextOption::clientTargeted);
	}

	private static void applyStatValues(CodAdapter.StatGroup group, Map<String, JSpinner> inputs, Map<String, Long> values)
	{
		for (CodAdapter.StatField field : group.fields())
		{
			Long value = values.get(field.key());
			if (value != null)
			{
				inputs.get(field.key()).setValue(Math.max(field.minimum(), Math.min(field.maximum(), value)));
			}
		}
	}

	private static JPanel section(String title, Component content)
	{
		JPanel section = new JPanel(new BorderLayout());
		section.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.setBorder(BorderFactory.createTitledBorder(title));
		section.add(content, BorderLayout.CENTER);
		section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(80, content.getPreferredSize().height + 34)));
		return section;
	}

	private static JPanel formPanel()
	{
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private static JPanel verticalPanel()
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		return panel;
	}

	private static void addFormRow(JPanel panel, int row, String label, Component input, Component action)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = row;
		constraints.insets = new Insets(4, 4, 4, 8);
		constraints.anchor = GridBagConstraints.LINE_START;
		constraints.gridx = 0;
		panel.add(new JLabel(label), constraints);
		constraints.gridx = 1;
		constraints.weightx = 0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		Dimension preferred = input.getPreferredSize();
		if (preferred.width < 190)
		{
			input.setPreferredSize(new Dimension(190, preferred.height));
		}
		panel.add(input, constraints);
		if (action != null)
		{
			constraints.gridx = 2;
			constraints.weightx = 0;
			constraints.fill = GridBagConstraints.NONE;
			panel.add(action, constraints);
		}
		constraints.gridx = 3;
		constraints.weightx = 1.0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		panel.add(Box.createHorizontalGlue(), constraints);
	}

	private static JScrollPane scroll(Component content)
	{
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	private boolean confirm(String message, String title)
	{
		return JOptionPane.showConfirmDialog(this, message, title, JOptionPane.YES_NO_OPTION,
			JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
	}

	private static final class ClientSelection
	{
		private final JComboBox<CodAdapter.ClientInfo> input;
		private final int maximumClients;

		private ClientSelection(int maximumClients)
		{
			this.maximumClients = maximumClients;
			input = new JComboBox<>();
			showSlots();
			input.setPreferredSize(new Dimension(250, input.getPreferredSize().height));
		}

		private JComboBox<CodAdapter.ClientInfo> input()
		{
			return input;
		}

		private int slot()
		{
			CodAdapter.ClientInfo selected = (CodAdapter.ClientInfo) input.getSelectedItem();
			return selected == null ? 0 : selected.slot();
		}

		private String description()
		{
			CodAdapter.ClientInfo selected = (CodAdapter.ClientInfo) input.getSelectedItem();
			return selected == null ? "the selected player" : selected.toString();
		}

		private void replace(List<CodAdapter.ClientInfo> clients)
		{
			int selectedSlot = slot();
			input.removeAllItems();
			if (clients.isEmpty())
			{
				showSlots();
				return;
			}
			for (CodAdapter.ClientInfo client : clients)
			{
				input.addItem(client);
				if (client.slot() == selectedSlot)
				{
					input.setSelectedItem(client);
				}
			}
		}

		private void showSlots()
		{
			for (int slot = 0; slot < maximumClients; slot++)
			{
				input.addItem(new CodAdapter.ClientInfo(slot, ""));
			}
		}
	}
}
