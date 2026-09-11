package openrtm.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Insets;

final class AspectRatioPanel extends JPanel
{
	private final JComponent content;
	private final int ratioWidth;
	private final int ratioHeight;

	AspectRatioPanel(JComponent content, int ratioWidth, int ratioHeight)
	{
		this.content = content;
		this.ratioWidth = ratioWidth;
		this.ratioHeight = ratioHeight;
		setLayout(null);
		add(content);
		setPreferredSize(new Dimension(640, 360));
	}

	@Override
	public void doLayout()
	{
		Insets insets = getInsets();
		int availableWidth = Math.max(0, getWidth() - insets.left - insets.right);
		int availableHeight = Math.max(0, getHeight() - insets.top - insets.bottom);
		int width = availableWidth;
		int height = ratioWidth == 0 ? 0 : (int) ((long) width * ratioHeight / ratioWidth);
		if (height > availableHeight)
		{
			height = availableHeight;
			width = ratioHeight == 0 ? 0 : (int) ((long) height * ratioWidth / ratioHeight);
		}
		int x = insets.left + (availableWidth - width) / 2;
		int y = insets.top + (availableHeight - height) / 2;
		content.setBounds(x, y, width, height);
	}
}
