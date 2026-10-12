# Getting Started

[All guides](README.md) | [Build instructions](../README.md#build-and-run)

## Requirements

- Windows or Linux, with Java 17 or newer to run the packaged app.
- For connected features: a modified Xbox 360 on the same local network, with `XBDM.xex` and `JRPC.xex` or `JRPC2.xex` configured as plugins.
- For Video Capture: a compatible capture device and its operating-system drivers. It does not require an OpenRTM console connection.
- For raw Xbox storage: permission to access the device. See [FATX Device Explorer](fatx-device-explorer.md).

Package Manager, Game Saves, local profile editing, Game Adder, Achievements, ISO Extractor, FATX Device Explorer, and GSC editing/compilation work without a console connection. KV Checker needs internet access, but not a connected console.

## Launch & Connect

1. Start the console and click **Scan for consoles**, or enter its IP address or hostname.
2. Select the correct address and click **Connect**.
3. Look for the connection notification on the console and its information on Home.

**Refresh Info** updates the console details. Recent addresses are remembered. **Autoconnect** uses the last selected console at startup; an active connection also attempts to recover after a network interruption. **Disconnect** deliberately stops reconnecting.

If discovery fails, enter the address manually, check that both devices are on the same local network, and check plugin and firewall settings.

## Common Controls

Use the theme selector on any page. Choices include FlatLaf Dark/Light and Synthetica BlackMoon, BlueLight, BlueSteel, Dark, GreenDream, MauveMetallic, and OrangeMetallic. Synthetica Dark is the default.

Most pages can pop out into their own window. Closing that window returns the page to the main window; it does not open a second copy. Video Capture has its own detached viewer.

Recognized profile and content IDs are shown as gamertags, game names, or content types. The **ID** buttons reveal the original value without renaming files. Unrecognized profiles show **Unknown Profile**; shared content shows **Shared Content**. Known gamertags are remembered.

## Drag and Drop

Drop files onto the relevant page to open profiles, packages, saves, or ISOs; install content; load modules; or import key vaults. File Transfer uploads into the selected console folder. FATX Device Explorer imports into the selected storage folder.

[GSC Menus](gsc-menus.md) accepts one menu for editing/injection or multiple source menus for automatic compilation after choosing an output folder. An ISO drop only chooses the input; it does not run the operation.

On Windows, dragging from a normal Explorer window into an administrator app is blocked. Run OpenRTM normally for desktop drag and drop, or use **Browse** when elevated storage access is necessary.

## Saved Files and Settings

Settings are stored in `.openrtm/settings.json` under your user home folder. They include recent consoles, autoconnect, theme, Discord preference, capture choices, known gamertags, and reusable save assignments.

New GSC projects are created in `.openrtm/GSC_Projects`. Capture defaults to `Videos/OpenRTM` under your home folder; you can choose another folder. Edited files, exports, and backups use the locations selected or shown in the app.
