package openrtm.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class AppSettings
{
	private static final int MAX_RECENT_HOSTS = 12;

	private final Path configDirectory;
	private final Path settingsFile;
	private final Properties properties = new Properties();

	public AppSettings()
	{
		this(Path.of(System.getProperty("user.home"), ".openrtm"));
	}

	AppSettings(Path configDirectory)
	{
		this.configDirectory = configDirectory;
		this.settingsFile = configDirectory.resolve("settings.properties");
		load();
	}

	public synchronized Path configDirectory()
	{
		return configDirectory;
	}

	public synchronized Optional<String> lastHost()
	{
		return clean(properties.getProperty("lastHost")).stream().findFirst();
	}

	public synchronized List<String> recentHosts()
	{
		List<String> hosts = new ArrayList<>();
		for (String raw : properties.getProperty("recentHosts", "").split(","))
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
		List<String> bounded = hosts.stream().limit(MAX_RECENT_HOSTS).toList();
		properties.setProperty("lastHost", value);
		properties.setProperty("recentHosts", String.join(",", bounded));
		save();
	}

	public synchronized boolean autoConnect()
	{
		return Boolean.parseBoolean(properties.getProperty("autoConnect", "false"));
	}

	public synchronized void autoConnect(boolean value)
	{
		properties.setProperty("autoConnect", Boolean.toString(value));
		save();
	}

	private void load()
	{
		if (!Files.isRegularFile(settingsFile))
		{
			return;
		}
		try (InputStream in = Files.newInputStream(settingsFile))
		{
			properties.load(in);
		}
		catch (IOException ignored)
		{
		}
	}

	private void save()
	{
		try
		{
			Files.createDirectories(configDirectory);
			try (OutputStream out = Files.newOutputStream(settingsFile))
			{
				properties.store(out, "OpenRTM settings");
			}
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Could not save settings to " + settingsFile, e);
		}
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
