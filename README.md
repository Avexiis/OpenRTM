# OpenRTM

OpenRTM is a Linux-focused tool for working with a modified Xbox 360 over your local network. It can display and record a video capture device, show console information, send console commands, debug running titles, transfer files and folders, organize installed content, inspect Xbox 360 packages, work with Xbox ISO files, and move game saves between profiles.

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

## Video Capture

The Video Capture page displays an HDMI capture card inside OpenRTM and can also open a detached viewer. Choose the video device, optional audio device, decoder, resolution, frame rate, and capture mode. The preview starts automatically when a capture device is available.

**Source resolution** leaves the capture card's incoming resolution unchanged. The **1080p** and **720p** choices request that exact capture mode without changing it when the viewer window is resized. **60 FPS** is suited to cards that can sustain it, while **30 FPS** can stabilize inexpensive cards at 1080p.

**Lowest latency** minimizes buffering. **High frame rate** requests the card's MJPEG mode when available. **Compatibility** uses more buffering for devices that do not remain stable in the faster modes. The **Automatic** decoder tries the available capture paths in order; FFmpeg and OpenCV can also be selected directly.

Select **Detached viewer** to open a separate display. With **Lock viewer size** selected, its video area stays at the selected resolution. Clearing the lock allows the existing stream to stretch with the window without requesting a higher capture resolution.

**Record** saves an MP4 file, including the selected audio input when available. **Screenshot** saves the current frame as a PNG. Both use the selected capture folder. If the capture card is disconnected or loses its HDMI signal, the viewer shows **No input source detected** and keeps checking for the signal to return.

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

Inside `Hdd:\Content`, Xbox 360 profile folders contain subfolders named with title IDs. OpenRTM shows a known game or application name in place of the raw title ID. The same conversion is used for the shared `0000000000000000` folder. Content type folders are also shown with names such as Saved Game, Marketplace Content, and Title Update.

The console folder is not renamed. Hover over or click the **ID** button beside a converted name to see the real folder name.

## Content Library

The Content Library reads packages stored under `Hdd:\Content` and groups the important details into a table. It shows whether an item belongs to shared content or a profile, the game or application name, the content type, file name, and size.

Click **Refresh Library** after connecting. Select an item and click **Show Selected Path** to see its exact owner, title ID, content type folder, and console path.

To install a package:

1. Click **Install Packages**.
2. Select one or more Xbox 360 content packages.
3. Wait while OpenRTM reads each package and creates its proper path under `Hdd:\Content`.

Existing packages with the same name are verified. Matching packages are left alone, while incomplete or corrupt copies are repaired. Use the Game Saves page for offline profile reassignment.

## Package Manager

The Package Manager opens CON, LIVE, and PIRS Xbox 360 packages. It shows package metadata, assignment IDs, header status, thumbnails, the suggested console folder, and the files stored inside an STFS package.

CON packages can be edited and re-signed. You can change their display text, title ID, profile ID, console ID, device ID, and images. Click **Save, Rehash & Resign** to update the loaded file or **Save As** to create another copy. A backup is enabled by default when replacing the loaded file.

LIVE and PIRS packages can be inspected and have their internal files extracted, but they cannot be edited because their original signing keys are not available.

To extract a file from a package, select it in the Package Files table and click **Extract Selected File**.

## ISO Extractor

The ISO Extractor page includes the bundled `extract-xiso` tool. It requires 64-bit Linux. On other operating systems, the page is locked and shows a Linux requirement message.

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

## Game Saves

Open a downloaded save to view its current profile, console, and device IDs. Enter your own IDs, then save it in place or to another file. After the save is rehashed and re-signed, OpenRTM asks whether you want to keep those IDs as a reusable profile. If you do, enter a short label. Using the profile's gamertag makes it easy to recognize later.

Loading another save always shows that save's original IDs. To replace them with a saved set, choose a label from the **Profiles** dropdown. The three ID fields update immediately.

## Module Manager

The Module Manager allows you to see and interact with what modules are currently loaded and running on your console.

You will have the options to:

* **Load** inject and load selected module.
* **Unload** unload selected module entirely.
* **Reload** unload and reload selected module.
* **TitleID Spoofing** spoofs whatever game/program you're running to appear as dashboard. Though this is not needed as most stealth servers (if you're playing online) have this built in.

Currently, the loading only supports injecting modules from the console storage and not from your PC. This is something we will look into at a later date.


## Platform

OpenRTM was developed on and for Linux. Most features use Java and can work on other platforms, but the bundled ISO tool is a 64-bit Linux program.
