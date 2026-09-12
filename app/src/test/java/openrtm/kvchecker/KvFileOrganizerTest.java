package openrtm.kvchecker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KvFileOrganizerTest
{
	@TempDir
	Path temporaryDirectory;

	@Test
	void discoversKeyVaultFilesAtEveryDepth() throws Exception
	{
		Path nested = Files.createDirectories(temporaryDirectory.resolve("one").resolve("two"));
		Path first = Files.write(temporaryDirectory.resolve("kv.bin"), new byte[]{1});
		Path second = Files.write(nested.resolve("KV.BIN"), new byte[]{2});
		Files.write(nested.resolve("other.bin"), new byte[]{3});

		KvFileOrganizer.Discovery discovery = new KvFileOrganizer().discover(temporaryDirectory);

		assertEquals(List.of(first, second), discovery.files());
		assertEquals(0, discovery.unreadableEntries());
	}

	@Test
	void movesCheckedFilesIntoTheirResultAndConsoleFolders() throws Exception
	{
		Path source = Files.write(temporaryDirectory.resolve("source.bin"), new byte[]{1, 2, 3});
		Path output = Files.createDirectory(temporaryDirectory.resolve("sorted"));

		Path destination = new KvFileOrganizer().move(source, output, "077340711208",
			KvCheckerService.Result.UNBANNED);

		assertEquals(output.resolve("unbanned").resolve("077340711208").resolve("KV.bin"),
			destination);
		assertTrue(Files.exists(destination));
		assertFalse(Files.exists(source));
	}

	@Test
	void doesNotOverwriteAnExistingKeyVault() throws Exception
	{
		Path source = Files.write(temporaryDirectory.resolve("source.bin"), new byte[]{1});
		Path output = Files.createDirectory(temporaryDirectory.resolve("sorted"));
		Path destination = Files.createDirectories(output.resolve("banned").resolve("077340711208"))
			.resolve("KV.bin");
		Files.write(destination, new byte[]{2});

		assertThrows(FileAlreadyExistsException.class, () -> new KvFileOrganizer().move(source,
			output, "077340711208", KvCheckerService.Result.BANNED));
		assertTrue(Files.exists(source));
		assertEquals(2, Files.readAllBytes(destination)[0]);
	}
}
