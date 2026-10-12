# FATX Device Explorer

[All guides](README.md) | Works offline

This page browses Xbox 360 storage attached to the computer, independently of networked [File Transfer](file-transfer.md).

Supported sources are raw storage devices, storage image files, and folders containing split Xbox 360 `Data` files, including an `Xbox360` subfolder.

## Open Storage

Select a **Detected device** and use **Use Device**, or browse/enter a source path. Click **Open**, choose a detected partition, and expand folders. **Back** returns to the previous folder; **Refresh** reloads the contents.

On Windows, administrator access may be required to discover or open physical disks. Close other programs using the disk. You can enter a path such as `\\.\PhysicalDrive1`, using the correct disk number from Disk Management.

On Linux, the account needs permission to read the device and write permission for changes. A device that opens read-only can be browsed or extracted, but not modified. Elevated access can affect desktop drag and drop on Windows; use the file chooser in that case.

## Extract and Import

- Select files or folders with Ctrl/Shift and extract them to a local folder. Folder contents are included.
- **Import File** accepts computer files or whole folders. You can also drop them onto the selected FATX folder.
- Use **New Folder** to create a directory.
- **Delete** removes the selected file or folder and its contents.

Same-name files require replacement confirmation. Invalid FATX names are reported before import. Individual files must be smaller than 4 GB.

Extraction and import show progress and estimated time remaining; **Cancel** stops them. Repeating extraction into the same destination skips files with the same size. Size matching is not a content check; use a fresh destination when the existing files may be damaged or different.

Content folders use friendly gamertag, title, and content-type names. **ID** shows an actual folder ID without renaming it.

Back up the device before writing. Unmount operating-system volumes on it, avoid simultaneous access from other tools, and do not unplug it during changes. Deletion cannot be undone.
