package com.jjrpc.xdevkit;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XbdmNotificationSessionTest
{
	@Test
	void establishesDedicatedNotificationConnectionAndReceivesEvents() throws Exception
	{
		List<String> commands = new CopyOnWriteArrayList<>();
		List<String> notifications = new CopyOnWriteArrayList<>();
		CountDownLatch received = new CountDownLatch(2);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (ServerSocket server = loopbackServer())
		{
			Future<?> served = executor.submit(() -> serveNotificationChannel(server, commands,
				List.of("debugstr thread=0x10 string=hello", "execution stopped"), true));
			XbdmNotificationSession session = XbdmNotificationSession.open(
				"127.0.0.1", server.getLocalPort(), "debugger connect override name=\"OpenRTM\" user=\"xeon\"",
				line -> {
					notifications.add(line);
					received.countDown();
				}, failure -> {
					throw new AssertionError(failure);
				});
			try
			{
				assertTrue(received.await(1, TimeUnit.SECONDS));
				assertEquals(List.of("debugger connect override name=\"OpenRTM\" user=\"xeon\"",
					"notify reconnectport=0"), commands);
				assertEquals(List.of("debugstr thread=0x10 string=hello", "execution stopped"), notifications);
			}
			finally
			{
				session.close();
			}
			served.get(1, TimeUnit.SECONDS);
			assertFalse(session.isRunning());
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	void reportsDebuggerAttachRejection() throws Exception
	{
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (ServerSocket server = loopbackServer())
		{
			Future<?> served = executor.submit(() -> {
				try (Socket socket = server.accept();
				     BufferedReader reader = reader(socket);
				     BufferedWriter writer = writer(socket))
				{
					writeLine(writer, "201- connected");
					assertEquals("debugger connect name=\"OpenRTM\"", reader.readLine());
					writeLine(writer, "400- debugger already connected");
				}
				return null;
			});

			IOException failure = assertThrows(IOException.class, () -> XbdmNotificationSession.open(
				"127.0.0.1", server.getLocalPort(), "debugger connect name=\"OpenRTM\"",
				line -> {
				}, problem -> {
				}));
			assertTrue(failure.getMessage().contains("400- debugger already connected"));
			served.get(1, TimeUnit.SECONDS);
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	void disconnectsDebuggerWhenNotificationSubscriptionIsRejected() throws Exception
	{
		List<String> commands = new CopyOnWriteArrayList<>();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (ServerSocket server = loopbackServer())
		{
			Future<?> served = executor.submit(() -> {
				try (Socket socket = server.accept();
				     BufferedReader reader = reader(socket);
				     BufferedWriter writer = writer(socket))
				{
					writeLine(writer, "201- connected");
					commands.add(reader.readLine());
					writeLine(writer, "200- OK");
					commands.add(reader.readLine());
					writeLine(writer, "400- notification unavailable");
					commands.add(reader.readLine());
					writeLine(writer, "200- OK");
				}
				return null;
			});

			IOException failure = assertThrows(IOException.class, () -> XbdmNotificationSession.open(
				"127.0.0.1", server.getLocalPort(), "debugger connect",
				line -> {
				}, problem -> {
				}));
			assertTrue(failure.getMessage().contains("400- notification unavailable"));
			served.get(1, TimeUnit.SECONDS);
			assertEquals(List.of("debugger connect", "notify reconnectport=0", "debugger disconnect"),
				commands);
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	@Test
	void reportsUnexpectedNotificationChannelClosure() throws Exception
	{
		CountDownLatch failed = new CountDownLatch(1);
		List<Throwable> failures = new CopyOnWriteArrayList<>();
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try (ServerSocket server = loopbackServer())
		{
			Future<?> served = executor.submit(() -> serveNotificationChannel(server,
				new CopyOnWriteArrayList<>(), List.of(), false));
			XbdmNotificationSession session = XbdmNotificationSession.open(
				"127.0.0.1", server.getLocalPort(), "debugger connect",
				line -> {
				}, failure -> {
					failures.add(failure);
					failed.countDown();
				});

			served.get(1, TimeUnit.SECONDS);
			assertTrue(failed.await(1, TimeUnit.SECONDS));
			assertFalse(session.isRunning());
			assertTrue(failures.get(0).getMessage().contains("closed"));
			session.close();
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	private static Void serveNotificationChannel(ServerSocket server, List<String> commands,
	                                             List<String> notifications,
	                                             boolean awaitClientClose) throws Exception
	{
		try (Socket socket = server.accept();
		     BufferedReader reader = reader(socket);
		     BufferedWriter writer = writer(socket))
		{
			writeLine(writer, "201- connected");
			commands.add(reader.readLine());
			writeLine(writer, "200- OK");
			commands.add(reader.readLine());
			writeLine(writer, "205- now a notification channel");
			for (String notification : notifications)
			{
				writeLine(writer, notification);
			}
			if (awaitClientClose)
			{
				assertEquals(-1, reader.read());
			}
		}
		return null;
	}

	private static ServerSocket loopbackServer() throws IOException
	{
		return new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
	}

	private static BufferedReader reader(Socket socket) throws IOException
	{
		return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
	}

	private static BufferedWriter writer(Socket socket) throws IOException
	{
		return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
	}

	private static void writeLine(BufferedWriter writer, String line) throws IOException
	{
		writer.write(line);
		writer.write("\r\n");
		writer.flush();
	}
}
