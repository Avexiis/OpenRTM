package openrtm.ui;

import openrtm.cod.CodAdapter;
import openrtm.cod.gsc.GscBatchCompiler;
import openrtm.cod.gsc.GscProjectTitle;
import openrtm.cod.gsc.GscProjects;
import openrtm.cod.gsc.Iw4GscBinary;
import openrtm.cod.gsc.Iw4GscCompiler;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class GscInjectionPanel extends FileDropPanel
{
	private final CodAdapter adapter;
	private final TaskRunner taskRunner;
	private final JTextField selectedPath = new JTextField(48);
	private final JButton inject = new JButton("Inject GSC");
	private final JButton browse = new JButton("Browse");
	private final JButton edit = new JButton("Open Editor");
	private final JButton create = new JButton("New Project");
	private final JButton export = new JButton("Export .gscbin");
	private final GscTaskProgress progress = new GscTaskProgress();
	private Path selected;
	private List<Path> selectedMenus = List.of();
	private Path outputDirectory;
	private GscEditorDialog editor;
	private boolean busy;

	GscInjectionPanel(CodAdapter adapter, TaskRunner taskRunner)
	{
		super(new BorderLayout());
		this.adapter = adapter;
		this.taskRunner = taskRunner;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		selectedPath.setEditable(false);
		JPanel content = new JPanel(new BorderLayout(0, 12));
		content.setBorder(BorderFactory.createTitledBorder("GSC Menu"));
		JPanel source = new JPanel(new BorderLayout(8, 0));
		source.add(new JLabel("File or Project"), BorderLayout.WEST);
		source.add(selectedPath, BorderLayout.CENTER);
		browse.addActionListener(event -> browse());
		browse.setIcon(GscEditorDialog.icon("folder-open", 16));
		source.add(browse, BorderLayout.EAST);
		content.add(source, BorderLayout.NORTH);
		JPanel commands = new JPanel(new BorderLayout(0, 8));
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		inject.setEnabled(false);
		inject.addActionListener(event -> inject(selected, progress, successful -> {
		}));
		actions.add(inject);
		export.setEnabled(false);
		export.setIcon(GscEditorDialog.icon("download", 16));
		export.addActionListener(event -> export());
		actions.add(export);
		commands.add(actions, BorderLayout.NORTH);
		JPanel projects = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		edit.setEnabled(false);
		edit.setIcon(GscEditorDialog.icon("braces", 16));
		edit.addActionListener(event -> openEditor(selected));
		create.setIcon(GscEditorDialog.icon("plus", 16));
		create.addActionListener(event -> newProject());
		projects.add(edit);
		projects.add(create);
		commands.add(projects, BorderLayout.CENTER);
		commands.add(progress, BorderLayout.SOUTH);
		content.add(commands, BorderLayout.SOUTH);
		add(content, BorderLayout.NORTH);
		enableFileDrop("Drop GSC menus or project folders", this::selectDropped);
	}

	private void browse()
	{
		JFileChooser chooser = selected == null ? new JFileChooser() : new JFileChooser(selected.toFile());
		chooser.setDialogTitle("Select GSC Menus or Projects");
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		chooser.setMultiSelectionEnabled(true);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter("GSC menus (*.gsc, *.gscbin)", "gsc", "gscbin"));
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			File[] files = chooser.getSelectedFiles();
			if (files.length == 0 && chooser.getSelectedFile() != null)
			{
				files = new File[] {chooser.getSelectedFile()};
			}
			selectDropped(Arrays.stream(files).map(File::toPath).toList());
		}
	}

	private boolean selectDropped(List<Path> paths)
	{
		if (busy || paths.isEmpty())
		{
			return false;
		}
		if (paths.size() == 1)
		{
			boolean accepted = select(paths.get(0));
			if (!accepted)
			{
				JOptionPane.showMessageDialog(this, "Select a .gsc file, .gscbin menu, or project folder",
					"GSC Menu", JOptionPane.INFORMATION_MESSAGE);
			}
			return accepted;
		}
		try
		{
			if (paths.stream().anyMatch(path -> !Files.isDirectory(path) && Iw4GscBinary.isCompiled(path)))
			{
				throw new IllegalArgumentException("Select one compiled menu for injection, or GSC source menus for compilation");
			}
			selectedMenus = GscBatchCompiler.sources(paths);
			selected = null;
			selectedPath.setText(selectedMenus.size() + " menus selected");
			selectedPath.setCaretPosition(0);
			setBusy(false);
			SwingUtilities.invokeLater(this::compileMenus);
			return true;
		}
		catch (IllegalArgumentException failure)
		{
			JOptionPane.showMessageDialog(this, failure.getMessage(), "Compile Menus", JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
	}

	private boolean select(Path path)
	{
		if (busy)
		{
			return false;
		}
		Path normalized = path.toAbsolutePath().normalize();
		if (!Files.isDirectory(normalized) && (!Files.isRegularFile(normalized)
			|| !(normalized.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gsc")
				|| Iw4GscBinary.isCompiled(normalized))))
		{
			return false;
		}
		selected = normalized;
		selectedMenus = List.of(normalized);
		selectedPath.setText(normalized.toString());
		selectedPath.setCaretPosition(0);
		setBusy(false);
		return true;
	}

	private void openEditor(Path source)
	{
		if (source == null || busy || Iw4GscBinary.isCompiled(source))
		{
			return;
		}
		if (editor == null || !editor.isDisplayable())
		{
			editor = new GscEditorDialog(this, source, this::inject, this::select);
		}
		else
		{
			editor.openProject(source);
		}
		editor.setVisible(true);
		editor.toFront();
	}

	private void newProject()
	{
		String name = JOptionPane.showInputDialog(this, "Project name", "New GSC Project", JOptionPane.PLAIN_MESSAGE);
		if (name == null)
		{
			return;
		}
		try
		{
			Path project = GscProjects.create(name);
			select(project);
			openEditor(project);
		}
		catch (Exception failure)
		{
			JOptionPane.showMessageDialog(this, failure.getMessage(), "New GSC Project", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void inject(Path source, GscTaskProgress display, Consumer<Boolean> finished)
	{
		if (source == null || busy)
		{
			if (busy)
			{
				JOptionPane.showMessageDialog(this, "Another GSC task is running. Wait for it to finish.",
					"GSC", JOptionPane.INFORMATION_MESSAGE);
			}
			finished.accept(false);
			return;
		}
		setBusy(true);
		progress.begin("Preparing injection");
		if (display != progress)
		{
			display.begin("Preparing injection");
		}
		taskRunner.run(adapter.game().tabName() + " inject GSC", () -> {
			boolean successful = false;
			try
			{
				adapter.injectGsc(source, (completed, total, message) -> {
					progress.update(completed, total, message);
					if (display != progress)
					{
						display.update(completed, total, message);
					}
				});
				successful = true;
			}
			finally
			{
				boolean result = successful;
				SwingUtilities.invokeLater(() -> {
					progress.finish(result);
					setBusy(false);
					finished.accept(result);
				});
			}
		});
	}

	private void export()
	{
		if (selected == null && !selectedMenus.isEmpty())
		{
			compileMenus();
			return;
		}
		if (selected == null || busy || Iw4GscBinary.isCompiled(selected))
		{
			return;
		}
		Path source = selected;
		Path root = Files.isDirectory(source) ? source : source.getParent();
		JFileChooser chooser = new JFileChooser(root.toFile());
		chooser.setDialogTitle("Export Compiled GSC");
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter("Compiled GSC (*.gscbin)", "gscbin"));
		String title = GscProjectTitle.read(source).replaceAll("[\\\\/:*?\"<>|]", "_");
		chooser.setSelectedFile(root.resolve(title + ".gscbin").toFile());
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		Path target = chooser.getSelectedFile().toPath();
		if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gscbin"))
		{
			target = target.resolveSibling(target.getFileName() + ".gscbin");
		}
		if (Files.exists(target) && JOptionPane.showConfirmDialog(this, "Replace " + target.getFileName() + "?",
			"Export GSC", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
		{
			return;
		}
		Path destination = target;
		setBusy(true);
		progress.begin("Compiling project");
		taskRunner.run("Export GSC", () -> {
			boolean successful = false;
			try
			{
				byte[] binary = Iw4GscCompiler.compile(source,
					(percent, message) -> progress.update(percent * 95L / 100, 100, message)).gscbin();
				progress.update(98, 100, "Saving compiled file");
				GscEditorDialog.writeAtomic(destination, binary);
				successful = true;
			}
			finally
			{
				boolean result = successful;
				SwingUtilities.invokeLater(() -> {
					progress.finish(result);
					setBusy(false);
				});
			}
		});
	}

	private void compileMenus()
	{
		if (busy || selectedMenus.isEmpty())
		{
			return;
		}
		List<Path> sources = selectedMenus;
		Path first = sources.get(0);
		Path initial = outputDirectory;
		if (initial == null)
		{
			initial = Files.isDirectory(first) ? first : first.getParent();
		}
		JFileChooser chooser = new JFileChooser(initial.toFile());
		chooser.setDialogTitle("Select Compiled Menu Output Folder");
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setApproveButtonText("Compile");
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		Path destination = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
		outputDirectory = destination;
		setBusy(true);
		progress.begin("Compiling menus");
		taskRunner.run("Compile GSC Menus", () -> {
			GscBatchCompiler.Result result = null;
			try
			{
				result = GscBatchCompiler.compile(sources, destination,
					(percent, message) -> progress.update(percent, 100, message));
			}
			finally
			{
				GscBatchCompiler.Result outcome = result;
				SwingUtilities.invokeLater(() -> {
					if (outcome == null)
					{
						progress.finish(false);
					}
					else
					{
						progress.finish(true, outcome.compiled().size() + " menus saved; " + outcome.failures().size() + " failed");
					}
					setBusy(false);
					if (outcome != null)
					{
						showCompiledMenus(destination, outcome);
					}
				});
			}
		});
	}

	private void showCompiledMenus(Path directory, GscBatchCompiler.Result result)
	{
		StringBuilder summary = new StringBuilder("Compiled " + result.compiled().size() + " of "
			+ (result.compiled().size() + result.failures().size()) + " menus.\nOutput folder: " + directory);
		for (Path file : result.compiled())
		{
			summary.append("\n\nSaved: ").append(file.getFileName());
		}
		for (GscBatchCompiler.Failure failure : result.failures())
		{
			summary.append("\n\nFailed: ").append(failure.source()).append("\n").append(failure.message());
		}
		JTextArea details = new JTextArea(summary.toString(), 12, 64);
		details.setEditable(false);
		details.setLineWrap(true);
		details.setWrapStyleWord(true);
		details.setCaretPosition(0);
		JOptionPane.showMessageDialog(this, new JScrollPane(details), "Compiled Menus",
			result.failures().isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
	}

	private void setBusy(boolean value)
	{
		busy = value;
		browse.setEnabled(!value);
		create.setEnabled(!value);
		inject.setEnabled(!value && selected != null);
		boolean source = selected != null && !Iw4GscBinary.isCompiled(selected);
		edit.setEnabled(!value && source);
		export.setText(selected == null && !selectedMenus.isEmpty() ? "Compile Menus" : "Export .gscbin");
		export.setEnabled(!value && (source || selected == null && !selectedMenus.isEmpty()));
	}

	@Override
	protected boolean acceptsDrop()
	{
		return !busy;
	}
}
