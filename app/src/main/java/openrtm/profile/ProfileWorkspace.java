package openrtm.profile;

import openrtm.config.ProfileIdentityStore;
import openrtm.console.ConsoleService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProfileWorkspace
{
	private final ProfileService profiles = new ProfileService();
	private final ProfileIdentityStore identities;
	private final ProfileTransferService transfers;
	private final List<Runnable> listeners = new ArrayList<>();
	private Path path;
	private ProfileService.Profile profile;

	public ProfileWorkspace(ConsoleService console, ProfileIdentityStore identities)
	{
		this.identities = identities;
		transfers = new ProfileTransferService(console);
	}

	public synchronized ProfileService.Profile open(Path profilePath) throws IOException
	{
		ProfileService.Profile loaded = profiles.inspect(profilePath);
		identities.remember(loaded.profileId(), loaded.gamertag());
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

	public ProfileIdentityStore identities()
	{
		return identities;
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
