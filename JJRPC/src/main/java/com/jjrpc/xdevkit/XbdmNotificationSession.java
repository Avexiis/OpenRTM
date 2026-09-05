package com.jjrpc.xdevkit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class XbdmNotificationSession implements AutoCloseable
{
	private static final int CONNECT_TIMEOUT_MS = 5_000;
	private static final int HANDSHAKE_TIMEOUT_MS = 10_000;

	private final Socket socket;
	private final BufferedReader reader;
	private final Consumer<String> notificationHandler;
	private final Consumer<Throwable> failureHandler;
	private final AtomicBoolean running = new AtomicBoolean(true);
	private final Thread worker;

	private XbdmNotificationSession(Socket socket, BufferedReader reader,
	                                Consumer<String> notificationHandler,
	                                Consumer<Throwable> failureHandler)
	{
		this.socket = socket;
		this.reader = reader;
		this.notificationHandler = notificationHandler;
		this.failureHandler = failureHandler;
		this.worker = new Thread(this::listen, "jjrpc-xbdm-notify");
		this.worker.setDaemon(true);
		this.worker.start();
	}

	public static XbdmNotificationSession open(String host, int port, String debuggerCommand,
	                                           Consumer<String> notificationHandler,
	                                           Consumer<Throwable> failureHandler) throws IOException
	{
		if (host == null || host.isBlank())
		{
			throw new IllegalArgumentException("XBDM host is required");
		}
		if (port < 1 || port > 65_535)
		{
			throw new IllegalArgumentException("XBDM port is out of range");
		}
		if (debuggerCommand == null || debuggerCommand.isBlank())
		{
			throw new IllegalArgumentException("Debugger attach command is required");
		}
		Objects.requireNonNull(notificationHandler, "notificationHandler");
		Objects.requireNonNull(failureHandler, "failureHandler");

		Socket socket = new Socket();
		BufferedReader reader = null;
		BufferedWriter writer = null;
		boolean debuggerAttached = false;
		try
		{
			socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
			socket.setKeepAlive(true);
			socket.setTcpNoDelay(true);
			socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
			reader = new BufferedReader(new InputStreamReader(
				socket.getInputStream(), StandardCharsets.US_ASCII));
			writer = new BufferedWriter(new OutputStreamWriter(
				socket.getOutputStream(), StandardCharsets.US_ASCII));

			expectSuccess("XBDM connection", readLine(reader));
			writeCommand(writer, debuggerCommand);
			expectSuccess(debuggerCommand, readLine(reader));
			debuggerAttached = true;
			writeCommand(writer, "notify reconnectport=0");
			expectSuccess("notify reconnectport=0", readLine(reader));

			socket.setSoTimeout(0);
			return new XbdmNotificationSession(socket, reader, notificationHandler, failureHandler);
		}
		catch (IOException | RuntimeException failure)
		{
			if (debuggerAttached)
			{
				disconnectDebuggerQuietly(writer, reader);
			}
			closeQuietly(socket);
			throw failure;
		}
	}

	public boolean isRunning()
	{
		return running.get();
	}

	private void listen()
	{
		Throwable failure = null;
		try
		{
			String line;
			while (running.get() && (line = reader.readLine()) != null)
			{
				try
				{
					notificationHandler.accept(line);
				}
				catch (RuntimeException ignored)
				{
				}
			}
			if (running.get())
			{
				failure = new EOFException("XBDM closed the debugger notification channel");
			}
		}
		catch (IOException problem)
		{
			if (running.get())
			{
				failure = problem;
			}
		}
		finally
		{
			boolean unexpectedlyClosed = running.getAndSet(false);
			closeQuietly(socket);
			if (unexpectedlyClosed && failure != null)
			{
				failureHandler.accept(failure);
			}
		}
	}

	@Override
	public void close()
	{
		if (!running.getAndSet(false))
		{
			return;
		}
		closeQuietly(socket);
		worker.interrupt();
	}

	private static void writeCommand(BufferedWriter writer, String command) throws IOException
	{
		writer.write(command);
		writer.write("\r\n");
		writer.flush();
	}

	private static String readLine(BufferedReader reader) throws IOException
	{
		String line = reader.readLine();
		if (line == null)
		{
			throw new EOFException("XBDM closed the connection during debugger setup");
		}
		return line;
	}

	private static void expectSuccess(String operation, String response) throws IOException
	{
		int status = response.length() >= 3 && Character.isDigit(response.charAt(0))
			&& Character.isDigit(response.charAt(1)) && Character.isDigit(response.charAt(2))
			? Integer.parseInt(response.substring(0, 3)) : -1;
		if (status < 200 || status >= 300)
		{
			throw new IOException(operation + " failed: " + response);
		}
	}

	private static void disconnectDebuggerQuietly(BufferedWriter writer, BufferedReader reader)
	{
		try
		{
			writeCommand(writer, "debugger disconnect");
			readLine(reader);
		}
		catch (IOException | RuntimeException ignored)
		{
		}
	}

	private static void closeQuietly(Socket socket)
	{
		try
		{
			socket.close();
		}
		catch (IOException ignored)
		{
		}
	}
}
