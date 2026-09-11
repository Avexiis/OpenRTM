package openrtm.ui;

import openrtm.console.ConsoleService;
import openrtm.console.ConsoleService.FileEntry;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;

public final class ConsoleFilePicker extends JDialog
{
	private static final Color LINE = new Color(55, 60, 66);
	private static final Color ACCENT = new Color(91, 141, 239);

	private final ConsoleService service;
	private final DefaultListModel<FileEntry> listModel = new DefaultListModel<>();
	private final JList<FileEntry> fileList = new JList<>(listModel);
	private final JTextField pathField;
	private final JLabel statusLabel = new JLabel(" ");

	private String selectedPath;

	public ConsoleFilePicker(JFrame parent, ConsoleService service, String initialPath)
	{
		super(parent, "Console File Browser", true);
		this.service = service;
		this.pathField = new JTextField(initialPath);

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setSize(600, 450);
		setLocationRelativeTo(parent);
		getContentPane().setLayout(new BorderLayout(8, 8));
		((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		add(createPathBar(), BorderLayout.NORTH);
		add(new JScrollPane(fileList), BorderLayout.CENTER);
		add(createButtons(), BorderLayout.SOUTH);

		fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		fileList.setCellRenderer(new ConsoleFileRenderer());

		// Double-click to navigate into folders or select files
		fileList.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				if (e.getClickCount() == 2)
				{
					FileEntry entry = fileList.getSelectedValue();
					if (entry != null)
					{
						if (entry.directory())
						{
							String newPath = childPath(pathField.getText(), entry.name());
							pathField.setText(newPath);
							loadDirectory(newPath);
						}
						else
						{
							selectAndClose(entry);
						}
					}
				}
			}
		});

		loadDirectory(initialPath);
	}

	private JPanel createPathBar()
	{
		JPanel panel = new JPanel(new BorderLayout(4, 4));
		JButton goButton = new JButton("Go");
		goButton.addActionListener(e -> loadDirectory(pathField.getText()));
		JButton upButton = new JButton("Up");
		upButton.addActionListener(e -> goUp());

		JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.add(upButton);
		buttons.add(goButton);

		panel.add(new JLabel("Path:"), BorderLayout.WEST);
		panel.add(pathField, BorderLayout.CENTER);
		panel.add(buttons, BorderLayout.EAST);

		return panel;
	}

	private JPanel createButtons()
	{
		JPanel panel = new JPanel(new BorderLayout(8, 8));
		panel.add(statusLabel, BorderLayout.WEST);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton selectButton = new JButton("Select");
		JButton cancelButton = new JButton("Cancel");

		selectButton.addActionListener(e -> {
			FileEntry entry = fileList.getSelectedValue();
			if (entry == null)
			{
				JOptionPane.showMessageDialog(this, "Select a file first", "Select File",
					JOptionPane.WARNING_MESSAGE);
				return;
			}
			selectAndClose(entry);
		});

		cancelButton.addActionListener(e -> {
			selectedPath = null;
			dispose();
		});

		buttons.add(selectButton);
		buttons.add(cancelButton);
		panel.add(buttons, BorderLayout.EAST);

		return panel;
	}

	private void goUp()
	{
		String path = pathField.getText().trim();
		int lastSlash = path.lastIndexOf('\\');
		if (lastSlash > 2)
		{
			path = path.substring(0, lastSlash);
		}
		else if (lastSlash == 2)
		{
			path = path.substring(0, 3); // Keep drive letter
		}
		pathField.setText(path);
		loadDirectory(path);
	}

	private void loadDirectory(String path)
	{
		SwingUtilities.invokeLater(() -> {
			statusLabel.setText("Loading...");
			listModel.clear();
		});

		new Thread(() -> {
			try
			{
				List<FileEntry> entries = service.listDirectory(path);
				SwingUtilities.invokeLater(() -> {
					listModel.clear();
					for (FileEntry entry : entries)
					{
						listModel.addElement(entry);
					}
					statusLabel.setText(entries.size() + " items");
				});
			}
			catch (Exception e)
			{
				SwingUtilities.invokeLater(() -> {
					statusLabel.setText("Error: " + e.getMessage());
				});
			}
		}, "console-file-browser").start();
	}

	private void selectAndClose(FileEntry entry)
	{
		String fullPath = childPath(pathField.getText(), entry.name());
		selectedPath = fullPath;
		dispose();
	}

	private static String childPath(String parent, String name)
	{
		if (parent.endsWith("\\"))
		{
			return parent + name;
		}
		return parent + "\\" + name;
	}

	public String getSelectedPath()
	{
		return selectedPath;
	}

	private static class ConsoleFileRenderer extends JLabel implements javax.swing.ListCellRenderer<FileEntry>
	{
		@Override
		public java.awt.Component getListCellRendererComponent(
			JList<? extends FileEntry> list, FileEntry value, int index,
			boolean isSelected, boolean cellHasFocus)
		{
			String icon = value.directory() ? "📁 " : "📄 ";
			String display = icon + value.name();
			if (!value.directory() && value.size() > 0)
			{
				display += String.format(Locale.ROOT, " (%.1f KB)", value.size() / 1024.0);
			}
			setText(display);

			if (isSelected)
			{
				setBackground(list.getSelectionBackground());
				setForeground(list.getSelectionForeground());
			}
			else
			{
				setBackground(list.getBackground());
				setForeground(list.getForeground());
			}

			setOpaque(true);
			setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
			return this;
		}
	}
}
