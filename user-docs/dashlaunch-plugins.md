# Dashlaunch Plugins

[Home](home.md) | [All guides](README.md)

This section on Home edits the five startup plugin slots in `Hdd:\Launch.ini`. It requires a connected console and an existing configuration at that location.

1. Click **Load Plugins**.
2. Select a slot and enter its full console path, or use **Browse Console** to select an existing `.xex`.
3. To use a computer file, choose **Browse PC**. OpenRTM uploads it to `Hdd:\OpenRTM\Plugins` and fills the slot with that path.
4. Click **Save Plugins** to write the selected paths.

**Clear Slot** clears that entry in the editor. It does not delete the plugin file. Clearing or browsing a slot does not save the configuration automatically; **Browse PC** does upload the file immediately.

Saving preserves other configuration sections but changes the startup plugin list. It does not load or unload modules in the current session; use [Module Manager](module-manager.md) for that. Restart the console when you are ready to use the saved startup configuration.

Back up `Launch.ini` with [File Transfer](file-transfer.md) first. Keep the plugins needed for OpenRTM connectivity configured, and do not replace slots you do not recognize. An incorrect startup plugin can prevent a normal boot.
