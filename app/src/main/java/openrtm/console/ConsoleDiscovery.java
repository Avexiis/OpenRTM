package openrtm.console;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ConsoleDiscovery
{
	private static final int XBDM_PORT = 730;
	private static final int UDP_TIMEOUT_MS = 1_500;
	private static final int PROBE_TIMEOUT_MS = 220;
	private static final int PROBE_THREADS = 24;

	public List<DiscoveredConsole> scan()
	{
		Map<String, DiscoveredConsole> found = new LinkedHashMap<>();
		udpScan(found);
		if (found.isEmpty())
		{
			tcpScan(found);
		}
		return found.values().stream()
			.sorted(Comparator.comparing(DiscoveredConsole::displayName, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private static void udpScan(Map<String, DiscoveredConsole> found)
	{
		try (DatagramSocket socket = new DatagramSocket(null))
		{
			socket.setReuseAddress(true);
			socket.setBroadcast(true);
			socket.bind(new InetSocketAddress(0));
			byte[] request = {3, 0};
			for (InetAddress address : broadcastAddresses())
			{
				try
				{
					socket.send(new DatagramPacket(request, request.length, address, XBDM_PORT));
				}
				catch (IOException | SecurityException ignored)
				{
				}
			}

			long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(UDP_TIMEOUT_MS);
			while (System.nanoTime() < deadline)
			{
				long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
				socket.setSoTimeout((int) Math.max(1, Math.min(250, remaining)));
				byte[] buffer = new byte[512];
				DatagramPacket response = new DatagramPacket(buffer, buffer.length);
				try
				{
					socket.receive(response);
					DiscoveredConsole console = parseReply(response);
					if (console != null)
					{
						found.putIfAbsent(console.address(), console);
					}
				}
				catch (SocketTimeoutException ignored)
				{
				}
			}
		}
		catch (IOException | SecurityException ignored)
		{
		}
	}

	private static DiscoveredConsole parseReply(DatagramPacket packet)
	{
		byte[] data = packet.getData();
		int offset = packet.getOffset();
		int length = packet.getLength();
		if (length < 2 || data[offset] != 2)
		{
			return null;
		}
		int nameLength = Byte.toUnsignedInt(data[offset + 1]);
		if (nameLength > length - 2)
		{
			return null;
		}
		String name = new String(data, offset + 2, nameLength, StandardCharsets.US_ASCII).trim();
		return new DiscoveredConsole(name, packet.getAddress().getHostAddress());
	}

	private static Set<InetAddress> broadcastAddresses() throws IOException
	{
		Set<InetAddress> addresses = new LinkedHashSet<>();
		Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
		while (interfaces.hasMoreElements())
		{
			NetworkInterface network = interfaces.nextElement();
			if (!network.isUp() || network.isLoopback())
			{
				continue;
			}
			for (InterfaceAddress address : network.getInterfaceAddresses())
			{
				if (address.getBroadcast() != null)
				{
					addresses.add(address.getBroadcast());
				}
			}
		}
		addresses.add(InetAddress.getByName("255.255.255.255"));
		return addresses;
	}

	private static void tcpScan(Map<String, DiscoveredConsole> found)
	{
		List<InetAddress> candidates = subnetCandidates();
		if (candidates.isEmpty())
		{
			return;
		}
		ExecutorService probes = Executors.newFixedThreadPool(Math.min(PROBE_THREADS, candidates.size()), runnable -> {
			Thread thread = new Thread(runnable, "openrtm-console-scan");
			thread.setDaemon(true);
			return thread;
		});
		try
		{
			List<Callable<DiscoveredConsole>> tasks = candidates.stream()
				.<Callable<DiscoveredConsole>>map(address -> () -> probe(address))
				.toList();
			for (Future<DiscoveredConsole> future : probes.invokeAll(tasks, 5, TimeUnit.SECONDS))
			{
				if (!future.isCancelled())
				{
					DiscoveredConsole console = future.get();
					if (console != null)
					{
						found.putIfAbsent(console.address(), console);
					}
				}
			}
		}
		catch (InterruptedException interrupted)
		{
			Thread.currentThread().interrupt();
		}
		catch (Exception ignored)
		{
		}
		finally
		{
			probes.shutdownNow();
		}
	}

	private static List<InetAddress> subnetCandidates()
	{
		Set<String> addresses = new LinkedHashSet<>();
		try
		{
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements())
			{
				NetworkInterface network = interfaces.nextElement();
				if (!network.isUp() || network.isLoopback())
				{
					continue;
				}
				for (InterfaceAddress binding : network.getInterfaceAddresses())
				{
					if (!(binding.getAddress() instanceof Inet4Address local))
					{
						continue;
					}
					byte[] bytes = local.getAddress();
					for (int host = 1; host < 255; host++)
					{
						if (Byte.toUnsignedInt(bytes[3]) != host)
						{
							addresses.add(Byte.toUnsignedInt(bytes[0]) + "." + Byte.toUnsignedInt(bytes[1])
								+ "." + Byte.toUnsignedInt(bytes[2]) + "." + host);
						}
					}
				}
			}
		}
		catch (SocketException ignored)
		{
		}
		List<InetAddress> result = new ArrayList<>();
		for (String address : addresses)
		{
			try
			{
				result.add(InetAddress.getByName(address));
			}
			catch (IOException ignored)
			{
			}
		}
		return result;
	}

	private static DiscoveredConsole probe(InetAddress address)
	{
		try (Socket socket = new Socket())
		{
			socket.connect(new InetSocketAddress(address, XBDM_PORT), PROBE_TIMEOUT_MS);
			socket.setSoTimeout(PROBE_TIMEOUT_MS);
			BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
			String banner = reader.readLine();
			if (banner == null || !banner.startsWith("201"))
			{
				return null;
			}
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
			writer.write("dbgname\r\n");
			writer.flush();
			String response = reader.readLine();
			String name = response == null || response.length() <= 4 ? "" : response.substring(4).trim();
			return new DiscoveredConsole(name, address.getHostAddress());
		}
		catch (IOException | SecurityException ignored)
		{
			return null;
		}
	}

	public record DiscoveredConsole(String name, String address)
	{
		public String displayName()
		{
			return name == null || name.isBlank() ? address : name + " (" + address + ")";
		}

		@Override
		public String toString()
		{
			return displayName();
		}
	}
}
