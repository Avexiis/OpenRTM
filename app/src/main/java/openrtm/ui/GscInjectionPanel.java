package openrtm.ui;

import openrtm.cod.CodAdapter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class GscInjectionPanel extends FileDropPanel
{
	private final CodAdapter adapter;
	private final TaskRunner taskRunner;
	private final JTextField selectedPath = new JTextField(48);
	private final JButton inject = new JButton("Inject GSC");
	private Path selected;

	GscInjectionPanel(CodAdapter adapter, TaskRunner taskRunner)
	{
		super(new BorderLayout());
		this.adapter = adapter;
		this.taskRunner = taskRunner;
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		selectedPath.setEditable(false);
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(BorderFactory.createTitledBorder("GSC Source"));
		JPanel source = new JPanel(new BorderLayout(8, 0));
		source.setAlignmentX(Component.LEFT_ALIGNMENT);
		source.add(new JLabel("File or Project"), BorderLayout.WEST);
		source.add(selectedPath, BorderLayout.CENTER);
		JButton browse = new JButton("Browse");
		browse.addActionListener(event -> browse());
		source.add(browse, BorderLayout.EAST);
		content.add(source);
		content.add(Box.createVerticalStrut(12));
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		actions.setAlignmentX(Component.LEFT_ALIGNMENT);
		inject.setEnabled(false);
		inject.addActionListener(event -> inject());
		actions.add(inject);
		content.add(actions);
		content.add(Box.createVerticalGlue());
		add(content, BorderLayout.CENTER);
		enableFileDrop("Drop a GSC file or project folder", this::selectDropped);
	}

	private void browse()
	{
		JFileChooser chooser = selected == null ? new JFileChooser() : new JFileChooser(selected.toFile());
		chooser.setDialogTitle("Select GSC File or Project");
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter("GSC scripts (*.gsc)", "gsc"));
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			select(chooser.getSelectedFile().toPath());
		}
	}

	private boolean selectDropped(List<Path> paths)
	{
		return paths.size() == 1 && select(paths.get(0));
	}

	private boolean select(Path path)
	{
		Path normalized = path.toAbsolutePath().normalize();
		if (!Files.isDirectory(normalized) && (!Files.isRegularFile(normalized)
			|| !normalized.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gsc")))
		{
			return false;
		}
		selected = normalized;
		selectedPath.setText(normalized.toString());
		selectedPath.setCaretPosition(0);
		inject.setEnabled(true);
		return true;
	}

	private void inject()
	{
		Path source = selected;
		if (source != null)
		{
			taskRunner.run(adapter.game().tabName() + " inject GSC", () -> adapter.injectGsc(source));
		}
	}
}
