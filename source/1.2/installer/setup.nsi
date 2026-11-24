Unicode true
RequestExecutionLevel user
!include "MUI2.nsh"
!include "x64.nsh"
Name "Vesper Launcher"
OutFile "../../releases/Vesper-Launcher-Setup-1.2-Windows-x64.exe"
InstallDir "$LOCALAPPDATA\Programs\Sonder Launcher"
BrandingText "Vesper Client"
SetCompressor /SOLID lzma
Icon "Sonder.ico"
UninstallIcon "Sonder.ico"
VIProductVersion "1.2.0.0"
VIAddVersionKey /LANG=1033 "ProductName" "Vesper Launcher"
VIAddVersionKey /LANG=1033 "FileDescription" "Vesper Launcher Setup"
VIAddVersionKey /LANG=1033 "FileVersion" "1.2"
VIAddVersionKey /LANG=1033 "LegalCopyright" "Vesper Client"
!define MUI_ICON "Sonder.ico"
!define MUI_UNICON "Sonder.ico"
!define MUI_WELCOMEFINISHPAGE_BITMAP "Wizard.bmp"
!include "nsDialogs.nsh"
!define UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\SonderLauncher"
!define SURFACE 0x191522
!define FOREGROUND 0xF9F2FF
!define MUTED 0xDDD0E8
Var FolderField
Var InstallButton
Var BannerHandle
Var InstalledText
InstallDirRegKey HKCU "${UNINSTALL_KEY}" "InstallLocation"
ShowInstDetails nevershow
Page custom SetupPage
!define MUI_INSTFILESPAGE_PROGRESSBAR smooth
!define MUI_HEADER_TEXT "Installing Vesper Launcher"
!define MUI_HEADER_SUBTEXT "Copying the launcher, resource packs, and desktop shortcut."
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_RUN "$INSTDIR\SonderLauncher.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Open Vesper Launcher"
!define MUI_FINISHPAGE_TITLE "Vesper is ready"
!define MUI_FINISHPAGE_TEXT "Select Finish to open the launcher. Your profiles are available from the sidebar."
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"
Function SetupPage
 InitPluginsDir
 File /oname=$PLUGINSDIR\banner.bmp "SetupBanner.bmp"
 nsDialogs::Create 1044
 Pop $0
 ${If} $0 == error
   Abort
 ${EndIf}
 SetCtlColors $0 ${FOREGROUND} ${SURFACE}
 GetDlgItem $InstallButton $HWNDPARENT 1
 ${NSD_CreateBitmap} 0 0 100% 54u ""
 Pop $1
 ${NSD_SetStretchedImage} $1 "$PLUGINSDIR\banner.bmp" $BannerHandle
 ${NSD_CreateLabel} 10% 61u 84% 20u "Vesper Launcher"
 Pop $1
 CreateFont $2 "Segoe UI" 17 700
 SendMessage $1 ${WM_SETFONT} $2 1
 SetCtlColors $1 ${FOREGROUND} ${SURFACE}
 ${NSD_CreateLabel} 10% 84u 84% 15u "$InstalledText"
 Pop $1
 SetCtlColors $1 ${MUTED} ${SURFACE}
 ${NSD_CreateLabel} 10% 109u 80% 12u "Install location"
 Pop $1
 SetCtlColors $1 ${FOREGROUND} ${SURFACE}
 ${NSD_CreateText} 10% 124u 62% 15u "$INSTDIR"
 Pop $FolderField
 SendMessage $FolderField ${EM_SETREADONLY} 1 0
 SetCtlColors $FolderField ${FOREGROUND} 0x312A3C
 ${NSD_CreateButton} 74% 123u 16% 17u "Change…"
 Pop $1
 ${NSD_OnClick} $1 PickFolder
 ${NSD_CreateLabel} 10% 152u 83% 18u "Setup 1.2  ·  Windows x64"
 Pop $1
 SetCtlColors $1 ${MUTED} ${SURFACE}
 IfFileExists "$INSTDIR\SonderLauncher.exe" 0 +3
 SendMessage $InstallButton ${WM_SETTEXT} 0 "STR:Update"
 Goto +2
 SendMessage $InstallButton ${WM_SETTEXT} 0 "STR:Install"
 nsDialogs::Show
 ${NSD_FreeImage} $BannerHandle
FunctionEnd
Function PickFolder
 nsDialogs::SelectFolderDialog "Choose the Vesper Launcher install folder" "$INSTDIR"
 Pop $0
 ${If} $0 != error
 ${AndIf} $0 != ""
   StrCpy $INSTDIR $0
   ${NSD_SetText} $FolderField "$INSTDIR"
   IfFileExists "$INSTDIR\SonderLauncher.exe" 0 +3
   SendMessage $InstallButton ${WM_SETTEXT} 0 "STR:Update"
   Goto +2
   SendMessage $InstallButton ${WM_SETTEXT} 0 "STR:Install"
 ${EndIf}
FunctionEnd
Function .onInit
 StrCpy $InstalledText "Install your Minecraft profiles and mod manager."
 IfFileExists "$INSTDIR\SonderLauncher.exe" 0 +2
 StrCpy $InstalledText "Vesper is installed. Update your existing launcher."
 ${IfNot} ${RunningX64}
  MessageBox MB_ICONSTOP "Vesper Launcher requires 64-bit Windows."
  Abort
 ${EndIf}
FunctionEnd
Section "Sonder Launcher"
 SetShellVarContext current
 SetOutPath "$INSTDIR"
 File "..\SonderLauncher.exe"
 File /oname=Vesper.ico "Sonder.ico"
 File "Source.zip"
 File "../resources/Photo-Credits.txt"
 SetOutPath "$INSTDIR\packs"
 File "../../upload/BSL_v7.1.04.1.zip"
 File "../../upload/Berserk-Mace.zip"
 File "../../upload/Blue_Edit_Texture_Pack.zip"
 File "../../upload/Frixion's White Rose Pack.zip"
 File "../../upload/Fullbright-UB-1.21 fub-6.0.zip"
 File "../../upload/Gold_Edit_Texture_Pack.zip"
 File "../../upload/InfusePack.zip"
 File "../../upload/Orange_Edit_Texture_Pack.zip"
 File "../../upload/PigPurp.zip"
 File "../../upload/PigPurp_Dark_Violet_Trims.zip"
 File "../../upload/Pink_Edit_Texture_Pack.zip"
 File "../../upload/Re-Zero-Totem-Sound-v1.21.9.zip"
 File "../../upload/Red_Edit_Muted_Vanilla_Wood.zip"
 File "../../upload/Red_Edit_Texture_Pack.zip"
 File "../../upload/Transparent Fog v3.zip"
 File "../../upload/TrimSMP-S2.zip"
 File "../../upload/White_Edit_Texture_Pack.zip"
 File "../../upload/reimagined_incendium_textures-v1.1.zip"
 File "../../upload/tgk @wend_rp.zip"
 SetOutPath "$INSTDIR"
 WriteUninstaller "$INSTDIR\Uninstall.exe"
 CreateShortcut "$DESKTOP\Vesper Launcher.lnk" "$INSTDIR\SonderLauncher.exe" "" "$INSTDIR\Vesper.ico" 0
 CreateShortcut "$SMPROGRAMS\Vesper Launcher.lnk" "$INSTDIR\SonderLauncher.exe" "" "$INSTDIR\Vesper.ico" 0
 WriteRegStr HKCU "${UNINSTALL_KEY}" "InstallLocation" "$INSTDIR"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SonderLauncher" "DisplayName" "Vesper Launcher"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SonderLauncher" "DisplayVersion" "1.2"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SonderLauncher" "DisplayIcon" "$INSTDIR\Vesper.ico"
 WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SonderLauncher" "UninstallString" '"$INSTDIR\Uninstall.exe"'
 Delete "$DESKTOP\Sonder Launcher.lnk"
 Delete "$SMPROGRAMS\Sonder Launcher.lnk"
SectionEnd
Section "Uninstall"
 SetShellVarContext current
 Delete "$DESKTOP\Vesper Launcher.lnk"
 Delete "$SMPROGRAMS\Vesper Launcher.lnk"
 Delete "$INSTDIR\SonderLauncher.exe"
 Delete "$INSTDIR\Vesper.ico"
 Delete "$INSTDIR\Source.zip"
 Delete "$INSTDIR\Photo-Credits.txt"
 Delete "$INSTDIR\packs\BSL_v7.1.04.1.zip"
 Delete "$INSTDIR\packs\Berserk-Mace.zip"
 Delete "$INSTDIR\packs\Blue_Edit_Texture_Pack.zip"
 Delete "$INSTDIR\packs\Frixion's White Rose Pack.zip"
 Delete "$INSTDIR\packs\Fullbright-UB-1.21 fub-6.0.zip"
 Delete "$INSTDIR\packs\Gold_Edit_Texture_Pack.zip"
 Delete "$INSTDIR\packs\InfusePack.zip"
 Delete "$INSTDIR\packs\Orange_Edit_Texture_Pack.zip"
 Delete "$INSTDIR\packs\PigPurp.zip"
 Delete "$INSTDIR\packs\PigPurp_Dark_Violet_Trims.zip"
 Delete "$INSTDIR\packs\Pink_Edit_Texture_Pack.zip"
 Delete "$INSTDIR\packs\Re-Zero-Totem-Sound-v1.21.9.zip"
 Delete "$INSTDIR\packs\Red_Edit_Muted_Vanilla_Wood.zip"
 Delete "$INSTDIR\packs\Red_Edit_Texture_Pack.zip"
 Delete "$INSTDIR\packs\Transparent Fog v3.zip"
 Delete "$INSTDIR\packs\TrimSMP-S2.zip"
 Delete "$INSTDIR\packs\White_Edit_Texture_Pack.zip"
 Delete "$INSTDIR\packs\reimagined_incendium_textures-v1.1.zip"
 Delete "$INSTDIR\packs\tgk @wend_rp.zip"
 RMDir "$INSTDIR\packs"
 Delete "$INSTDIR\Uninstall.exe"
 RMDir "$INSTDIR"
 DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\SonderLauncher"
SectionEnd
