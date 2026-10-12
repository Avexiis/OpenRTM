# GSC Menus

[MW2](mw2.md) | [GSC Editor](gsc-editor.md) | [All guides](README.md)

Open **COD Options > MW2 > Inject GSC**. Editing, checking, exporting, and batch compilation work offline. Injection requires a connected console running Modern Warfare 2 (2009) multiplayer.

## Select a Menu

Use **Browse** or drop one of these onto the page:

- A `.gsc` source file.
- A source project folder.
- A supported MW2 `.gscbin` compiled menu.

Selecting one item does not automatically compile or inject it. **Open Editor** opens source projects; compiled files cannot be edited or decompiled here. **New Project** creates a named starter project and opens the editor.

A standalone source file is treated as one menu. A source file beside `config.il` is treated as part of that project. Selecting a project folder includes its GSC files, so keep unrelated menus in separate folders.

## Inject Source or a Compiled File

1. Load MW2 multiplayer and wait in a pre-game lobby.
2. Connect OpenRTM and select the menu.
3. Click **Inject GSC** and wait for the progress to finish.
4. Start the match and follow the menu's own controls.

Source is compiled first; a compiled file is validated and loaded directly. The game may pause briefly during installation and verification. Do not close OpenRTM, disconnect, change titles, or try to cancel during this operation.

The `.gscbin` extension alone does not establish compatibility. Files for other games or unsupported compiled formats are rejected; use the source when available.

If another injector is active, the game state is incompatible, or OpenRTM asks for a restart, restart the game before trying again. Resume a deliberately paused game first. An injection error keeps the last progress stage visible; if the game cannot be restored and remains paused, follow the restart instruction.

## Export One Menu

Select a source file or project, click **Export .gscbin**, and choose the destination filename. The extension is added when missing. Replacing an existing file requires confirmation.

Exporting creates a file only. It does not inject it or overwrite the source.

## Compile Multiple Menus

1. Select several source files/project folders in **Browse**, or drag and drop them together.
2. Choose the output folder in the prompt and click **Compile**.
3. Wait for the results listing saved menus and any failures.

Compilation starts automatically after you choose the folder. There is no need to open each menu individually. **Compile Menus** lets you run the selected batch again and choose an output folder.

Each selected standalone file or project produces its own `.gscbin`. Files from the same recognized project are grouped once. Existing output files are preserved: duplicate names receive suffixes such as `Menu (2).gscbin`.

A failed menu does not stop the others. Review the per-menu results, fix failed sources in the editor, and retry as needed. The output folder is remembered during the session.

Batch compilation accepts source files and project folders, not compiled files. Do not mix `.gscbin` files into a source batch. Dropping a single parent folder treats it as one project, not as a batch of every menu below it.

## Compatibility

The compiler and loader support MW2 menus within their implemented language and game-function coverage. Unsupported statements, functions, or oversized projects can produce errors. Successful compilation is not a guarantee that every menu action works in-game.

Use only trusted menus and test in a private session. Keep your original source and compiled files.
