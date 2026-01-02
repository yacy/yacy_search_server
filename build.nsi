; YaCy Windows Installer (NSIS)

!include MUI2.nsh
!include x64.nsh
!include FileFunc.nsh
!include LogicLib.nsh
!include WordFunc.nsh
!include WinMessages.nsh

!ifndef WS_SIZEBOX
!define WS_SIZEBOX 0x00040000
!endif
!ifndef WS_MAXIMIZEBOX
!define WS_MAXIMIZEBOX 0x00010000
!endif
!ifndef GWL_STYLE
!define GWL_STYLE -16
!endif

!ifndef SWP_NOMOVE
!define SWP_NOMOVE 0x0002
!endif
!ifndef SWP_NOSIZE
!define SWP_NOSIZE 0x0001
!endif
!ifndef SWP_NOZORDER
!define SWP_NOZORDER 0x0004
!endif
!ifndef SWP_FRAMECHANGED
!define SWP_FRAMECHANGED 0x0020
!endif

VIProductVersion "@REPL_VERSION@.0.0"
VIAddVersionKey "ProductName" "YaCy"
VIAddVersionKey "LegalCopyright" "YaCy"
VIAddVersionKey "FileVersion" "@REPL_VERSION@"
VIAddVersionKey "FileDescription" "YaCy"
VIAddVersionKey "OriginalFilename" "@REPL_RELEASESTUB@.exe"

Name "YaCy @REPL_VERSION@"
OutFile "@REPL_RELEASE_WINDOWS_ABS@/@REPL_RELEASESTUB@.exe"

InstallDir "$LOCALAPPDATA\Programs\YaCy"
InstallDirRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "InstallLocation"

RequestExecutionLevel user
SetCompressor /SOLID LZMA
!insertmacro MUI_RESERVEFILE_LANGDLL

!define MUI_ICON "@REPL_RELEASE_MAIN_ABS@/addon/YaCy.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\orange-uninstall.ico"
!define MUI_ABORTWARNING

!define MUI_CUSTOMFUNCTION_GUIINIT YaCyGuiInit

!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "@REPL_ROOT_ABS@/gpl.txt"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!define MUI_FINISHPAGE_SHOWREADME "$TEMP\\yacy-installer.log"
!define MUI_FINISHPAGE_SHOWREADME_TEXT "Open installer log"
!define MUI_FINISHPAGE_SHOWREADME_NOTCHECKED
!insertmacro MUI_PAGE_FINISH

!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES

!insertmacro MUI_LANGUAGE "English"

LangString javaMissing 0 "Java 11 or newer is required to run YaCy. Download and install the latest Temurin JRE now?"
LangString javaDownloadFail 0 "Java download failed. Please install Java 11+ manually."
LangString javaInstallFail 0 "Java 11+ was not found after installation. YaCy may not start until Java is installed."
LangString keepData 0 "Remove YaCy user data as well?"

Var JavaVersion
Var JavaMajor
Var JavaFound
Var JavaInstallerUrl
Var JavaInstallerPath
Var DataDir
Var LogFile
Var JavaHome

Section "YaCy" Sec_YaCy
    SetDetailsView show
    Push "Installer log: $LogFile"
    Call LogLine
    Call EnsureJava
    SetShellVarContext current

    SetOutPath $INSTDIR
    RMDir /r "$INSTDIR\lib"
    RMDir /r "$SMPROGRAMS\YaCy"
    Delete "$DESKTOP\YaCy.lnk"

    File /r /x *.sh "@REPL_RELEASE_MAIN_ABS@/*"

    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "DisplayName" "YaCy"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "InstallLocation" "$INSTDIR"
    WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "UninstallString" '"$INSTDIR\uninstall.exe"'
    WriteRegStr HKCU "Software\YaCy" "DataDir" "$LOCALAPPDATA\YaCy"
    WriteUninstaller "$INSTDIR\uninstall.exe"

    CreateDirectory "$SMPROGRAMS\YaCy"
    CreateShortCut "$SMPROGRAMS\YaCy\YaCy.lnk" "$INSTDIR\startYACY.bat" "" "$INSTDIR\addon\YaCy.ico" "" SW_SHOWMINIMIZED
    CreateShortCut "$SMPROGRAMS\YaCy\Readme.lnk" "$INSTDIR\readme.txt"
    CreateShortCut "$SMPROGRAMS\YaCy\Uninstall.lnk" "$INSTDIR\uninstall.exe"
    CreateShortCut "$DESKTOP\YaCy.lnk" "$INSTDIR\startYACY.bat" "" "$INSTDIR\addon\YaCy.ico" "" SW_SHOWMINIMIZED
SectionEnd

Section "Uninstall"
    SetShellVarContext current

    RMDir /r "$INSTDIR\addon"
    RMDir /r "$INSTDIR\bin"
    RMDir /r "$INSTDIR\classes"
    RMDir /r "$INSTDIR\defaults"
    RMDir /r "$INSTDIR\htroot"
    RMDir /r "$INSTDIR\langstats"
    RMDir /r "$INSTDIR\langdetect"
    RMDir /r "$INSTDIR\lib"
    RMDir /r "$INSTDIR\libbuild"
    RMDir /r "$INSTDIR\libx"
    RMDir /r "$INSTDIR\locales"
    RMDir /r "$INSTDIR\ranking"
    RMDir /r "$INSTDIR\skins"
    RMDir /r "$INSTDIR\source"
    Delete "$INSTDIR\*.*"

    RMDir /r "$SMPROGRAMS\YaCy"
    Delete "$DESKTOP\YaCy.lnk"

    ReadRegStr $DataDir HKCU "Software\YaCy" "DataDir"
    ${If} $DataDir == ""
        StrCpy $DataDir "$LOCALAPPDATA\YaCy"
    ${EndIf}
    MessageBox MB_YESNO|MB_ICONQUESTION "$(keepData)" IDYES RemoveData
    Goto SkipRemoveData
    RemoveData:
        RMDir /r "$DataDir"
    SkipRemoveData:

    DeleteRegKey HKCU "Software\YaCy"
    DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy"
SectionEnd

Function EnsureJava
    Push "Java detection: starting"
    Call LogLine
    Call GetJavaVersion
    Push "Java detection: found=$JavaFound version='$JavaVersion' major=$JavaMajor"
    Call LogLine
    StrCmp $JavaFound "1" JavaOk JavaMissing
    JavaMissing:
        Push "Java detection: no Java found"
        Call LogLine
        MessageBox MB_ICONEXCLAMATION|MB_YESNO "$(javaMissing)" IDYES JavaDownload IDNO JavaContinue
        Goto JavaContinue
    JavaOk:
        Push "Java detection: OK"
        Call LogLine
        Goto JavaContinue

    JavaDownload:
        Push "Java install: downloading Temurin JRE"
        Call LogLine
        Call DownloadJava
        Push "Java install: download/install finished, re-checking"
        Call LogLine
        Call GetJavaVersion
        Push "Java detection (post-install): found=$JavaFound version='$JavaVersion' major=$JavaMajor"
        Call LogLine
        StrCmp $JavaFound "1" 0 JavaStillLow
        JavaStillLow:
            MessageBox MB_ICONEXCLAMATION "$(javaInstallFail)"
    JavaContinue:
FunctionEnd

Function GetJavaVersion
    StrCpy $JavaVersion ""
    StrCpy $JavaMajor "0"
    StrCpy $JavaFound "0"

    ${If} ${RunningX64}
        SetRegView 64
        Push "Java detection: registry view 64"
        Call LogLine
        Call FindJavaVersionKeys
        SetRegView 32
        Push "Java detection: registry view 32"
        Call LogLine
        Call FindJavaVersionKeys
    ${Else}
        Push "Java detection: registry view default"
        Call LogLine
        Call FindJavaVersionKeys
    ${EndIf}

    Push "Java detection: registry done, found=$JavaFound version='$JavaVersion' major=$JavaMajor"
    Call LogLine
    Call FindJavaFromEnv
    Call FindJavaFromProgramFiles
    Call FindJavaFromPath
    ${If} $JavaFound == "1"
        ${If} $JavaMajor == "0"
            StrCpy $JavaMajor "11"
            Push "Java detection: version unknown, assuming >=11"
            Call LogLine
        ${EndIf}
    ${EndIf}
    Push "Java detection: PATH/env/fs check done, found=$JavaFound version='$JavaVersion' major=$JavaMajor"
    Call LogLine
FunctionEnd

Function FindJavaVersionKeys
    Push "SOFTWARE\JavaSoft\Java Runtime Environment"
    Call FindJavaVersionInKeyHKLM
    Push "SOFTWARE\JavaSoft\JDK"
    Call FindJavaVersionInKeyHKLM
    Push "SOFTWARE\Eclipse Adoptium\JRE"
    Call FindJavaVersionInKeyHKLM
    Push "SOFTWARE\Eclipse Adoptium\JDK"
    Call FindJavaVersionInKeyHKLM
    Push "SOFTWARE\Adoptium\JRE"
    Call FindJavaVersionInKeyHKLM
    Push "SOFTWARE\Adoptium\JDK"
    Call FindJavaVersionInKeyHKLM
    Push "SOFTWARE\Microsoft\JDK"
    Call FindJavaVersionInKeyHKLM
    Push "SOFTWARE\Microsoft\JRE"
    Call FindJavaVersionInKeyHKLM

    Push "SOFTWARE\JavaSoft\Java Runtime Environment"
    Call FindJavaVersionInKeyHKCU
    Push "SOFTWARE\JavaSoft\JDK"
    Call FindJavaVersionInKeyHKCU
    Push "SOFTWARE\Eclipse Adoptium\JRE"
    Call FindJavaVersionInKeyHKCU
    Push "SOFTWARE\Eclipse Adoptium\JDK"
    Call FindJavaVersionInKeyHKCU
    Push "SOFTWARE\Adoptium\JRE"
    Call FindJavaVersionInKeyHKCU
    Push "SOFTWARE\Adoptium\JDK"
    Call FindJavaVersionInKeyHKCU
    Push "SOFTWARE\Microsoft\JDK"
    Call FindJavaVersionInKeyHKCU
    Push "SOFTWARE\Microsoft\JRE"
    Call FindJavaVersionInKeyHKCU
FunctionEnd

Function FindJavaVersionInKeyHKLM
    Exch $1
    Push "Java detection: HKLM\\$1"
    Call LogLine
    Push $2
    Push $3
    Push $4
    Push $5
    Push $6
    Push $7
    Push $8

    StrCpy $2 ""
    ReadRegStr $2 HKLM "$1" "CurrentVersion"
    ${If} $2 != ""
        ReadRegStr $3 HKLM "$1\\$2" "JavaHome"
        Push "Java detection: HKLM\\$1 CurrentVersion=$2 JavaHome='$3'"
        Call LogLine
        ${If} $3 != ""
            IfFileExists "$3\\bin\\java.exe" 0 CheckEnum
            Push "Java detection: java.exe found at $3\\bin\\java.exe"
            Call LogLine
            StrCpy $JavaFound "1"
            Push $2
            Call GetJavaMajor
            Pop $6
            IntCmp $6 $JavaMajor NoUpdate NoUpdate UpdateMajor
            NoUpdate:
                Goto DoneCompare
            UpdateMajor:
                StrCpy $JavaMajor $6
                StrCpy $JavaVersion $2
            DoneCompare:
            Goto DoneHKLM
        ${EndIf}
    ${EndIf}

    CheckEnum:
        StrCpy $3 0
        StrCpy $4 ""
        EnumLoop:
            EnumRegKey $5 HKLM "$1" $3
            ${If} $5 == ""
                Goto EnumDone
            ${EndIf}
            ReadRegStr $7 HKLM "$1\\$5" "JavaHome"
            ${If} $7 != ""
                Push "Java detection: HKLM\\$1\\$5 JavaHome='$7'"
                Call LogLine
                IfFileExists "$7\\bin\\java.exe" 0 EnumNext
                ${If} $4 == ""
                    StrCpy $4 $5
                ${Else}
                    StrCpy $8 ""
                    ${VersionCompare} $5 $4 $8
                    ${If} $8 == "1"
                        StrCpy $4 $5
                    ${EndIf}
                ${EndIf}
            ${EndIf}
            EnumNext:
            IntOp $3 $3 + 1
            Goto EnumLoop
        EnumDone:
        StrCpy $2 $4
        ${If} $2 != ""
            StrCpy $JavaFound "1"
            Push $2
            Call GetJavaMajor
            Pop $6
            IntCmp $6 $JavaMajor NoUpdateEnum NoUpdateEnum UpdateMajorEnum
            NoUpdateEnum:
                Goto DoneHKLM
            UpdateMajorEnum:
                StrCpy $JavaMajor $6
                StrCpy $JavaVersion $2
        ${EndIf}
    DoneHKLM:

    Pop $8
    Pop $7
    Pop $6
    Pop $5
    Pop $4
    Pop $3
    Pop $2
    Pop $1
FunctionEnd

Function FindJavaVersionInKeyHKCU
    Exch $1
    Push "Java detection: HKCU\\$1"
    Call LogLine
    Push $2
    Push $3
    Push $4
    Push $5
    Push $6
    Push $7
    Push $8

    StrCpy $2 ""
    ReadRegStr $2 HKCU "$1" "CurrentVersion"
    ${If} $2 != ""
        ReadRegStr $3 HKCU "$1\\$2" "JavaHome"
        Push "Java detection: HKCU\\$1 CurrentVersion=$2 JavaHome='$3'"
        Call LogLine
        ${If} $3 != ""
            IfFileExists "$3\\bin\\java.exe" 0 CheckEnumCU
            Push "Java detection: java.exe found at $3\\bin\\java.exe"
            Call LogLine
            StrCpy $JavaFound "1"
            Push $2
            Call GetJavaMajor
            Pop $6
            IntCmp $6 $JavaMajor NoUpdate NoUpdate UpdateMajor
            NoUpdate:
                Goto DoneCompare
            UpdateMajor:
                StrCpy $JavaMajor $6
                StrCpy $JavaVersion $2
            DoneCompare:
            Goto DoneHKCU
        ${EndIf}
    ${EndIf}

    CheckEnumCU:
        StrCpy $3 0
        StrCpy $4 ""
        EnumLoopCU:
            EnumRegKey $5 HKCU "$1" $3
            ${If} $5 == ""
                Goto EnumDoneCU
            ${EndIf}
            ReadRegStr $7 HKCU "$1\\$5" "JavaHome"
            ${If} $7 != ""
                Push "Java detection: HKCU\\$1\\$5 JavaHome='$7'"
                Call LogLine
                IfFileExists "$7\\bin\\java.exe" 0 EnumNextCU
                ${If} $4 == ""
                    StrCpy $4 $5
                ${Else}
                    StrCpy $8 ""
                    ${VersionCompare} $5 $4 $8
                    ${If} $8 == "1"
                        StrCpy $4 $5
                    ${EndIf}
                ${EndIf}
            ${EndIf}
            EnumNextCU:
            IntOp $3 $3 + 1
            Goto EnumLoopCU
        EnumDoneCU:
        StrCpy $2 $4
        ${If} $2 != ""
            StrCpy $JavaFound "1"
            Push $2
            Call GetJavaMajor
            Pop $6
            IntCmp $6 $JavaMajor NoUpdateEnum NoUpdateEnum UpdateMajorEnum
            NoUpdateEnum:
                Goto DoneHKCU
            UpdateMajorEnum:
                StrCpy $JavaMajor $6
                StrCpy $JavaVersion $2
        ${EndIf}
    DoneHKCU:

    Pop $8
    Pop $7
    Pop $6
    Pop $5
    Pop $4
    Pop $3
    Pop $2
    Pop $1
FunctionEnd

Function LogLine
    Exch $0
    Push $1
    FileOpen $1 $LogFile a
    FileWrite $1 "$0$\r$\n"
    FileClose $1
    DetailPrint "$0"
    Pop $1
    Pop $0
FunctionEnd

Function .onInit
    StrCpy $LogFile "$TEMP\\yacy-installer.log"
    Delete $LogFile
FunctionEnd

Function YaCyGuiInit
    ${If} ${RunningX64}
        System::Call 'user32::GetWindowLongPtr(p $HWNDPARENT, i ${GWL_STYLE}) p .r0'
        IntOp $0 $0 | ${WS_SIZEBOX}
        IntOp $0 $0 | ${WS_MAXIMIZEBOX}
        System::Call 'user32::SetWindowLongPtr(p $HWNDPARENT, i ${GWL_STYLE}, p r0)'
    ${Else}
        System::Call 'user32::GetWindowLong(p $HWNDPARENT, i ${GWL_STYLE}) i .r0'
        IntOp $0 $0 | ${WS_SIZEBOX}
        IntOp $0 $0 | ${WS_MAXIMIZEBOX}
        System::Call 'user32::SetWindowLong(p $HWNDPARENT, i ${GWL_STYLE}, i r0)'
    ${EndIf}
    System::Call 'user32::SetWindowPos(p $HWNDPARENT, p 0, i 0, i 0, i 0, i 0, i ${SWP_NOMOVE}|${SWP_NOSIZE}|${SWP_NOZORDER}|${SWP_FRAMECHANGED})'
FunctionEnd

Function FindJavaFromPath
    Push $0
    Push $1
    Push $2

    nsExec::ExecToStack 'cmd /c java -version 2^>^&1'
    Pop $0
    Pop $1
    Push "Java detection: java -version exit=$0"
    Call LogLine
    Push "Java detection: java -version output=$1"
    Call LogLine
    ${If} $0 == "0"
        StrCpy $JavaFound "1"
        Push $1
        Call FindFirstInt
        Pop $2
        IntCmp $2 $JavaMajor PathNoUpdate PathNoUpdate PathUpdate
        PathNoUpdate:
            Goto PathDone
        PathUpdate:
            StrCpy $JavaMajor $2
            StrCpy $JavaVersion $2
        PathDone:
    ${EndIf}

    Pop $2
    Pop $1
    Pop $0
FunctionEnd

Function FindJavaFromEnv
    ReadEnvStr $JavaHome "JAVA_HOME"
    ${If} $JavaHome != ""
        Push "Java detection: JAVA_HOME=$JavaHome"
        Call LogLine
        IfFileExists "$JavaHome\\bin\\java.exe" 0 JavaHomeDone
        Push "$JavaHome\\bin\\java.exe"
        Call TryJavaExe
    ${EndIf}
    JavaHomeDone:
    ReadEnvStr $JavaHome "JDK_HOME"
    ${If} $JavaHome != ""
        Push "Java detection: JDK_HOME=$JavaHome"
        Call LogLine
        IfFileExists "$JavaHome\\bin\\java.exe" 0 JdkHomeDone
        Push "$JavaHome\\bin\\java.exe"
        Call TryJavaExe
    ${EndIf}
    JdkHomeDone:
FunctionEnd

Function FindJavaFromProgramFiles
    Push "$PROGRAMFILES64\\Java"
    Call ScanJavaDir
    Push "$PROGRAMFILES\\Java"
    Call ScanJavaDir
    Push "$PROGRAMFILES64\\Eclipse Adoptium"
    Call ScanJavaDir
    Push "$PROGRAMFILES\\Eclipse Adoptium"
    Call ScanJavaDir
    Push "$PROGRAMFILES64\\Adoptium"
    Call ScanJavaDir
    Push "$PROGRAMFILES\\Adoptium"
    Call ScanJavaDir
FunctionEnd

Function ScanJavaDir
    Exch $0
    Push $1
    Push $2
    Push $3

    IfFileExists "$0\\*" 0 ScanDone
    FindFirst $1 $2 "$0\\*"
    ScanLoop:
        StrCmp $2 "" ScanDone
        IfFileExists "$0\\$2\\bin\\java.exe" 0 ScanNext
        Push "$0\\$2\\bin\\java.exe"
        Call TryJavaExe
        ScanNext:
        FindNext $1 $2
        Goto ScanLoop
    ScanDone:
    FindClose $1
    Pop $3
    Pop $2
    Pop $1
    Pop $0
FunctionEnd

Function TryJavaExe
    Exch $0
    Push $1
    Push $2
    Push $3

    StrCpy $1 'cmd /c ""$0" -version 2^>^&1"'
    nsExec::ExecToStack $1
    Pop $2
    Pop $3
    ${If} $2 == "0"
        StrCpy $JavaFound "1"
        Push $3
        Call FindFirstInt
        Pop $1
        IntCmp $1 $JavaMajor 0 0 TryUpdate
        Goto TryDone
        TryUpdate:
            StrCpy $JavaMajor $1
            StrCpy $JavaVersion $1
        TryDone:
    ${EndIf}

    Pop $3
    Pop $2
    Pop $1
    Pop $0
FunctionEnd

Function GetJavaMajor
    Exch $0
    Push $1
    Push $2
    Push $3
    Push $4

    StrCpy $1 $0 2
    ${If} $1 == "1."
        StrCpy $0 $0 "" 2
    ${EndIf}

    StrCpy $1 ""
    StrLen $2 $0
    StrCpy $3 0
    LoopDigits:
        IntCmp $3 $2 DoneDigits DoneDigits
        StrCpy $4 $0 1 $3
        ${If} $4 >= "0"
        ${AndIf} $4 <= "9"
            StrCpy $1 "$1$4"
            IntOp $3 $3 + 1
            Goto LoopDigits
        ${Else}
            Goto DoneDigits
        ${EndIf}
    DoneDigits:
        ${If} $1 == ""
            StrCpy $1 "0"
        ${EndIf}

    StrCpy $0 $1
    Pop $4
    Pop $3
    Pop $2
    Pop $1
    Exch $0
FunctionEnd

Function FindFirstInt
    Exch $0
    Push $1
    Push $2
    Push $3
    Push $4
    Push $5

    StrLen $1 $0
    StrCpy $2 0
    StrCpy $3 ""
    StrCpy $4 0
    FindLoop:
        IntCmp $2 $1 FindDone FindDone
        StrCpy $5 $0 1 $2
        ${If} $4 == 0
            ${If} $5 >= "0"
            ${AndIf} $5 <= "9"
                StrCpy $4 1
                StrCpy $3 "$3$5"
            ${EndIf}
        ${Else}
            ${If} $5 >= "0"
            ${AndIf} $5 <= "9"
                StrCpy $3 "$3$5"
            ${Else}
                Goto FindDone
            ${EndIf}
        ${EndIf}
        IntOp $2 $2 + 1
        Goto FindLoop
    FindDone:
        ${If} $3 == ""
            StrCpy $3 "0"
        ${EndIf}

    StrCpy $0 $3
    Pop $5
    Pop $4
    Pop $3
    Pop $2
    Pop $1
    Exch $0
FunctionEnd

Function DownloadJava
    ${If} ${RunningX64}
        StrCpy $JavaInstallerUrl "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25%2B36/OpenJDK25U-jdk_x64_windows_hotspot_25_36.msi"
        StrCpy $JavaInstallerPath "$TEMP\\temurin-jdk-25-x64.msi"
    ${Else}
        MessageBox MB_ICONEXCLAMATION "This installer only supports 64-bit Windows."
        Abort
    ${EndIf}

    Push "Java install: download URL=$JavaInstallerUrl"
    Call LogLine
    Push "Java install: download path=$JavaInstallerPath"
    Call LogLine

    Push "Java install: using PowerShell downloader (HTTPS)"
    Call LogLine
    StrCpy $2 "$TEMP\\yacy-download.ps1"
    StrCpy $3 "$TEMP\\yacy-download.done"
    StrCpy $4 "$TEMP\\yacy-download.err"
    Delete $3
    Delete $4
    FileOpen $1 $2 w
    FileWrite $1 "try {$\r$\n"
    FileWrite $1 "  [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12$\r$\n"
    FileWrite $1 "  $$wc = New-Object System.Net.WebClient$\r$\n"
    FileWrite $1 "  $$wc.DownloadFile('$JavaInstallerUrl', '$JavaInstallerPath')$\r$\n"
    FileWrite $1 "  'OK' | Out-File -FilePath '$3' -Encoding ASCII$\r$\n"
    FileWrite $1 "} catch { $\r$\n"
    FileWrite $1 "  $$_.Exception.Message | Out-File -FilePath '$4' -Encoding ASCII$\r$\n"
    FileWrite $1 "  exit 1$\r$\n"
    FileWrite $1 "}$\r$\n"
    FileClose $1
    Exec '"$SYSDIR\\WindowsPowerShell\\v1.0\\powershell.exe" -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "$2"'

    StrCpy $5 0
    StrCpy $8 -1
    PowerLoop:
        IfFileExists "$3" PowerDone
        IfFileExists "$4" PowerError
        IfFileExists "$JavaInstallerPath" 0 PowerWait
        FileOpen $1 "$JavaInstallerPath" r
        FileSeek $1 0 END $6
        FileClose $1
        IntOp $7 $6 / 1048576
        IntCmp $7 $8 PowerWait PowerWait PowerPrint
        PowerPrint:
            StrCpy $8 $7
            Push "Java install: downloaded ~$7 MB..."
            Call LogLine
        PowerWait:
            Sleep 2000
            IntOp $5 $5 + 2
            IntCmp $5 1200 PowerTimeout PowerLoop PowerLoop
    PowerTimeout:
        Push "Java install: PowerShell download timeout"
        Call LogLine
        MessageBox MB_ICONEXCLAMATION "$(javaDownloadFail)$\r$\n$JavaInstallerUrl"
        Delete $2
        Delete $3
        Delete $4
        Return
    PowerError:
        Push "Java install: PowerShell download failed"
        Call LogLine
        MessageBox MB_ICONEXCLAMATION "$(javaDownloadFail)$\r$\n$JavaInstallerUrl"
        Delete $2
        Delete $3
        Delete $4
        Return
    PowerDone:
        Delete $2
        Delete $3
        Delete $4

    ExecWait '"msiexec" /i "$JavaInstallerPath" /passive /norestart'
FunctionEnd
