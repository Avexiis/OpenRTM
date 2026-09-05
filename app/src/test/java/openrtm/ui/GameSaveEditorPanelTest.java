package openrtm.ui;

import org.junit.jupiter.api.Test;
import openrtm.stfs.GameSaveService;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameSaveEditorPanelTest {
    @Test
    void createsDefaultChooserWhenPathHasNotBeenSelected() {
        assertNotNull(GameSaveEditorPanel.chooserFor(""));
        assertNotNull(GameSaveEditorPanel.chooserFor("   "));
        assertNotNull(GameSaveEditorPanel.chooserFor(null));
    }

    @Test
    void loadedPackageValuesAreNotClippedToPlaceholderWidth() {
        GameSaveEditorPanel panel = new GameSaveEditorPanel((label, action) -> {
        });
        panel.showInfo(new GameSaveService.Info(
                Path.of("savegame"),
                "E0000123456789AB",
                "0123456789",
                "00112233445566778899AABBCCDDEEFF00112233",
                "454107D9",
                1,
                "Xbox 360 Saved Game",
                122_880,
                0xA000,
                true,
                true), "Ready");

        panel.setSize(1_000, 650);
        layoutAll(panel);

        assertFullyVisible(panel, "Xbox 360 Saved Game");
        assertFullyVisible(panel, "NFS Most Wanted");
        assertFullyVisible(panel, "Package size");
    }

    private static void assertFullyVisible(Container root, String text) {
        JLabel label = findLabel(root, text);
        assertNotNull(label, "Missing label: " + text);
        assertTrue(label.getWidth() >= label.getPreferredSize().width,
                () -> text + " is clipped to " + label.getWidth() + " pixels");
    }

    private static JLabel findLabel(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JLabel label && text.equals(label.getText())) return label;
            if (component instanceof Container child) {
                JLabel match = findLabel(child, text);
                if (match != null) return match;
            }
        }
        return null;
    }

    private static void layoutAll(Component component) {
        if (!(component instanceof Container container)) return;
        container.doLayout();
        for (Component child : container.getComponents()) layoutAll(child);
    }
}
