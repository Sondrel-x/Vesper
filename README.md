# Vesper
Vesper is a performance-focused Minecraft Java launcher for Windows with fast launches, account switching, customizable profiles, and built-in Modrinth browsing.

## What’s new in 1.1

- Import a supported Modrinth `.mrpack` from Home or Profiles, then add your own mods and resource packs.
- Browse mods with improved icon support, including WebP images and icons from imported mod files.
- Check for declared Fabric mod conflicts before launch and choose **Auto-fix** to look for compatible versions.

## Review the code before installing

You're welcome to inspect Vesper before deciding whether to run it. The links below open the source files from the `Source.zip` bundled with the Vesper 1.1 installer. You can read them right here on GitHub.

- [Windows launcher and Java runtime download](source/1.1/windows/main.go)
- [Windows installer script](source/1.1/installer/setup.nsi)
- [Minecraft account sign-in](source/1.1/src/Accounts.java) and [Windows credential protection](source/1.1/windows/vault_windows.go)
- [Launcher update checks](source/1.1/src/ReleaseUpdates.java) and [all included launcher and HUD source files](source/1.1)

## Windows SmartScreen and download verification

Windows may show **“Windows protected your PC”** when SmartScreen has limited reputation information about a download or its publisher. If you see that warning, you can close the installer while you review the code and check your download. [How Microsoft Defender SmartScreen works](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation)

Download the installer from the [official Vesper 1.1 release](https://github.com/Sondrel-x/Vesper/releases/tag/v1.1). To check your copy, open PowerShell in the folder containing the file and run:

```powershell
Get-FileHash .\Vesper-Launcher-Setup-1.1-Windows-x64.exe -Algorithm SHA256
```

The expected SHA-256 for **version 1.1** is:

```text
7d69af5ecd018934000fd0938d8374bb8d1bb9db99f89d4c230244a924402d63
```

A match confirms your file is identical to the published installer. Source review and a matching hash cannot guarantee software is malware-free. If the hash differs, discard the file.
