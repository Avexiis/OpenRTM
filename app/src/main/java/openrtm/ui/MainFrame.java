package openrtm.ui;

import com.jjrpc.JRPC;
import openrtm.console.ConsoleService;
import openrtm.ui.files.FileBrowserPanel;
import openrtm.util.HexUtils;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class MainFrame extends JFrame
{
	private static final Color LINE = new Color(55, 60, 66);
	private static final Color ACCENT = new Color(91, 141, 239);
	private static final Color OK = new Color(92, 184, 117);
	private static final Color WARN = new Color(210, 157, 73);
	private static final Color DANGER = new Color(192, 85, 85);

	private final ConsoleService service = new ConsoleService();
	private final CardLayout pages = new CardLayout();
	private final JPanel pageDeck = new JPanel(pages);
	private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
		Thread thread = new Thread(r, "openrtm-worker");
		thread.setDaemon(true);
		return thread;
	});
	private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "openrtm-reconnect");
		thread.setDaemon(true);
		return thread;
	});
	private final JComboBox<String> hostField = hostCombo();
	private final JCheckBox autoConnect = new JCheckBox("Autoconnect", service.autoConnect());
	private final JLabel status = new JLabel("Disconnected");
	private FileBrowserPanel fileBrowserPanel;
	private VideoCapturePanel videoCapturePanel;
	private volatile boolean reconnectWanted;
	private volatile String reconnectHost = "";
	private ScheduledFuture<?> reconnectFuture;
	private int reconnectDelayIndex;
	private final DefaultTableModel infoModel = new DefaultTableModel(new Object[]{"Field", "Value"}, 0)
	{
		@Override
		public boolean isCellEditable(int row, int column)
		{
			return false;
		}
	};

	public MainFrame()
	{
		super("OpenRTM");
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setMinimumSize(new Dimension(1100, 720));
		setSize(1250, 780);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout());

		add(connectionBar(), BorderLayout.NORTH);
		add(shell(), BorderLayout.CENTER);
		status.setForeground(WARN);

		autoConnect.addActionListener(e -> {
			service.autoConnect(autoConnect.isSelected());
			if (autoConnect.isSelected() && !service.isConnected() && !selectedHost().isBlank())
			{
				connect();
			}
			else if (!autoConnect.isSelected() && !service.isConnected())
			{
				stopReconnect();
			}
		});
		addWindowListener(new java.awt.event.WindowAdapter()
		{
			@Override
			public void windowClosed(java.awt.event.WindowEvent e)
			{
				stopReconnect();
				service.cancelFileTransfer();
				service.disconnect();
				if (videoCapturePanel != null)
				{
					videoCapturePanel.shutdown();
				}
				executor.shutdownNow();
				reconnectExecutor.shutdownNow();
			}
		});
		if (autoConnect.isSelected() && !selectedHost().isBlank())
		{
			SwingUtilities.invokeLater(this::connect);
		}
	}

	private JPanel connectionBar()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
		panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
		JButton connect = button("Connect");
		JButton disconnect = button("Disconnect");
		JButton refresh = button("Refresh Info");
		connect.addActionListener(e -> connect());
		disconnect.addActionListener(e -> runTask("disconnect", () -> {
			stopReconnect();
			service.cancelFileTransfer();
			service.disconnect();
			SwingUtilities.invokeLater(() -> setStatus("Disconnected", WARN));
		}));
		refresh.addActionListener(e -> refreshInfo());
		panel.add(new JLabel("Console IP"));
		panel.add(hostField);
		panel.add(connect);
		panel.add(disconnect);
		panel.add(refresh);
		panel.add(autoConnect);
		panel.add(status);
		return panel;
	}

	private JPanel shell()
	{
		DefaultListModel<String> navigationModel = new DefaultListModel<>();
		addPage(navigationModel, "Home", dashboardPanel());
		videoCapturePanel = new VideoCapturePanel();
		addPage(navigationModel, "Video Capture", videoCapturePanel);
		addPage(navigationModel, "Memory & Commands", memoryPanel());
		addPage(navigationModel, "Debugger", new DebuggerPanel(service.debugger(), this::runTask));
		addPage(navigationModel, "File Transfer", filesPanel());
		addPage(navigationModel, "Content Library", new ContentLibraryPanel(service, this::runTask));
		addPage(navigationModel, "Package Manager", new PackageManagerPanel(this::runTask));
		addPage(navigationModel, "ISO Extractor", new IsoToolPanel(this::runTask));
		addPage(navigationModel, "Game Saves", new GameSaveEditorPanel(this::runTask));
		addPage(navigationModel, "Module Manager", new ModuleManagerPanel(service, this::runTask));

		JList<String> navigation = new JList<>(navigationModel);
		navigation.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		navigation.setFixedCellHeight(34);
		navigation.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		navigation.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting())
			{
				pages.show(pageDeck, navigation.getSelectedValue());
			}
		});
		navigation.setSelectedIndex(0);

		JScrollPane navScroll = new JScrollPane(navigation);
		navScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, LINE));
		navScroll.setPreferredSize(new Dimension(190, 0));

		JPanel shell = new JPanel(new BorderLayout());
		shell.add(navScroll, BorderLayout.WEST);
		shell.add(pageDeck, BorderLayout.CENTER);
		return shell;
	}

	private void addPage(DefaultListModel<String> navigationModel, String name, Component page)
	{
		navigationModel.addElement(name);
		pageDeck.add(page, name);
	}

	private JPanel dashboardPanel()
	{
		JPanel panel = page();

		JTable info = new JTable(infoModel);
		info.setFillsViewportHeight(true);
		JScrollPane tableScroll = new JScrollPane(info);
		tableScroll.setPreferredSize(new Dimension(700, 260));
		JPanel infoSection = section("Console Info");
		infoSection.add(tableScroll);
		panel.add(infoSection, BorderLayout.CENTER);

		JPanel controls = section("Console Controls");
		JPanel grid = new JPanel(new GridLayout(0, 3, 8, 8));
		addAction(grid, "XNotify", () -> {
			String message = JOptionPane.showInputDialog(this, "Message", "OpenRTM");
			if (message != null)
			{
				service.xNotify(message, JRPC.XNotiyLogo.FLASHING_XBOX_CONSOLE);
			}
		});
		addAction(grid, "Warm Reboot", service::rebootWarm);
		addAction(grid, "Cold Reboot", service::rebootCold);
		addAction(grid, "Shutdown", service::shutdown);
		addAction(grid, "DVD Eject", () -> service.ejectDvd(true));
		addAction(grid, "DVD Close", () -> service.ejectDvd(false));
		controls.add(grid);
		panel.add(controls, BorderLayout.SOUTH);
		return panel;
	}

	private JPanel memoryPanel()
	{
		JPanel panel = page();
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Memory", memoryEditorPanel());
		tabs.addTab("Commands", commandsPanel());
		panel.add(tabs, BorderLayout.CENTER);
		return panel;
	}

	private JPanel memoryEditorPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(10, 10));
		JTextArea output = textArea(16);

		JPanel read = section("Read Memory");
		JTextField address = new JTextField("0x82000000", 14);
		JTextField length = new JTextField("64", 6);
		JButton readButton = button("Read");
		readButton.addActionListener(e -> runTask("read memory", () -> {
			long a = HexUtils.parseAddress(address.getText());
			int n = HexUtils.parseInt(length.getText());
			byte[] data = service.readMemory(a, n);
			SwingUtilities.invokeLater(() -> output.setText(HexUtils.formatHex(data)));
		}));
		JPanel readRow = row();
		readRow.add(new JLabel("Address"));
		readRow.add(address);
		readRow.add(new JLabel("Length"));
		readRow.add(length);
		readRow.add(readButton);
		read.add(readRow);
		read.add(new JScrollPane(output));
		panel.add(read, BorderLayout.NORTH);

		JPanel write = section("Write Memory");
		JTextField writeAddress = new JTextField("0x82000000", 14);
		JTextField typedValue = new JTextField("", 18);
		JComboBox<String> type = new JComboBox<>(new String[]{"Byte", "Bool", "UInt16 BE", "UInt32 BE", "Int32 LE", "ASCII NUL", "UTF-16BE NUL"});
		JTextArea hex = textArea(7);
		JButton writeHex = button("Write Hex");
		writeHex.addActionListener(e -> runTask("write hex memory", () -> service.writeMemory(HexUtils.parseAddress(writeAddress.getText()), HexUtils.parseHex(hex.getText()))));
		JButton writeTyped = button("Write Typed");
		writeTyped.addActionListener(e -> runTask("write typed memory", () -> writeTyped(writeAddress.getText(), (String) type.getSelectedItem(), typedValue.getText())));
		JPanel writeRow = row();
		writeRow.add(new JLabel("Address"));
		writeRow.add(writeAddress);
		writeRow.add(type);
		writeRow.add(typedValue);
		writeRow.add(writeTyped);
		write.add(writeRow);
		write.add(new JScrollPane(hex));
		write.add(writeHex);
		panel.add(write, BorderLayout.CENTER);
		return panel;
	}

	private JPanel filesPanel()
	{
		fileBrowserPanel = new FileBrowserPanel(service, this::runTask);
		return fileBrowserPanel;
	}

	private JPanel commandsPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(10, 10));
		JPanel section = section("Console Command");
		JTextField command = new JTextField("systeminfo", 38);
		JTextArea output = textArea(22);
		JButton send = button("Send");
		send.addActionListener(e -> runTask("raw command", () -> {
			String response = service.rawCommand(command.getText());
			SwingUtilities.invokeLater(() -> output.setText(response));
		}));
		JPanel row = row();
		row.add(command);
		row.add(send);
		section.add(row);
		section.add(new JScrollPane(output));
		panel.add(section, BorderLayout.CENTER);
		return panel;
	}

	private void connect()
	{
		String host = selectedHost();
		if (host.isBlank())
		{
			JOptionPane.showMessageDialog(this, "Enter a console host or IP address", "Connect", JOptionPane.WARNING_MESSAGE);
			return;
		}
		reconnectHost = host;
		reconnectWanted = true;
		runTask("connect", () -> {
			try
			{
				if (!service.connect(host))
				{
					throw new IllegalStateException("Could not connect to " + host);
				}
				reconnectDelayIndex = 0;
				SwingUtilities.invokeLater(() -> connected(host, true));
			}
			finally
			{
				scheduleReconnectCheck(nextReconnectDelay());
			}
		});
	}

	private void connected(String host, boolean refresh)
	{
		rememberHostInCombo(host);
		setStatus("Connected to " + host, OK);
		if (refresh)
		{
			if (fileBrowserPanel != null)
			{
				fileBrowserPanel.refreshConsoleDrives();
			}
			refreshInfo();
		}
	}

	private void reconnectTick()
	{
		if (!reconnectWanted)
		{
			return;
		}
		if (service.isFileTransferInProgress())
		{
			scheduleReconnectCheck(nextReconnectDelay());
			return;
		}
		if (service.connectionAlive())
		{
			reconnectDelayIndex = 0;
			scheduleReconnectCheck(nextReconnectDelay());
			return;
		}

		String host = reconnectHost;
		SwingUtilities.invokeLater(() -> setStatus("Reconnecting to " + host + "...", WARN));
		boolean connected = false;
		try
		{
			connected = service.connect(host);
		}
		catch (RuntimeException ignored)
		{
		}
		if (connected)
		{
			reconnectDelayIndex = 0;
			SwingUtilities.invokeLater(() -> connected(host, false));
		}
		scheduleReconnectCheck(nextReconnectDelay());
	}

	private synchronized void scheduleReconnectCheck(long delayMs)
	{
		if (!reconnectWanted || reconnectExecutor.isShutdown())
		{
			return;
		}
		if (reconnectFuture != null && !reconnectFuture.isDone())
		{
			return;
		}
		reconnectFuture = reconnectExecutor.schedule(() -> {
			synchronized (MainFrame.this)
			{
				reconnectFuture = null;
			}
			reconnectTick();
		}, delayMs, TimeUnit.MILLISECONDS);
	}

	private long nextReconnectDelay()
	{
		long[] delays = {3_500, 5_000, 4_250, 6_500, 5_750};
		long base = delays[reconnectDelayIndex++ % delays.length];
		return base + ThreadLocalRandom.current().nextLong(250, 1_000);
	}

	private synchronized void stopReconnect()
	{
		reconnectWanted = false;
		if (reconnectFuture != null)
		{
			reconnectFuture.cancel(false);
		}
		reconnectFuture = null;
	}

	private void refreshInfo()
	{
		runTask("refresh info", () -> {
			Map<String, String> info = service.readInfo();
			SwingUtilities.invokeLater(() -> {
				infoModel.setRowCount(0);
				info.forEach((key, value) -> infoModel.addRow(new Object[]{key, value}));
			});
		});
	}

	private void writeTyped(String addressText, String type, String value)
	{
		long address = HexUtils.parseAddress(addressText);
		switch (type)
		{
			case "Byte" -> service.writeByte(address, HexUtils.parseInt(value));
			case "Bool" -> service.writeBool(address, parseBool(value));
			case "UInt16 BE" -> service.writeUInt16BE(address, HexUtils.parseInt(value));
			case "UInt32 BE" -> service.writeUInt32BE(address, HexUtils.parseUnsigned32(value));
			case "Int32 LE" -> service.writeInt32LE(address, HexUtils.parseInt(value));
			case "ASCII NUL" -> service.writeAsciiNull(address, value);
			case "UTF-16BE NUL" -> service.writeUtf16BigNull(address, value);
			default -> throw new IllegalArgumentException("Unsupported type: " + type);
		}
	}

	private void runTask(String label, TaskRunner.ThrowingRunnable action)
	{
		setStatus(label + "...", WARN);
		executor.submit(() -> {
			try
			{
				action.run();
				SwingUtilities.invokeLater(() -> setStatus(label + " complete", OK));
			}
			catch (Throwable t)
			{
				SwingUtilities.invokeLater(() -> {
					setStatus(label + " failed", DANGER);
					JOptionPane.showMessageDialog(this, t.getMessage(), label, JOptionPane.ERROR_MESSAGE);
				});
			}
		});
	}

	private static boolean parseBool(String value)
	{
		String normalized = value == null ? "" : value.trim();
		if (normalized.equalsIgnoreCase("true"))
		{
			return true;
		}
		if (normalized.equalsIgnoreCase("false"))
		{
			return false;
		}
		return HexUtils.parseInt(normalized) != 0;
	}

	private void addAction(JPanel panel, String label, TaskRunner.ThrowingRunnable action)
	{
		JButton button = button(label);
		button.addActionListener(e -> runTask(label, action));
		panel.add(button);
	}

	private JPanel page()
	{
		JPanel panel = new JPanel(new BorderLayout(10, 10));
		panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
		return panel;
	}

	private JPanel section(String title)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
			BorderFactory.createEmptyBorder(12, 0, 14, 0)));
		JLabel label = new JLabel(title);
		label.setForeground(ACCENT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(label);
		return panel;
	}

	private JPanel row()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private JTextArea textArea(int rows)
	{
		JTextArea area = new JTextArea(rows, 80);
		area.setFont(Font.decode(Font.MONOSPACED + "-12"));
		area.setLineWrap(false);
		return area;
	}

	private static JButton button(String label)
	{
		return new JButton(label);
	}

	private void setStatus(String text, Color color)
	{
		status.setText(text);
		status.setForeground(color);
	}

	private JComboBox<String> hostCombo()
	{
		JComboBox<String> combo = new JComboBox<>(service.recentHosts().toArray(String[]::new));
		combo.setEditable(true);
		combo.setPrototypeDisplayValue("000.000.000.000");
		combo.setSelectedItem(service.savedHost());
		return combo;
	}

	private String selectedHost()
	{
		Object item = hostField.getEditor().getItem();
		return item == null ? "" : item.toString().trim();
	}

	private void rememberHostInCombo(String host)
	{
		for (int i = 0; i < hostField.getItemCount(); i++)
		{
			if (hostField.getItemAt(i).equalsIgnoreCase(host))
			{
				hostField.setSelectedIndex(i);
				return;
			}
		}
		hostField.insertItemAt(host, 0);
		hostField.setSelectedIndex(0);
	}
}
