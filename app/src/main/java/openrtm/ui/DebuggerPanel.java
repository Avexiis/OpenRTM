package openrtm.ui;

import openrtm.console.DebuggerService;
import openrtm.console.DebuggerService.Breakpoint;
import openrtm.console.DebuggerService.BreakpointType;
import openrtm.console.DebuggerService.DebugEvent;
import openrtm.console.DebuggerService.ModuleInfo;
import openrtm.console.DebuggerService.RegisterValue;
import openrtm.console.DebuggerService.StopCondition;
import openrtm.console.DebuggerService.ThreadInfo;
import openrtm.util.HexUtils;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DebuggerPanel extends JPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private static final Color OK = new Color(92, 184, 117);
	private static final Color WARN = new Color(210, 157, 73);
	private static final int MAX_EVENTS = 10_000;
	private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
		.withZone(ZoneId.systemDefault());

	private final DebuggerService debugger;
	private final TaskRunner tasks;
	private final Deque<DebugEvent> events = new ArrayDeque<>();
	private final Queue<DebugEvent> pendingEvents = new ConcurrentLinkedQueue<>();
	private final AtomicBoolean eventDrainScheduled = new AtomicBoolean();
	private final List<JButton> attachedButtons = new ArrayList<>();
	private final Map<StopCondition, JCheckBox> stopConditionBoxes = new EnumMap<>(StopCondition.class);
	private final DefaultListModel<Breakpoint> breakpointModel = new DefaultListModel<>();
	private final DefaultTableModel eventModel = tableModel("Time", "Type", "Thread", "Address", "Details");
	private final DefaultTableModel threadModel = tableModel(
		"Thread", "State", "Priority", "Suspend", "Start", "Stack Base", "Stack Limit", "CPU");
	private final DefaultTableModel registerModel = tableModel("Register", "Value");
	private final DefaultTableModel moduleModel = tableModel("Name", "Base", "Size", "Checksum", "Type");
	private final JTable eventTable = new JTable(eventModel);
	private final JTable threadTable = new JTable(threadModel);
	private final JLabel sessionStatus = new JLabel("Detached");
	private final JLabel executionStatus = new JLabel("Execution: Unknown");
	private final JButton attachButton = new JButton("Attach");
	private final JButton detachButton = new JButton("Detach");
	private final JButton pauseButton = attachedButton("Pause");
	private final JButton continueButton = attachedButton("Continue");
	private final JButton refreshButton = attachedButton("Refresh");
	private final JCheckBox overrideExisting = new JCheckBox("Override existing debugger");
	private List<ThreadInfo> threadRows = List.of();

	public DebuggerPanel(DebuggerService debugger, TaskRunner tasks)
	{
		this.debugger = debugger;
		this.tasks = tasks;
		setLayout(new BorderLayout(10, 10));
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));

		add(sessionControls(), BorderLayout.NORTH);
		add(debuggerWorkspace(), BorderLayout.CENTER);
		configureTables();
		setAttachedState(debugger.attached());
		debugger.eventConsumer(this::receiveEvent);
	}

	private JPanel sessionControls()
	{
		JPanel controls = new JPanel();
		controls.setLayout(new javax.swing.BoxLayout(controls, javax.swing.BoxLayout.Y_AXIS));
		controls.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));

		JPanel session = row();
		attachButton.addActionListener(e -> attach());
		detachButton.addActionListener(e -> detach());
		pauseButton.addActionListener(e -> runAndRefreshState("pause debugger", debugger::pause));
		continueButton.addActionListener(e -> runAndRefreshState("continue debugger", debugger::resume));
		refreshButton.addActionListener(e -> refreshAll());
		session.add(attachButton);
		session.add(detachButton);
		session.add(overrideExisting);
		session.add(separator());
		session.add(pauseButton);
		session.add(continueButton);
		session.add(refreshButton);
		session.add(separator());
		session.add(sessionStatus);
		session.add(executionStatus);
		controls.add(session);

		JPanel conditions = row();
		conditions.add(new JLabel("Break on"));
		for (StopCondition condition : StopCondition.values())
		{
			JCheckBox box = new JCheckBox(condition.toString());
			stopConditionBoxes.put(condition, box);
			conditions.add(box);
		}
		JButton apply = attachedButton("Apply");
		apply.addActionListener(e -> applyStopConditions());
		conditions.add(apply);
		controls.add(conditions);
		return controls;
	}

	private Component debuggerWorkspace()
	{
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, eventPanel(), detailTabs());
		split.setResizeWeight(0.56);
		split.setDividerLocation(330);
		return split;
	}

	private JPanel eventPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		JPanel tools = row();
		JLabel title = new JLabel("Debug Output");
		JButton clear = new JButton("Clear");
		JButton save = new JButton("Save Log");
		clear.addActionListener(e -> clearEvents());
		save.addActionListener(e -> saveLog());
		tools.add(title);
		tools.add(clear);
		tools.add(save);
		panel.add(tools, BorderLayout.NORTH);
		panel.add(new JScrollPane(eventTable), BorderLayout.CENTER);
		return panel;
	}

	private JTabbedPane detailTabs()
	{
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Breakpoints", breakpointPanel());
		tabs.addTab("Threads", threadPanel());
		tabs.addTab("Modules", modulePanel());
		return tabs;
	}

	private JPanel breakpointPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		JTextField address = new JTextField("0x82000000", 13);
		JComboBox<BreakpointType> type = new JComboBox<>(BreakpointType.values());
		JComboBox<Integer> size = new JComboBox<>(new Integer[]{1, 2, 4, 8});
		size.setSelectedItem(4);
		size.setEnabled(false);
		type.addActionListener(e -> size.setEnabled(type.getSelectedItem() != BreakpointType.SOFTWARE_EXECUTE));

		JList<Breakpoint> list = new JList<>(breakpointModel);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		JButton add = attachedButton("Add");
		JButton remove = attachedButton("Remove");
		JButton clear = attachedButton("Clear All");
		add.addActionListener(e -> {
			BreakpointType selectedType = (BreakpointType) type.getSelectedItem();
			int selectedSize = (Integer) size.getSelectedItem();
			String addressText = address.getText();
			tasks.run("add breakpoint", () -> {
				Breakpoint breakpoint = new Breakpoint(
					selectedType, HexUtils.parseAddress(addressText), selectedSize);
				debugger.setBreakpoint(breakpoint);
				SwingUtilities.invokeLater(this::refreshBreakpointModel);
			});
		});
		remove.addActionListener(e -> {
			Breakpoint selected = list.getSelectedValue();
			if (selected != null)
			{
				tasks.run("remove breakpoint", () -> {
					debugger.clearBreakpoint(selected);
					SwingUtilities.invokeLater(this::refreshBreakpointModel);
				});
			}
		});
		clear.addActionListener(e -> tasks.run("clear breakpoints", () -> {
			debugger.clearAllBreakpoints();
			SwingUtilities.invokeLater(this::refreshBreakpointModel);
		}));

		JPanel tools = row();
		tools.add(new JLabel("Address"));
		tools.add(address);
		tools.add(new JLabel("Type"));
		tools.add(type);
		tools.add(new JLabel("Size"));
		tools.add(size);
		tools.add(add);
		tools.add(remove);
		tools.add(clear);
		panel.add(tools, BorderLayout.NORTH);
		panel.add(new JScrollPane(list), BorderLayout.CENTER);
		return panel;
	}

	private JPanel threadPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		JTable registers = new JTable(registerModel);
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
			new JScrollPane(threadTable), new JScrollPane(registers));
		split.setResizeWeight(0.72);
		split.setDividerLocation(720);

		JButton refresh = attachedButton("Refresh Threads");
		JButton context = attachedButton("Read Context");
		JButton halt = attachedButton("Halt");
		JButton proceed = attachedButton("Continue");
		JButton passException = attachedButton("Continue Exception");
		JButton suspend = attachedButton("Suspend");
		JButton resume = attachedButton("Resume");
		refresh.addActionListener(e -> refreshThreads());
		context.addActionListener(e -> withSelectedThread("read thread context", id -> {
			List<RegisterValue> values = debugger.threadContext(id);
			SwingUtilities.invokeLater(() -> showRegisters(values));
		}));
		halt.addActionListener(e -> threadCommand("halt thread", debugger::haltThread));
		proceed.addActionListener(e -> threadCommand("continue thread", id -> debugger.continueThread(id, false)));
		passException.addActionListener(e -> threadCommand("continue thread exception", id -> debugger.continueThread(id, true)));
		suspend.addActionListener(e -> threadCommand("suspend thread", debugger::suspendThread));
		resume.addActionListener(e -> threadCommand("resume thread", debugger::resumeThread));

		JPanel tools = row();
		tools.add(refresh);
		tools.add(context);
		tools.add(halt);
		tools.add(proceed);
		tools.add(passException);
		tools.add(suspend);
		tools.add(resume);
		panel.add(tools, BorderLayout.NORTH);
		panel.add(split, BorderLayout.CENTER);
		return panel;
	}

	private JPanel modulePanel()
	{
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		JButton refresh = attachedButton("Refresh Modules");
		refresh.addActionListener(e -> refreshModules());
		JPanel tools = row();
		tools.add(refresh);
		panel.add(tools, BorderLayout.NORTH);
		panel.add(new JScrollPane(new JTable(moduleModel)), BorderLayout.CENTER);
		return panel;
	}

	private void configureTables()
	{
		eventTable.setFillsViewportHeight(true);
		eventTable.setAutoCreateRowSorter(true);
		eventTable.getColumnModel().getColumn(0).setPreferredWidth(95);
		eventTable.getColumnModel().getColumn(1).setPreferredWidth(110);
		eventTable.getColumnModel().getColumn(2).setPreferredWidth(85);
		eventTable.getColumnModel().getColumn(3).setPreferredWidth(90);
		eventTable.getColumnModel().getColumn(4).setPreferredWidth(700);
		threadTable.setFillsViewportHeight(true);
		threadTable.setAutoCreateRowSorter(true);
		threadTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
	}

	private void attach()
	{
		boolean replaceExisting = overrideExisting.isSelected();
		setWorkingState("Attaching...");
		tasks.run("attach debugger", () -> {
			try
			{
				debugger.attach(replaceExisting);
				DebuggerService.ExecutionState state = initialExecutionState();
				SwingUtilities.invokeLater(() -> {
					setAttachedState(true);
					executionStatus.setText("Execution: " + state);
				});
			}
			catch (Exception failure)
			{
				SwingUtilities.invokeLater(() -> setAttachedState(false));
				throw failure;
			}
		});
	}

	private void detach()
	{
		setWorkingState("Detaching...");
		tasks.run("detach debugger", () -> {
			try
			{
				debugger.detach();
			}
			finally
			{
				SwingUtilities.invokeLater(() -> setAttachedState(false));
			}
		});
	}

	private void refreshAll()
	{
		tasks.run("refresh debugger", () -> {
			DebuggerSnapshot snapshot = snapshot();
			SwingUtilities.invokeLater(() -> showSnapshot(snapshot));
		});
	}

	private DebuggerSnapshot snapshot()
	{
		return new DebuggerSnapshot(debugger.executionState(), debugger.threads(), debugger.modules());
	}

	private DebuggerService.ExecutionState initialExecutionState()
	{
		try
		{
			return debugger.executionState();
		}
		catch (RuntimeException ignored)
		{
			return DebuggerService.ExecutionState.UNKNOWN;
		}
	}

	private void showSnapshot(DebuggerSnapshot snapshot)
	{
		executionStatus.setText("Execution: " + snapshot.executionState());
		showThreads(snapshot.threads());
		showModules(snapshot.modules());
		refreshBreakpointModel();
	}

	private void refreshThreads()
	{
		tasks.run("refresh threads", () -> {
			List<ThreadInfo> values = debugger.threads();
			SwingUtilities.invokeLater(() -> showThreads(values));
		});
	}

	private void showThreads(List<ThreadInfo> values)
	{
		threadRows = List.copyOf(values);
		threadModel.setRowCount(0);
		for (ThreadInfo thread : values)
		{
			threadModel.addRow(new Object[]{hex(thread.id()), thread.stopped() ? "Stopped" : "Running",
				thread.priority(), thread.suspendCount(), hex(thread.startAddress()), hex(thread.stackBase()),
				hex(thread.stackLimit()), thread.processor()});
		}
	}

	private void showRegisters(List<RegisterValue> values)
	{
		registerModel.setRowCount(0);
		for (RegisterValue value : values)
		{
			registerModel.addRow(new Object[]{value.name(), value.value()});
		}
	}

	private void refreshModules()
	{
		tasks.run("refresh modules", () -> {
			List<ModuleInfo> values = debugger.modules();
			SwingUtilities.invokeLater(() -> showModules(values));
		});
	}

	private void showModules(List<ModuleInfo> values)
	{
		moduleModel.setRowCount(0);
		for (ModuleInfo module : values)
		{
			moduleModel.addRow(new Object[]{module.name(), hex(module.baseAddress()), hex(module.size()),
				hex(module.checksum()), module.dll() ? "DLL" : "XEX"});
		}
	}

	private void runAndRefreshState(String label, TaskRunner.ThrowingRunnable command)
	{
		tasks.run(label, () -> {
			command.run();
			DebuggerService.ExecutionState state = debugger.executionState();
			SwingUtilities.invokeLater(() -> executionStatus.setText("Execution: " + state));
		});
	}

	private void threadCommand(String label, ThreadCommand command)
	{
		withSelectedThread(label, id -> {
			command.run(id);
			List<ThreadInfo> values = debugger.threads();
			SwingUtilities.invokeLater(() -> showThreads(values));
		});
	}

	private void withSelectedThread(String label, ThreadCommand command)
	{
		int viewRow = threadTable.getSelectedRow();
		if (viewRow < 0)
		{
			tasks.run(label, () -> {
				throw new IllegalArgumentException("Select a thread");
			});
			return;
		}
		int modelRow = threadTable.convertRowIndexToModel(viewRow);
		long threadId = threadRows.get(modelRow).id();
		tasks.run(label, () -> command.run(threadId));
	}

	private void applyStopConditions()
	{
		EnumSet<StopCondition> selected = EnumSet.noneOf(StopCondition.class);
		stopConditionBoxes.forEach((condition, box) -> {
			if (box.isSelected())
			{
				selected.add(condition);
			}
		});
		tasks.run("apply debugger stop conditions", () -> debugger.applyStopConditions(selected));
	}

	private void refreshBreakpointModel()
	{
		breakpointModel.clear();
		debugger.breakpoints().forEach(breakpointModel::addElement);
	}

	private void receiveEvent(DebugEvent event)
	{
		synchronized (events)
		{
			events.addLast(event);
			if (events.size() > MAX_EVENTS)
			{
				events.removeFirst();
			}
		}
		pendingEvents.add(event);
		scheduleEventDrain();
	}

	private void scheduleEventDrain()
	{
		if (eventDrainScheduled.compareAndSet(false, true))
		{
			SwingUtilities.invokeLater(this::drainEvents);
		}
	}

	private void drainEvents()
	{
		DebugEvent event;
		int handled = 0;
		boolean changedSessionState = false;
		while (handled++ < 500 && (event = pendingEvents.poll()) != null)
		{
			eventModel.addRow(eventRow(event));
			if (eventModel.getRowCount() > MAX_EVENTS)
			{
				eventModel.removeRow(0);
			}
			if (event.type() == DebuggerService.EventType.SESSION && !debugger.attached())
			{
				changedSessionState = true;
			}
			else if (event.type() == DebuggerService.EventType.EXECUTION)
			{
				executionStatus.setText("Execution: " + DebuggerService.parseExecutionState(event.details()));
			}
		}
		int last = eventModel.getRowCount() - 1;
		if (last >= 0)
		{
			eventTable.scrollRectToVisible(eventTable.getCellRect(last, 0, true));
		}
		if (changedSessionState)
		{
			setAttachedState(false);
		}
		eventDrainScheduled.set(false);
		if (!pendingEvents.isEmpty())
		{
			scheduleEventDrain();
		}
	}

	private Object[] eventRow(DebugEvent event)
	{
		return new Object[]{EVENT_TIME.format(event.timestamp()), event.type(), nullableHex(event.threadId()),
			nullableHex(event.address()), event.details()};
	}

	private void clearEvents()
	{
		synchronized (events)
		{
			events.clear();
		}
		pendingEvents.clear();
		eventModel.setRowCount(0);
	}

	private void saveLog()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save debugger log");
		chooser.setSelectedFile(new java.io.File("openrtm-debug.log"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		Path path = chooser.getSelectedFile().toPath();
		List<DebugEvent> copy;
		synchronized (events)
		{
			copy = List.copyOf(events);
		}
		tasks.run("save debugger log", () -> writeLog(path, copy));
	}

	private static void writeLog(Path path, List<DebugEvent> values) throws IOException
	{
		StringBuilder output = new StringBuilder();
		for (DebugEvent event : values)
		{
			output.append(event.timestamp()).append(' ').append(event.type()).append(' ');
			if (!event.raw().isBlank())
			{
				output.append(event.raw());
			}
			else
			{
				output.append(event.details());
			}
			output.append(System.lineSeparator());
		}
		Files.writeString(path, output, StandardCharsets.UTF_8);
	}

	private void setWorkingState(String value)
	{
		sessionStatus.setText(value);
		sessionStatus.setForeground(WARN);
		attachButton.setEnabled(false);
		detachButton.setEnabled(false);
		overrideExisting.setEnabled(false);
		attachedButtons.forEach(button -> button.setEnabled(false));
		stopConditionBoxes.values().forEach(box -> box.setEnabled(false));
	}

	private void setAttachedState(boolean attached)
	{
		sessionStatus.setText(attached ? "Attached" : "Detached");
		sessionStatus.setForeground(attached ? OK : WARN);
		attachButton.setEnabled(!attached);
		detachButton.setEnabled(attached);
		overrideExisting.setEnabled(!attached);
		attachedButtons.forEach(button -> button.setEnabled(attached));
		stopConditionBoxes.values().forEach(box -> box.setEnabled(attached));
		if (!attached)
		{
			executionStatus.setText("Execution: Unknown");
		}
	}

	private static JPanel row()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private static JLabel separator()
	{
		JLabel label = new JLabel("|");
		label.setForeground(LINE);
		return label;
	}

	private JButton attachedButton(String label)
	{
		JButton button = new JButton(label);
		attachedButtons.add(button);
		return button;
	}

	private static DefaultTableModel tableModel(String... columns)
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

	private static String nullableHex(Long value)
	{
		return value == null ? "" : hex(value);
	}

	private static String hex(long value)
	{
		return String.format(Locale.ROOT, "0x%08X", value);
	}

	@FunctionalInterface
	private interface ThreadCommand
	{
		void run(long threadId) throws Exception;
	}

	private record DebuggerSnapshot(DebuggerService.ExecutionState executionState,
	                                List<ThreadInfo> threads, List<ModuleInfo> modules)
	{
	}
}
