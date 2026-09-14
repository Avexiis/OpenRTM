package openrtm;

import openrtm.config.AppSettings;
import openrtm.console.ConsoleService;
import openrtm.ui.ApplicationIcon;
import openrtm.ui.MainFrame;
import openrtm.ui.TextUndoSupport;
import openrtm.ui.ThemeManager;

import javax.swing.SwingUtilities;
import java.util.Map;

public final class Main
{
	private Main()
	{
	}

	public static void main(String[] args)
	{
		if (args.length > 0 && "--probe".equals(args[0]))
		{
			probe(args.length > 1 ? args[1] : "");
			return;
		}

		ThemeManager.initialize(new AppSettings());
		TextUndoSupport.install();
		ApplicationIcon.applyToTaskbar();
		SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
	}

	private static void probe(String host)
	{
		ConsoleService service = new ConsoleService();
		String target = host == null || host.isBlank() ? service.savedHost() : host.trim();
		if (target.isBlank())
		{
			System.out.println("No probe host supplied and no saved host found in "
				+ service.settingsDirectory().resolve("settings.json"));
			return;
		}

		System.out.println("Connecting to " + target + "...");
		if (!service.connect(target))
		{
			System.out.println("Connection failed");
			return;
		}
		try
		{
			for (Map.Entry<String, String> entry : service.readInfo().entrySet())
			{
				System.out.println(entry.getKey() + ": " + entry.getValue());
			}
		}
		finally
		{
			service.disconnect();
		}
	}
}
