package openrtm.profile;

import openrtm.profile.BioPresetCatalog.BioPreset;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BioPresetCatalogTest
{
	@Test
	void loadsEveryBundledPresetWithinTheProfileLimit() throws Exception
	{
		List<BioPreset> presets = BioPresetCatalog.load();

		assertEquals(22, presets.size());
		assertTrue(presets.stream().anyMatch(preset -> preset.name().equals("Modern Warfare")));
		assertTrue(presets.stream().anyMatch(preset -> preset.name().equals("Shotz")));
		assertTrue(presets.stream().allMatch(preset -> preset.content().length() <= 499));
	}

	@Test
	void rejectsUnsupportedPresetVersions()
	{
		String input = "{\"version\":2,\"presets\":[]}";

		assertThrows(IOException.class, () -> BioPresetCatalog.parse(new StringReader(input)));
	}

	@Test
	void preservesStructuredPresetLineSpacing() throws Exception
	{
		String input = "{\"version\":1,\"presets\":["
			+ "{\"name\":\"First\",\"lines\":[\"  line one  \",\" line two\"]},"
			+ "{\"name\":\"Second\",\"lines\":[\"content\"]}]}";

		List<BioPreset> presets = BioPresetCatalog.parse(new StringReader(input));

		assertEquals(2, presets.size());
		assertEquals("  line one  \n line two", presets.get(0).content());
		assertEquals("content", presets.get(1).content());
	}

	@Test
	void symbolPaletteIncludesEverySpecialCharacterUsedByPresets() throws Exception
	{
		List<BioPreset> presets = BioPresetCatalog.load();
		Map<String, List<String>> categories = BioSymbolCatalog.create(presets);

		assertTrue(categories.get("Frames").contains("┏"));
		assertTrue(categories.get("Arrows").contains("→"));
		assertTrue(categories.get("Miscellaneous").contains("ㅁ"));
	}
}
