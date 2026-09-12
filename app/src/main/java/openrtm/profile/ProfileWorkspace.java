package openrtm.profile;

import openrtm.console.ConsoleService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProfileWorkspace
{
	private final ProfileService profiles = new ProfileService();
	private final ProfileTransferService transfers;
	private final List<Runnable> listeners = new ArrayList<>();
	private Path path;
	private ProfileService.Profile profile;

	public ProfileWorkspace(ConsoleService console)
	{
		transfers = new ProfileTransferService(console);
	}

	public synchronized ProfileService.Profile open(Path profilePath) throws IOException
	{
		ProfileService.Profile loaded = profiles.inspect(profilePath);
		path = loaded.path();
		profile = loaded;
		notifyListeners();
		return loaded;
	}

	public synchronized ProfileService.Profile refresh() throws IOException
	{
		if (path == null)
		{
			throw new IllegalStateException("Open a profile first");
		}
		return open(path);
	}

	public synchronized Path path()
	{
		return path;
	}

	public synchronized ProfileService.Profile profile()
	{
		return profile;
	}

	public ProfileService profiles()
	{
		return profiles;
	}

	public ProfileTransferService transfers()
	{
		return transfers;
	}

	public synchronized void addListener(Runnable listener)
	{
		listeners.add(listener);
	}

	private void notifyListeners()
	{
		for (Runnable listener : listeners)
		{
			listener.run();
		}
	}
}
