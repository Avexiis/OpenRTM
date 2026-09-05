package openrtm.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class GameSaveEditorPanelTest {
    @Test
    void createsDefaultChooserWhenPathHasNotBeenSelected() {
        assertNotNull(GameSaveEditorPanel.chooserFor(""));
        assertNotNull(GameSaveEditorPanel.chooserFor("   "));
        assertNotNull(GameSaveEditorPanel.chooserFor(null));
    }
}
