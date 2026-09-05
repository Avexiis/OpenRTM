package openrtm.ui.files;

import org.junit.jupiter.api.Test;
import openrtm.titleids.TitleIds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileBrowserPanelTest {
    @Test
    void resolvesTitleFoldersBelowSharedAndProfileContentOwners() {
        TitleIds.Title shared = FileBrowserPanel.contentTitle(
                "Hdd:\\Content\\0000000000000000\\", "545408B5", true).orElseThrow();
        TitleIds.Title profile = FileBrowserPanel.contentTitle(
                "hdd:/content/E0000123456789AB", "4d5307e6", true).orElseThrow();
        TitleIds.Title homebrew = FileBrowserPanel.contentTitle(
                "Hdd:\\Content\\0000000000000000", "C0DE9999", true).orElseThrow();

        assertEquals("NBA 2K15", shared.name());
        assertEquals("Halo 3", profile.name());
        assertEquals("XeX Menu", homebrew.name());
    }

    @Test
    void doesNotAliasFilesUnknownIdsOrFoldersOutsideContentOwnerLevel() {
        assertTrue(FileBrowserPanel.contentTitle(
                "Hdd:\\Content\\0000000000000000", "545408B5", false).isEmpty());
        assertTrue(FileBrowserPanel.contentTitle(
                "Hdd:\\Content\\0000000000000000", "12345678", true).isEmpty());
        assertTrue(FileBrowserPanel.contentTitle(
                "Hdd:\\Content\\0000000000000000\\545408B5", "00000002", true).isEmpty());
        assertTrue(FileBrowserPanel.contentTitle(
                "Usb0:\\Content\\0000000000000000", "545408B5", true).isEmpty());
    }
}
