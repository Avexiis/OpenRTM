package openrtm.ui;

import openrtm.config.ProfileIdentityStore;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.Locale;

final class ProfileIdField extends JPanel
{
	private final ProfileIdentityStore identities;
	private final JTextField gamertag = new JTextField(20);
	private final JButton idButton = new JButton("ID");
	private String profileId = "";
	private boolean editable = true;

	ProfileIdField(ProfileIdentityStore identities)
	{
		super(new BorderLayout(5, 0));
		this.identities = identities;
		gamertag.setEditable(false);
		idButton.setMargin(new Insets(2, 8, 2, 8));
		idButton.setToolTipText("View or change the profile ID");
		idButton.addActionListener(event -> showId());
		add(gamertag, BorderLayout.CENTER);
		add(idButton, BorderLayout.EAST);
		setText("");
		identities.addListener(() -> SwingUtilities.invokeLater(this::refreshGamertag));
	}

	String getText()
	{
		return profileId;
	}

	void setText(String value)
	{
		profileId = normalize(value);
		refreshGamertag();
		idButton.setEnabled(isEnabled() && (editable || !profileId.isEmpty()));
	}

	private void refreshGamertag()
	{
		gamertag.setText(profileId.isEmpty() ? "" : identities.displayName(profileId));
	}

	void setIdentity(String value, String name)
	{
		identities.remember(value, name);
		setText(value);
	}

	void setEditable(boolean editable)
	{
		this.editable = editable;
		idButton.setToolTipText(editable ? "View or change the profile ID" : "View the profile ID");
		idButton.setEnabled(isEnabled() && (editable || !profileId.isEmpty()));
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		super.setEnabled(enabled);
		gamertag.setEnabled(enabled);
		idButton.setEnabled(enabled && (editable || !profileId.isEmpty()));
	}

	@Override
	public Dimension getMaximumSize()
	{
		return getPreferredSize();
	}

	private void showId()
	{
		if (!editable)
		{
			JOptionPane.showMessageDialog(this, profileId, "Profile ID", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		String value = JOptionPane.showInputDialog(this, "Profile ID", profileId);
		if (value == null)
		{
			return;
		}
		String normalized = normalize(value);
		if (normalized.isEmpty() && !value.trim().isEmpty())
		{
			JOptionPane.showMessageDialog(this, "Profile ID must contain exactly 16 hexadecimal digits",
				"Profile ID", JOptionPane.WARNING_MESSAGE);
			return;
		}
		setText(normalized);
	}

	private static String normalize(String value)
	{
		String normalized = value == null ? "" : value.trim().replaceAll("[^0-9A-Fa-f]", "")
			.toUpperCase(Locale.ROOT);
		return normalized.length() == 16 ? normalized : "";
	}
}
