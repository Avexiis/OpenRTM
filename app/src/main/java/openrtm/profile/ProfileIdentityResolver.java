package openrtm.profile;

import openrtm.config.ProfileIdentityStore;
import openrtm.console.ConsoleService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfileIdentityResolver
{
	private final ProfileIdentityStore identities;
	private final ProfileService profiles = new ProfileService();
	private final Map<String, Long> unavailable = new ConcurrentHashMap<>();

	public ProfileIdentityResolver(ProfileIdentityStore identities)
	{
		this.identities = identities;
	}

	public ProfileIdentityStore identities()
	{
		return identities;
	}

	public String name(String profileId)
	{
		return identities.displayName(profileId);
	}

	public String remember(Path profile) throws IOException
	{
		ProfileService.Identity loaded = profiles.inspectIdentity(profile);
		identities.remember(loaded.profileId(), loaded.gamertag());
		unavailable.remove(loaded.profileId().toUpperCase(Locale.ROOT));
		return loaded.gamertag();
	}

	public String resolveRemote(ConsoleService console, String profileId)
	{
		String id = profileId == null ? "" : profileId.trim().toUpperCase(Locale.ROOT);
		if ("0000000000000000".equals(id))
		{
			return identities.displayName(id);
		}
		String known = identities.find(id).orElse(null);
		Long failedAt = unavailable.get(id);
		if (known != null || failedAt != null && System.currentTimeMillis() - failedAt < 30_000L)
		{
			return known == null ? "Unknown Profile" : known;
		}
		Path temporary = null;
		try
		{
			temporary = Files.createTempFile("openrtm-profile-", ".tmp");
			String remote = "Hdd:\\Content\\" + id + "\\FFFE07D1\\00010000\\" + id;
			console.downloadFile(remote, temporary);
			return remember(temporary);
		}
		catch (IOException | RuntimeException ignored)
		{
			unavailable.put(id, System.currentTimeMillis());
			return "Unknown Profile";
		}
		finally
		{
			if (temporary != null)
			{
				try
				{
					Files.deleteIfExists(temporary);
				}
				catch (IOException ignored)
				{
				}
			}
		}
	}
}
