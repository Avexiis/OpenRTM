package openrtm.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import de.javasoft.plaf.synthetica.SyntheticaLookAndFeel;
import de.javasoft.synthetica.blackmoon.SyntheticaBlackMoonLookAndFeel;
import de.javasoft.synthetica.bluelight.SyntheticaBlueLightLookAndFeel;
import de.javasoft.synthetica.bluesteel.SyntheticaBlueSteelLookAndFeel;
import de.javasoft.synthetica.dark.SyntheticaDarkLookAndFeel;
import de.javasoft.synthetica.greendream.SyntheticaGreenDreamLookAndFeel;
import de.javasoft.synthetica.mauvemetallic.SyntheticaMauveMetallicLookAndFeel;
import de.javasoft.synthetica.orangemetallic.SyntheticaOrangeMetallicLookAndFeel;
import openrtm.config.AppSettings;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.LookAndFeel;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.basic.BasicRootPaneUI;
import java.awt.Color;
import java.awt.Window;
import java.util.Arrays;
import java.util.Locale;

public final class ThemeManager
{
	private static final String[] DISABLED_POPUP_EFFECTS = {
		"Synthetica.popupRobot.enabled",
		"Synthetica.popupMenu.blur.enabled",
		"Synthetica.popupMenu.fade-in.enabled",
		"Synthetica.popupMenu.fade-out.enabled",
		"Synthetica.popupMenu.showLater"
	};
	private static Theme current = Theme.SYNTHETICA_DARK;

	private ThemeManager()
	{
	}

	public static void initialize(AppSettings settings)
	{
		Theme requested = Theme.fromSetting(settings.theme());
		try
		{
			install(requested);
			current = requested;
		}
		catch (Exception | LinkageError failure)
		{
			installFallback();
		}
	}

	public static Theme current()
	{
		return current;
	}

	public static boolean apply(Theme requested, AppSettings settings)
	{
		if (requested == null || requested == current)
		{
			return true;
		}
		Theme previous = current;
		Window[] windows = activeWindows();
		try
		{
			prepareWindows(windows);
			install(requested);
			current = requested;
			settings.theme(requested.setting());
			refreshWindows(windows);
			return true;
		}
		catch (Exception | LinkageError failure)
		{
			try
			{
				prepareWindows(windows);
				install(previous);
				current = previous;
				refreshWindows(windows);
			}
			catch (Exception | LinkageError restoreFailure)
			{
				prepareWindows(windows);
				installFallback();
				refreshWindows(windows);
			}
			settings.theme(current.setting());
			return false;
		}
	}

	private static void install(Theme theme) throws Exception
	{
		JFrame.setDefaultLookAndFeelDecorated(false);
		JDialog.setDefaultLookAndFeelDecorated(false);
		LookAndFeel lookAndFeel;
		if (theme != Theme.FLAT_DARK && theme != Theme.FLAT_LIGHT)
		{
			requireSyntheticaAccess();
			SyntheticaLookAndFeel.setWindowsDecorated(false);
		}
		switch (theme)
		{
			case FLAT_DARK:
				lookAndFeel = new FlatDarkLaf();
				break;
			case FLAT_LIGHT:
				lookAndFeel = new FlatLightLaf();
				break;
			case SYNTHETICA_BLACK_MOON:
				lookAndFeel = new SyntheticaBlackMoonLookAndFeel();
				break;
			case SYNTHETICA_BLUE_LIGHT:
				lookAndFeel = new SyntheticaBlueLightLookAndFeel();
				break;
			case SYNTHETICA_BLUE_STEEL:
				lookAndFeel = new SyntheticaBlueSteelLookAndFeel();
				break;
			case SYNTHETICA_DARK:
				lookAndFeel = new SyntheticaDarkLookAndFeel();
				break;
			case SYNTHETICA_GREEN_DREAM:
				lookAndFeel = new SyntheticaGreenDreamLookAndFeel();
				break;
			case SYNTHETICA_MAUVE_METALLIC:
				lookAndFeel = new SyntheticaMauveMetallicLookAndFeel();
				break;
			case SYNTHETICA_ORANGE_METALLIC:
				lookAndFeel = new SyntheticaOrangeMetallicLookAndFeel();
				break;
			default:
				throw new UnsupportedLookAndFeelException("Unknown application theme");
		}
		UIManager.setLookAndFeel(lookAndFeel);
		if (theme != Theme.FLAT_DARK && theme != Theme.FLAT_LIGHT)
		{
			disableSyntheticaPopupEffects();
		}
		else
		{
			UIManager.put("Component.arc", 8);
			UIManager.put("Button.arc", 8);
			UIManager.put("TextComponent.arc", 6);
		}
	}

	private static void disableSyntheticaPopupEffects()
	{
		UIDefaults defaults = UIManager.getLookAndFeelDefaults();
		for (String key : DISABLED_POPUP_EFFECTS)
		{
			defaults.put(key, Boolean.FALSE);
			UIManager.put(key, Boolean.FALSE);
		}
	}

	private static void requireSyntheticaAccess() throws UnsupportedLookAndFeelException
	{
		Module desktop = UIManager.class.getModule();
		Module application = ThemeManager.class.getModule();
		String[] exports = {"sun.swing", "sun.swing.table", "sun.swing.plaf.synth", "sun.awt.shell"};
		String[] opens = {"javax.swing.plaf.synth", "javax.swing.plaf.basic", "javax.swing",
			"javax.swing.tree", "java.awt.event"};
		for (String packageName : exports)
		{
			if (!desktop.isExported(packageName, application))
			{
				throw new UnsupportedLookAndFeelException("Synthetica module access is unavailable");
			}
		}
		for (String packageName : opens)
		{
			if (!desktop.isOpen(packageName, application))
			{
				throw new UnsupportedLookAndFeelException("Synthetica module access is unavailable");
			}
		}
	}

	private static void installFallback()
	{
		try
		{
			install(Theme.SYNTHETICA_DARK);
			current = Theme.SYNTHETICA_DARK;
		}
		catch (Exception | LinkageError ignored)
		{
			FlatDarkLaf.setup();
			current = Theme.FLAT_DARK;
		}
	}

	private static Window[] activeWindows()
	{
		return Arrays.stream(Window.getWindows())
			.filter(Window::isShowing)
			.filter(window -> window instanceof JFrame || window instanceof JDialog)
			.toArray(Window[]::new);
	}

	private static void prepareWindows(Window[] windows)
	{
		for (Window window : windows)
		{
			Color background = window.getBackground();
			if (background != null)
			{
				window.setBackground(new Color(background.getRGB(), true));
			}
			if (window instanceof RootPaneContainer)
			{
				JRootPane rootPane = ((RootPaneContainer) window).getRootPane();
				rootPane.setUI(new BasicRootPaneUI());
			}
		}
	}

	private static void refreshWindows(Window[] windows)
	{
		for (Window window : windows)
		{
			SwingUtilities.updateComponentTreeUI(window);
			Color background = UIManager.getColor("control");
			if (background != null)
			{
				window.setBackground(background);
			}
			window.invalidate();
			window.validate();
			window.repaint();
		}
	}

	public enum Theme
	{
		FLAT_DARK("flat-dark", "FlatLaf Dark"),
		FLAT_LIGHT("flat-light", "FlatLaf Light"),
		SYNTHETICA_BLACK_MOON("synthetica-black-moon", "Synthetica BlackMoon"),
		SYNTHETICA_BLUE_LIGHT("synthetica-blue-light", "Synthetica BlueLight"),
		SYNTHETICA_BLUE_STEEL("synthetica-blue-steel", "Synthetica BlueSteel"),
		SYNTHETICA_DARK("synthetica-dark", "Synthetica Dark"),
		SYNTHETICA_GREEN_DREAM("synthetica-green-dream", "Synthetica GreenDream"),
		SYNTHETICA_MAUVE_METALLIC("synthetica-mauve-metallic", "Synthetica MauveMetallic"),
		SYNTHETICA_ORANGE_METALLIC("synthetica-orange-metallic", "Synthetica OrangeMetallic");

		private final String setting;
		private final String displayName;

		Theme(String setting, String displayName)
		{
			this.setting = setting;
			this.displayName = displayName;
		}

		public String setting()
		{
			return setting;
		}

		static Theme fromSetting(String value)
		{
			String requested = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
			for (Theme theme : values())
			{
				if (theme.setting.equals(requested))
				{
					return theme;
				}
			}
			return SYNTHETICA_DARK;
		}

		@Override
		public String toString()
		{
			return displayName;
		}
	}
}
