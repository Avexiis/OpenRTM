# ISO Extractor

[All guides](README.md) | Works offline on 64-bit Windows/Linux (x86-64)

ISO Extractor includes the tools needed to work with Xbox ISO images.

| Operation | Result |
| --- | --- |
| Extract | Unpacks an ISO into an existing output folder. |
| List | Displays image contents without extracting them. |
| Create / Pack | Builds an ISO from a folder. |
| Rewrite | Rewrites an image into an existing output folder. |

Choose an operation, browse for the input and output, then click **Run**. The operation's output appears below the controls. **Cancel** stops the current operation.

Dropping an `.iso` or `.xiso` selects the input but does not run it. If Create / Pack was selected, an ISO drop switches to Extract.

## Options

- **Skip $SystemUpdate** omits that folder where supported.
- **Disable XBE media patch** disables the media patch during image creation or rewriting.
- **Delete original after rewrite** deletes the input after a successful rewrite.

For creation, choose a source folder and output ISO file. Extraction and rewriting need an existing output folder; listing does not need one.

Keep an untouched copy until you have tested the result, especially when deleting the original after rewriting.

## Included Tool

[extract-xiso](https://github.com/XboxDev/extract-xiso/tree/master) is copyright (c) in@fishtank.com and distributed under a [modified Berkeley Software License](https://github.com/XboxDev/extract-xiso/blob/master/LICENSE.TXT).
