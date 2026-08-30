package openrtm.games;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayloadStoreTest {
    @Test
    void loadsGeneratedPayloads() {
        PayloadStore store = new PayloadStore();
        assertEquals(196, store.blob("bo2.unlock").length);
        assertEquals(9248, store.blob("bo2.unlock2").length);
        assertEquals(65192, store.blob("bo2.unlockAll.data").length);
        assertEquals(5, store.operations("mw3.unlockAll").size());
        assertEquals(170, store.operations("bo1.legacyClassUnlock").size());
    }
}
