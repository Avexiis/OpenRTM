package openrtm.console;

import org.junit.jupiter.api.Test;

import static openrtm.console.DebuggerService.BreakpointType.READ_WRITE;
import static openrtm.console.DebuggerService.BreakpointType.SOFTWARE_EXECUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DebuggerServiceTest
{
	@Test
	void parsesDebugStringNotification()
	{
		DebuggerService.DebugEvent event = DebuggerService.parseEvent(
			"debugstr thread=0x0000002A lf string=loading default.xex and symbols");

		assertEquals(DebuggerService.EventType.DEBUG_STRING, event.type());
		assertEquals("loading default.xex and symbols", event.details());
		assertEquals(0x2AL, event.threadId());
		assertNull(event.address());
	}

	@Test
	void parsesDevkitExecutionNotification()
	{
		DebuggerService.DebugEvent event = DebuggerService.parseEvent("execution stopped");

		assertEquals(DebuggerService.EventType.EXECUTION, event.type());
		assertEquals(DebuggerService.ExecutionState.STOPPED,
			DebuggerService.parseExecutionState(event.details()));
	}

	@Test
	void parsesDevkitDataBreakpointNotification()
	{
		DebuggerService.DebugEvent event = DebuggerService.parseEvent(
			"data write=0x83000000 addr=0x82001000 thread=0x2A stop");

		assertEquals(DebuggerService.EventType.DATA_BREAK, event.type());
		assertEquals(0x2AL, event.threadId());
		assertEquals(0x82001000L, event.address());
	}

	@Test
	void parsesExceptionAddressAndThread()
	{
		DebuggerService.DebugEvent event = DebuggerService.parseEvent(
			"exception code=0xC0000005 thread=0x12 address=0x82001000 read=0x4");

		assertEquals(DebuggerService.EventType.EXCEPTION, event.type());
		assertEquals(0x12L, event.threadId());
		assertEquals(0x82001000L, event.address());
	}

	@Test
	void formatsDevkitBreakpointCommands()
	{
		DebuggerService.Breakpoint software = new DebuggerService.Breakpoint(
			SOFTWARE_EXECUTE, 0x82001000L, 8);
		DebuggerService.Breakpoint data = new DebuggerService.Breakpoint(
			READ_WRITE, 0x83002000L, 4);

		assertEquals("break addr=0x82001000", DebuggerService.breakpointCommand(software, false));
		assertEquals("break addr=0x82001000 clear", DebuggerService.breakpointCommand(software, true));
		assertEquals("break readwrite=0x83002000 size=4", DebuggerService.breakpointCommand(data, false));
		assertEquals("break readwrite=0x83002000 size=4 clear", DebuggerService.breakpointCommand(data, true));
	}

	@Test
	void parsesSignedDevkitThreadIdsAsUnsigned32BitValues()
	{
		assertEquals(0xFB00_0008L, DebuggerService.parseThreadId("-83886072"));
		assertEquals(42L, DebuggerService.parseThreadId("42"));
	}

	@Test
	void treatsDevkitNotStoppedResponseAsRunningThreadState()
	{
		assertEquals("Running", DebuggerService.threadStopReason("408- not stopped"));
		assertEquals("Stopped", DebuggerService.threadStopReason("200- stopped"));
	}

}
