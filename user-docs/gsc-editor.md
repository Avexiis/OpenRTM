# GSC Editor

[GSC Menus](gsc-menus.md) | [All guides](README.md)

Open **COD Options > MW2 > Inject GSC**, select source, and click **Open Editor**, or choose **New Project**. A console connection is only needed for injection.

## Projects and Files

- **Open project** selects a source file or folder.
- **New project** creates a named starter under `.openrtm/GSC_Projects` in your home folder.
- **New GSC file** adds a file to the current project.
- **Save file** saves the active file; **Save all** saves every changed file.

The file list opens source tabs. Closing a changed tab, switching projects, or closing the editor prompts about unsaved work. If a file has changed outside OpenRTM, saving offers **Overwrite**, **Reload**, or **Cancel**; reloading discards the editor's unsaved changes for that file.

Keep the source needed for editor compilation directly in the project folder. Nested files can appear in the editor and syntax checks, but editor compilation/export currently uses the top-level files. Compilation from the [GSC Menus page](gsc-menus.md) includes the selected folder's GSC files recursively.

Adding a file to an opened standalone script changes the selection to its folder as a project. Avoid keeping unrelated menus together.

## Editing and Navigation

The editor provides syntax highlighting, line numbers, folding, matching brackets, automatic indentation, occurrence highlighting, and completion suggestions for supported names and project functions. The `spawnloop` completion inserts a player-spawn loop.

Use **Undo**, **Redo**, and **Format file** from the toolbar. The function dropdown jumps to a function in the current file.

Find and replace supports **Case** and **Regex**, previous/next matches, **Replace**, and **All** for replacing every match in the current file.

| Shortcut on Windows/Linux | Action |
| --- | --- |
| Ctrl+S | Save all |
| Ctrl+F | Focus find |
| Ctrl+H | Focus replacement |
| Ctrl+Shift+F | Format file |

Hover over toolbar icons to see their names.

## Check, Export, and Inject

The editor checks changes automatically. **Check project** runs a check immediately against the current editor contents, including unsaved changes. Select a problem to jump to its location. A successful check reports the compiled size.

**Export .gscbin** compiles the current editor contents to a chosen file, including unsaved changes. It asks before replacing an existing output. Exporting does not save the source files.

**Inject GSC** saves all source changes first, then injects the selected menu using the [MW2 injection workflow](gsc-menus.md#inject-source-or-a-compiled-file).

Check and resolve reported problems before export or injection. This editor is for supported MW2 source, not arbitrary GSC files for every Call of Duty game.
