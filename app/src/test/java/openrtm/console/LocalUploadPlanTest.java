package openrtm.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalUploadPlanTest {
    @TempDir
    Path tempDirectory;

    @Test
    void preservesNestedFilesAndEmptyFolders() throws Exception {
        Path source = Files.createDirectory(tempDirectory.resolve("Game Folder"));
        Files.createDirectory(source.resolve("empty"));
        Path nested = Files.createDirectories(source.resolve("content").resolve("maps"));
        Files.write(source.resolve("default.xex"), new byte[]{1, 2, 3});
        Files.write(nested.resolve("arena.map"), new byte[]{4, 5, 6, 7});

        LocalUploadPlan.Plan plan = LocalUploadPlan.build(source, "Hdd:\\Games\\Game Folder");
        Map<String, LocalUploadPlan.Entry> entries = plan.entries().stream()
                .collect(Collectors.toMap(LocalUploadPlan.Entry::remotePath, entry -> entry));

        assertEquals(7, plan.totalBytes());
        assertEquals(6, entries.size());
        assertTrue(entries.get("Hdd:\\Games\\Game Folder").directory());
        assertTrue(entries.get("Hdd:\\Games\\Game Folder\\empty").directory());
        assertTrue(entries.get("Hdd:\\Games\\Game Folder\\content\\maps").directory());
        assertEquals(3, entries.get("Hdd:\\Games\\Game Folder\\default.xex").size());
        assertEquals(4, entries.get("Hdd:\\Games\\Game Folder\\content\\maps\\arena.map").size());
    }

    @Test
    void extractsSafeLeafNamesFromConsolePaths() {
        assertEquals("default.xex", LocalUploadPlan.remoteLeaf("Hdd:\\Games\\Halo\\default.xex"));
        assertEquals("Halo", LocalUploadPlan.remoteLeaf("Hdd:\\Games\\Halo\\"));
    }
}
