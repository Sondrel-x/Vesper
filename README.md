# Vesper
Vesper is a performance-focused Minecraft Java launcher for Windows with fast launches, account switching, customizable profiles, and built-in Modrinth browsing.

## What’s new in 1.4

- Delete a profile from **Profiles**. Vesper asks for confirmation and keeps its worlds, mods, and settings in a backup folder. An empty library now offers Create profile and Import modpack.
- Choose **Resource Packs → Share selection** to reuse your enabled texture packs in the same order across existing and future profiles on their next launch. Individual profiles can still change their selections.
- Toggle a mod, resource pack, or shader without losing your scroll position or search filter.
- Browse Modrinth results as you type. Results stay filtered by profile compatibility, and older searches cannot overwrite newer ones.
- On Fabric profiles, use **Performance → Choose performance mods** to enable individual mods or select **Enable all**. Presets also have an Enable all performance options button.
- Fabric 1.21.11 presets include Sodium Relief for item-hover and tooltip stutter. Additional performance choices include Better Block Entities, BadOptimizations, and ThreadTweak.
- For hitches when opening or closing inventories and containers, try **Performance → Menu stutter workaround**. It is off by default and disables raw mouse input and menu background blur for that profile. Switching it off restores the previous settings. Players can also adjust both settings individually. The effect on frame times depends on the system.

## Review the code before installing

You're welcome to inspect Vesper before deciding whether to run it. The links below open the source files from the `Source.zip` bundled with the Vesper 1.4 installer. You can read them right here on GitHub.

- [Windows launcher and Java runtime download](source/1.4/windows/main.go)
- [Windows installer script](source/1.4/installer/setup.nsi)
- [Minecraft account sign-in](source/1.4/src/Accounts.java) and [Windows credential protection](source/1.4/windows/vault_windows.go)
- [Launcher update checks](source/1.4/src/ReleaseUpdates.java) and [all included launcher and HUD source files](source/1.4)

## Windows SmartScreen and download verification

Windows may show **“Windows protected your PC”** when SmartScreen has limited reputation information about a download or its publisher. If you see that warning, you can close the installer while you review the code and check your download. [How Microsoft Defender SmartScreen works](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation)

Download the installer from the [official Vesper 1.4 release](https://github.com/Sondrel-x/Vesper/releases/tag/v1.4). To check your copy, open PowerShell in the folder containing the file and run:

```powershell
Get-FileHash .\Vesper-Launcher-Setup-1.4-Windows-x64.exe -Algorithm SHA256
```

The expected SHA-256 for **version 1.4** is:

```text
cfc62f0a7413d5d1c3f7535fe452c80a3332bdd68d01fd30f05633835739e0b7
```

A match confirms your file is identical to the published installer. Source review and a matching hash cannot guarantee software is malware-free. If the hash differs, discard the file.
