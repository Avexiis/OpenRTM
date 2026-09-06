package openrtm.console;

import openrtm.stfs.PackageService;
import openrtm.titleids.TitleIds;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ContentLibraryService
{
	private static final Pattern HEX16 = Pattern.compile("(?i)[0-9A-F]{16}");
	private static final Pattern HEX8 = Pattern.compile("(?i)[0-9A-F]{8}");
	private static final String ROOT = "Hdd:\\Content\\";

	private final ConsoleService console;
	private final PackageService packages = new PackageService();

	public ContentLibraryService(ConsoleService console)
	{
		this.console = console;
	}

	public List<Item> scan(Progress progress)
	{
		Progress updates = progress == null ? message -> { } : progress;
		List<Item> items = new ArrayList<>();
		for (ConsoleService.FileEntry ownerEntry : console.listDirectory(ROOT))
		{
			String owner = leaf(ownerEntry.name()).toUpperCase(Locale.ROOT);
			if (!ownerEntry.directory() || !HEX16.matcher(owner).matches())
			{
				continue;
			}
			updates.update(owner.equals("0000000000000000") ? "Reading shared content" : "Reading profile " + owner);
			String ownerPath = ROOT + owner + "\\";
			for (ConsoleService.FileEntry titleEntry : console.listDirectory(ownerPath))
			{
				String titleId = leaf(titleEntry.name()).toUpperCase(Locale.ROOT);
				if (!titleEntry.directory() || !HEX8.matcher(titleId).matches())
				{
					continue;
				}
				String title = TitleIds.displayName(titleId);
				String titlePath = ownerPath + titleId + "\\";
				for (ConsoleService.FileEntry typeEntry : console.listDirectory(titlePath))
				{
					String typeFolder = leaf(typeEntry.name()).toUpperCase(Locale.ROOT);
					if (!typeEntry.directory() || !HEX8.matcher(typeFolder).matches())
					{
						continue;
					}
					int type = (int) Long.parseLong(typeFolder, 16);
					String typePath = titlePath + typeFolder + "\\";
					for (ConsoleService.FileEntry packageEntry : console.listDirectory(typePath))
					{
						if (!packageEntry.directory())
						{
							String name = leaf(packageEntry.name());
							items.add(new Item(owner, titleId, title, type, PackageService.contentTypeName(type),
								name, packageEntry.size(), typePath + name));
						}
					}
				}
			}
		}
		return List.copyOf(items);
	}

	public InstallResult install(Path localPackage, ConsoleService.TransferProgress progress) throws IOException
	{
		PackageService.Info info = packages.inspect(localPackage);
		String directory = PackageService.recommendedDirectory(info, null);
		console.ensureDirectory(directory);
		String name = PackageService.safeRemoteFileName(localPackage.getFileName().toString());
		boolean exists = console.listDirectory(directory).stream()
			.anyMatch(entry -> !entry.directory() && leaf(entry.name()).equalsIgnoreCase(name));
		String destination = directory + name;
		console.uploadFile(localPackage, destination, progress);
		return new InstallResult(info, destination, exists);
	}

	private static String leaf(String path)
	{
		String value = path == null ? "" : path.replace('/', '\\');
		int slash = value.lastIndexOf('\\');
		return slash < 0 ? value : value.substring(slash + 1);
	}

	@FunctionalInterface
	public interface Progress
	{
		void update(String message);
	}

	public record Item(String ownerId, String titleId, String title, int contentType, String contentTypeName,
	                   String fileName, long size, String remotePath)
	{
		public String ownerName()
		{
			return ownerId.equals("0000000000000000") ? "Shared Content" : ownerId;
		}
	}

	public record InstallResult(PackageService.Info packageInfo, String remotePath, boolean existingDestination)
	{
	}
}
