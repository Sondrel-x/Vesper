# Vesper
Vesper is a performance-focused Minecraft Java launcher for Windows with fast launches, account switching, customizable profiles, and built-in Modrinth browsing.

## What’s new in 1.2

- Fixed the false missing-MixinExtras warning when launching Velocity with Dynamic FPS enabled.
- Vesper now checks the official Minecraft installation for a verified copy of a missing game file before downloading it.
- If a game file still fails to download, the error shows the file and host and offers **Retry**.

## Review the code before installing

You're welcome to inspect Vesper before deciding whether to run it. The links below open the source files from the `Source.zip` bundled with the Vesper 1.2 installer. You can read them right here on GitHub.

- [Windows launcher and Java runtime download](source/1.2/windows/main.go)
- [Windows installer script](source/1.2/installer/setup.nsi)
- [Minecraft account sign-in](source/1.2/src/Accounts.java) and [Windows credential protection](source/1.2/windows/vault_windows.go)
- [Launcher update checks](source/1.2/src/ReleaseUpdates.java) and [all included launcher and HUD source files](source/1.2)

## Windows SmartScreen and download verification

Windows may show **“Windows protected your PC”** when SmartScreen has limited reputation information about a download or its publisher. If you see that warning, you can close the installer while you review the code and check your download. [How Microsoft Defender SmartScreen works](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation)

Download the installer from the [official Vesper 1.2 release](https://github.com/Sondrel-x/Vesper/releases/tag/v1.2). To check your copy, open PowerShell in the folder containing the file and run:

```powershell
Get-FileHash .\Vesper-Launcher-Setup-1.2-Windows-x64.exe -Algorithm SHA256
```

The expected SHA-256 for **version 1.2** is:

```text
814896e9de59edb8dfd9cf3f6d28ad38286bbb66526ff66d20ca5bfe524a1ee5
```

A match confirms your file is identical to the published installer. Source review and a matching hash cannot guarantee software is malware-free. If the hash differs, discard the file.
