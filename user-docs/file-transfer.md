# File Transfer

[All guides](README.md) | Requires a connected console

The computer is on the left and console storage is on the right. Expand folders to load their contents. The storage bar shows total and free space for `Hdd:\`; hover over it for used space.

## Upload and Download

To upload, select a local file or folder, select the console destination folder, and click **Upload**. You can also drop desktop files or folders onto the page. A selected console file uses its parent folder as the destination.

Uploading a folder preserves its name and subfolders: uploading `MW2` into `Hdd:\Games` creates `Hdd:\Games\MW2`.

To download, select console files or folders, select the local destination, and click **Download**. Folder contents are included, and existing local destinations require overwrite confirmation.

Uploads check existing data, keep matching portions, and continue or repair incomplete/mismatched files where supported. A network interruption waits for reconnection before continuing. Progress shows speed and estimated time remaining; **Cancel** stops the operation.

## Drag Between Browsers

Hold Ctrl or Shift to select multiple items, then drag:

- Computer to console: upload.
- Console to computer: download.
- Console folder to another console folder: move, after confirmation.
- Console to the desktop or an operating-system folder: download first, then pass the files to that destination. Large items may make the app wait.

Windows blocks desktop drops into an administrator app. Use **Browse** or the local file browser when running elevated.

## Manage and Launch Files

Use **Refresh PC** or **Refresh Xbox** after changes outside OpenRTM. **New Folder**, **Rename**, **Properties**, and **Delete** apply to the side you used most recently. Confirm the intended side before acting.

Properties shows type, size, timestamps, and attributes. Read-only and hidden attributes can be changed where supported. Renaming and deleting require confirmation; deletion cannot be undone.

**Double-click a console `.xex` file to launch it.** This can leave the current title, so save progress first.

Recognized content folders show gamertags, game names, and content types without renaming the actual folders. Use the **ID** buttons to see their real names. For automatic package placement, use [Content Library](content-library.md).
