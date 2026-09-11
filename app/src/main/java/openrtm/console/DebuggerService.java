package openrtm.console;

import com.jjrpc.JRPC;
import com.jjrpc.xdevkit.XbdmNotificationSession;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DebuggerService
{
	private static final Pattern FIELD = Pattern.compile(
		"(?i)([a-z][a-z0-9_]*)=(\"(?:\\\\.|[^\"])*\"|\\S+)");

	private final ConsoleService console;
	private final Object sessionLock = new Object();
	private final Set<Breakpoint> breakpoints = new LinkedHashSet<>();
	private volatile Consumer<DebugEvent> eventConsumer = ignored -> {
	};
	private XbdmNotificationSession notificationSession;

	DebuggerService(ConsoleService console)
	{
		this.console = Objects.requireNonNull(console, "console");
	}

	public void eventConsumer(Consumer<DebugEvent> consumer)
	{
		eventConsumer = Objects.requireNonNull(consumer, "consumer");
	}

	public boolean attached()
	{
		synchronized (sessionLock)
		{
			return notificationSession != null && notificationSession.isRunning();
		}
	}

	public void attach(boolean overrideExisting) throws IOException
	{
		synchronized (sessionLock)
		{
			if (notificationSession != null && notificationSession.isRunning())
			{
				throw new IllegalStateException("The OpenRTM debugger is already attached");
			}
			notificationSession = null;
		}

		StringBuilder attach = new StringBuilder("debugger connect");
		if (overrideExisting)
		{
			attach.append(" override");
		}
		attach.append(" name=").append(JRPC.XbdmXboxConsole.quoteXbdm("OpenRTM"));
		String user = System.getProperty("user.name", "OpenRTM");
		attach.append(" user=").append(JRPC.XbdmXboxConsole.quoteXbdm(user));

		XbdmNotificationSession opened = XbdmNotificationSession.open(
			console.debuggerHost(), console.debuggerPort(), attach.toString(),
			line -> emit(parseEvent(line)), this::notificationFailed);
		synchronized (sessionLock)
		{
			if (notificationSession != null)
			{
				opened.close();
				throw new IllegalStateException("The OpenRTM debugger is already attached");
			}
			notificationSession = opened;
		}

		emit(DebugEvent.session("Debugger attached"));
	}

	public void detach()
	{
		XbdmNotificationSession session;
		synchronized (sessionLock)
		{
			session = notificationSession;
			notificationSession = null;
		}
		if (session == null)
		{
			return;
		}

		RuntimeException failure = null;
		try
		{
			clearAllBreakpoints();
		}
		catch (RuntimeException problem)
		{
			failure = problem;
		}
		try
		{
			command("debugger disconnect");
		}
		catch (RuntimeException problem)
		{
			failure = preserve(failure, problem);
		}
		finally
		{
			session.close();
		}
		emit(DebugEvent.session("Debugger detached"));
		if (failure != null)
		{
			throw failure;
		}
	}

	void connectionClosing()
	{
		XbdmNotificationSession session;
		synchronized (sessionLock)
		{
			session = notificationSession;
			notificationSession = null;
		}
		synchronized (breakpoints)
		{
			breakpoints.clear();
		}
		if (session != null)
		{
			session.close();
			emit(DebugEvent.session("Debugger connection closed"));
		}
	}

	public ExecutionState executionState()
	{
		return parseExecutionState(statusMessage(command("getexecstate")));
	}

	public static ExecutionState parseExecutionState(String value)
	{
		String state = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		return switch (state)
		{
			case "start", "started" -> ExecutionState.RUNNING;
			case "stop", "stopped" -> ExecutionState.STOPPED;
			case "pending" -> ExecutionState.PENDING;
			case "reboot", "rebooting" -> ExecutionState.REBOOTING;
			case "pending_title" -> ExecutionState.TITLE_PENDING;
			case "reboot_title" -> ExecutionState.TITLE_REBOOTING;
			default -> ExecutionState.UNKNOWN;
		};
	}

	public void pause()
	{
		command("stop");
	}

	public void resume()
	{
		command("go");
	}

	public void applyStopConditions(Set<StopCondition> conditions)
	{
		Set<StopCondition> selected = conditions == null || conditions.isEmpty()
			? Collections.emptySet() : EnumSet.copyOf(conditions);
		command("nostopon all");
		if (!selected.isEmpty())
		{
			StringBuilder value = new StringBuilder("stopon");
			selected.forEach(condition -> value.append(' ').append(condition.token()));
			command(value.toString());
		}
	}

	public void setBreakpoint(Breakpoint breakpoint)
	{
		Objects.requireNonNull(breakpoint, "breakpoint");
		command(breakpointCommand(breakpoint, false));
		synchronized (breakpoints)
		{
			breakpoints.add(breakpoint);
		}
	}

	public void clearBreakpoint(Breakpoint breakpoint)
	{
		Objects.requireNonNull(breakpoint, "breakpoint");
		command(breakpointCommand(breakpoint, true));
		synchronized (breakpoints)
		{
			breakpoints.remove(breakpoint);
		}
	}

	public void clearAllBreakpoints()
	{
		List<Breakpoint> copy;
		synchronized (breakpoints)
		{
			copy = List.copyOf(breakpoints);
		}
		RuntimeException failure = null;
		for (Breakpoint breakpoint : copy)
		{
			try
			{
				clearBreakpoint(breakpoint);
			}
			catch (RuntimeException problem)
			{
				failure = preserve(failure, problem);
			}
		}
		if (failure != null)
		{
			throw failure;
		}
	}

	public List<Breakpoint> breakpoints()
	{
		synchronized (breakpoints)
		{
			return List.copyOf(breakpoints);
		}
	}

	public List<ThreadInfo> threads()
	{
		List<ThreadInfo> result = new ArrayList<>();
		for (String line : bodyLines(command("threads")))
		{
			long threadId;
			try
			{
				threadId = parseThreadId(line);
			}
			catch (NumberFormatException ignored)
			{
				continue;
			}
			try
			{
				String infoResponse = command("threadinfo thread=" + hex(threadId));
				Map<String, String> fields = fields(responsePayload(infoResponse));
				String stopReason = threadStopReason(console.rawCommand("isstopped thread=" + hex(threadId)));
				result.add(new ThreadInfo(threadId,
					parseNumber(fields.get("priority")), parseNumber(fields.get("suspend")),
					parseNumber(fields.get("start")), parseNumber(fields.get("base")),
					parseNumber(fields.get("limit")), parseNumber(fields.get("proc")),
					parseNumber(fields.get("lasterr")), stopReason));
			}
			catch (RuntimeException ignored)
			{
			}
		}
		return result;
	}

	public List<RegisterValue> threadContext(long threadId)
	{
		String response = command("getcontext thread=" + hex(threadId) + " control int");
		List<RegisterValue> values = new ArrayList<>();
		fields(responsePayload(response)).forEach((name, value) ->
			values.add(new RegisterValue(name, value)));
		return values;
	}

	public void haltThread(long threadId)
	{
		command("halt thread=" + hex(threadId));
	}

	public void continueThread(long threadId, boolean passException)
	{
		command("continue thread=" + hex(threadId) + (passException ? " exception" : ""));
	}

	public void suspendThread(long threadId)
	{
		command("suspend thread=" + hex(threadId));
	}

	public void resumeThread(long threadId)
	{
		command("resume thread=" + hex(threadId));
	}

	public List<ModuleInfo> modules()
	{
		List<ModuleInfo> result = new ArrayList<>();
		for (String line : bodyLines(command("modules")))
		{
			Map<String, String> fields = fields(line);
			result.add(new ModuleInfo(fields.getOrDefault("name", ""),
				parseNumber(fields.get("base")), parseNumber(fields.get("size")),
				parseNumber(fields.get("check")), containsToken(line, "dll")));
		}
		return result;
	}

	static DebugEvent parseEvent(String line)
	{
		String raw = line == null ? "" : line.trim();
		int space = raw.indexOf(' ');
		String token = (space < 0 ? raw : raw.substring(0, space)).toLowerCase(Locale.ROOT);
		EventType type = switch (token)
		{
			case "debugstr" -> EventType.DEBUG_STRING;
			case "execution" -> EventType.EXECUTION;
			case "break" -> EventType.BREAKPOINT;
			case "data" -> EventType.DATA_BREAK;
			case "singlestep" -> EventType.SINGLE_STEP;
			case "assert" -> EventType.ASSERTION;
			case "rip" -> EventType.RIP;
			case "exception" -> EventType.EXCEPTION;
			case "modload" -> EventType.MODULE_LOAD;
			case "modunload" -> EventType.MODULE_UNLOAD;
			case "create" -> EventType.THREAD_CREATE;
			case "terminate" -> EventType.THREAD_DESTROY;
			case "sectload" -> EventType.SECTION_LOAD;
			case "sectunload" -> EventType.SECTION_UNLOAD;
			case "stacktrace" -> EventType.STACK_TRACE;
			default -> EventType.OTHER;
		};
		Map<String, String> values = fields(raw);
		Long thread = optionalNumber(values.get("thread"));
		Long address = optionalNumber(firstPresent(values, "address", "addr"));
		String details = space < 0 ? "" : raw.substring(space + 1).trim();
		if (type == EventType.DEBUG_STRING)
		{
			details = debugStringDetails(raw, details);
		}
		return new DebugEvent(Instant.now(), type, details, raw, thread, address);
	}

	static String breakpointCommand(Breakpoint breakpoint, boolean clear)
	{
		StringBuilder command = new StringBuilder("break ");
		if (breakpoint.type() == BreakpointType.SOFTWARE_EXECUTE)
		{
			command.append("addr=").append(hex(breakpoint.address()));
		}
		else
		{
			command.append(breakpoint.type().token()).append('=').append(hex(breakpoint.address()));
			command.append(" size=").append(breakpoint.size());
		}
		if (clear)
		{
			command.append(" clear");
		}
		return command.toString();
	}

	private String command(String value)
	{
		String response = console.rawCommand(value);
		int status = statusCode(response);
		if (status < 200 || status >= 300)
		{
			throw new IllegalStateException(value + " failed: " + response);
		}
		return response;
	}

	private void bestEffortCommand(String value)
	{
		try
		{
			command(value);
		}
		catch (RuntimeException ignored)
		{
		}
	}

	private void notificationFailed(Throwable failure)
	{
		synchronized (sessionLock)
		{
			if (notificationSession != null && !notificationSession.isRunning())
			{
				notificationSession = null;
			}
		}
		synchronized (breakpoints)
		{
			breakpoints.clear();
		}
		emit(new DebugEvent(Instant.now(), EventType.SESSION,
			"Debugger notification channel closed: " + usefulMessage(failure), "", null, null));
	}

	private void emit(DebugEvent event)
	{
		try
		{
			eventConsumer.accept(event);
		}
		catch (RuntimeException ignored)
		{
		}
	}

	private static RuntimeException preserve(RuntimeException first, RuntimeException next)
	{
		if (first == null)
		{
			return next;
		}
		first.addSuppressed(next);
		return first;
	}

	private static String usefulMessage(Throwable failure)
	{
		return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
	}

	private static String debugStringDetails(String raw, String fallback)
	{
		int at = raw.toLowerCase(Locale.ROOT).indexOf("string=");
		return at >= 0 ? unquote(raw.substring(at + 7)) : fallback;
	}

	private static Map<String, String> fields(String value)
	{
		Map<String, String> result = new LinkedHashMap<>();
		Matcher matcher = FIELD.matcher(value == null ? "" : value);
		while (matcher.find())
		{
			result.put(matcher.group(1).toLowerCase(Locale.ROOT), unquote(matcher.group(2)));
		}
		return result;
	}

	private static String unquote(String value)
	{
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\""))
		{
			return value.substring(1, value.length() - 1)
				.replace("\\\"", "\"").replace("\\\\", "\\");
		}
		return value;
	}

	private static String firstPresent(Map<String, String> values, String... keys)
	{
		for (String key : keys)
		{
			if (values.containsKey(key))
			{
				return values.get(key);
			}
		}
		return null;
	}

	private static boolean containsToken(String value, String token)
	{
		for (String part : value.split("\\s+"))
		{
			if (part.equalsIgnoreCase(token))
			{
				return true;
			}
		}
		return false;
	}

	static long parseThreadId(String value)
	{
		long parsed = Long.parseLong(value.trim(), 10);
		if (parsed < Integer.MIN_VALUE || parsed > 0xFFFF_FFFFL)
		{
			throw new NumberFormatException("Thread ID is outside the 32-bit range");
		}
		return parsed & 0xFFFF_FFFFL;
	}

	static String threadStopReason(String response)
	{
		int status = statusCode(response);
		if (status == 408)
		{
			return "Running";
		}
		if (status >= 200 && status < 300)
		{
			String reason = statusMessage(response).trim();
			return reason.isEmpty() ? "Stopped"
				: Character.toUpperCase(reason.charAt(0)) + reason.substring(1);
		}
		throw new IllegalStateException("isstopped failed: " + response);
	}

	private static long parseNumber(String value)
	{
		if (value == null || value.isBlank())
		{
			return 0;
		}
		String normalized = value.trim();
		int radix = 10;
		if (normalized.startsWith("0x") || normalized.startsWith("0X"))
		{
			normalized = normalized.substring(2);
			radix = 16;
		}
		return Long.parseUnsignedLong(normalized, radix);
	}

	private static Long optionalNumber(String value)
	{
		try
		{
			return value == null ? null : parseNumber(value);
		}
		catch (NumberFormatException ignored)
		{
			return null;
		}
	}

	private static int statusCode(String response)
	{
		if (response == null || response.length() < 3)
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(response.substring(0, 3));
		}
		catch (NumberFormatException ignored)
		{
			return 0;
		}
	}

	private static String statusMessage(String response)
	{
		if (response == null || response.isBlank())
		{
			return "";
		}
		String first = response.lines().findFirst().orElse("");
		if (statusCode(first) > 0 && first.length() > 3)
		{
			return first.substring(4).trim();
		}
		return first.trim();
	}

	private static String responsePayload(String response)
	{
		List<String> lines = response == null ? List.of() : response.lines().toList();
		if (lines.size() > 1)
		{
			return String.join(" ", lines.subList(1, lines.size()));
		}
		return statusMessage(response);
	}

	private static List<String> bodyLines(String response)
	{
		if (response == null)
		{
			return List.of();
		}
		List<String> lines = response.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
		if (!lines.isEmpty() && statusCode(lines.get(0)) > 0)
		{
			return lines.subList(1, lines.size());
		}
		return lines;
	}

	private static String hex(long value)
	{
		if (value < 0 || value > 0xFFFF_FFFFL)
		{
			throw new IllegalArgumentException("Xbox 360 addresses and thread IDs must fit in 32 bits");
		}
		return String.format(Locale.ROOT, "0x%08X", value);
	}

	public enum ExecutionState
	{
		RUNNING("Running"), STOPPED("Stopped"), PENDING("Pending"), REBOOTING("Rebooting"),
		TITLE_PENDING("Title pending"), TITLE_REBOOTING("Title rebooting"), UNKNOWN("Unknown");

		private final String label;

		ExecutionState(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public enum StopCondition
	{
		FIRST_CHANCE_EXCEPTION("First chance exceptions", "fce"),
		DEBUG_STRING("DbgPrint", "debugstr"),
		THREAD_CREATE("Thread creation", "createthread"),
		STACK_TRACE("Stack traces", "stacktrace"),
		MODULE_LOAD("Module loads", "modload");

		private final String label;
		private final String token;

		StopCondition(String label, String token)
		{
			this.label = label;
			this.token = token;
		}

		public String token()
		{
			return token;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public enum BreakpointType
	{
		SOFTWARE_EXECUTE("Software execute", "addr"), READ("Read", "read"), WRITE("Write", "write"),
		READ_WRITE("Read/write", "readwrite"), HARDWARE_EXECUTE("Hardware execute", "execute");

		private final String label;
		private final String token;

		BreakpointType(String label, String token)
		{
			this.label = label;
			this.token = token;
		}

		String token()
		{
			return token;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public enum EventType
	{
		SESSION("Session"), DEBUG_STRING("DbgPrint"), EXCEPTION("Exception"), EXECUTION("Execution"),
		BREAKPOINT("Breakpoint"), DATA_BREAK("Data break"), SINGLE_STEP("Single step"),
		ASSERTION("Assertion"), RIP("RIP"), MODULE_LOAD("Module load"), MODULE_UNLOAD("Module unload"),
		THREAD_CREATE("Thread create"), THREAD_DESTROY("Thread destroy"), SECTION_LOAD("Section load"),
		SECTION_UNLOAD("Section unload"), STACK_TRACE("Stack trace"), OTHER("Other");

		private final String label;

		EventType(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	public record Breakpoint(BreakpointType type, long address, int size)
	{
		public Breakpoint
		{
			Objects.requireNonNull(type, "type");
			if (address < 0 || address > 0xFFFF_FFFFL)
			{
				throw new IllegalArgumentException("Breakpoint address must fit in 32 bits");
			}
			if (type == BreakpointType.SOFTWARE_EXECUTE)
			{
				size = 1;
			}
			else if (size != 1 && size != 2 && size != 4 && size != 8)
			{
				throw new IllegalArgumentException("Hardware breakpoint size must be 1, 2, 4, or 8 bytes");
			}
		}

		@Override
		public String toString()
		{
			return type + " at " + hex(address) + (type == BreakpointType.SOFTWARE_EXECUTE ? "" : " (" + size + " bytes)");
		}
	}

	public record DebugEvent(Instant timestamp, EventType type, String details, String raw,
	                         Long threadId, Long address)
	{
		static DebugEvent session(String details)
		{
			return new DebugEvent(Instant.now(), EventType.SESSION, details, "", null, null);
		}
	}

	public record ThreadInfo(long id, long priority, long suspendCount, long startAddress,
	                         long stackBase, long stackLimit, long processor, long lastError,
	                         String stopReason)
	{
		public boolean stopped()
		{
			return stopReason != null && !stopReason.isBlank();
		}
	}

	public record RegisterValue(String name, String value)
	{
	}

	public record ModuleInfo(String name, long baseAddress, long size, long checksum, boolean dll)
	{
	}
}
