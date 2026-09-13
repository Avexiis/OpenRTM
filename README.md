# OpenRTM

OpenRTM is a Windows and Linux desktop tool for working with a modified Xbox 360. It can connect to a console over the local network, display and record a capture card, transfer files, organize installed content, edit gamer profiles and achievements, inspect and edit Xbox 360 packages, manage game saves, work with Xbox storage devices and ISO images, check key vaults, and load modules.

OpenRTM is intended for RGH, JTAG, and BadUpdate/aBadAvatar consoles. Only use it with game backups and files that you legally own.

## Requirements

Connected-console features require:

* A modified Xbox 360 on the same local network as the computer
* `XBDM.xex` and `JRPC.xex` or `JRPC2.xex` configured as console plugins
* The console's local IP address or hostname

The desktop application requires Java 17 or newer. Video capture and the ISO tools are packaged for 64-bit Windows and Linux. Offline tools such as Package Manager, Game Saves, ISO Extractor, and Xbox Storage can be used without connecting to a console.

## Starting OpenRTM

Double-click `OpenRTM.jar`, or start it from a terminal:

```text
java -jar OpenRTM.jar
```

Enter the console address in the top row and click **Connect**. A successful connection displays `OpenRTM Connected!` on the Xbox 360 and loads console information and storage drives.

OpenRTM remembers recently used addresses. **Autoconnect** connects to the last selected console when the application starts. While a connection is active, OpenRTM periodically checks it and attempts to reconnect after a network interruption. Use **Disconnect** to stop reconnecting deliberately.

## Common Controls

The theme selector in the upper-right corner is available on every page. Synthetica Dark is the default. The available themes are:

* FlatLaf Dark and FlatLaf Light
* Synthetica BlackMoon, BlueLight, BlueSteel, Dark, GreenDream, MauveMetallic, and OrangeMetallic

Most pages have a pop-out button near their upper-right corner. A popped-out page moves into its own window and returns to the main window when closed. Different pages can be open separately at the same time, but the same page cannot be opened twice. Video Capture uses its own detached viewer instead.

Text fields support undo and redo using the normal system shortcuts. On Windows and Linux, use **Ctrl+Z** to undo and **Ctrl+Y** or **Ctrl+Shift+Z** to redo.

### Gamertags and IDs

When OpenRTM learns the gamertag associated with a profile, it uses that name in place of the profile ID throughout the application. Click the nearby **ID** button to view the actual ID. Unknown profiles remain labeled **Unknown Profile**, and the all-zero shared-content profile is labeled **Shared Content**. Discovered gamertags are remembered for later sessions.

### Drag and drop

Gamer Profile, Package Manager, Content Library, ISO Extractor, Game Saves, Module Manager, File Transfer, and Xbox Storage display a drag-and-drop label. While desktop files are held over one of these pages, its controls are blurred and a drop prompt is shown. KV Checker also accepts dropped files and folders through its existing import workflow.

Drag and drop is available for:

* Gamer profiles, packages, game saves, and Xbox ISO files
* One or more content packages for installation
* One or more `.xex` modules for loading
* Files or folders uploaded into the selected console folder on File Transfer
* One or more files imported into the selected folder on Xbox Storage
* Key vault files and folders on KV Checker

Profiles, packages, game saves, content packages, key vaults, and modules are validated before the requested operation proceeds. An ISO drop fills the input field; choose the output and click **Run** when ready.

## Home

The Home page shows information reported by the connected console, including its IP address, CPU key, signed-in gamertag, current title, console type, kernel version, console ID, SMC version, and temperatures. Click **Refresh Info** in the top row to read it again.

The console controls can:

* Display a custom notification on the Xbox 360
* Perform a warm or cold reboot
* Shut down the console
* Open or close the disc tray

Save any open work before using the reboot or shutdown controls.

### Discord Rich Presence

When configured in the build, **Show Current Title As Discord Rich Presence (RPC)** shares the connected console's current title. The status refreshes every 30 seconds. Its elapsed timer restarts when the title changes, and the status is cleared when the console disconnects or OpenRTM closes.

Known title IDs are displayed as game names. An unknown title is displayed using its title ID without a leading `0x`.

## Video Capture

Video Capture displays a USB or HDMI capture device inside OpenRTM. Device discovery and preview start automatically when the page is created. Use **Refresh devices** after connecting or disconnecting capture hardware.

The preview stays at a 16:9 aspect ratio. If the selected device has no video signal, it displays **No input source detected** and continues waiting for the signal to return.

Available video choices include:

* **Source resolution**, **1080p**, and **720p**
* **Source FPS**, **60 FPS**, and **30 FPS**
* **Automatic**, **FFmpeg**, and **OpenCV** decoders
* **Lowest latency**, **High frame rate**, and **Compatibility** performance modes

**Source resolution** leaves the incoming size unchanged. Choosing **1080p** or **720p** requests that exact capture mode. Resizing a viewer stretches the existing stream and does not request a higher capture resolution.

**Lowest latency** minimizes buffering. **High frame rate** favors capture modes suited to higher frame rates, including MJPEG where available. **Compatibility** allows more buffering for devices that do not remain stable in the faster modes. **Automatic** tries the available decoders in order; a decoder can also be selected directly for troubleshooting.

Choose an audio input separately from the video device. **Monitor audio** plays the selected input through the computer while the preview is active. Audio status is shown beside the capture actions. Selecting **No audio** disables capture audio.

Enable **Detached viewer** to open a separate video window. With **Lock viewer size** enabled, the video area uses the selected resolution and the window cannot be resized. Clearing the lock allows the stream to stretch with the window.

**Record** starts or stops an MP4 recording. When audio is available, it is included in the recording. **Screenshot** saves the current frame as a PNG. Recordings and screenshots use the selected capture folder. All capture selections and viewer options are remembered between runs.

If video support cannot start on a computer, the rest of OpenRTM remains available and the Video Capture page reports that capture is unavailable.

## Memory & Commands

Memory & Commands provides advanced runtime access to a connected console. The Memory tab can read and write console memory using hexadecimal or typed values. The Commands tab sends a raw console command and displays its response.

An incorrect location, value, or command can crash the running title or freeze the console. Use these controls only when you understand the expected data.

## Debugger

The Debugger receives live events from a connected console and can control a running title. Click **Attach** before using its controls. The computer's firewall must allow OpenRTM to receive local-network connections from the console.

The debugger can:

* Pause and continue the running title
* Display debug strings, exceptions, execution changes, loaded modules, and thread events
* Stop on selected event types
* Add and remove software and hardware breakpoints
* List threads, change their state, and read registers
* List modules used by the current title
* Save displayed output to a text log

Enable **Override existing debugger** only when the console reports that another debugger is attached and you intend to replace it. Remove breakpoints and detach normally before leaving a title when possible.

Debugger controls can interrupt a title at sensitive points. If the console stops responding, avoid sending repeated commands and reconnect only after it becomes available again.

## File Transfer

File Transfer shows the computer on the left and console storage on the right. Expand a folder to load its contents.

### Uploading

1. Select a file or folder on the left.
2. Select the destination folder on the right.
3. Click **Upload ->**.

You can instead drag one or more desktop files or folders onto the page. Dropped items are uploaded into the selected console folder. If a file is selected on the console side, its parent folder is used as the destination.

Uploading a folder creates that folder inside the selected destination and preserves its subfolders. For example, uploading a local folder named `MW2` to `Hdd:\Games` creates `Hdd:\Games\MW2`.

Uploads inspect data already present on the console. Matching data is kept, incomplete data is continued when supported, and mismatched data is repaired or replaced. If the connection drops, OpenRTM waits, reconnects, and continues. Use **Cancel Transfer** to stop the current operation.

### Downloading

1. Select a file or folder on the right.
2. Select the destination folder on the left.
3. Click **<- Download**.

Downloaded folders keep their files and subfolders. OpenRTM asks before replacing an existing local destination.

Use the refresh buttons when either side changes outside OpenRTM. You can also create console folders and delete the selected console item. Deletion cannot be undone.

### Friendly folder names

Under the console's `Content` folder, OpenRTM replaces recognized profile IDs, title IDs, and content type folders with gamertags and familiar names. The shared content profile is labeled **Shared Content**.

These names are visual only; console folders are not renamed. Click or hover over an **ID** button to see the real folder name. Gamertags discovered from profiles are remembered for later sessions.

## Content Library

Content Library scans installed packages under the console's `Content` folder and presents their owner, title, content type, file name, and size in one table. Click **Refresh Library** after connecting. Select a row and click **Show Selected Path** to see its exact console location and IDs.

To install content, click **Install Packages** and choose one or more package files, or drop them onto the page. OpenRTM validates the entire selection before uploading any file. Valid packages are placed into the correct owner, title, and content type folders. Existing matching data is verified, while incomplete or mismatched data is repaired or replaced.

Use Game Saves when a save needs to be assigned to another profile before installation.

## KV Checker

KV Checker accepts one or more 16 KB `.bin` key vault files through **Add Files** or drag and drop. Files with other extensions are skipped, while invalid key vaults remain visible with the reason they were rejected.

Use **Add Folder** to recursively find every file named `KV.bin` in a selected folder and its subfolders. Dropped folders are searched the same way.

Use **Check All** to process every valid key vault, or select rows and use **Check Selected**. Each file keeps its banned, unbanned, or error result. Checking requires an internet connection that permits Xbox authentication traffic over UDP port 88.

After checking, **Sort Results** moves successful results beneath a folder you choose:

```text
banned/<console serial>/KV.bin
unbanned/<console serial>/KV.bin
```

Existing destination files are never overwritten.

## Gamer Profile

Gamer Profile opens an Xbox 360 gamer profile from the computer. Use **Browse**, enter its path, or drop the profile onto the page. A file must be a valid editable gamer profile before it is loaded.

The page displays the profile's gamertag, game count, achievement count, gamerscore, motto, name, location, and bio. **Save Changes** updates the editable text and creates a `.bak` copy before replacing the original profile.

### Bio Creator

**Bio Creator** provides searchable presets and categorized symbol buttons while preserving fixed-width spacing. Select a preset or build a bio in the editor, then:

* **Copy** places it on the clipboard.
* **Use Bio** returns it to the open profile editor.
* **Clear** empties the editor.

The character counter warns when the Xbox 360 bio limit of 499 characters is exceeded. An over-limit bio cannot be returned to the profile editor.

### Console profile transfer

Enter or confirm the profile ID, then use **Download From Console** to save and open the console copy. If the console denies access while exactly one user is active, OpenRTM may briefly sign that profile out for the download and restore the sign-in afterward.

Before using **Upload To Console**, sign out every profile on the Xbox 360. OpenRTM saves a timestamped backup of the existing console profile beside the local file, uploads the edited copy, and downloads it again for verification. If upload or verification fails, it attempts to restore the backup.

Keep an additional known-good profile backup. Do not interrupt the console or network while a profile upload is in progress.

## Game Adder

Game Adder uses the gamer profile currently open on the Gamer Profile page. It shows games already in the profile and a searchable catalog of bundled Xbox 360 game data. Search by game name or title ID, select an available title, and click **Add Selected Game**.

Added games begin with their achievements locked and their earned gamerscore at zero. OpenRTM updates the profile totals, rehashes and re-signs the profile, and creates a `.bak` copy of the original. A title already present in the profile cannot be added again.

## Achievements

Achievements uses the gamer profile currently open on the Gamer Profile page. Choose a game to see each achievement's name, description, score, status, and recorded unlock time.

Select one or more achievements, choose **Online unlock** when an online timestamp should be stored, set the date and time, and click **Apply Changes**. **Use Current Time** fills the current local time. Clearing **Online unlock** records an offline unlock without a timestamp.

Selecting an already unlocked achievement loads its existing unlock type and time, allowing those details to be adjusted. **Select All Locked** selects only achievements that have not been earned. Applying changes updates game and profile totals as needed, rehashes and re-signs the profile, and creates a `.bak` copy.

## Xbox Storage

Xbox Storage opens Xbox 360 FATX storage independently of the networked File Transfer page. It supports:

* Raw Xbox 360 storage devices
* Storage image files
* Folders containing split Xbox 360 `Data` files, including an `Xbox360` subfolder

On Linux, readable storage devices may appear automatically under **Detected device**. A source can also be entered or selected manually. Open the source, choose a detected partition, and expand folders to browse it.

The page can extract a selected file, import a computer file, create a folder, and recursively delete a selected file or folder. You can also drop one or more computer files onto the page to import them into the selected FATX folder. Same-name imports replace the existing file after confirmation. Individual FATX files must be smaller than 4 GB.

Content folders use the same friendly gamertag, title, and content type names as File Transfer. Use the **ID** button to view an actual folder ID.

Writing directly to physical storage carries a risk of data loss. Unmount the device from the operating system first, keep a backup, and do not disconnect it while an import, folder creation, or deletion is in progress.

## Package Manager

Package Manager opens valid CON, LIVE, and PIRS Xbox 360 packages through **Browse** or drag and drop. It displays package metadata, assignment IDs, integrity status, package and title images, a suggested console folder, and the files inside an STFS package.

CON packages can be edited and re-signed. Editable fields include display name, title name, description, publisher, title ID, profile assignment, console ID, device ID, and both images. **Save, Rehash & Resign** updates the loaded file, while **Save As** writes another copy. A `.bak` copy is enabled by default when replacing the loaded file.

LIVE and PIRS packages can be inspected and their internal files can be extracted, but they cannot be edited because their original signing keys are not available.

To extract an internal file, select it in the Package Files table and click **Extract Selected File**.

## ISO Extractor

ISO Extractor includes `extract-xiso` for 64-bit Windows and Linux. Drop an `.iso` or `.xiso` file onto the page, or use **Browse**. Dropping an ISO while **Create / Pack** is selected changes the operation to **Extract** but does not start it automatically.

Available operations are:

* **Extract** unpacks an Xbox ISO into an existing output folder.
* **List** displays the ISO contents without extracting them.
* **Create / Pack** builds an ISO from a selected folder.
* **Rewrite** rewrites an ISO into an existing output folder.

Choose the input and output, select any needed options, then click **Run**. Operation output appears in the lower part of the page. **Cancel** stops the active operation.

**Skip $SystemUpdate** omits that folder from supported operations. **Disable XBE media patch** turns off the media patch used while creating or rewriting an image. **Delete original after rewrite** removes the source after a successful rewrite, so use it carefully.

Keep an untouched copy until the extracted, created, or rewritten image has been tested.

[`extract-xiso`](https://github.com/XboxDev/extract-xiso/tree/master) is copyright (c) in@fishtank.com and is distributed under a [modified Berkeley Software License](https://github.com/XboxDev/extract-xiso/blob/master/LICENSE.TXT).

## Game Saves

Game Saves opens supported Xbox and Xbox 360 save packages through **Browse** or drag and drop. It displays the content type, title, package size, integrity status, and current profile, console, and device assignments.

Edit the assignment values, then use **Save, Rehash & Resign** to replace the loaded save or **Save As** to write another file. A `.bak` copy is enabled by default when replacing the loaded save.

After saving a new assignment, OpenRTM can remember it for reuse. Open the matching gamer profile once so its gamertag can be detected; saved assignments are then labeled by gamertag instead of a user-written name. Choose a gamertag from **Profiles** to apply its saved IDs to the open game save.

## Module Manager

Module Manager lists modules currently loaded by the connected console, including their names and sizes.

**Load from PC** accepts one or more valid `.xex` files. Files can also be dropped onto the page. Each module is copied to the console temporarily, loaded, and then removed from console storage. The module itself remains loaded until it is unloaded or the console restarts.

**Inject from Console** loads a module that already exists on console storage. Enter its console path or use **Browse Console** to select it.

Select a listed module to unload or reload it. A module loaded from the computer can be reloaded from its original local file during the same OpenRTM session. Console modules can be reloaded when their listed path is on the console HDD or USB storage.

Title ID spoofing can use the Xbox 360 dashboard title ID or a custom title ID. **Undo Spoof** restores the value saved when spoofing began. Restore it before closing OpenRTM or changing titles.

Loading, unloading, reloading, or spoofing at the wrong time can crash the running title. Save game progress first.

## Saved Settings

OpenRTM stores its persistent settings in `settings.json` inside the `.openrtm` folder in the current user's home directory. Saved data includes recent console addresses, autoconnect, theme, Discord Rich Presence preference, video and audio selections, capture options, the capture folder, known gamertags, and reusable game-save assignments.

Recordings, screenshots, profile backups, and edited files are stored in the locations shown or selected by the user; they are not placed in the settings folder.

## Building From Source

A Java 17 development kit is required. Build the packaged application with:

```text
./gradlew :app:shadowJar
```

On Windows, use:

```text
gradlew.bat :app:shadowJar
```

The resulting file is `app/build/libs/OpenRTM.jar`.

To enable Discord Rich Presence in a source build, create:

```text
app/src/main/resources/openrtm/discord/discord-rpc.properties
```

with this content:

```properties
applicationId=YOUR_DISCORD_APPLICATION_ID
```

That file is intentionally ignored by Git and is bundled into the application during the build.

## Platform Notes

OpenRTM is developed primarily on Linux and also supports Windows. Capture devices, audio inputs, raw storage access, firewalls, and permissions are supplied by the operating system, so their displayed names and availability can differ between computers.
