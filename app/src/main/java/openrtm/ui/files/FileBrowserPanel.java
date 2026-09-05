package openrtm.ui.files;

import openrtm.console.ConsoleService;
import openrtm.ui.TaskRunner;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public final class FileBrowserPanel extends JPanel {
    private static final Color LINE = new Color(55, 60, 66);
    private static final Color ACCENT = new Color(91, 141, 239);

    private final ConsoleService service;
    private final TaskRunner tasks;
    private final DefaultMutableTreeNode localRoot = new DefaultMutableTreeNode(BrowserNode.localRoot());
    private final DefaultMutableTreeNode remoteRoot = new DefaultMutableTreeNode(BrowserNode.remoteRoot());
    private final DefaultTreeModel localModel = new DefaultTreeModel(localRoot);
    private final DefaultTreeModel remoteModel = new DefaultTreeModel(remoteRoot);
    private final JTree localTree = new JTree(localModel);
    private final JTree remoteTree = new JTree(remoteModel);
    private final JLabel localPath = new JLabel(" ");
    private final JLabel remotePath = new JLabel(" ");
    private final JProgressBar transferProgress = new JProgressBar(0, 100);
    private final JLabel transferStatus = new JLabel(" ");
    private final JButton cancelTransfer = button("Cancel Transfer");
    private boolean transferActive;

    public FileBrowserPanel(ConsoleService service, TaskRunner tasks) {
        super(new BorderLayout(10, 10));
        this.service = service;
        this.tasks = tasks;
        setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));

        configureTree(localTree, true);
        configureTree(remoteTree, false);
        reloadLocalRoots();
        setRemoteMessage("No console drives loaded");

        add(toolbar(), BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                browserSide("PC", localTree, localPath),
                browserSide("Console HDD", remoteTree, remotePath));
        split.setResizeWeight(0.5);
        split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);
    }

    private JPanel toolbar() {
        JPanel panel = new JPanel(new BorderLayout(8, 2));
        panel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

        JButton refreshLocal = button("Refresh PC");
        JButton refreshRemote = button("Refresh Console");
        JButton upload = button("Upload ->");
        JButton download = button("<- Download");
        JButton newFolder = button("New Folder");
        JButton delete = button("Delete Remote");

        refreshLocal.addActionListener(e -> refreshSelectedLocal());
        refreshRemote.addActionListener(e -> refreshSelectedRemote());
        upload.addActionListener(e -> uploadSelected());
        download.addActionListener(e -> downloadSelected());
        newFolder.addActionListener(e -> createRemoteFolder());
        delete.addActionListener(e -> deleteSelectedRemote());
        cancelTransfer.addActionListener(e -> service.cancelFileTransfer());
        cancelTransfer.setEnabled(false);

        actions.add(refreshLocal);
        actions.add(refreshRemote);
        actions.add(upload);
        actions.add(download);
        actions.add(newFolder);
        actions.add(delete);
        actions.add(cancelTransfer);

        transferProgress.setStringPainted(true);
        transferProgress.setVisible(false);
        JPanel progress = new JPanel(new BorderLayout(8, 0));
        progress.add(transferStatus, BorderLayout.CENTER);
        progress.add(transferProgress, BorderLayout.EAST);

        panel.add(actions, BorderLayout.NORTH);
        panel.add(progress, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel browserSide(String title, JTree tree, JLabel pathLabel) {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(ACCENT);
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(new JScrollPane(tree), BorderLayout.CENTER);
        pathLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        panel.add(pathLabel, BorderLayout.SOUTH);
        return panel;
    }

    private void configureTree(JTree tree, boolean local) {
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new BrowserTreeCellRenderer());
        tree.addTreeWillExpandListener(new TreeWillExpandListener() {
            @Override
            public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
                DefaultMutableTreeNode node = treeNode(event.getPath());
                if (local) loadLocalNode(node);
                else loadRemoteNode(node);
            }

            @Override
            public void treeWillCollapse(TreeExpansionEvent event) {
            }
        });
        tree.addTreeSelectionListener(this::updateSelectedPaths);
    }

    private void reloadLocalRoots() {
        localRoot.removeAllChildren();
        Set<Path> seen = new HashSet<>();
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        if (seen.add(home)) {
            localRoot.add(newNode(BrowserNode.local("Home", home, true)));
        }
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                Path path = root.toPath().toAbsolutePath().normalize();
                if (seen.add(path)) {
                    localRoot.add(newNode(BrowserNode.local(root.getPath(), path, true)));
                }
            }
        }
        localModel.reload();
    }

    private void setRemoteMessage(String message) {
        remoteRoot.removeAllChildren();
        remoteRoot.add(new DefaultMutableTreeNode(BrowserNode.placeholder(message)));
        remoteModel.reload();
    }

    private void refreshSelectedLocal() {
        DefaultMutableTreeNode selected = selectedNode(localTree);
        BrowserNode entry = browserNode(selected);
        if (selected == null || entry == null || !entry.localEntry()) {
            reloadLocalRoots();
            return;
        }
        DefaultMutableTreeNode directory = entry.directory() ? selected : parentNode(selected);
        if (directory == null) {
            reloadLocalRoots();
            return;
        }
        BrowserNode directoryEntry = browserNode(directory);
        directoryEntry.loaded(false);
        loadLocalNode(directory);
    }

    private void refreshSelectedRemote() {
        DefaultMutableTreeNode selected = selectedNode(remoteTree);
        BrowserNode entry = browserNode(selected);
        if (selected == null || entry == null || !entry.remoteEntry()) {
            refreshConsoleDrives();
            return;
        }
        DefaultMutableTreeNode directory = entry.directory() ? selected : parentNode(selected);
        if (directory == null || directory == remoteRoot) {
            refreshConsoleDrives();
            return;
        }
        BrowserNode directoryEntry = browserNode(directory);
        directoryEntry.loaded(false);
        loadRemoteNode(directory);
    }

    public void refreshConsoleDrives() {
        tasks.run("refresh console drives", () -> {
            List<String> drives = service.listDrives();
            List<DefaultMutableTreeNode> children = new ArrayList<>();
            for (String drive : drives) {
                children.add(newNode(BrowserNode.remote(drive, drive, true, 0)));
            }
            SwingUtilities.invokeLater(() -> {
                remoteRoot.removeAllChildren();
                if (children.isEmpty()) {
                    remoteRoot.add(new DefaultMutableTreeNode(BrowserNode.placeholder("No drives returned")));
                } else {
                    children.forEach(remoteRoot::add);
                }
                remoteModel.reload();
            });
        });
    }

    private void loadLocalNode(DefaultMutableTreeNode node) {
        BrowserNode directory = browserNode(node);
        if (directory == null || !directory.loadableLocalDirectory() || directory.loaded()) return;
        directory.loaded(true);
        tasks.run("load PC folder", () -> {
            List<DefaultMutableTreeNode> children = localChildren(directory.localPath());
            SwingUtilities.invokeLater(() -> replaceChildren(localModel, node, directory, children));
        });
    }

    private void loadRemoteNode(DefaultMutableTreeNode node) {
        BrowserNode directory = browserNode(node);
        if (directory == null || !directory.loadableRemoteDirectory() || directory.loaded()) return;
        directory.loaded(true);
        tasks.run("load console folder", () -> {
            List<DefaultMutableTreeNode> children = remoteChildren(directory);
            SwingUtilities.invokeLater(() -> replaceChildren(remoteModel, node, directory, children));
        });
    }

    private List<DefaultMutableTreeNode> localChildren(Path directory) throws IOException {
        List<Path> paths;
        try (Stream<Path> stream = Files.list(directory)) {
            paths = stream.sorted(Comparator
                    .comparing((Path path) -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .thenComparing(path -> fileName(path).toLowerCase(Locale.ROOT)))
                    .toList();
        }

        List<DefaultMutableTreeNode> children = new ArrayList<>();
        for (Path path : paths) {
            boolean directoryEntry = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
            children.add(newNode(BrowserNode.local(fileName(path), path, directoryEntry)));
        }
        return children;
    }

    private List<DefaultMutableTreeNode> remoteChildren(BrowserNode directory) {
        String parentPath = ensureRemoteDirectory(directory.remotePath());
        List<ConsoleService.FileEntry> entries = new ArrayList<>(service.listDirectory(parentPath));
        entries.sort(Comparator
                .comparing((ConsoleService.FileEntry entry) -> !entry.directory())
                .thenComparing(entry -> displayRemoteName(entry.name()).toLowerCase(Locale.ROOT)));

        List<DefaultMutableTreeNode> children = new ArrayList<>();
        for (ConsoleService.FileEntry entry : entries) {
            String displayName = displayRemoteName(entry.name());
            String path = childRemotePath(parentPath, entry.name());
            children.add(newNode(BrowserNode.remote(displayName, path, entry.directory(), entry.size())));
        }
        return children;
    }

    private void replaceChildren(DefaultTreeModel model, DefaultMutableTreeNode parent, BrowserNode parentEntry, List<DefaultMutableTreeNode> children) {
        parent.removeAllChildren();
        if (children.isEmpty()) {
            parent.add(new DefaultMutableTreeNode(BrowserNode.placeholder("Empty")));
        } else {
            children.forEach(parent::add);
        }
        parentEntry.loaded(true);
        model.reload(parent);
    }

    private void uploadSelected() {
        if (transferActive) {
            showSelectionError("A file transfer is already running");
            return;
        }
        DefaultMutableTreeNode localNode = selectedNode(localTree);
        BrowserNode localEntry = browserNode(localNode);
        if (localEntry == null || !localEntry.localEntry()) {
            showSelectionError("Select a local file or folder");
            return;
        }

        DefaultMutableTreeNode remoteDirectoryNode = selectedRemoteDirectoryNode();
        BrowserNode remoteDirectory = browserNode(remoteDirectoryNode);
        if (remoteDirectory == null) {
            showSelectionError("Select a console folder");
            return;
        }

        Path localName = localEntry.localPath().getFileName();
        if (localName == null) {
            showSelectionError("Filesystem roots cannot be uploaded as folders");
            return;
        }

        String remotePath = childRemotePath(ensureRemoteDirectory(remoteDirectory.remotePath()), localName.toString());
        String label = localEntry.directory() ? "upload folder" : "upload file";
        beginTransfer("Preparing " + label);
        tasks.run(label, () -> {
            try {
                if (localEntry.directory()) {
                    service.uploadDirectory(localEntry.localPath(), remotePath, this::updateTransferProgress);
                } else {
                    service.uploadFile(localEntry.localPath(), remotePath, this::updateTransferProgress);
                }
                List<DefaultMutableTreeNode> children = remoteChildren(remoteDirectory);
                SwingUtilities.invokeLater(() -> replaceChildren(remoteModel, remoteDirectoryNode, remoteDirectory, children));
            } finally {
                SwingUtilities.invokeLater(this::finishTransfer);
            }
        });
    }

    private void updateTransferProgress(long completed, long total, String message) {
        SwingUtilities.invokeLater(() -> {
            boolean enumerating = total < 0;
            transferProgress.setIndeterminate(enumerating);
            if (enumerating) {
                transferProgress.setString("");
            } else {
                int percent = total == 0 ? 100 : (int) Math.min(100, Math.round((completed * 100.0) / total));
                transferProgress.setValue(percent);
                transferProgress.setString(percent + "%");
            }
            transferStatus.setText(message);
        });
    }

    private void downloadSelected() {
        if (transferActive) {
            showSelectionError("A file transfer is already running");
            return;
        }
        DefaultMutableTreeNode remoteNode = selectedNode(remoteTree);
        BrowserNode remoteEntry = browserNode(remoteNode);
        if (remoteEntry == null || !remoteEntry.remoteEntry()) {
            showSelectionError("Select a console file or folder");
            return;
        }
        if (remoteEntry.directory() && parentNode(remoteNode) == remoteRoot) {
            showSelectionError("Select a folder inside a console drive");
            return;
        }

        DefaultMutableTreeNode localDirectoryNode = selectedLocalDirectoryNode();
        BrowserNode localDirectory = browserNode(localDirectoryNode);
        if (localDirectory == null) {
            showSelectionError("Select a PC folder");
            return;
        }

        Path destination = localDirectory.localPath().resolve(displayRemoteName(remoteEntry.remotePath()));
        if (!confirmOverwrite(destination)) return;

        String label = remoteEntry.directory() ? "download folder" : "download file";
        beginTransfer("Preparing " + label);
        tasks.run(label, () -> {
            try {
                if (remoteEntry.directory()) {
                    service.downloadDirectory(remoteEntry.remotePath(), destination, this::updateTransferProgress);
                } else {
                    service.downloadFile(remoteEntry.remotePath(), destination, this::updateTransferProgress);
                }
                List<DefaultMutableTreeNode> children = localChildren(localDirectory.localPath());
                SwingUtilities.invokeLater(() -> replaceChildren(localModel, localDirectoryNode, localDirectory, children));
            } finally {
                SwingUtilities.invokeLater(this::finishTransfer);
            }
        });
    }

    private void beginTransfer(String message) {
        transferActive = true;
        cancelTransfer.setEnabled(true);
        transferProgress.setIndeterminate(false);
        transferProgress.setValue(0);
        transferProgress.setString("0%");
        transferProgress.setVisible(true);
        transferStatus.setText(message);
    }

    private void finishTransfer() {
        transferActive = false;
        cancelTransfer.setEnabled(false);
        transferProgress.setIndeterminate(false);
        transferProgress.setVisible(false);
        transferStatus.setText(" ");
    }

    private void createRemoteFolder() {
        DefaultMutableTreeNode remoteDirectoryNode = selectedRemoteDirectoryNode();
        BrowserNode remoteDirectory = browserNode(remoteDirectoryNode);
        if (remoteDirectory == null) {
            showSelectionError("Select a console folder");
            return;
        }

        String name = JOptionPane.showInputDialog(this, "Folder name", "");
        if (name == null) return;
        String cleanName = name.trim();
        if (cleanName.isBlank() || cleanName.contains("\\") || cleanName.contains("/")) {
            showSelectionError("Folder name cannot be blank or contain path separators");
            return;
        }

        String remoteFolderPath = childRemotePath(ensureRemoteDirectory(remoteDirectory.remotePath()), cleanName);
        tasks.run("create console folder", () -> {
            service.makeDirectory(remoteFolderPath);
            List<DefaultMutableTreeNode> children = remoteChildren(remoteDirectory);
            SwingUtilities.invokeLater(() -> replaceChildren(remoteModel, remoteDirectoryNode, remoteDirectory, children));
        });
    }

    private void deleteSelectedRemote() {
        DefaultMutableTreeNode remoteNode = selectedNode(remoteTree);
        BrowserNode remoteEntry = browserNode(remoteNode);
        if (remoteEntry == null || !remoteEntry.remoteEntry()) {
            showSelectionError("Select a console file or folder");
            return;
        }
        if (parentNode(remoteNode) == remoteRoot) {
            showSelectionError("Drive roots cannot be deleted from the browser");
            return;
        }
        if (JOptionPane.showConfirmDialog(this, remoteEntry.remotePath(), "Delete Remote", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }

        DefaultMutableTreeNode parentNode = parentNode(remoteNode);
        BrowserNode parentEntry = browserNode(parentNode);
        tasks.run("delete remote path", () -> {
            service.deletePath(remoteEntry.remotePath(), remoteEntry.directory());
            if (parentNode != null && parentNode != remoteRoot && parentEntry != null) {
                List<DefaultMutableTreeNode> children = remoteChildren(parentEntry);
                SwingUtilities.invokeLater(() -> replaceChildren(remoteModel, parentNode, parentEntry, children));
            } else {
                SwingUtilities.invokeLater(this::refreshConsoleDrives);
            }
        });
    }

    private DefaultMutableTreeNode selectedLocalDirectoryNode() {
        DefaultMutableTreeNode selected = selectedNode(localTree);
        BrowserNode entry = browserNode(selected);
        if (entry == null || !entry.localEntry()) return null;
        return entry.directory() ? selected : parentNode(selected);
    }

    private DefaultMutableTreeNode selectedRemoteDirectoryNode() {
        DefaultMutableTreeNode selected = selectedNode(remoteTree);
        BrowserNode entry = browserNode(selected);
        if (entry == null || !entry.remoteEntry()) return null;
        return entry.directory() ? selected : parentNode(selected);
    }

    private void updateSelectedPaths(TreeSelectionEvent ignored) {
        BrowserNode local = browserNode(selectedNode(localTree));
        BrowserNode remote = browserNode(selectedNode(remoteTree));
        localPath.setText(local != null && local.localEntry() ? local.localPath().toString() : " ");
        remotePath.setText(remote != null && remote.remoteEntry() ? remote.remotePath() : " ");
    }

    private boolean confirmOverwrite(Path destination) {
        if (!Files.exists(destination)) return true;
        return JOptionPane.showConfirmDialog(this, destination + " already exists", "Overwrite", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }

    private void showSelectionError(String message) {
        JOptionPane.showMessageDialog(this, message, "Files", JOptionPane.WARNING_MESSAGE);
    }

    private static JButton button(String label) {
        return new JButton(label);
    }

    private static DefaultMutableTreeNode newNode(BrowserNode entry) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(entry);
        if (entry.loadableDirectory()) {
            node.add(new DefaultMutableTreeNode(BrowserNode.placeholder("Loading")));
        }
        return node;
    }

    private static DefaultMutableTreeNode treeNode(TreePath path) {
        return (DefaultMutableTreeNode) path.getLastPathComponent();
    }

    private static DefaultMutableTreeNode selectedNode(JTree tree) {
        TreePath path = tree.getSelectionPath();
        return path == null ? null : treeNode(path);
    }

    private static DefaultMutableTreeNode parentNode(DefaultMutableTreeNode node) {
        if (node == null || !(node.getParent() instanceof DefaultMutableTreeNode parent)) return null;
        return parent;
    }

    private static BrowserNode browserNode(DefaultMutableTreeNode node) {
        if (node == null || !(node.getUserObject() instanceof BrowserNode entry)) return null;
        return entry;
    }

    private static String fileName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private static String ensureRemoteDirectory(String path) {
        String normalized = path == null ? "" : path.trim().replace('/', '\\');
        if (normalized.endsWith("\\")) return normalized;
        return normalized + "\\";
    }

    private static String childRemotePath(String parent, String child) {
        String normalizedChild = child == null ? "" : child.trim().replace('/', '\\');
        if (normalizedChild.matches("(?i)^[A-Za-z0-9]+:\\\\.*")) return normalizedChild;
        return ensureRemoteDirectory(parent) + displayRemoteName(normalizedChild);
    }

    private static String displayRemoteName(String path) {
        String normalized = path == null ? "" : path.trim().replace('/', '\\');
        int slash = normalized.lastIndexOf('\\');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static final class BrowserTreeCellRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            BrowserNode entry = browserNode((DefaultMutableTreeNode) value);
            if (entry != null && !entry.placeholder()) {
                if (entry.root()) {
                    setIcon(UIManager.getIcon("FileView.computerIcon"));
                } else if (entry.directory()) {
                    setIcon(UIManager.getIcon("FileView.directoryIcon"));
                } else {
                    setIcon(UIManager.getIcon("FileView.fileIcon"));
                }
            }
            return this;
        }
    }

    private static final class BrowserNode {
        private final NodeType type;
        private final String name;
        private final Path localPath;
        private final String remotePath;
        private final boolean directory;
        private final long size;
        private boolean loaded;

        private BrowserNode(NodeType type, String name, Path localPath, String remotePath, boolean directory, long size, boolean loaded) {
            this.type = type;
            this.name = name;
            this.localPath = localPath;
            this.remotePath = remotePath;
            this.directory = directory;
            this.size = size;
            this.loaded = loaded;
        }

        static BrowserNode localRoot() {
            return new BrowserNode(NodeType.LOCAL_ROOT, "PC", null, null, true, 0, true);
        }

        static BrowserNode remoteRoot() {
            return new BrowserNode(NodeType.REMOTE_ROOT, "Console", null, null, true, 0, true);
        }

        static BrowserNode local(String name, Path path, boolean directory) {
            return new BrowserNode(NodeType.LOCAL_ENTRY, name, path, null, directory, 0, !directory);
        }

        static BrowserNode remote(String name, String path, boolean directory, long size) {
            return new BrowserNode(NodeType.REMOTE_ENTRY, name, null, path, directory, size, !directory);
        }

        static BrowserNode placeholder(String name) {
            return new BrowserNode(NodeType.PLACEHOLDER, name, null, null, false, 0, true);
        }

        boolean root() {
            return type == NodeType.LOCAL_ROOT || type == NodeType.REMOTE_ROOT;
        }

        boolean placeholder() {
            return type == NodeType.PLACEHOLDER;
        }

        boolean localEntry() {
            return type == NodeType.LOCAL_ENTRY;
        }

        boolean remoteEntry() {
            return type == NodeType.REMOTE_ENTRY;
        }

        boolean directory() {
            return directory;
        }

        boolean loaded() {
            return loaded;
        }

        void loaded(boolean loaded) {
            this.loaded = loaded;
        }

        Path localPath() {
            return localPath;
        }

        String remotePath() {
            return remotePath;
        }

        boolean loadableDirectory() {
            return loadableLocalDirectory() || loadableRemoteDirectory();
        }

        boolean loadableLocalDirectory() {
            return localEntry() && directory && localPath != null;
        }

        boolean loadableRemoteDirectory() {
            return remoteEntry() && directory && remotePath != null;
        }

        @Override
        public String toString() {
            if (remoteEntry() && !directory && size > 0) {
                return name + "  " + readableSize(size);
            }
            return name;
        }

        private static String readableSize(long size) {
            if (size < 1024) return size + " B";
            if (size < 1024L * 1024L) return String.format(Locale.ROOT, "%.1f KB", size / 1024.0);
            if (size < 1024L * 1024L * 1024L) return String.format(Locale.ROOT, "%.1f MB", size / (1024.0 * 1024.0));
            return String.format(Locale.ROOT, "%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private enum NodeType {
        LOCAL_ROOT,
        LOCAL_ENTRY,
        REMOTE_ROOT,
        REMOTE_ENTRY,
        PLACEHOLDER
    }
}
