package openrtm.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HexUtilsTest {
    @Test
    void parsesFlexibleHex() {
        assertArrayEquals(new byte[]{0x60, 0x00, 0x00, 0x00}, HexUtils.parseHex("0x60 00,00-00"));
    }

    @Test
    void parsesAddresses() {
        assertEquals(0x824015E0L, HexUtils.parseAddress("0x824015E0"));
        assertEquals(0x824015E0L, HexUtils.parseAddress("824015E0"));
        assertEquals(1234L, HexUtils.parseAddress("1234"));
    }
}
