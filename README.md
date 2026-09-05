# OpenRTM

OpenRTM is a Linux-focused RTM tool for working with a modified Xbox 360 over your local network, written in Java. It can show console information, send basic console commands, read and write memory, transfer files and folders, work with Xbox ISO files, and reassign game saves.

OpenRTM is intended for RGH, JTAG, and BadUpdate/aBadAvatar consoles. Only use it with game backups and files that you legally own.

## What you need

* A modified Xbox 360 connected to the same network as your computer.
* XBDM.xex and JRPC.xex/JRPC2.xex set as plugins.
* The IP local address of your console.
* Java 17 or newer on your computer.
* A 64-bit Linux to use the bundled ISO extractor - other features should work on other platforms.

## Starting OpenRTM

Double click, or run the OpenRTM jar file from a terminal:

```text
java -jar OpenRTM.jar
```

Enter the console IP address at the top of the window and click **Connect**. A successful connection shows `OpenRTM Connected!` on the Xbox 360.

OpenRTM remembers recently used console addresses. Enable **Autoconnect** to connect to the last console when the program starts. If an idle connection is lost, OpenRTM waits a few seconds between reconnection attempts.

## Home

The Home page shows information reported by the connected console, including its IP address, CPU key, gamertag, current title, console type, kernel version, console ID, SMC version, and temperatures.

Click **Refresh Info** at the top of the window to read the information again.

The console controls can:

* Show a custom notification on the Xbox 360
* Perform a warm or cold reboot
* Shut down the console
* Open or close the disc tray

Save any open work before using the reboot or shutdown controls.

## Memory and commands

The Memory page can read bytes from a console memory address. Enter an address and a length, then click **Read**. The result is shown as hexadecimal bytes.

To write memory, enter an address and either:

* Choose a value type, enter a value, and click **Write Typed**
* Enter hexadecimal bytes in the larger box and click **Write Hex**

The Commands page sends a raw XBDM command and shows the console response.

Memory writes and raw commands are advanced features. An incorrect address, value, or command can crash the current game or freeze the console.

## Debugger

The Debugger page can receive live debugging messages from the console and control a running title. Connect to the console first, open the Debugger page, then click **Attach**.

The console opens a separate connection back to OpenRTM for debugger messages. Your computer's firewall must allow OpenRTM to receive connections from the local network. The callback uses a temporary port chosen each time the debugger is attached.

Enable **Override existing debugger** only when the console reports that another debugger is already attached, and you intend to replace it.

The debugger controls can:

* Pause and continue the running title
* Show DbgPrint and other debug strings, exceptions, execution changes, module changes, and thread events
* Stop on selected exception, debug string, thread, stack trace, or module events
* Add and remove software or hardware breakpoints
* List threads, halt or continue a thread, change its suspend count, and read its registers
* List the modules loaded by the current title
* Save the displayed output to a text log

For a software breakpoint, enter the instruction address and choose **Software execute**. Hardware breakpoints can watch a 1, 2, 4, or 8 byte address range for reads, writes, both, or execution. Remove breakpoints before leaving a title when possible. OpenRTM also clears the breakpoints it created when you detach normally.

Select a thread before using the thread controls. **Continue Exception** passes the current exception back to the title while continuing that thread. A normal **Continue** resumes it without passing the exception.

Pause, thread halt, break conditions, and breakpoints can interrupt a title at sensitive points. If the console stops responding, avoid repeatedly sending more commands. Detach or reconnect after the console becomes available again.

DbgPrint output forwarded by XBDM is captured automatically after attaching. The separate KD network transport used for early boot and kernel debugging is not enabled by this page.

## File transfer

The File Transfer page shows files on your computer on the left and console storage on the right. Expand a folder to load its contents.

### Uploading

1. Select a file or folder on the left.
2. Select the destination folder on the right.
3. Click **Upload ->**.

When a folder is selected, the folder itself is created inside the chosen console folder. All files and subfolders are copied into it. For example, uploading a local folder named `MW2` to `Hdd:\Games` creates `Hdd:\Games\MW2`.

Uploads check data that is already present on the console. If a connection drops, OpenRTM reconnects, checks how much data arrived, repairs mismatched data when supported, and continues the upload.

### Downloading

1. Select a file or folder on the right.
2. Select the destination folder on the left.
3. Click **<- Download**.

A downloaded folder keeps its files and subfolders. OpenRTM asks before replacing an existing destination.

Use **Cancel Transfer** to stop the current upload or download. Use the refresh buttons if either side changed outside OpenRTM.

You can also create a console folder or delete the selected console item. Deletion cannot be undone.

### Content folder names

Inside `Hdd:\Content`, Xbox 360 profile folders contain subfolders named with title IDs. OpenRTM shows a known game or application name in place of the raw title ID. The same conversion is used for the shared `0000000000000000` folder.

The console folder is not renamed. Hover over or click the **ID** button beside a converted name to see the real folder name.

## ISO Extractor

The ISO Extractor page includes the bundled `extract-xiso` tool. It currently requires 64-bit Linux.

Choose an operation from the list:

* **Extract** unpacks an Xbox ISO into an existing output folder.
* **List** shows the contents of an ISO without extracting it.
* **Create / Pack** builds an ISO from a selected folder.
* **Rewrite** rewrites an ISO into an existing output folder.

Use **Browse** to select the input and output paths, choose any needed options, then click **Run**. Output from the operation appears in the lower part of the page. Click **Cancel** to stop it.

**Skip $SystemUpdate** leaves the console update folder out of supported operations. **Disable XBE media patch** turns off the media patch used while creating or rewriting an image. **Delete original after rewrite** removes the source ISO after a successful rewrite, so use that option carefully.

Keep an untouched copy of an ISO until you have tested the extracted or rewritten result.

[extract-xiso](https://github.com/XboxDev/extract-xiso/tree/master) itself is copyright (c) in@fishtank.com, and is licensed under a [slightly
modified version of the Berkeley Software License](https://github.com/XboxDev/extract-xiso/blob/master/LICENSE.TXT). 

## Game Save Editor

The Game Save Editor changes the profile, console, and device assignment stored in an Xbox 360 game-save package. It then updates the package hashes and signature.

1. Click **Browse** and select a game save.
2. Click **Open** if the file was entered manually.
3. Review the package type, game title, size, header status, and signature status.
4. Enter the new Profile ID, Console ID, and Device ID.
5. Click **Save, Rehash & Resign** to update the original file, or click **Save As** to create a separate file.

The Profile ID must contain 16 hexadecimal characters. The Console ID must contain 10, and the Device ID must contain 40. Leaving one of these boxes empty clears that assignment.

**Create .bak backup** is enabled by default when replacing the original file. Leave it enabled unless you already have a separate copy. After saving, OpenRTM checks the new header and signature before reporting success.

##
#### This project was developed on and for Linux!
