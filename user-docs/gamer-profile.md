# Gamer Profile

[All guides](README.md) | Local editing works offline

Open an Xbox 360 gamer profile with **Browse**, its file path, or drag and drop. Only valid editable gamer profiles can be loaded.

The page shows the gamertag, game count, achievement count, gamerscore, motto, name, location, and bio. Edit the available text fields and choose **Save Changes**. The profile is rehashed and re-signed, and a `.bak` copy is created before replacing the original.

**Bio Creator** opens a separate [bio editor](bio-creator.md) with presets and symbols. The loaded profile is also shared with [Game Adder](game-adder.md) and [Achievements](achievements.md).

## Download From Console

Connect, enter or confirm the profile ID, and choose **Download From Console**. Select a local destination; the downloaded profile opens for editing.

If the console denies access while exactly one profile is signed in, OpenRTM may temporarily sign it out to download and then restore sign-in.

## Upload To Console

1. Keep an additional known-good backup of the profile.
2. Sign out every profile on the Xbox 360.
3. Choose **Upload To Console** and wait for upload and verification.

OpenRTM saves a timestamped backup of the existing console profile beside the local file, uploads the edited copy, and downloads it again for comparison. If upload or verification fails, it attempts to restore the backup.

Do not interrupt the console or network during upload. Local edits do not reach the console until uploaded.
