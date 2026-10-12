# Memory & Commands

[All guides](README.md) | Requires a connected console

These are advanced controls. Incorrect input can crash a title, freeze the console, or change data unexpectedly. Use only known locations and commands appropriate to the running title.

## Memory

Enter a location and byte length, then click **Read** to view the data as hexadecimal bytes. To change data, enter hexadecimal bytes and click **Write Hex**, or choose a value type and use **Write Typed**.

Typed writes support:

- Byte and Bool.
- Unsigned 16-bit and 32-bit big-endian numbers.
- Signed 32-bit little-endian numbers.
- Null-terminated ASCII or UTF-16 big-endian text.

Choose the type that matches the data already stored there. Text length and number format matter; the tool does not know the intended meaning of arbitrary memory.

## Commands

Enter a console command and click **Send** from the **Commands** tab. Its response is shown on the page. This is a console-control command interface, not a computer terminal or a game chat box.

Prefer a dedicated OpenRTM page when it already provides the operation you need.
