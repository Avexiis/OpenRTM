package openrtm.ui;

import openrtm.console.ConsoleService;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.Container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DebuggerPanelTest
{
	@Test
	void exposesDebuggerWorkspacesWithoutAConsoleConnection()
	{
		DebuggerPanel panel = new DebuggerPanel(new ConsoleService().debugger(), (label, action) -> {
		});

		JTabbedPane tabs = find(panel, JTabbedPane.class);
		assertNotNull(tabs);
		assertEquals("Breakpoints", tabs.getTitleAt(0));
		assertEquals("Threads", tabs.getTitleAt(1));
		assertEquals("Modules", tabs.getTitleAt(2));
		JButton detach = findButton(panel, "Detach");
		JButton pause = findButton(panel, "Pause");
		assertNotNull(detach);
		assertNotNull(pause);
		assertFalse(detach.isEnabled());
		assertFalse(pause.isEnabled());
	}

	private static JButton findButton(Container root, String text)
	{
		for (Component child : root.getComponents())
		{
			if (child instanceof JButton button && text.equals(button.getText()))
			{
				return button;
			}
			if (child instanceof Container container)
			{
				JButton found = findButton(container, text);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}

	private static <T> T find(Container root, Class<T> type)
	{
		for (Component child : root.getComponents())
		{
			if (type.isInstance(child))
			{
				return type.cast(child);
			}
			if (child instanceof Container container)
			{
				T found = find(container, type);
				if (found != null)
				{
					return found;
				}
			}
		}
		return null;
	}
}
