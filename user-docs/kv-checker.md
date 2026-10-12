# KV Checker

[All guides](README.md) | Internet required; no console connection required

KV Checker checks Xbox 360 key vault files and keeps each file's banned, unbanned, or error result.

## Add and Check

- **Add Files** accepts one or more 16 KB `.bin` key vault files.
- **Add Folder** searches recursively for files named `KV.bin`.
- Drag and drop accepts files or folders using the same rules.

Other file extensions are skipped. Invalid key vaults remain visible with the reason they were rejected.

Use **Check All**, or select rows and **Check Selected**. **Cancel** stops the active check. An error is not a banned or unbanned result; check its details before retrying.

**Remove Selected** removes selected entries from the list, and **Clear** empties the list. These do not delete source files.

## Sort Results

**Sort Results** moves successfully checked files into a folder you choose:

```text
banned/<console serial>/KV.bin
unbanned/<console serial>/KV.bin
```

This moves the original files rather than making copies. Existing destination files are never overwritten, and files without a successful result are not sorted. Keep a separate backup before sorting.
