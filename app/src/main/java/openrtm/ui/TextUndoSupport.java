package openrtm.ui;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import javax.swing.undo.UndoManager;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ContainerEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public final class TextUndoSupport
{
	private static final String INSTALLED = "openrtm.undo.installed";
	private static final String MANAGER = "openrtm.undo.manager";

	private TextUndoSupport()
	{
	}

	public static void install()
	{
		Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
			if (event instanceof ContainerEvent && event.getID() == ContainerEvent.COMPONENT_ADDED)
			{
				install(((ContainerEvent) event).getChild());
			}
		}, AWTEvent.CONTAINER_EVENT_MASK);
	}

	private static void install(Component component)
	{
		if (component instanceof JTextComponent)
		{
			install((JTextComponent) component);
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				install(child);
			}
		}
	}

	private static void install(JTextComponent text)
	{
		if (Boolean.TRUE.equals(text.getClientProperty(INSTALLED)))
		{
			return;
		}
		text.putClientProperty(INSTALLED, Boolean.TRUE);
		installDocument(text);
		text.addPropertyChangeListener("document", event -> installDocument(text));
		int shortcut = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		text.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut), "openrtm-undo");
		text.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, shortcut), "openrtm-redo");
		text.getInputMap(JComponent.WHEN_FOCUSED).put(
			KeyStroke.getKeyStroke(KeyEvent.VK_Z, shortcut | InputEvent.SHIFT_DOWN_MASK), "openrtm-redo");
		text.getActionMap().put("openrtm-undo", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent event)
			{
				UndoManager manager = (UndoManager) text.getClientProperty(MANAGER);
				if (manager != null && manager.canUndo())
				{
					manager.undo();
				}
			}
		});
		text.getActionMap().put("openrtm-redo", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent event)
			{
				UndoManager manager = (UndoManager) text.getClientProperty(MANAGER);
				if (manager != null && manager.canRedo())
				{
					manager.redo();
				}
			}
		});
	}

	private static void installDocument(JTextComponent text)
	{
		UndoManager manager = new UndoManager();
		text.getDocument().addUndoableEditListener(manager);
		text.putClientProperty(MANAGER, manager);
	}
}
