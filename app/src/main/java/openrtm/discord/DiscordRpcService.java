package openrtm.discord;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.entities.ActivityType;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.StatusDisplayType;
import com.jagrosh.discordipc.entities.pipe.PipeStatus;
import openrtm.console.ConsoleService;
import openrtm.titleids.TitleIds;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class DiscordRpcService implements AutoCloseable
{
	private static final String CONFIG_RESOURCE = "/openrtm/discord/discord-rpc.properties";
	private static final long REFRESH_INTERVAL_SECONDS = 30;

	private final ConsoleService consoleService;
	private final long applicationId;
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r ->
	{
		Thread thread = new Thread(r, "openrtm-discord-rpc");
		thread.setDaemon(true);
		return thread;
	});
	private boolean enabled;
	private boolean consoleConnected;
	private boolean closed;
	private ScheduledFuture<?> refreshTask;
	private IPCClient client;
	private String currentTitleId;
	private long titleStartedAt;

	public DiscordRpcService(ConsoleService consoleService)
	{
		this.consoleService = consoleService;
		applicationId = loadApplicationId();
	}

	public boolean configured()
	{
		return applicationId > 0;
	}

	public synchronized void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
		updateTask();
	}

	public synchronized void consoleConnected()
	{
		consoleConnected = true;
		updateTask();
	}

	public synchronized void consoleDisconnected()
	{
		consoleConnected = false;
		stopTask();
	}

	@Override
	public synchronized void close()
	{
		if (closed)
		{
			return;
		}
		closed = true;
		consoleConnected = false;
		stopTask();
		executor.shutdownNow();
	}

	private void updateTask()
	{
		if (!closed && configured() && enabled && consoleConnected)
		{
			if (refreshTask == null || refreshTask.isDone())
			{
				refreshTask = executor.scheduleWithFixedDelay(this::refresh,
					0, REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
			}
		}
		else
		{
			stopTask();
		}
	}

	private void stopTask()
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(true);
			refreshTask = null;
		}
		closeClient();
		currentTitleId = null;
		titleStartedAt = 0;
	}

	private void refresh()
	{
		String titleId;
		try
		{
			titleId = consoleService.currentTitleId();
		}
		catch (RuntimeException ignored)
		{
			return;
		}

		synchronized (this)
		{
			if (closed || !enabled || !consoleConnected)
			{
				return;
			}
			if (!titleId.equals(currentTitleId))
			{
				currentTitleId = titleId;
				titleStartedAt = Instant.now().getEpochSecond();
			}
			try
			{
				ensureClient();
				client.sendRichPresence(presence(titleId, titleStartedAt));
			}
			catch (Exception ignored)
			{
				closeClient();
			}
		}
	}

	private void ensureClient() throws Exception
	{
		if (client != null && client.getStatus() == PipeStatus.CONNECTED)
		{
			return;
		}
		client = new IPCClient(applicationId);
		client.connect();
	}

	private RichPresence presence(String titleId, long startedAt)
	{
		String title = TitleIds.find(titleId).map(TitleIds.Title::name).orElse(titleId);
		return new RichPresence.Builder()
			.setActivityType(ActivityType.Playing)
			.setStatusDisplayType(StatusDisplayType.Details)
			.setName("Xbox 360")
			.setDetails(title)
			.setState("")
			.setStartTimestamp(startedAt)
			.build();
	}

	private void closeClient()
	{
		IPCClient active = client;
		client = null;
		if (active == null || active.getStatus() != PipeStatus.CONNECTED)
		{
			return;
		}
		try
		{
			active.sendRichPresence(null);
		}
		catch (RuntimeException ignored)
		{
		}
		try
		{
			active.close();
		}
		catch (RuntimeException ignored)
		{
		}
	}

	private static long loadApplicationId()
	{
		Properties properties = new Properties();
		try (InputStream stream = DiscordRpcService.class.getResourceAsStream(CONFIG_RESOURCE))
		{
			if (stream == null)
			{
				return 0;
			}
			properties.load(stream);
			long value = Long.parseLong(properties.getProperty("applicationId", "").trim());
			return value > 0 ? value : 0;
		}
		catch (IOException | RuntimeException ignored)
		{
			return 0;
		}
	}
}
