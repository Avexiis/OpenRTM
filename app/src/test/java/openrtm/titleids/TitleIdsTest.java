package openrtm.titleids;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleIdsTest {
    @Test
    void resolvesCanonicalAndPrefixedTitleIds() {
        assertEquals("Halo 3", TitleIds.displayName("4d5307e6"));
        assertEquals("Halo 3", TitleIds.displayName("0x4D5307E6"));
        assertEquals("4D5307E5", TitleIds.displayName("4D5307E5"));
    }

    @Test
    void exposesCanonicalIdForUiDetails() {
        TitleIds.Title title = TitleIds.find("0x545408b5").orElseThrow();
        assertEquals("545408B5", title.id());
        assertEquals("NBA 2K15", title.name());
        assertEquals("XeX Menu", TitleIds.displayName("c0de9999"));
        assertTrue(TitleIds.find("CODE9999").isEmpty());
        assertTrue(TitleIds.find("not-an-id").isEmpty());
    }
}
