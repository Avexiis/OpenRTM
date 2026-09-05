package openrtm.iso;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExtractXisoServiceTest {
    private static final Path TOOL = Path.of("/tmp/extract-xiso");
    private static final Path ISO = Path.of("/games/My Game.iso");
    private static final Path DIRECTORY = Path.of("/games/My Game");

    @Test
    void buildsExtractCommand() {
        ExtractXisoService.Request request = new ExtractXisoService.Request(
                ExtractXisoService.Mode.EXTRACT, ISO, DIRECTORY, true, false, false);

        assertEquals(List.of("/tmp/extract-xiso", "-s", "-x", "-d", "/games/My Game", "/games/My Game.iso"),
                ExtractXisoService.arguments(TOOL, request));
    }

    @Test
    void buildsListCommand() {
        ExtractXisoService.Request request = new ExtractXisoService.Request(
                ExtractXisoService.Mode.LIST, ISO, null, false, false, false);

        assertEquals(List.of("/tmp/extract-xiso", "-l", "/games/My Game.iso"),
                ExtractXisoService.arguments(TOOL, request));
    }

    @Test
    void buildsCreateCommand() {
        ExtractXisoService.Request request = new ExtractXisoService.Request(
                ExtractXisoService.Mode.CREATE, DIRECTORY, ISO, true, true, false);

        assertEquals(List.of("/tmp/extract-xiso", "-s", "-m", "-c", "/games/My Game", "/games/My Game.iso"),
                ExtractXisoService.arguments(TOOL, request));
    }

    @Test
    void buildsRewriteCommand() {
        ExtractXisoService.Request request = new ExtractXisoService.Request(
                ExtractXisoService.Mode.REWRITE, ISO, DIRECTORY, true, true, true);

        assertEquals(List.of("/tmp/extract-xiso", "-s", "-m", "-r", "-D", "-d", "/games/My Game", "/games/My Game.iso"),
                ExtractXisoService.arguments(TOOL, request));
    }

    @Test
    void bundledBinaryCanBeExtractedAndExecuted() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        String architecture = System.getProperty("os.arch", "").toLowerCase();
        assumeTrue(os.contains("linux") && (architecture.equals("amd64") || architecture.equals("x86_64")));

        ExtractXisoService service = new ExtractXisoService();
        Process process = new ProcessBuilder(service.executable().toString(), "-v").start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertEquals(0, process.waitFor());
        assertTrue(output.contains("extract-xiso v2.7.1"));
    }
}
