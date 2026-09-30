package openrtm.ui;

import openrtm.fatx.FatxDevice;
import openrtm.profile.ProfileIdentityResolver;
import openrtm.stfs.PackageService;
import openrtm.titleids.TitleIds;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
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
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;

public final class FatxBrowserPanel extends FileDropPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private final TaskRunner tasks;
	private final ProfileIdentityResolver identities;
	private final JTextField source = new JTextField(24);
	private final JComboBox<FatxDevice.DetectedDevice> detected = new JComboBox<>();
	private final JComboBox<FatxDevice.Partition> partitions = new JComboBox<>();
	private final DefaultMutableTreeNode root = new DefaultMutableTreeNode(StorageNode.placeholder("No storage opened"));
	private final DefaultTreeModel model = new DefaultTreeModel(root);
	private final JTree tree = new JTree(model);
	private final JLabel selectedPath = new JLabel(" ");
	private final TransferEstimate estimator = new TransferEstimate();
	private String progressVerb = "Extracting";
	private final JButton cancelExtract = new JButton("Cancel");
	private volatile boolean cancelRequested;
	private volatile long lastScanUpdate;
	private final ArrayDeque<TreePath> history = new ArrayDeque<>();
	private boolean navigating;
	private final JLabel extractStatus = new JLabel(" ");
	private final JProgressBar extractProgress = new JProgressBar(0, 100);
	private FatxDevice device;

	public FatxBrowserPanel(TaskRunner tasks, ProfileIdentityResolver identities)
	{
		super(new BorderLayout(10, 10));
		this.tasks = tasks;
		this.identities = identities;
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
		configureTree();
		add(toolbar(), BorderLayout.NORTH);
		add(new JScrollPane(tree), BorderLayout.CENTER);
		extractProgress.setStringPainted(true);
		extractProgress.setVisible(false);
		JPanel footer = new JPanel(new BorderLayout(8, 0));
		footer.add(selectedPath, BorderLayout.WEST);
		footer.add(extractStatus, BorderLayout.CENTER);
		cancelExtract.setVisible(false);
		cancelExtract.addActionListener(event -> {
			cancelRequested = true;
			cancelExtract.setEnabled(false);
			extractStatus.setText("Cancelling");
		});
		JPanel controls = new JPanel(new BorderLayout(8, 0));
		controls.add(cancelExtract, BorderLayout.WEST);
		controls.add(extractProgress, BorderLayout.CENTER);
		footer.add(controls, BorderLayout.EAST);
		add(footer, BorderLayout.SOUTH);
		refreshDetected();
		enableFileDrop("Drop files here to use!", this::dropFiles);
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
		detected.setPrototypeDisplayValue(new FatxDevice.DetectedDevice(
			Path.of("\\\\.\\PhysicalDrive10"), "500GB HDD Xbox"));
		detected.addPopupMenuListener(new PopupMenuListener()
		{
			@Override
			public void popupMenuWillBecomeVisible(PopupMenuEvent event)
			{
				refreshDetected();
			}

			@Override
			public void popupMenuWillBecomeInvisible(PopupMenuEvent event)
			{
			}

			@Override
			public void popupMenuCanceled(PopupMenuEvent event)
			{
			}
		});
		open.add(detected);
		JButton useDetected = new JButton("Use Device");
		useDetected.addActionListener(event -> {
			FatxDevice.DetectedDevice selected = (FatxDevice.DetectedDevice) detected.getSelectedItem();
			if (selected != null)
			{
				source.setText(selected.path().toString());
				openSource();
			}
		});
		open.add(useDetected);

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
		actions.add(new JLabel("Partition"));
		partitions.addActionListener(event -> showPartition());
		actions.add(partitions);
		JButton back = new JButton("Back");
		back.addActionListener(event -> goBack());
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
		actions.add(back);
		actions.add(refresh);
		actions.add(extract);
		actions.add(importFile);
		actions.add(newFolder);
		actions.add(delete);
		actions.add(fileDropHint("Drag and drop PC files to import"));
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
			TreePath current = tree.getSelectionPath();
			if (!navigating && current != null && selected != null && selected.entry != null
				&& selected.entry.directory() && !current.equals(history.peekLast()))
			{
				history.addLast(current);
				if (history.size() > 100)
				{
					history.removeFirst();
				}
			}
			selectedPath.setText(selected == null || selected.entry == null
				? " " : friendlyStoragePath(selected.entry.path()));
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

	private String friendlyStoragePath(String path)
	{
		if (path == null || !path.startsWith("/") || path.length() < 17)
		{
			return path;
		}
		String profileId = path.substring(1, 17).toUpperCase(Locale.ROOT);
		if (!profileId.matches("[0-9A-F]{16}"))
		{
			return path;
		}
		return "/" + identities.name(profileId) + path.substring(17);
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

	private void goBack()
	{
		history.pollLast();
		while (!history.isEmpty())
		{
			TreePath previous = history.pollLast();
			Object last = previous.getLastPathComponent();
			if (last instanceof DefaultMutableTreeNode && ((DefaultMutableTreeNode) last).getRoot() == root)
			{
				navigating = true;
				try
				{
					tree.setSelectionPath(previous);
					tree.scrollPathToVisible(previous);
				}
				finally
				{
					navigating = false;
				}
				history.addLast(previous);
				return;
			}
		}
		TreePath current = tree.getSelectionPath();
		if (current != null && current.getParentPath() != null && current.getParentPath().getParentPath() != null)
		{
			tree.setSelectionPath(current.getParentPath());
			tree.scrollPathToVisible(current.getParentPath());
		}
	}

	private void showPartition()
	{
		history.clear();
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
		List<FatxDevice.Entry> entries = selectedEntries();
		if (entries.isEmpty())
		{
			showWarning("Select one or more files or folders to extract");
			return;
		}
		if (entries.size() == 1 && !entries.get(0).directory())
		{
			FatxDevice.Entry file = entries.get(0);
			JFileChooser chooser = new JFileChooser();
			addHistory(chooser);
			chooser.setSelectedFile(Path.of(file.name()).toFile());
			if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION)
			{
				extractWithProgress(List.of(file), List.of(chooser.getSelectedFile().toPath()));
			}
			return;
		}
		JFileChooser folderChooser = new JFileChooser();
		folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		addHistory(folderChooser);
		folderChooser.setDialogTitle(entries.size() == 1
			? "Choose where to extract the folder" : "Choose where to extract the selected items");
		if (folderChooser.showDialog(this, "Extract Here") == JFileChooser.APPROVE_OPTION)
		{
			Path base = folderChooser.getSelectedFile().toPath();
			Set<String> used = new HashSet<>();
			List<Path> targets = new ArrayList<>();
			for (FatxDevice.Entry entry : entries)
			{
				targets.add(base.resolve(uniqueName(entry.name(), used)));
			}
			extractWithProgress(entries, targets);
		}
	}

	private void extractWithProgress(List<FatxDevice.Entry> entries, List<Path> targets)
	{
		runWithProgress("Extracting", "extract Xbox storage items",
			progress -> device.extractItems(entries, targets, progress), null);
	}

	private void runWithProgress(String verb, String label, ProgressTask work, Runnable afterwards)
	{
		cancelRequested = false;
		boolean[] cancelled = {false};
		SwingUtilities.invokeLater(() -> {
			progressVerb = verb;
			estimator.reset();
			extractProgress.setIndeterminate(true);
			extractProgress.setString("");
			extractProgress.setVisible(true);
			cancelExtract.setEnabled(true);
			cancelExtract.setVisible(true);
			extractStatus.setText("Scanning selected items");
		});
		tasks.run(label, () -> {
			try
			{
				work.run((completed, total, number, count, name) -> {
					if (cancelRequested)
					{
						throw new CancellationException("Cancelled");
					}
					long now = System.nanoTime();
					if (total < 0)
					{
						if (now - lastScanUpdate < 100_000_000L)
						{
							return;
						}
						lastScanUpdate = now;
					}
					SwingUtilities.invokeLater(() -> showExtractProgress(completed, total, number, count, name));
				});
			}
			catch (CancellationException stopped)
			{
				cancelled[0] = true;
			}
			finally
			{
				SwingUtilities.invokeLater(() -> {
					extractProgress.setIndeterminate(false);
					extractProgress.setVisible(false);
					cancelExtract.setVisible(false);
					extractStatus.setText(cancelled[0] ? "Cancelled" : " ");
					if (afterwards != null)
					{
						afterwards.run();
					}
				});
			}
		});
	}

	@FunctionalInterface
	private interface ProgressTask
	{
		void run(FatxDevice.Progress progress) throws Exception;
	}

	private void showExtractProgress(long completed, long total, int number, int count, String name)
	{
		if (total < 0)
		{
			extractProgress.setIndeterminate(true);
			return;
		}
		int percent = total == 0 ? 100 : (int) Math.min(100, Math.round(completed * 100.0 / total));
		extractProgress.setIndeterminate(false);
		extractProgress.setValue(percent);
		extractProgress.setString(percent + "%");
		String past = progressVerb.equals("Importing") ? "Imported" : "Extracted";
		extractStatus.setText(count == 0 ? "No files found"
			: name.isEmpty() ? past + " " + count + " files"
			: estimator.describe(completed, total) + progressVerb + " " + number + " of " + count + ": " + name);
	}

	private static void addHistory(JFileChooser chooser)
	{
		UIManager.put("FileChooser.useShellFolder", Boolean.FALSE);
		chooser.putClientProperty("FileChooser.useShellFolder", Boolean.FALSE);
		List<File> visited = new ArrayList<>();
		int[] position = {-1};
		boolean[] moving = {false};
		JButton back = new JButton("Back");
		JButton forward = new JButton("Forward");
		Runnable refresh = () -> {
			back.setEnabled(position[0] > 0);
			forward.setEnabled(position[0] >= 0 && position[0] < visited.size() - 1);
		};
		File start = chooser.getCurrentDirectory();
		if (start != null)
		{
			visited.add(start);
			position[0] = 0;
		}
		chooser.addPropertyChangeListener(JFileChooser.DIRECTORY_CHANGED_PROPERTY, event -> {
			File current = chooser.getCurrentDirectory();
			if (moving[0] || current == null || (position[0] >= 0 && current.equals(visited.get(position[0]))))
			{
				return;
			}
			while (visited.size() > position[0] + 1)
			{
				visited.remove(visited.size() - 1);
			}
			visited.add(current);
			position[0]++;
			refresh.run();
		});
		back.addActionListener(event -> {
			moving[0] = true;
			position[0]--;
			chooser.setCurrentDirectory(visited.get(position[0]));
			moving[0] = false;
			refresh.run();
		});
		forward.addActionListener(event -> {
			moving[0] = true;
			position[0]++;
			chooser.setCurrentDirectory(visited.get(position[0]));
			moving[0] = false;
			refresh.run();
		});
		refresh.run();
		JPanel accessory = new JPanel(new GridLayout(2, 1, 0, 6));
		accessory.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
		accessory.add(back);
		accessory.add(forward);
		JPanel holder = new JPanel(new BorderLayout());
		holder.add(accessory, BorderLayout.NORTH);
		chooser.setAccessory(holder);
	}

	private List<FatxDevice.Entry> selectedEntries()
	{
		List<FatxDevice.Entry> all = new ArrayList<>();
		TreePath[] paths = tree.getSelectionPaths();
		if (paths != null)
		{
			for (TreePath path : paths)
			{
				StorageNode value = storageNode((DefaultMutableTreeNode) path.getLastPathComponent());
				if (value != null && value.entry != null)
				{
					all.add(value.entry);
				}
			}
		}
		List<FatxDevice.Entry> result = new ArrayList<>();
		for (FatxDevice.Entry entry : all)
		{
			boolean covered = all.stream().anyMatch(other -> other != entry && other.directory()
				&& other.partition() == entry.partition() && isInside(entry.path(), other.path()));
			if (!covered)
			{
				result.add(entry);
			}
		}
		return result;
	}

	private static boolean isInside(String path, String folder)
	{
		String prefix = folder.equals("/") ? "/" : folder + "/";
		return !path.equals(folder) && path.startsWith(prefix);
	}

	private static String uniqueName(String name, Set<String> used)
	{
		String clean = name.replaceAll("[\\\\/:*?\"<>|]", "_");
		String candidate = clean;
		int number = 2;
		while (!used.add(candidate.toLowerCase(Locale.ROOT)))
		{
			candidate = clean + " (" + number++ + ")";
		}
		return candidate;
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
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		chooser.setMultiSelectionEnabled(true);
		addHistory(chooser);
		chooser.setDialogTitle("Choose files or folders to import");
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			List<Path> paths = new ArrayList<>();
			for (File file : chooser.getSelectedFiles())
			{
				paths.add(file.toPath());
			}
			importPaths(directoryNode, directory, paths);
		}
	}

	private boolean importPaths(DefaultMutableTreeNode directoryNode, StorageNode directory, List<Path> paths)
	{
		if (paths.isEmpty())
		{
			return false;
		}
		String message = paths.size() == 1
			? "Import " + paths.get(0).getFileName() + " into the selected folder? Same-name files will be replaced."
			: "Import " + paths.size() + " items into the selected folder? Same-name files will be replaced.";
		if (JOptionPane.showConfirmDialog(this, message, "Import",
			JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
		{
			return false;
		}
		runWithProgress("Importing", "import Xbox storage items",
			progress -> device.importItems(directory.entry, paths, progress),
			() -> {
				directory.loaded = false;
				loadNode(directoryNode);
			});
		return true;
	}

	private boolean dropFiles(List<Path> paths)
	{
		if (paths.isEmpty() || paths.stream().anyMatch(path -> !Files.isRegularFile(path) && !Files.isDirectory(path)))
		{
			showWarning("Drop one or more PC files or folders");
			return false;
		}
		DefaultMutableTreeNode directoryNode = selectedDirectoryNode();
		StorageNode directory = storageNode(directoryNode);
		if (directory == null)
		{
			showWarning("Select a destination folder");
			return false;
		}
		return importPaths(directoryNode, directory, paths);
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
		FatxDevice.DetectedDevice previous = (FatxDevice.DetectedDevice) detected.getSelectedItem();
		detected.removeAllItems();
		for (FatxDevice.DetectedDevice device : FatxDevice.discover())
		{
			detected.addItem(device);
			if (previous != null && previous.path().equals(device.path()))
			{
				detected.setSelectedItem(device);
			}
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

	private Alias alias(String parent, FatxDevice.Entry entry)
	{
		if (!entry.directory())
		{
			return new Alias(entry.name(), null);
		}
		String name = entry.name().toUpperCase(Locale.ROOT);
		if (parent.equals("/") && name.matches("[0-9A-F]{16}")
			&& entry.partition().name().equals("Content"))
		{
			return new Alias(resolveStorageProfile(entry, name), name);
		}
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

	private String resolveStorageProfile(FatxDevice.Entry owner, String profileId)
	{
		String cached = identities.identities().find(profileId).orElse(null);
		if (cached != null || profileId.equals("0000000000000000"))
		{
			return identities.name(profileId);
		}
		Path temporary = null;
		try
		{
			FatxDevice.Entry dashboard = child(owner, "FFFE07D1", true);
			FatxDevice.Entry profileFolder = child(dashboard, "00010000", true);
			FatxDevice.Entry profile = child(profileFolder, profileId, false);
			temporary = Files.createTempFile("openrtm-storage-profile-", ".tmp");
			device.extract(profile, temporary);
			return identities.remember(temporary);
		}
		catch (IOException | RuntimeException ignored)
		{
			return "Unknown Profile";
		}
		finally
		{
			if (temporary != null)
			{
				try
				{
					Files.deleteIfExists(temporary);
				}
				catch (IOException ignored)
				{
				}
			}
		}
	}

	private FatxDevice.Entry child(FatxDevice.Entry parent, String name, boolean directory) throws IOException
	{
		return device.list(parent).stream()
			.filter(entry -> entry.directory() == directory && entry.name().equalsIgnoreCase(name))
			.findFirst()
			.orElseThrow(() -> new IOException("Profile data was not found"));
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
