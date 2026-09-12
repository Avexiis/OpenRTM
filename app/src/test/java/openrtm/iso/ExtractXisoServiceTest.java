package openrtm.iso;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtractXisoServiceTest
{
	@Test
	void selectsBundledBinariesForSupportedPlatforms() throws Exception
	{
		assertEquals("/openrtm/iso/extract-xiso",
			ExtractXisoService.resourceFor("linux", "x86_64"));
		assertEquals("/openrtm/iso/extract-xiso.exe",
			ExtractXisoService.resourceFor("windows 11", "amd64"));
	}

	@Test
	void rejectsUnsupportedSystemsAndProcessors()
	{
		assertThrows(IOException.class,
			() -> ExtractXisoService.resourceFor("mac os x", "x86_64"));
		assertThrows(IOException.class,
			() -> ExtractXisoService.resourceFor("windows 11", "aarch64"));
	}

	@Test
	void packagesBothSupportedBinaries()
	{
		assertNotNull(ExtractXisoService.class.getResource("/openrtm/iso/extract-xiso"));
		assertNotNull(ExtractXisoService.class.getResource("/openrtm/iso/extract-xiso.exe"));
	}
}
