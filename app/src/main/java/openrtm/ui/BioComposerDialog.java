package openrtm.ui;

import openrtm.profile.BioPresetCatalog;
import openrtm.profile.BioPresetCatalog.BioPreset;
import openrtm.profile.BioSymbolCatalog;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

final class BioComposerDialog extends JDialog
{
	static final int MAX_BIO_CHARACTERS = 499;
	private static final Color LIMIT_COLOR = new Color(210, 65, 65);
	private final List<BioPreset> presets;
	private final Consumer<String> useBio;
	private final DefaultListModel<BioPreset> presetModel = new DefaultListModel<>();
	private final JList<BioPreset> presetList = new JList<>(presetModel);
	private final JTextField search = new JTextField();
	private final JTextArea editor = new JTextArea();
	private final JLabel characterCount = new JLabel();
	private Color normalCountColor;

	static void showDialog(Component parent, String initialBio, Consumer<String> useBio)
	{
		List<BioPreset> presets;
		try
		{
			presets = BioPresetCatalog.load();
		}
		catch (IOException failure)
		{
			presets = Collections.emptyList();
			JOptionPane.showMessageDialog(parent, failure.getMessage(), "Bio Creator",
				JOptionPane.WARNING_MESSAGE);
		}
		Window owner = SwingUtilities.getWindowAncestor(parent);
		BioComposerDialog dialog = new BioComposerDialog(owner, initialBio, presets, useBio);
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	private BioComposerDialog(Window owner, String initialBio, List<BioPreset> presets,
	                          Consumer<String> useBio)
	{
		super(owner, "Bio Creator", Dialog.ModalityType.APPLICATION_MODAL);
		this.presets = presets;
		this.useBio = useBio;
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setContentPane(content());
		populatePresets("");
		editor.setText(initialBio == null ? "" : initialBio);
		editor.setCaretPosition(0);
		pack();
		setSize(new Dimension(980, 760));
		setMinimumSize(new Dimension(780, 620));
	}

	private JPanel content()
	{
		JPanel panel = new JPanel(new BorderLayout(12, 10));
		panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, presetPanel(), editorPanel());
		split.setBorder(null);
		split.setDividerLocation(230);
		split.setResizeWeight(0);
		panel.add(split, BorderLayout.CENTER);
		panel.add(actions(), BorderLayout.SOUTH);
		return panel;
	}

	private JPanel presetPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		panel.setMinimumSize(new Dimension(190, 300));
		JPanel header = new JPanel(new BorderLayout(0, 4));
		header.add(new JLabel("Presets"), BorderLayout.NORTH);
		header.add(search, BorderLayout.CENTER);
		panel.add(header, BorderLayout.NORTH);
		presetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		presetList.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				if (event.getClickCount() == 2)
				{
					loadSelectedPreset();
				}
			}
		});
		panel.add(new JScrollPane(presetList), BorderLayout.CENTER);
		JButton load = new JButton("Load Preset");
		load.addActionListener(event -> loadSelectedPreset());
		panel.add(load, BorderLayout.SOUTH);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void changedUpdate(DocumentEvent event)
			{
				filterPresets();
			}

			@Override
			public void insertUpdate(DocumentEvent event)
			{
				filterPresets();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				filterPresets();
			}
		});
		return panel;
	}

	private JPanel editorPanel()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 8));
		JPanel header = new JPanel(new BorderLayout());
		header.add(new JLabel("Bio"), BorderLayout.WEST);
		normalCountColor = characterCount.getForeground();
		header.add(characterCount, BorderLayout.EAST);
		panel.add(header, BorderLayout.NORTH);
		editor.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 15));
		editor.setLineWrap(false);
		editor.setTabSize(4);
		editor.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void changedUpdate(DocumentEvent event)
			{
				updateCharacterCount();
			}

			@Override
			public void insertUpdate(DocumentEvent event)
			{
				updateCharacterCount();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				updateCharacterCount();
			}
		});
		panel.add(new JScrollPane(editor), BorderLayout.CENTER);
		panel.add(symbolTabs(), BorderLayout.SOUTH);
		return panel;
	}

	private JTabbedPane symbolTabs()
	{
		JTabbedPane tabs = new JTabbedPane();
		for (Map.Entry<String, List<String>> category : BioSymbolCatalog.create(presets).entrySet())
		{
			JPanel symbols = new JPanel(new GridLayout(0, 10, 4, 4));
			symbols.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
			for (String symbol : category.getValue())
			{
				JButton button = new JButton(symbol);
				button.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
				button.setMargin(new Insets(2, 2, 2, 2));
				button.setToolTipText("Insert " + symbol);
				button.addActionListener(event -> insertSymbol(symbol));
				symbols.add(button);
			}
			JScrollPane scroll = new JScrollPane(symbols);
			scroll.setPreferredSize(new Dimension(500, 250));
			tabs.addTab(category.getKey(), scroll);
		}
		return tabs;
	}

	private JPanel actions()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		JButton clear = new JButton("Clear");
		JButton copy = new JButton("Copy");
		JButton use = new JButton("Use Bio");
		clear.addActionListener(event -> editor.setText(""));
		copy.addActionListener(event -> copyToClipboard());
		use.addActionListener(event -> useBio());
		panel.add(clear);
		panel.add(copy);
		panel.add(use);
		return panel;
	}

	private void filterPresets()
	{
		populatePresets(search.getText());
	}

	private void populatePresets(String filter)
	{
		String requested = filter.trim().toLowerCase(Locale.ROOT);
		presetModel.clear();
		for (BioPreset preset : presets)
		{
			if (requested.isEmpty() || preset.name().toLowerCase(Locale.ROOT).contains(requested))
			{
				presetModel.addElement(preset);
			}
		}
		if (!presetModel.isEmpty())
		{
			presetList.setSelectedIndex(0);
		}
	}

	private void loadSelectedPreset()
	{
		BioPreset selected = presetList.getSelectedValue();
		if (selected != null)
		{
			editor.setText(selected.content());
			editor.setCaretPosition(0);
		}
	}

	private void insertSymbol(String symbol)
	{
		editor.replaceSelection(symbol);
		editor.requestFocusInWindow();
	}

	private void updateCharacterCount()
	{
		int length = editor.getText().length();
		characterCount.setText(length + " / " + MAX_BIO_CHARACTERS);
		characterCount.setForeground(length > MAX_BIO_CHARACTERS ? LIMIT_COLOR : normalCountColor);
	}

	private void copyToClipboard()
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
				new StringSelection(editor.getText()), null);
		}
		catch (IllegalStateException failure)
		{
			JOptionPane.showMessageDialog(this, "The clipboard is currently unavailable.",
				"Bio Creator", JOptionPane.WARNING_MESSAGE);
		}
	}

	private void useBio()
	{
		if (editor.getText().length() > MAX_BIO_CHARACTERS)
		{
			JOptionPane.showMessageDialog(this, "Shorten the bio to 499 characters or fewer.",
				"Bio Creator", JOptionPane.WARNING_MESSAGE);
			return;
		}
		useBio.accept(editor.getText());
		dispose();
	}
}
