package openrtm.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class AppSettings
{
	private static final int MAX_RECENT_HOSTS = 12;

	private final ConfigManager config;

	public AppSettings()
	{
		this(ConfigManager.shared());
	}

	AppSettings(Path configDirectory)
	{
		this(new ConfigManager(configDirectory));
	}

	AppSettings(ConfigManager config)
	{
		this.config = config;
	}

	public synchronized Path configDirectory()
	{
		return config.directory();
	}

	public synchronized Optional<String> lastHost()
	{
		return clean(config.string("console.lastHost").orElse(null));
	}

	public synchronized List<String> recentHosts()
	{
		List<String> hosts = new ArrayList<>();
		for (String raw : config.strings("console.recentHosts"))
		{
			clean(raw).ifPresent(hosts::add);
		}
		return hosts;
	}

	public synchronized void rememberHost(String host)
	{
		String value = clean(host).orElse("");
		if (value.isBlank())
		{
			return;
		}
		Set<String> hosts = new LinkedHashSet<>();
		hosts.add(value);
		hosts.addAll(recentHosts());
		List<String> bounded = new ArrayList<>();
		hosts.stream().limit(MAX_RECENT_HOSTS).forEach(bounded::add);
		config.put("console.lastHost", value);
		config.putStrings("console.recentHosts", bounded);
	}

	public synchronized boolean autoConnect()
	{
		return config.bool("console.autoConnect", false);
	}

	public synchronized void autoConnect(boolean value)
	{
		config.put("console.autoConnect", value);
	}

	private static Optional<String> clean(String value)
	{
		if (value == null)
		{
			return Optional.empty();
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
	}
}
