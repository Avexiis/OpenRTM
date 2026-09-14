package openrtm.ui;

import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.Taskbar;
import java.awt.Window;
import java.net.URL;
import java.util.List;

public final class ApplicationIcon
{
	private static final Image IMAGE = load();

	private ApplicationIcon()
	{
	}

	public static void apply(Window window)
	{
		if (IMAGE != null)
		{
			window.setIconImages(List.of(IMAGE));
		}
	}

	public static void applyToTaskbar()
	{
		if (IMAGE == null || !Taskbar.isTaskbarSupported())
		{
			return;
		}
		try
		{
			Taskbar taskbar = Taskbar.getTaskbar();
			if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE))
			{
				taskbar.setIconImage(IMAGE);
			}
		}
		catch (SecurityException | UnsupportedOperationException ignored)
		{
		}
	}

	private static Image load()
	{
		URL resource = ApplicationIcon.class.getResource("/openrtm/images/openrtm.png");
		return resource == null ? null : new ImageIcon(resource).getImage();
	}
}
