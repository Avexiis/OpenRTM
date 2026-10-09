package openrtm.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import openrtm.cod.gsc.GscLanguageSupport;
import openrtm.cod.gsc.GscLanguageSupport.Diagnostic;
import openrtm.cod.gsc.GscProjects;
import openrtm.cod.gsc.GscProjectTitle;
import openrtm.cod.gsc.Iw4GscCompiler;
import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.ShorthandCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxDocument;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Style;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenMaker;
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory;
import org.fife.ui.rsyntaxtextarea.folding.CurlyFoldParser;
import org.fife.ui.rsyntaxtextarea.folding.FoldParserManager;
import org.fife.ui.rsyntaxtextarea.parser.AbstractParser;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParseResult;
import org.fife.ui.rsyntaxtextarea.parser.DefaultParserNotice;
import org.fife.ui.rsyntaxtextarea.parser.ParseResult;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.fife.ui.rtextarea.SearchContext;
import org.fife.ui.rtextarea.SearchEngine;
import org.fife.ui.rtextarea.SearchResult;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class GscEditorDialog extends JDialog
{
	@FunctionalInterface
	interface Injection
	{
		void start(Path source, GscTaskProgress progress, Consumer<Boolean> finished);
	}

	private static final String STYLE = "text/openrtm-gsc";
	private final Injection injection;
	private final Consumer<Path> selectedProject;
	private final Map<Path, SourceFile> files = new LinkedHashMap<>();
	private final DefaultListModel<Path> fileModel = new DefaultListModel<>();
	private final JList<Path> fileList = new JList<>(fileModel);
	private final DefaultListModel<Diagnostic> problems = new DefaultListModel<>();
	private final JList<Diagnostic> problemList = new JList<>(problems);
	private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
	private final JTextField find = new JTextField(20);
	private final JTextField replacement = new JTextField(16);
	private final JCheckBox matchCase = new JCheckBox("Case");
	private final JCheckBox regex = new JCheckBox("Regex");
	private final JLabel status = new JLabel(" ");
	private final JLabel position = new JLabel(" ");
	private final JComboBox<String> functions = new JComboBox<>();
	private final GscTaskProgress progress = new GscTaskProgress();
	private final List<JButton> taskButtons = new ArrayList<>();
	private final Timer checkTimer = new Timer(800, event -> checkProject());
	private SwingWorker<CheckResult, Void> checkWorker;
	private Path selected;
	private Path root;
	private long revision;
	private boolean busy;
	private boolean updatingFunctions;

	GscEditorDialog(Component parent, Path selected, Injection injection, Consumer<Path> selectedProject)
	{
		super(SwingUtilities.getWindowAncestor(parent), "GSC Editor", ModalityType.MODELESS);
		this.injection = injection;
		this.selectedProject = selectedProject;
		TokenMakerFactory.setDefaultInstance(new TokenMakerFactory()
		{
			@Override
			protected TokenMaker getTokenMakerImpl(String key)
			{
				return new GscTokenMaker();
			}

			@Override
			public Set<String> keySet()
			{
				return Set.of(STYLE);
			}
		});
		FoldParserManager.get().addFoldParserMapping(STYLE, new CurlyFoldParser());
		checkTimer.setRepeats(false);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent event)
			{
				if (!busy && confirmSaved())
				{
					dispose();
				}
			}
		});
		setContentPane(content());
		ApplicationIcon.apply(this);
		setMinimumSize(new Dimension(800, 540));
		setSize(new Dimension(1100, 780));
		setLocationRelativeTo(parent);
		loadProject(selected);
	}

	private JPanel content()
	{
		JPanel content = new JPanel(new BorderLayout(8, 8));
		content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		toolbar.add(tool("folder-open", "Open project", this::chooseProject));
		toolbar.add(tool("plus", "New project", this::newProject));
		toolbar.add(tool("file-plus", "New GSC file", this::newFile));
		toolbar.add(tool("save", "Save file", () -> save(current())));
		toolbar.add(tool("files", "Save all", this::saveAll));
		toolbar.add(tool("undo-2", "Undo", () -> {
			SourceFile file = current();
			if (file != null)
			{
				file.editor.undoLastAction();
			}
		}));
		toolbar.add(tool("redo-2", "Redo", () -> {
			SourceFile file = current();
			if (file != null)
			{
				file.editor.redoLastAction();
			}
		}));
		toolbar.add(tool("braces", "Format file", this::format));
		toolbar.add(tool("list-checks", "Check project", this::checkProject));
		toolbar.add(tool("download", "Export .gscbin", this::export));
		JButton inject = new JButton("Inject GSC");
		inject.addActionListener(event -> inject());
		taskButtons.add(inject);
		toolbar.add(inject);
		functions.setPreferredSize(new Dimension(180, 30));
		functions.setToolTipText("Go to function");
		functions.addActionListener(event -> goToFunction());
		toolbar.add(functions);
		content.add(toolbar, BorderLayout.NORTH);

		fileList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		fileList.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused)
			{
				JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
				if (value instanceof Path path && root != null)
				{
					label.setText(root.relativize(path).toString());
					label.setIcon(UIManager.getIcon("FileView.fileIcon"));
				}
				return label;
			}
		});
		fileList.addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting() && fileList.getSelectedValue() != null)
			{
				openFile(fileList.getSelectedValue());
			}
		});
		tabs.addChangeListener(event -> updateFunctions());
		JPanel editors = new JPanel(new BorderLayout(0, 6));
		editors.add(searchBar(), BorderLayout.NORTH);
		editors.add(tabs, BorderLayout.CENTER);
		JSplitPane horizontal = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(fileList), editors);
		horizontal.setResizeWeight(0);
		horizontal.setDividerLocation(200);
		horizontal.setBorder(null);
		problemList.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused)
			{
				JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
				if (value instanceof Diagnostic issue)
				{
					label.setText(issue.file() + ":" + issue.line() + ":" + issue.column() + "  " + issue.message());
				}
				return label;
			}
		});
		problemList.addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting())
			{
				goToProblem(problemList.getSelectedValue());
			}
		});
		JScrollPane problemScroll = new JScrollPane(problemList);
		problemScroll.setBorder(BorderFactory.createTitledBorder("Problems"));
		problemScroll.setMinimumSize(new Dimension(0, 65));
		JSplitPane vertical = new JSplitPane(JSplitPane.VERTICAL_SPLIT, horizontal, problemScroll);
		vertical.setResizeWeight(0.82);
		vertical.setDividerLocation(510);
		vertical.setBorder(null);
		content.add(vertical, BorderLayout.CENTER);
		JPanel footer = new JPanel(new BorderLayout(8, 4));
		footer.add(progress, BorderLayout.NORTH);
		footer.add(status, BorderLayout.CENTER);
		footer.add(position, BorderLayout.EAST);
		content.add(footer, BorderLayout.SOUTH);
		bind(KeyEvent.VK_S, false, this::saveAll);
		bind(KeyEvent.VK_F, false, find::requestFocusInWindow);
		bind(KeyEvent.VK_H, false, replacement::requestFocusInWindow);
		bind(KeyEvent.VK_F, true, this::format);
		return content;
	}

	private JPanel searchBar()
	{
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints cell = new GridBagConstraints();
		cell.insets = new Insets(2, 2, 2, 2);
		cell.anchor = GridBagConstraints.WEST;
		cell.gridx = 0;
		cell.gridy = 0;
		panel.add(new JLabel("Find"), cell);
		cell.gridx = 1;
		cell.weightx = 1;
		cell.fill = GridBagConstraints.HORIZONTAL;
		find.setMinimumSize(new Dimension(80, 28));
		replacement.setMinimumSize(new Dimension(80, 28));
		panel.add(find, cell);
		cell.gridx = 2;
		cell.weightx = 0;
		cell.fill = GridBagConstraints.NONE;
		JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
		options.add(tool("chevron-up", "Previous match", () -> search(false, 0)));
		options.add(tool("chevron-down", "Next match", () -> search(true, 0)));
		options.add(matchCase);
		options.add(regex);
		panel.add(options, cell);
		cell.gridx = 0;
		cell.gridy = 1;
		panel.add(new JLabel("Replace"), cell);
		cell.gridx = 1;
		cell.weightx = 1;
		cell.fill = GridBagConstraints.HORIZONTAL;
		panel.add(replacement, cell);
		cell.gridx = 2;
		cell.weightx = 0;
		cell.fill = GridBagConstraints.NONE;
		JPanel replacements = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		JButton replace = new JButton("Replace");
		replace.addActionListener(event -> search(true, 1));
		replacements.add(replace);
		taskButtons.add(replace);
		JButton replaceAll = new JButton("All");
		replaceAll.addActionListener(event -> search(true, 2));
		replacements.add(replaceAll);
		taskButtons.add(replaceAll);
		panel.add(replacements, cell);
		find.addActionListener(event -> search(true, 0));
		return panel;
	}

	private JButton tool(String icon, String tooltip, Runnable action)
	{
		JButton button = new JButton(icon(icon, 18));
		button.setToolTipText(tooltip);
		button.getAccessibleContext().setAccessibleName(tooltip);
		button.setPreferredSize(new Dimension(32, 30));
		button.addActionListener(event -> action.run());
		taskButtons.add(button);
		return button;
	}

	static FlatSVGIcon icon(String name, int size)
	{
		FlatSVGIcon icon = new FlatSVGIcon("openrtm/images/editor/" + name + ".svg", size, size);
		icon.setColorFilter(new FlatSVGIcon.ColorFilter((component, color) -> {
			Color foreground = component == null ? UIManager.getColor("Button.foreground") : component.getForeground();
			return foreground == null ? color : foreground;
		}));
		return icon;
	}

	private void bind(int key, boolean shift, Runnable action)
	{
		int modifiers = KeyEvent.CTRL_DOWN_MASK | (shift ? KeyEvent.SHIFT_DOWN_MASK : 0);
		String name = "gsc-" + key + "-" + shift;
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, modifiers), name);
		getRootPane().getActionMap().put(name, new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent event)
			{
				if (!busy)
				{
					action.run();
				}
			}
		});
	}

	private void loadProject(Path source)
	{
		setBusy(true);
		status.setText("Opening project...");
		new SwingWorker<List<SourceFile>, Void>()
		{
			@Override
			protected List<SourceFile> doInBackground() throws Exception
			{
				List<SourceFile> loaded = new ArrayList<>();
				for (Path file : GscProjects.files(source))
				{
					loaded.add(new SourceFile(file));
				}
				return loaded;
			}

			@Override
			protected void done()
			{
				try
				{
					List<SourceFile> loaded = get();
					revision++;
					if (checkWorker != null)
					{
						checkWorker.cancel(true);
					}
					for (SourceFile file : files.values())
					{
						if (file.completion != null)
						{
							file.completion.uninstall();
						}
					}
					files.clear();
					tabs.removeAll();
					fileModel.clear();
					selected = source.toAbsolutePath().normalize();
					root = Files.isDirectory(selected) ? selected : selected.getParent();
					for (SourceFile file : loaded)
					{
						files.put(file.path, file);
						fileModel.addElement(file.path);
					}
					setTitle(GscProjectTitle.read(selected) + " - GSC Editor");
					selectedProject.accept(selected);
					Path initial = Files.isRegularFile(selected) ? selected : root.resolve("main.gsc");
					if (files.containsKey(initial))
					{
						fileList.setSelectedValue(initial, true);
					}
					else if (!files.isEmpty())
					{
						fileList.setSelectedIndex(0);
					}
					status.setText(files.isEmpty() ? "No GSC files" : "Project opened");
					checkTimer.restart();
				}
				catch (InterruptedException | ExecutionException failure)
				{
					showFailure(failure);
				}
				finally
				{
					setBusy(false);
				}
			}
		}.execute();
	}

	void openProject(Path source)
	{
		Path normalized = source.toAbsolutePath().normalize();
		if (!busy && !normalized.equals(selected) && confirmSaved())
		{
			loadProject(normalized);
		}
	}

	private void openFile(Path path)
	{
		SourceFile file = files.get(path);
		if (file == null)
		{
			return;
		}
		if (file.editor == null)
		{
			file.editor = new RSyntaxTextArea();
			file.editor.putClientProperty("openrtm.undo.installed", true);
			file.editor.setSyntaxEditingStyle(STYLE);
			file.editor.setCodeFoldingEnabled(true);
			file.editor.setMarkOccurrences(true);
			file.editor.setBracketMatchingEnabled(true);
			file.editor.setAutoIndentEnabled(true);
			file.editor.setCloseCurlyBraces(true);
			file.editor.setTabSize(4);
			file.editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
			Color background = UIManager.getColor("TextArea.background");
			Color foreground = UIManager.getColor("TextArea.foreground");
			file.editor.setBackground(background);
			file.editor.setForeground(foreground);
			file.editor.setCaretColor(foreground);
			boolean dark = background != null && background.getRed() + background.getGreen() + background.getBlue() < 384;
			var scheme = file.editor.getSyntaxScheme();
			for (Style style : scheme.getStyles())
			{
				if (style != null)
				{
					style.foreground = foreground;
				}
			}
			file.editor.setCurrentLineHighlightColor(dark ? new Color(48, 51, 56) : new Color(241, 243, 246));
			file.editor.setMatchedBracketBGColor(dark ? new Color(51, 75, 91) : new Color(210, 231, 243));
			scheme.getStyle(Token.RESERVED_WORD).foreground = dark ? new Color(117, 175, 255) : new Color(30, 85, 175);
			scheme.getStyle(Token.FUNCTION).foreground = dark ? new Color(222, 193, 117) : new Color(135, 96, 20);
			scheme.getStyle(Token.IDENTIFIER).foreground = foreground;
			scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground = dark ? new Color(134, 208, 151) : new Color(31, 115, 54);
			scheme.getStyle(Token.LITERAL_NUMBER_FLOAT).foreground = dark ? new Color(235, 161, 150) : new Color(161, 51, 43);
			for (int type : new int[]{Token.COMMENT_EOL, Token.COMMENT_MULTILINE, Token.COMMENT_DOCUMENTATION})
			{
				scheme.getStyle(type).foreground = dark ? new Color(152, 162, 174) : new Color(104, 114, 123);
			}
			file.editor.setText(file.text);
			file.editor.setCaretPosition(0);
			file.editor.discardAllEdits();
			file.parser = new ErrorParser();
			file.editor.addParser(file.parser);
			file.provider = new DefaultCompletionProvider();
			populateCompletions(file, GscLanguageSupport.completions(snapshot(false)));
			file.completion = new AutoCompletion(file.provider);
			file.completion.setAutoActivationEnabled(true);
			file.completion.setAutoActivationDelay(350);
			file.completion.install(file.editor);
			file.editor.getDocument().addDocumentListener(new DocumentListener()
			{
				@Override
				public void insertUpdate(DocumentEvent event)
				{
					changed(file);
				}

				@Override
				public void removeUpdate(DocumentEvent event)
				{
					changed(file);
				}

				@Override
				public void changedUpdate(DocumentEvent event)
				{
				}
			});
			file.editor.addCaretListener(event -> {
				try
				{
					int line = file.editor.getLineOfOffset(event.getDot());
					position.setText("Line " + (line + 1) + ", Column " + (event.getDot() - file.editor.getLineStartOffset(line) + 1));
				}
				catch (BadLocationException ignored)
				{
				}
			});
			file.scroll = new RTextScrollPane(file.editor);
			file.scroll.setLineNumbersEnabled(true);
			file.scroll.setIconRowHeaderEnabled(true);
			file.scroll.setFoldIndicatorEnabled(true);
			file.scroll.getGutter().setBackground(background);
			file.scroll.getGutter().setLineNumberColor(dark ? new Color(152, 162, 174) : new Color(104, 114, 123));
			file.scroll.setMinimumSize(new Dimension(240, 180));
			tabs.addTab(path.getFileName().toString(), file.scroll);
			JPanel tab = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			tab.setOpaque(false);
			file.tabLabel = new JLabel(path.getFileName().toString());
			tab.add(file.tabLabel);
			JButton close = new JButton(icon("x", 12));
			close.setToolTipText("Close file");
			close.setPreferredSize(new Dimension(20, 20));
			close.addActionListener(event -> closeFile(file));
			tab.add(close);
			tabs.setTabComponentAt(tabs.indexOfComponent(file.scroll), tab);
		}
		tabs.setSelectedComponent(file.scroll);
		file.editor.requestFocusInWindow();
	}

	private void changed(SourceFile file)
	{
		revision++;
		file.tabLabel.setText(file.path.getFileName() + (file.dirty() ? " *" : ""));
		checkTimer.restart();
	}

	private void checkProject()
	{
		if (busy || files.isEmpty())
		{
			return;
		}
		if (checkWorker != null)
		{
			checkWorker.cancel(true);
		}
		Map<String, String> all = snapshot(false);
		Map<String, String> compiling = snapshot(true);
		long currentRevision = revision;
		status.setText("Checking project...");
		checkWorker = new SwingWorker<>()
		{
			@Override
			protected CheckResult doInBackground()
			{
				List<Diagnostic> issues = new ArrayList<>();
				for (Map.Entry<String, String> source : all.entrySet())
				{
					if (isCancelled())
					{
						return null;
					}
					issues.addAll(GscLanguageSupport.syntax(source.getKey(), source.getValue()));
				}
				int length = 0;
				if (issues.isEmpty())
				{
					try
					{
						var program = Iw4GscCompiler.compile(compiling);
						program.gscbin();
						length = program.size();
					}
					catch (IllegalArgumentException failure)
					{
						issues.add(GscLanguageSupport.diagnostic(compiling.keySet().stream().findFirst().orElse("main.gsc"), failure));
					}
				}
				return new CheckResult(issues, GscLanguageSupport.completions(all), length);
			}

			@Override
			protected void done()
			{
				if (isCancelled() || currentRevision != revision || !isDisplayable())
				{
					return;
				}
				try
				{
					CheckResult result = get();
					problems.clear();
					result.issues().forEach(problems::addElement);
					for (SourceFile file : files.values())
					{
						if (file.editor == null)
						{
							continue;
						}
						file.parser.issues = result.issues().stream().filter(issue -> issue.file().equals(name(file.path))).toList();
						file.editor.forceReparsing(file.parser);
						populateCompletions(file, result.names());
					}
					status.setText(result.issues().isEmpty() ? "Ready - " + result.length() + " bytes compiled"
						: result.issues().size() + " problem(s)");
					updateFunctions();
				}
				catch (InterruptedException | ExecutionException failure)
				{
					showFailure(failure);
				}
				catch (CancellationException ignored)
				{
				}
			}
		};
		checkWorker.execute();
	}

	private void populateCompletions(SourceFile file, Set<String> names)
	{
		file.provider.clear();
		for (String name : names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList())
		{
			file.provider.addCompletion(new BasicCompletion(file.provider, name));
		}
		file.provider.addCompletion(new ShorthandCompletion(file.provider, "spawnloop",
			"for (;;)\n{\n\tself waittill(\"spawned_player\");\n\tself iPrintLnBold(\"Hello!\");\n}", "Player spawn loop"));
	}

	private Map<String, String> snapshot(boolean compiling)
	{
		Map<String, String> sources = new LinkedHashMap<>();
		boolean single = selected != null && Files.isRegularFile(selected) && !Files.isRegularFile(root.resolve("config.il"));
		for (SourceFile file : files.values())
		{
			if (!compiling || (single ? file.path.equals(selected) : file.path.getParent().equals(root)))
			{
				sources.put(name(file.path), file.source());
			}
		}
		return sources;
	}

	private String name(Path path)
	{
		return root.relativize(path).toString();
	}

	private SourceFile current()
	{
		for (SourceFile file : files.values())
		{
			if (file.editor != null && file.scroll == tabs.getSelectedComponent())
			{
				return file;
			}
		}
		return null;
	}

	private boolean save(SourceFile file)
	{
		if (file == null || !file.dirty())
		{
			return true;
		}
		try
		{
			if (Files.exists(file.path) && !Arrays.equals(Files.readAllBytes(file.path), file.savedBytes))
			{
				Object[] choices = {"Overwrite", "Reload", "Cancel"};
				int choice = JOptionPane.showOptionDialog(this, file.path.getFileName() + " changed outside the editor.",
					"File Changed", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, choices[2]);
				if (choice == 1)
				{
					file.load();
					file.editor.setText(file.text);
					file.editor.discardAllEdits();
					changed(file);
					return false;
				}
				if (choice != 0)
				{
					return false;
				}
			}
			String source = file.source();
			byte[] bytes = ((file.bom ? "\uFEFF" : "") + source.replace("\n", file.lineEnding)).getBytes(StandardCharsets.UTF_8);
			writeAtomic(file.path, bytes);
			file.savedBytes = bytes;
			file.text = source;
			file.tabLabel.setText(file.path.getFileName().toString());
			status.setText("Saved " + file.path.getFileName());
			return true;
		}
		catch (IOException failure)
		{
			showFailure(failure);
			return false;
		}
	}

	private boolean saveAll()
	{
		for (SourceFile file : files.values())
		{
			if (!save(file))
			{
				return false;
			}
		}
		return true;
	}

	private boolean confirmSaved()
	{
		if (files.values().stream().noneMatch(SourceFile::dirty))
		{
			return true;
		}
		int choice = JOptionPane.showConfirmDialog(this, "Save project changes?", "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION);
		return choice == JOptionPane.NO_OPTION || choice == JOptionPane.YES_OPTION && saveAll();
	}

	private void closeFile(SourceFile file)
	{
		if (busy)
		{
			return;
		}
		if (file.dirty())
		{
			int choice = JOptionPane.showConfirmDialog(this, "Save changes to " + file.path.getFileName() + "?", "Unsaved Changes", JOptionPane.YES_NO_CANCEL_OPTION);
			if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION || choice == JOptionPane.YES_OPTION && !save(file))
			{
				return;
			}
		}
		file.completion.uninstall();
		tabs.remove(file.scroll);
		if (file.path.equals(fileList.getSelectedValue()))
		{
			fileList.clearSelection();
		}
		file.editor = null;
		file.scroll = null;
		revision++;
		checkTimer.restart();
	}

	private void format()
	{
		SourceFile file = current();
		if (file == null)
		{
			return;
		}
		String source = file.source();
		long before = revision;
		setBusy(true);
		new SwingWorker<String, Void>()
		{
			@Override
			protected String doInBackground()
			{
				return GscLanguageSupport.format(source);
			}

			@Override
			protected void done()
			{
				try
				{
					String formatted = get();
					if (before == revision && !formatted.equals(source))
					{
						int caret = file.editor.getCaretPosition();
						file.editor.beginAtomicEdit();
						try
						{
							file.editor.replaceRange(formatted, 0, file.editor.getDocument().getLength());
						}
						finally
						{
							file.editor.endAtomicEdit();
						}
						file.editor.setCaretPosition(Math.min(caret, formatted.length()));
					}
				}
				catch (InterruptedException | ExecutionException failure)
				{
					showFailure(failure);
				}
				finally
				{
					setBusy(false);
					checkTimer.restart();
				}
			}
		}.execute();
	}

	private void search(boolean forward, int operation)
	{
		SourceFile file = current();
		if (file == null || find.getText().isEmpty() || busy)
		{
			return;
		}
		SearchContext context = new SearchContext(find.getText());
		context.setReplaceWith(replacement.getText());
		context.setMatchCase(matchCase.isSelected());
		context.setRegularExpression(regex.isSelected());
		context.setSearchForward(forward);
		context.setSearchWrap(true);
		try
		{
			SearchResult result = operation == 0 ? SearchEngine.find(file.editor, context)
				: operation == 1 ? SearchEngine.replace(file.editor, context) : SearchEngine.replaceAll(file.editor, context);
			status.setText(result.wasFound() ? operation == 0 ? "Match found" : result.getCount() + " replacement(s)" : "No matches");
		}
		catch (IllegalArgumentException | IndexOutOfBoundsException failure)
		{
			showFailure(failure);
		}
	}

	private void updateFunctions()
	{
		updatingFunctions = true;
		functions.removeAllItems();
		functions.addItem("Functions");
		SourceFile file = current();
		if (file != null)
		{
			var matcher = Pattern.compile("(?m)^\\s*([A-Za-z_][A-Za-z_0-9]*)\\s*\\([^;{}]*\\)\\s*\\{").matcher(file.source());
			while (matcher.find())
			{
				functions.addItem(matcher.group(1));
			}
		}
		updatingFunctions = false;
	}

	private void goToFunction()
	{
		SourceFile file = current();
		if (updatingFunctions || file == null || functions.getSelectedIndex() <= 0)
		{
			return;
		}
		String function = (String) functions.getSelectedItem();
		if (function == null)
		{
			return;
		}
		var matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(function) + "\\s*\\(").matcher(file.source());
		if (matcher.find())
		{
			file.editor.setCaretPosition(matcher.start());
			file.editor.requestFocusInWindow();
		}
	}

	private void goToProblem(Diagnostic issue)
	{
		if (issue == null)
		{
			return;
		}
		SourceFile file = files.get(root.resolve(issue.file()).normalize());
		if (file == null)
		{
			return;
		}
		openFile(file.path);
		try
		{
			int line = Math.min(Math.max(0, issue.line() - 1), file.editor.getLineCount() - 1);
			int offset = Math.min(file.editor.getLineEndOffset(line) - 1, file.editor.getLineStartOffset(line) + Math.max(0, issue.column() - 1));
			file.editor.setCaretPosition(Math.max(0, offset));
		}
		catch (BadLocationException ignored)
		{
		}
	}

	private void newFile()
	{
		if (root == null)
		{
			return;
		}
		String requested = JOptionPane.showInputDialog(this, "File name", "New GSC File", JOptionPane.PLAIN_MESSAGE);
		if (requested == null)
		{
			return;
		}
		try
		{
			String name = GscProjects.checkedName(requested);
			if (!name.toLowerCase(Locale.ROOT).endsWith(".gsc"))
			{
				name += ".gsc";
			}
			Path path = root.resolve(name);
			Files.createFile(path);
			files.put(path, new SourceFile(path));
			fileModel.addElement(path);
			fileList.setSelectedValue(path, true);
			if (Files.isRegularFile(selected) && !Files.isRegularFile(root.resolve("config.il")))
			{
				selected = root;
				selectedProject.accept(selected);
				setTitle(GscProjectTitle.read(selected) + " - GSC Editor");
			}
			revision++;
			checkTimer.restart();
		}
		catch (IOException | IllegalArgumentException failure)
		{
			showFailure(failure);
		}
	}

	private void newProject()
	{
		if (!confirmSaved())
		{
			return;
		}
		String name = JOptionPane.showInputDialog(this, "Project name", "New GSC Project", JOptionPane.PLAIN_MESSAGE);
		if (name == null)
		{
			return;
		}
		try
		{
			loadProject(GscProjects.create(name));
		}
		catch (IOException | IllegalArgumentException failure)
		{
			showFailure(failure);
		}
	}

	private void chooseProject()
	{
		if (!confirmSaved())
		{
			return;
		}
		JFileChooser chooser = new JFileChooser(root == null ? GscProjects.directory().toFile() : root.toFile());
		chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setFileFilter(new FileNameExtensionFilter("GSC scripts (*.gsc)", "gsc"));
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			loadProject(chooser.getSelectedFile().toPath());
		}
	}

	private void inject()
	{
		if (selected == null || !saveAll())
		{
			return;
		}
		setBusy(true);
		progress.begin("Preparing injection");
		injection.start(selected, progress, successful -> {
			progress.finish(successful);
			setBusy(false);
			checkTimer.restart();
		});
	}

	private void export()
	{
		if (selected == null)
		{
			return;
		}
		JFileChooser chooser = new JFileChooser(root.toFile());
		chooser.setFileFilter(new FileNameExtensionFilter("Compiled GSC (*.gscbin)", "gscbin"));
		chooser.setAcceptAllFileFilterUsed(false);
		chooser.setSelectedFile(root.resolve(GscProjectTitle.read(selected).replaceAll("[\\\\/:*?\"<>|]", "_") + ".gscbin").toFile());
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		Path target = chooser.getSelectedFile().toPath();
		if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gscbin"))
		{
			target = target.resolveSibling(target.getFileName() + ".gscbin");
		}
		if (Files.exists(target) && JOptionPane.showConfirmDialog(this, "Replace " + target.getFileName() + "?", "Export GSC", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
		{
			return;
		}
		Path destination = target;
		Map<String, String> sources = snapshot(true);
		setBusy(true);
		progress.begin("Compiling project");
		new SwingWorker<Void, Void>()
		{
			@Override
			protected Void doInBackground() throws Exception
			{
				byte[] binary = Iw4GscCompiler.compile(sources, (percent, message) -> progress.update(percent * 95L / 100, 100, message)).gscbin();
				progress.update(98, 100, "Saving compiled file");
				writeAtomic(destination, binary);
				return null;
			}

			@Override
			protected void done()
			{
				boolean successful = false;
				try
				{
					get();
					successful = true;
					status.setText("Exported " + destination.getFileName());
				}
				catch (InterruptedException | ExecutionException failure)
				{
					showFailure(failure);
				}
				finally
				{
					progress.finish(successful);
					setBusy(false);
					checkTimer.restart();
				}
			}
		}.execute();
	}

	private void setBusy(boolean value)
	{
		busy = value;
		if (value)
		{
			checkTimer.stop();
			if (checkWorker != null)
			{
				checkWorker.cancel(true);
			}
		}
		for (JButton button : taskButtons)
		{
			button.setEnabled(!value);
		}
		fileList.setEnabled(!value);
		functions.setEnabled(!value);
		for (SourceFile file : files.values())
		{
			if (file.editor != null)
			{
				file.editor.setEditable(!value);
			}
		}
	}

	private void showFailure(Exception failure)
	{
		Throwable cause = failure instanceof ExecutionException ? failure.getCause() : failure;
		JOptionPane.showMessageDialog(this, cause.getMessage(), "GSC Editor", JOptionPane.ERROR_MESSAGE);
	}

	static void writeAtomic(Path path, byte[] bytes) throws IOException
	{
		Path target = path.toAbsolutePath().normalize();
		Path temporary = Files.createTempFile(target.getParent(), "gsc-", ".tmp");
		try
		{
			Files.write(temporary, bytes);
			try
			{
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException ignored)
			{
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally
		{
			Files.deleteIfExists(temporary);
		}
	}

	@Override
	public void dispose()
	{
		checkTimer.stop();
		if (checkWorker != null)
		{
			checkWorker.cancel(true);
		}
		for (SourceFile file : files.values())
		{
			if (file.completion != null)
			{
				file.completion.uninstall();
			}
		}
		super.dispose();
	}

	private record CheckResult(List<Diagnostic> issues, Set<String> names, int length)
	{
	}

	private static final class SourceFile
	{
		private final Path path;
		private byte[] savedBytes;
		private String text;
		private String lineEnding;
		private boolean bom;
		private RSyntaxTextArea editor;
		private RTextScrollPane scroll;
		private JLabel tabLabel;
		private DefaultCompletionProvider provider;
		private AutoCompletion completion;
		private ErrorParser parser;

		private SourceFile(Path path) throws IOException
		{
			this.path = path.toAbsolutePath().normalize();
			load();
		}

		private void load() throws IOException
		{
			savedBytes = Files.readAllBytes(path);
			String source = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(savedBytes)).toString();
			bom = source.startsWith("\uFEFF");
			lineEnding = source.contains("\r\n") ? "\r\n" : "\n";
			text = (bom ? source.substring(1) : source).replace("\r\n", "\n").replace('\r', '\n');
		}

		private String source()
		{
			return editor == null ? text : editor.getText();
		}

		private boolean dirty()
		{
			return editor != null && !source().equals(text);
		}
	}

	private static final class ErrorParser extends AbstractParser
	{
		private List<Diagnostic> issues = List.of();

		@Override
		public ParseResult parse(RSyntaxDocument document, String style)
		{
			DefaultParseResult result = new DefaultParseResult(this);
			var lines = document.getDefaultRootElement();
			result.setParsedLines(0, lines.getElementCount() - 1);
			for (Diagnostic issue : issues)
			{
				int line = Math.min(Math.max(0, issue.line() - 1), lines.getElementCount() - 1);
				var element = lines.getElement(line);
				int offset = Math.min(document.getLength(), element.getStartOffset() + Math.max(0, issue.column() - 1));
				result.addNotice(new DefaultParserNotice(this, issue.message(), line, offset, Math.min(1, document.getLength() - offset)));
			}
			return result;
		}
	}
}
