package openrtm.ui;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

final class GscTaskProgress extends JPanel
{
	private final JProgressBar bar = new JProgressBar(0, 100);
	private final JLabel step = new JLabel(" ");

	GscTaskProgress()
	{
		super(new BorderLayout(0, 4));
		bar.setStringPainted(true);
		add(bar, BorderLayout.NORTH);
		add(step, BorderLayout.SOUTH);
		setVisible(false);
	}

	void begin(String message)
	{
		bar.setValue(0);
		bar.setIndeterminate(false);
		bar.setString("0%");
		step.setText(message);
		setVisible(true);
	}

	void update(long completed, long total, String message)
	{
		SwingUtilities.invokeLater(() -> {
			bar.setIndeterminate(total < 0);
			int percent = total <= 0 ? 0 : (int) Math.min(100, completed * 100 / total);
			bar.setValue(percent);
			bar.setString(total < 0 ? "" : percent + "%");
			step.setText(message);
		});
	}

	void finish(boolean successful)
	{
		bar.setIndeterminate(false);
		bar.setString(successful ? "100%" : "Stopped");
		if (successful)
		{
			bar.setValue(100);
		}
		step.setText(successful ? "Complete" : "Stopped: " + step.getText());
	}
}
