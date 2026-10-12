# Module Manager

[All guides](README.md) | Requires a connected console

Module Manager lists loaded modules, including their names and sizes. Use **Refresh** to update the list.

## Load Modules

**Load from PC** accepts one or more valid `.xex` files. You can also drop them onto the page. Each file is uploaded temporarily, loaded, and removed from console storage afterward. The module stays in memory until unloaded or the console restarts.

**Inject from Console** loads an existing console file. Enter its console path or use **Browse Console**.

Select a listed module to **Unload** or **Reload** it. Reloading a computer-sourced module uses its original local file during the same app session. Other modules can be reloaded when their listed path is on the console HDD or USB storage.

## Title ID Spoofing

Choose the dashboard title ID or enter a custom title ID to change the title identity reported by the console. This does not launch a different game.

**Undo Spoof** restores the value saved when spoofing began. Undo it before changing titles or closing OpenRTM.

Loading, unloading, reloading, or spoofing can crash a title. Save progress first, and do not unload system modules or connectivity plugins you rely on. For startup plugins, use [Dashlaunch Plugins](dashlaunch-plugins.md).
