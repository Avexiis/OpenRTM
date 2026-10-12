# Debugger

[All guides](README.md) | Requires a connected console

The Debugger receives live console events and controls the running title. These controls can interrupt a game at sensitive points; save progress before using them.

## Session and Events

Click **Attach**. Allow OpenRTM to receive connections from the console through the computer's local-network firewall. Use **Override existing debugger** only when deliberately replacing another attached debugger.

**Pause** stops execution, **Continue** resumes it, and **Refresh** updates the execution state, threads, and modules. **Detach** ends the debugger session.

Debug Output lists event times, types, threads, locations, and details, including debug messages, exceptions, execution changes, module/section activity, and thread changes. Click column headings to sort, **Clear** to empty the displayed events, or **Save Log** to save them as text.

Under **Break on**, choose first-chance exceptions, debug messages, thread creation, stack traces, or module loads, then click **Apply**. These choices control which events stop execution, not which events are displayed.

## Breakpoints

Choose a location and breakpoint type, then **Add**:

- Software execute.
- Hardware execute.
- Read, write, or read/write access.

Hardware types also offer a size of 1, 2, 4, or 8 bytes. **Remove** deletes the selected breakpoint; **Clear All** removes the listed breakpoints.

## Threads and Registers

**Refresh Threads** updates the thread list and stop reasons. Select a thread to **Halt**, **Continue**, **Continue Exception**, **Suspend**, or **Resume** it. Continue Exception passes the exception onward rather than treating it as handled. Suspending and resuming a thread is separate from pausing and continuing the whole title.

To inspect or edit registers, select a stopped thread and click **Read Context**. Edit the integer or control register values in the table, then **Write Context**. Changes are only accepted while that same thread is still stopped.

## Modules and Cleanup

The **Modules** tab lists loaded modules; **Refresh Modules** updates it. Loading and unloading belong on [Module Manager](module-manager.md).

Remove breakpoints and detach normally before leaving a title when possible. If the console stops responding, avoid repeated commands and wait for it to become available again. Check logs for sensitive console information before sharing them.
