package openrtm.ui;

import openrtm.fatx.FatxDevice;
import openrtm.stfs.PackageService;
import openrtm.titleids.TitleIds;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FatxBrowserPanel extends JPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private final TaskRunner tasks;
	private final JTextField source = new JTextField(38);
	private final JComboBox<Path> detected = new JComboBox<>();
	private final JComboBox<FatxDevice.Partition> partitions = new JComboBox<>();
	private final DefaultMutableTreeNode root = new DefaultMutableTreeNode(StorageNode.placeholder("No storage opened"));
	private final DefaultTreeModel model = new DefaultTreeModel(root);
	private final JTree tree = new JTree(model);
	private final JLabel selectedPath = new JLabel(" ");
	private FatxDevice device;

	public FatxBrowserPanel(TaskRunner tasks)
	{
		super(new BorderLayout(10, 10));
		this.tasks = tasks;
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
		configureTree();
		add(toolbar(), BorderLayout.NORTH);
		add(new JScrollPane(tree), BorderLayout.CENTER);
		add(selectedPath, BorderLayout.SOUTH);
		refreshDetected();
	}

	private JPanel toolbar()
	{
		JPanel panel = new JPanel(new BorderLayout(8, 4));
		panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));
		JPanel open = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		open.add(new JLabel("Storage source"));
		open.add(source);
		JButton browse = new JButton("Browse");
		JButton openButton = new JButton("Open");
		browse.addActionListener(event -> chooseSource());
		openButton.addActionListener(event -> openSource());
		open.add(browse);
		open.add(openButton);
		open.add(new JLabel("Detected device"));
		detected.setPrototypeDisplayValue(Path.of("/dev/device-name"));
		open.add(detected);
		JButton useDetected = new JButton("Use Device");
		useDetected.addActionListener(event -> {
			Path selected = (Path) detected.getSelectedItem();
			if (selected != null)
			{
				source.setText(selected.toString());
				openSource();
			}
		});
		open.add(useDetected);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		actions.add(new JLabel("Partition"));
		partitions.addActionListener(event -> showPartition());
		actions.add(partitions);
		JButton refresh = new JButton("Refresh");
		JButton extract = new JButton("Extract");
		JButton importFile = new JButton("Import File");
		JButton newFolder = new JButton("New Folder");
		JButton delete = new JButton("Delete");
		refresh.addActionListener(event -> refreshSelected());
		extract.addActionListener(event -> extractSelected());
		importFile.addActionListener(event -> importSelected());
		newFolder.addActionListener(event -> createFolder());
		delete.addActionListener(event -> deleteSelected());
		actions.add(refresh);
		actions.add(extract);
		actions.add(importFile);
		actions.add(newFolder);
		actions.add(delete);
		panel.add(open, BorderLayout.NORTH);
		panel.add(actions, BorderLayout.SOUTH);
		return panel;
	}

	private void configureTree()
	{
		tree.setRootVisible(false);
		tree.setShowsRootHandles(true);
		tree.setCellRenderer(new StorageRenderer());
		ToolTipManager.sharedInstance().registerComponent(tree);
		tree.addTreeSelectionListener(event -> {
			StorageNode selected = selectedNode();
			selectedPath.setText(selected == null || selected.entry == null ? " " : selected.entry.path());
		});
		tree.addTreeWillExpandListener(new TreeWillExpandListener()
		{
			@Override
			public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException
			{
				loadNode((DefaultMutableTreeNode) event.getPath().getLastPathComponent());
			}

			@Override
			public void treeWillCollapse(TreeExpansionEvent event)
			{
			}
		});
		tree.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				showActualId(event);
			}
		});
	}

	private void chooseSource()
	{
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		if (!source.getText().isBlank())
		{
			chooser.setSelectedFile(Path.of(source.getText()).toFile());
		}
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			source.setText(chooser.getSelectedFile().getAbsolutePath());
			openSource();
		}
	}

	private void openSource()
	{
		if (source.getText().isBlank())
		{
			showWarning("Choose an Xbox 360 storage source");
			return;
		}
		Path path = Path.of(source.getText().trim());
		tasks.run("open Xbox storage", () -> {
			FatxDevice opened = FatxDevice.open(path);
			FatxDevice previous = device;
			device = opened;
			if (previous != null)
			{
				previous.close();
			}
			SwingUtilities.invokeLater(() -> setPartitions(opened.partitions()));
		});
	}

	private void setPartitions(List<FatxDevice.Partition> values)
	{
		partitions.removeAllItems();
		for (FatxDevice.Partition partition : values)
		{
			partitions.addItem(partition);
		}
		for (int index = 0; index < partitions.getItemCount(); index++)
		{
			if (partitions.getItemAt(index).name().equals("Content"))
			{
				partitions.setSelectedIndex(index);
				return;
			}
		}
		if (partitions.getItemCount() > 0)
		{
			partitions.setSelectedIndex(0);
		}
	}

	private void showPartition()
	{
		FatxDevice.Partition partition = (FatxDevice.Partition) partitions.getSelectedItem();
		root.removeAllChildren();
		if (partition != null)
		{
			root.add(node(StorageNode.entry(partition.root(), partition.name(), null)));
		}
		else
		{
			root.add(new DefaultMutableTreeNode(StorageNode.placeholder("No partition selected")));
		}
		model.reload();
	}

	private void loadNode(DefaultMutableTreeNode treeNode)
	{
		StorageNode selected = storageNode(treeNode);
		if (selected == null || selected.entry == null || !selected.entry.directory() || selected.loaded)
		{
			return;
		}
		selected.loaded = true;
		tasks.run("load Xbox storage folder", () -> {
			List<FatxDevice.Entry> children = device.list(selected.entry);
			List<DefaultMutableTreeNode> nodes = new ArrayList<>();
			for (FatxDevice.Entry child : children)
			{
				Alias alias = alias(selected.entry.path(), child);
				nodes.add(node(StorageNode.entry(child, alias.displayName, alias.actualId)));
			}
			SwingUtilities.invokeLater(() -> replaceChildren(treeNode, selected, nodes));
		});
	}

	private void replaceChildren(DefaultMutableTreeNode parent, StorageNode parentValue,
	                             List<DefaultMutableTreeNode> children)
	{
		parent.removeAllChildren();
		if (children.isEmpty())
		{
			parent.add(new DefaultMutableTreeNode(StorageNode.placeholder("Empty")));
		}
		else
		{
			for (DefaultMutableTreeNode child : children)
			{
				parent.add(child);
			}
		}
		parentValue.loaded = true;
		model.reload(parent);
	}

	private void refreshSelected()
	{
		DefaultMutableTreeNode selected = selectedTreeNode();
		StorageNode value = storageNode(selected);
		if (value == null || value.entry == null)
		{
			showPartition();
			return;
		}
		DefaultMutableTreeNode directory = value.entry.directory() ? selected
			: (DefaultMutableTreeNode) selected.getParent();
		StorageNode directoryValue = storageNode(directory);
		if (directoryValue != null)
		{
			directoryValue.loaded = false;
			loadNode(directory);
		}
	}

	private void extractSelected()
	{
		StorageNode value = selectedNode();
		if (value == null || value.entry == null || value.entry.directory())
		{
			showWarning("Select a file to extract");
			return;
		}
		JFileChooser chooser = new JFileChooser();
		chooser.setSelectedFile(Path.of(value.entry.name()).toFile());
		if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			tasks.run("extract Xbox storage file",
				() -> device.extract(value.entry, chooser.getSelectedFile().toPath()));
		}
	}

	private void importSelected()
	{
		DefaultMutableTreeNode directoryNode = selectedDirectoryNode();
		StorageNode directory = storageNode(directoryNode);
		if (directory == null)
		{
			showWarning("Select a destination folder");
			return;
		}
		JFileChooser chooser = new JFileChooser();
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			String fileName = chooser.getSelectedFile().getName();
			int answer = JOptionPane.showConfirmDialog(this,
				"Import " + fileName + " into the selected folder? A same-name file will be replaced.",
				"Import File", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (answer != JOptionPane.YES_OPTION)
			{
				return;
			}
			tasks.run("import Xbox storage file", () -> {
				device.importFile(directory.entry, chooser.getSelectedFile().toPath());
				directory.loaded = false;
				SwingUtilities.invokeLater(() -> loadNode(directoryNode));
			});
		}
	}

	private void createFolder()
	{
		DefaultMutableTreeNode directoryNode = selectedDirectoryNode();
		StorageNode directory = storageNode(directoryNode);
		if (directory == null)
		{
			showWarning("Select a parent folder");
			return;
		}
		String name = JOptionPane.showInputDialog(this, "Folder name", "New Folder",
			JOptionPane.PLAIN_MESSAGE);
		if (name != null)
		{
			tasks.run("create Xbox storage folder", () -> {
				device.createDirectory(directory.entry, name.trim());
				directory.loaded = false;
				SwingUtilities.invokeLater(() -> loadNode(directoryNode));
			});
		}
	}

	private void deleteSelected()
	{
		DefaultMutableTreeNode selected = selectedTreeNode();
		StorageNode value = storageNode(selected);
		if (value == null || value.entry == null || value.entry.path().equals("/"))
		{
			showWarning("Select a file or folder to delete");
			return;
		}
		int answer = JOptionPane.showConfirmDialog(this,
			"Delete " + value.entry.name() + " from the storage device?",
			"Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (answer != JOptionPane.YES_OPTION)
		{
			return;
		}
		DefaultMutableTreeNode parent = (DefaultMutableTreeNode) selected.getParent();
		tasks.run("delete Xbox storage entry", () -> {
			device.delete(value.entry);
			StorageNode parentValue = storageNode(parent);
			parentValue.loaded = false;
			SwingUtilities.invokeLater(() -> loadNode(parent));
		});
	}

	private DefaultMutableTreeNode selectedDirectoryNode()
	{
		DefaultMutableTreeNode selected = selectedTreeNode();
		StorageNode value = storageNode(selected);
		if (value == null || value.entry == null)
		{
			return null;
		}
		return value.entry.directory() ? selected : (DefaultMutableTreeNode) selected.getParent();
	}

	private void showActualId(MouseEvent event)
	{
		if (!SwingUtilities.isLeftMouseButton(event))
		{
			return;
		}
		TreePath path = tree.getPathForLocation(event.getX(), event.getY());
		if (path == null)
		{
			return;
		}
		StorageNode value = storageNode((DefaultMutableTreeNode) path.getLastPathComponent());
		Rectangle bounds = tree.getPathBounds(path);
		if (value != null && value.actualId != null && bounds != null
			&& event.getX() >= bounds.x + bounds.width - StorageRenderer.ID_WIDTH)
		{
			JOptionPane.showMessageDialog(this, value.actualId, "Actual folder name",
				JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private void refreshDetected()
	{
		detected.removeAllItems();
		for (Path path : FatxDevice.discover())
		{
			detected.addItem(path);
		}
	}

	public void shutdown()
	{
		if (device != null)
		{
			try
			{
				device.close();
			}
			catch (IOException ignored)
			{
			}
			device = null;
		}
	}

	private static Alias alias(String parent, FatxDevice.Entry entry)
	{
		if (!entry.directory())
		{
			return new Alias(entry.name(), null);
		}
		String name = entry.name().toUpperCase(Locale.ROOT);
		if (parent.matches("(?i)^/[0-9a-f]{16}$") && name.matches("[0-9A-F]{8}"))
		{
			String display = TitleIds.displayName(name);
			return display.equalsIgnoreCase(name) ? new Alias(entry.name(), null) : new Alias(display, name);
		}
		if (parent.matches("(?i)^/[0-9a-f]{16}/[0-9a-f]{8}$") && name.matches("[0-9A-F]{8}"))
		{
			String display = PackageService.contentTypeName((int) Long.parseLong(name, 16));
			return display.startsWith("Unknown") ? new Alias(entry.name(), null) : new Alias(display, name);
		}
		return new Alias(entry.name(), null);
	}

	private static DefaultMutableTreeNode node(StorageNode value)
	{
		DefaultMutableTreeNode node = new DefaultMutableTreeNode(value);
		if (value.entry != null && value.entry.directory())
		{
			node.add(new DefaultMutableTreeNode(StorageNode.placeholder("Loading")));
		}
		return node;
	}

	private DefaultMutableTreeNode selectedTreeNode()
	{
		TreePath path = tree.getSelectionPath();
		return path == null ? null : (DefaultMutableTreeNode) path.getLastPathComponent();
	}

	private StorageNode selectedNode()
	{
		return storageNode(selectedTreeNode());
	}

	private static StorageNode storageNode(DefaultMutableTreeNode node)
	{
		return node != null && node.getUserObject() instanceof StorageNode
			? (StorageNode) node.getUserObject() : null;
	}

	private void showWarning(String message)
	{
		JOptionPane.showMessageDialog(this, message, "Xbox Storage", JOptionPane.WARNING_MESSAGE);
	}

	private static final class Alias
	{
		private final String displayName;
		private final String actualId;

		private Alias(String displayName, String actualId)
		{
			this.displayName = displayName;
			this.actualId = actualId;
		}
	}

	private static final class StorageNode
	{
		private final FatxDevice.Entry entry;
		private final String displayName;
		private final String actualId;
		private boolean loaded;

		private StorageNode(FatxDevice.Entry entry, String displayName, String actualId)
		{
			this.entry = entry;
			this.displayName = displayName;
			this.actualId = actualId;
		}

		private static StorageNode entry(FatxDevice.Entry entry, String displayName, String actualId)
		{
			return new StorageNode(entry, displayName, actualId);
		}

		private static StorageNode placeholder(String message)
		{
			StorageNode value = new StorageNode(null, message, null);
			value.loaded = true;
			return value;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}

	private static final class StorageRenderer extends JPanel implements TreeCellRenderer
	{
		private static final int ID_WIDTH = 34;
		private final DefaultTreeCellRenderer label = new DefaultTreeCellRenderer();
		private final JButton id = new JButton("ID");
		private String actual;

		private StorageRenderer()
		{
			super(new BorderLayout(4, 0));
			setOpaque(true);
			id.setFocusable(false);
			id.setMargin(new Insets(0, 4, 0, 4));
			id.setPreferredSize(new Dimension(ID_WIDTH, 22));
		}

		@Override
		public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
		                                              boolean expanded, boolean leaf, int row, boolean hasFocus)
		{
			label.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
			StorageNode node = storageNode((DefaultMutableTreeNode) value);
			if (node != null && node.entry != null)
			{
				label.setIcon(UIManager.getIcon(node.entry.directory()
					? "FileView.directoryIcon" : "FileView.fileIcon"));
			}
			removeAll();
			add(label, BorderLayout.CENTER);
			actual = node == null ? null : node.actualId;
			if (actual != null)
			{
				id.setToolTipText("Actual folder: " + actual);
				add(id, BorderLayout.EAST);
			}
			setBackground(selected ? label.getBackgroundSelectionColor()
				: label.getBackgroundNonSelectionColor());
			return this;
		}

		@Override
		public String getToolTipText(MouseEvent event)
		{
			return actual != null && event.getX() >= getWidth() - ID_WIDTH
				? "Actual folder: " + actual : null;
		}
	}
}
