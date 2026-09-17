package openrtm.ui;

import openrtm.console.ConsoleService;
import openrtm.games.OtherGameAdapter;
import openrtm.games.OtherGameAdapters;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Locale;

public final class OtherGamesPanel extends JPanel
{
	private final TaskRunner taskRunner;

	public OtherGamesPanel(ConsoleService console, TaskRunner taskRunner)
	{
		super(new BorderLayout());
		this.taskRunner = taskRunner;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		JTabbedPane games = new JTabbedPane();
		games.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
		for (OtherGameAdapter adapter : OtherGameAdapters.create(console))
		{
			games.addTab(adapter.game().tabName(), createGamePanel(adapter));
		}
		add(games, BorderLayout.CENTER);
	}

	private Component createGamePanel(OtherGameAdapter adapter)
	{
		JPanel panel = new JPanel(new BorderLayout(0, 10));
		panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		JLabel title = new JLabel(adapter.game().displayName());
		title.setFont(title.getFont().deriveFont(Font.BOLD, 18.0F));
		panel.add(title, BorderLayout.NORTH);

		if (adapter.sectionTabs())
		{
			JTabbedPane sections = new JTabbedPane();
			for (OtherGameAdapter.Section section : adapter.sections())
			{
				sections.addTab(section.label(), createSectionPanel(adapter, section));
			}
			panel.add(sections, BorderLayout.CENTER);
		}
		else
		{
			panel.add(createCombinedPanel(adapter), BorderLayout.CENTER);
		}
		return panel;
	}

	private Component createSectionPanel(OtherGameAdapter adapter, OtherGameAdapter.Section section)
	{
		JPanel content = verticalPanel();
		content.add(createSectionForm(adapter, section));
		content.add(Box.createVerticalGlue());
		return scroll(content);
	}

	private Component createCombinedPanel(OtherGameAdapter adapter)
	{
		JPanel content = verticalPanel();
		for (OtherGameAdapter.Section section : adapter.sections())
		{
			JPanel group = new JPanel(new BorderLayout(0, 6));
			group.setAlignmentX(Component.LEFT_ALIGNMENT);
			group.setBorder(BorderFactory.createEmptyBorder(4, 0, 14, 0));
			JLabel title = new JLabel(section.label());
			title.setFont(title.getFont().deriveFont(Font.BOLD, 14.0F));
			group.add(title, BorderLayout.NORTH);
			group.add(createSectionForm(adapter, section), BorderLayout.CENTER);
			group.setMaximumSize(new Dimension(Integer.MAX_VALUE, group.getPreferredSize().height));
			content.add(group);
		}
		content.add(Box.createVerticalGlue());
		return scroll(content);
	}

	private Component createSectionForm(OtherGameAdapter adapter, OtherGameAdapter.Section section)
	{
		JPanel form = formPanel();
		int row = 0;
		for (OtherGameAdapter.NumberControl control : adapter.numberControls())
		{
			if (control.section().equals(section.key()))
			{
				addFormRow(form, row++, control.label(), createNumberEditor(adapter, control));
			}
		}
		for (OtherGameAdapter.SliderControl control : adapter.sliderControls())
		{
			if (control.section().equals(section.key()))
			{
				addFormRow(form, row++, control.label(), createSliderEditor(adapter, control));
			}
		}
		for (OtherGameAdapter.MeterControl control : adapter.meterControls())
		{
			if (control.section().equals(section.key()))
			{
				addFormRow(form, row++, control.label(), createMeterEditor(adapter, control));
			}
		}
		for (OtherGameAdapter.Toggle toggle : adapter.toggles())
		{
			if (toggle.section().equals(section.key()))
			{
				addFormRow(form, row++, toggle.label(), createToggleEditor(adapter, toggle));
			}
		}
		List<OtherGameAdapter.Action> actions = adapter.actions().stream()
			.filter(action -> action.section().equals(section.key())).toList();
		if (!actions.isEmpty())
		{
			addFormRow(form, row, "Actions", createActions(adapter, actions));
		}
		return form;
	}

	private Component createNumberEditor(OtherGameAdapter adapter, OtherGameAdapter.NumberControl control)
	{
		JPanel editor = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		JTextField current = new JTextField("Not read", 11);
		current.setEditable(false);
		JSpinner value = new JSpinner(new SpinnerNumberModel(control.initialAmount(), control.minimum(), control.maximum(), 1L));
		value.setPreferredSize(new Dimension(150, value.getPreferredSize().height));
		JButton refresh = new JButton("Refresh");
		JButton add = new JButton("Add");
		JButton remove = new JButton("Remove");
		JButton set = new JButton("Set");
		refresh.addActionListener(event -> refreshNumber(adapter, control, current));
		add.addActionListener(event -> adjustNumber(adapter, control, value, current, false));
		remove.addActionListener(event -> adjustNumber(adapter, control, value, current, true));
		set.addActionListener(event -> setNumber(adapter, control, value, current));
		editor.add(new JLabel("Current"));
		editor.add(current);
		editor.add(new JLabel("Amount"));
		editor.add(value);
		editor.add(refresh);
		editor.add(add);
		editor.add(remove);
		editor.add(set);
		return editor;
	}

	private void refreshNumber(OtherGameAdapter adapter, OtherGameAdapter.NumberControl control, JTextField output)
	{
		taskRunner.run(adapter.game().tabName() + " read " + control.label(), () -> {
			long current = adapter.readNumber(control.key());
			SwingUtilities.invokeLater(() -> output.setText(String.format(Locale.US, "%,d", current)));
		});
	}

	private void adjustNumber(OtherGameAdapter adapter, OtherGameAdapter.NumberControl control, JSpinner input,
		JTextField output, boolean remove)
	{
		long amount = ((Number) input.getValue()).longValue();
		long adjustment = remove ? -amount : amount;
		String operation = remove ? "remove " : "add ";
		taskRunner.run(adapter.game().tabName() + " " + operation + control.label(), () -> {
			adapter.adjustNumber(control.key(), adjustment);
			long current = adapter.readNumber(control.key());
			SwingUtilities.invokeLater(() -> output.setText(String.format(Locale.US, "%,d", current)));
		});
	}

	private void setNumber(OtherGameAdapter adapter, OtherGameAdapter.NumberControl control, JSpinner input,
		JTextField output)
	{
		long value = ((Number) input.getValue()).longValue();
		taskRunner.run(adapter.game().tabName() + " set " + control.label(), () -> {
			adapter.setNumber(control.key(), value);
			long current = adapter.readNumber(control.key());
			SwingUtilities.invokeLater(() -> output.setText(String.format(Locale.US, "%,d", current)));
		});
	}

	private Component createSliderEditor(OtherGameAdapter adapter, OtherGameAdapter.SliderControl control)
	{
		JPanel editor = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		JSlider input = new JSlider(control.minimum(), control.maximum(), control.initial());
		input.setPreferredSize(new Dimension(340, input.getPreferredSize().height));
		input.setMinorTickSpacing(1);
		input.setMajorTickSpacing(control.divisor());
		input.setPaintTicks(true);
		JLabel value = new JLabel(formatSlider(control, input.getValue()));
		value.setPreferredSize(new Dimension(42, value.getPreferredSize().height));
		input.addChangeListener(event -> value.setText(formatSlider(control, input.getValue())));
		JButton apply = new JButton("Apply");
		apply.addActionListener(event -> {
			double selected = (double) input.getValue() / control.divisor();
			taskRunner.run(adapter.game().tabName() + " set " + control.label(), () -> adapter.setSlider(control.key(), selected));
		});
		editor.add(input);
		editor.add(value);
		editor.add(apply);
		return editor;
	}

	private Component createMeterEditor(OtherGameAdapter adapter, OtherGameAdapter.MeterControl control)
	{
		JPanel editor = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		JProgressBar meter = new JProgressBar(0, 1000);
		meter.setStringPainted(true);
		meter.setString("Not read");
		meter.setPreferredSize(new Dimension(340, meter.getPreferredSize().height));
		JButton refresh = new JButton("Refresh");
		refresh.addActionListener(event -> taskRunner.run(adapter.game().tabName() + " read " + control.label(), () -> {
			double current = adapter.readMeter(control.key());
			SwingUtilities.invokeLater(() -> {
				meter.setValue((int) Math.round(current * 1000.0));
				meter.setString(String.format(Locale.US, "%.0f%%", current * 100.0));
			});
		}));
		editor.add(meter);
		editor.add(refresh);
		return editor;
	}

	private Component createToggleEditor(OtherGameAdapter adapter, OtherGameAdapter.Toggle toggle)
	{
		JPanel editor = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		JCheckBox input = new JCheckBox(toggle.enabledLabel(), toggle.initial());
		input.addActionListener(event -> {
			boolean selected = input.isSelected();
			taskRunner.run(adapter.game().tabName() + " set " + toggle.label(), () -> {
				try
				{
					adapter.setToggle(toggle.key(), selected);
				}
				catch (RuntimeException failure)
				{
					SwingUtilities.invokeLater(() -> input.setSelected(!selected));
					throw failure;
				}
			});
		});
		editor.add(input);
		if (toggle.readable())
		{
			JButton refresh = new JButton("Refresh");
			refresh.addActionListener(event -> taskRunner.run(adapter.game().tabName() + " read " + toggle.label(), () -> {
				boolean selected = adapter.readToggle(toggle.key());
				SwingUtilities.invokeLater(() -> input.setSelected(selected));
			}));
			editor.add(refresh);
		}
		return editor;
	}

	private Component createActions(OtherGameAdapter adapter, List<OtherGameAdapter.Action> actions)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		for (OtherGameAdapter.Action action : actions)
		{
			JButton button = new JButton(action.label());
			button.addActionListener(event -> taskRunner.run(adapter.game().tabName() + " " + action.label(),
				() -> adapter.runAction(action.key())));
			row.add(button);
		}
		return row;
	}

	private static String formatSlider(OtherGameAdapter.SliderControl control, int value)
	{
		return String.format(Locale.US, "%.1f", (double) value / control.divisor());
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

	private static void addFormRow(JPanel panel, int row, String label, Component editor)
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = row;
		constraints.insets = new Insets(8, 4, 8, 12);
		constraints.anchor = GridBagConstraints.LINE_START;
		constraints.gridx = 0;
		panel.add(new JLabel(label), constraints);
		constraints.gridx = 1;
		constraints.weightx = 1.0;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		panel.add(editor, constraints);
	}

	private static JScrollPane scroll(Component content)
	{
		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}
}
