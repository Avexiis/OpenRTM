package openrtm.ui;

import openrtm.profile.ProfileService;
import openrtm.profile.ProfileTransferService;
import openrtm.profile.ProfileWorkspace;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;

public final class ProfileEditorPanel extends JPanel
{
	private static final Color LINE = new Color(55, 60, 66);
	private static final Color ACCENT = new Color(91, 141, 239);
	private final ProfileWorkspace workspace;
	private final TaskRunner tasks;
	private final JTextField source = new JTextField(42);
	private final JTextField profileId = new JTextField(18);
	private final JTextField motto = new JTextField(32);
	private final JTextField userName = new JTextField(32);
	private final JTextField location = new JTextField(32);
	private final JTextArea bio = new JTextArea(4, 32);
	private final JLabel summary = new JLabel("Open a gamer profile to begin");

	public ProfileEditorPanel(ProfileWorkspace workspace, TaskRunner tasks)
	{
		super(new BorderLayout(10, 10));
		this.workspace = workspace;
		this.tasks = tasks;
		setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
		add(sourceSection(), BorderLayout.NORTH);
		add(detailsSection(), BorderLayout.CENTER);
		add(transferSection(), BorderLayout.SOUTH);
		workspace.addListener(() -> SwingUtilities.invokeLater(this::workspaceChanged));
	}

	private JPanel sourceSection()
	{
		JPanel section = section("Gamer Profile");
		JPanel row = row();
		row.add(new JLabel("Profile file"));
		row.add(source);
		JButton browse = new JButton("Browse");
		JButton open = new JButton("Open");
		browse.addActionListener(event -> chooseProfile());
		open.addActionListener(event -> openProfile());
		row.add(browse);
		row.add(open);
		section.add(row);
		summary.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.add(summary);
		return section;
	}

	private JPanel detailsSection()
	{
		JPanel section = section("Profile Details");
		JPanel form = new JPanel(new GridBagLayout());
		form.setAlignmentX(Component.LEFT_ALIGNMENT);
		GridBagConstraints label = constraints(0);
		GridBagConstraints field = constraints(1);
		field.weightx = 1;
		field.fill = GridBagConstraints.HORIZONTAL;
		addField(form, label, field, 0, "Motto", motto);
		addField(form, label, field, 1, "Name", userName);
		addField(form, label, field, 2, "Location", location);
		label.gridy = 3;
		label.anchor = GridBagConstraints.NORTHWEST;
		form.add(new JLabel("Bio"), label);
		field.gridy = 3;
		bio.setLineWrap(true);
		bio.setWrapStyleWord(true);
		form.add(new JScrollPane(bio), field);
		section.add(form);
		JButton save = new JButton("Save Changes");
		save.addActionListener(event -> saveDetails());
		JPanel actions = row();
		actions.add(save);
		section.add(actions);
		return section;
	}

	private JPanel transferSection()
	{
		JPanel section = section("Console Transfer");
		JPanel row = row();
		row.add(new JLabel("Profile ID"));
		row.add(profileId);
		JButton download = new JButton("Download From Console");
		JButton upload = new JButton("Upload To Console");
		download.addActionListener(event -> downloadProfile());
		upload.addActionListener(event -> uploadProfile());
		row.add(download);
		row.add(upload);
		section.add(row);
		return section;
	}

	private void chooseProfile()
	{
		JFileChooser chooser = new JFileChooser();
		if (!source.getText().isBlank())
		{
			chooser.setSelectedFile(Path.of(source.getText()).toFile());
		}
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
		{
			source.setText(chooser.getSelectedFile().getAbsolutePath());
			openProfile();
		}
	}

	private void openProfile()
	{
		String value = source.getText().trim();
		if (value.isEmpty())
		{
			showWarning("Choose a gamer profile");
			return;
		}
		tasks.run("open gamer profile", () -> workspace.open(Path.of(value)));
	}

	private void saveDetails()
	{
		Path path = workspace.path();
		if (path == null)
		{
			showWarning("Open a gamer profile first");
			return;
		}
		ProfileService.Details details = new ProfileService.Details(
			motto.getText(), userName.getText(), location.getText(), bio.getText());
		tasks.run("save profile details", () -> {
			workspace.profiles().saveDetails(path, path, details, true);
			workspace.refresh();
		});
	}

	private void downloadProfile()
	{
		String id = profileId.getText().trim();
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Save Console Profile");
		chooser.setSelectedFile(Path.of(id.isEmpty() ? "gamer-profile" : id).toFile());
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
		{
			return;
		}
		Path destination = chooser.getSelectedFile().toPath();
		tasks.run("download gamer profile", () -> {
			ProfileTransferService.DownloadResult result = workspace.transfers().download(id, destination);
			workspace.open(result.path());
		});
	}

	private void uploadProfile()
	{
		Path path = workspace.path();
		if (path == null)
		{
			showWarning("Open a gamer profile first");
			return;
		}
		int answer = JOptionPane.showConfirmDialog(this,
			"Upload this profile to the console? All console profiles must be signed out.",
			"Upload Gamer Profile", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (answer != JOptionPane.YES_OPTION)
		{
			return;
		}
		tasks.run("upload gamer profile", () -> {
			ProfileTransferService.UploadResult result = workspace.transfers().upload(path);
			SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
				"Upload verified. Console backup: " + result.backup(), "Gamer Profile",
				JOptionPane.INFORMATION_MESSAGE));
		});
	}

	private void workspaceChanged()
	{
		ProfileService.Profile profile = workspace.profile();
		if (profile == null)
		{
			return;
		}
		source.setText(profile.path().toString());
		profileId.setText(profile.profileId());
		motto.setText(profile.motto());
		userName.setText(profile.userName());
		location.setText(profile.location());
		bio.setText(profile.bio());
		summary.setText(profile.games().size() + " games  |  " + profile.achievements()
			+ " achievements  |  " + profile.gamerscore() + " gamerscore");
	}

	private static void addField(JPanel panel, GridBagConstraints label, GridBagConstraints field,
	                             int row, String name, Component component)
	{
		label.gridy = row;
		field.gridy = row;
		panel.add(new JLabel(name), label);
		panel.add(component, field);
	}

	private static GridBagConstraints constraints(int column)
	{
		GridBagConstraints value = new GridBagConstraints();
		value.gridx = column;
		value.insets = new Insets(5, column == 0 ? 0 : 10, 5, 0);
		value.anchor = GridBagConstraints.WEST;
		return value;
	}

	private static JPanel section(String title)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
			BorderFactory.createEmptyBorder(12, 0, 14, 0)));
		JLabel label = new JLabel(title);
		label.setForeground(ACCENT);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.add(label);
		return panel;
	}

	private static JPanel row()
	{
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		return panel;
	}

	private void showWarning(String message)
	{
		JOptionPane.showMessageDialog(this, message, "Gamer Profile", JOptionPane.WARNING_MESSAGE);
	}
}
