;yacy.nsi
; ----------------------------------------
;(C) 2004-2006 by Alexander Schier
;(C) 2008-2010 by David Wieditz
;(C) 2011      by Rene Kluge
;(C) 2018      by luccioman; https://github.com/luccioman
/*----------------------------------------
MANUALS
http://nsis.sourceforge.net/Docs/
http://nsis.sourceforge.net/Docs/Modern%20UI%202/Readme.html
----------------------------------------
COMMAND LINE OPTIONS (case sensitive):
/S - silent install/uninstall
/D="C:\yacy" - installation folder
----------------------------------------*/

; ----------------------------------------
; MODERN UI

!include MUI2.nsh
!include x64.nsh
!include FileFunc.nsh
!include WinVer.nsh

; ----------------------------------------
; GENERAL

;Unicode true

VIProductVersion "@REPL_VERSION@.0.0"
VIAddVersionKey "ProductName" "YaCy"
VIAddVersionKey "LegalCopyright" "YaCy"
VIAddVersionKey "FileVersion" "@REPL_VERSION@"
VIAddVersionKey "FileDescription" "YaCy"
VIAddVersionKey "OriginalFilename" "yacy_v@REPL_VERSION@_@REPL_REPVERDATE@@REPL_REPVERTIME@_@REPL_REPVERHASH@.exe"

Name "YaCy @REPL_VERSION@"
OutFile "RELEASE\WINDOWS\yacy_v@REPL_VERSION@_@REPL_REPVERDATE@@REPL_REPVERTIME@_@REPL_REPVERHASH@.exe"

;default installation folder
InstallDir "$PROFILE\YaCy"
InstallDirRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "UninstallString"

;recommend free space in GB for YaCy
!define RecommendSpace "4"

; commands for firewall config, see http://support.microsoft.com/kb/947709/en-us
!define WinXPAddFwRulePort 'netsh firewall add portopening TCP 8090 name="YaCy"'
!define WinXPDelFwRulePort 'netsh firewall del portopening TCP 8090'
!define WinVistaAddFwRulePort 'netsh advfirewall firewall add rule name="YaCy" dir=in action=allow enable=yes protocol=TCP localport=8090'
!define WinVistaDelFwRulePort 'netsh advfirewall firewall del rule name="YaCy"'
var WinAddFwRulePort
var WinDelFwRulePort

;requested execution level on Vista / 7
RequestExecutionLevel admin

SetCompressor /SOLID LZMA
!insertmacro MUI_RESERVEFILE_LANGDLL ;loads faster

; ----------------------------------------
; GENERAL APPEARANCE

;BrandingText "yacy.net"

!define MUI_ICON "RELEASE\MAIN\addon\YaCy.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\orange-uninstall.ico"

!define MUI_WELCOMEFINISHPAGE_BITMAP "RELEASE\MAIN\addon\installer\network.bmp"

!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "RELEASE\MAIN\addon\installer\logo.bmp"
!define MUI_HEADERIMAGE_BITMAP_NOSTRETCH

!define MUI_ABORTWARNING ;display warning before aborting installation

; ----------------------------------------
; INSTALLER PAGES

!define MUI_PAGE_CUSTOMFUNCTION_SHOW MyWelcomeShowCallback
!insertmacro MUI_PAGE_WELCOME

Function MyWelcomeShowCallback
	SendMessage $mui.WelcomePage.Text ${WM_SETTEXT} 0 "STR:$(MUI_TEXT_WELCOME_INFO_TEXT)$\r$\n$\r$\nThe YaCy project needs a maintainer for this installer!$\r$\nSee github.com/yacy/yacy_search_server/labels/Windows"
FunctionEnd

!insertmacro MUI_PAGE_LICENSE gpl.txt

!define MUI_COMPONENTSPAGE_NODESC
!insertmacro MUI_PAGE_COMPONENTS
ComponentText "YaCy v@REPL_VERSION@"

!define MUI_PAGE_CUSTOMFUNCTION_LEAVE CheckDriveSpace
!insertmacro MUI_PAGE_DIRECTORY

!insertmacro MUI_PAGE_INSTFILES

!define MUI_PAGE_CUSTOMFUNCTION_SHOW SHOW_PageFinish_custom
!define MUI_FINISHPAGE_SHOWREADME https://www.youtube.com/yacy_tutorials
!define MUI_FINISHPAGE_SHOWREADME_TEXT $(finishPage)
!insertmacro MUI_PAGE_FINISH

; ----------------------------------------
; UNINSTALLER PAGES

!insertmacro MUI_UNPAGE_CONFIRM

!insertmacro MUI_UNPAGE_INSTFILES

; ----------------------------------------
; LANGUAGES

!insertmacro MUI_LANGUAGE "English"
!insertmacro MUI_LANGUAGE "French"
!insertmacro MUI_LANGUAGE "German"

LangString stillRunning ${LANG_ENGLISH} "YaCy is still active. Please stop YaCy first."
LangString keepData 0 "Do you want to keep the data?"
LangString finishPage 0 "Show how to configure the Windows Firewall for YaCy."
LangString yacyNoHd 0 "YaCy should be installed on a hard disk. Continue with selected folder?" 
LangString yacyNeedSpace 0 "We recommend ${RecommendSpace} GB free space for YaCy. There are only $TempDriveFree GB left. Continue anyway?"
LangString yacyNeedOs 0 "Please run Windows 2000 or better (e.g. Windows XP, Vista or Windows 7) for YaCy."
LangString yacyNoJavaFoundOpenBrowser ${LANG_ENGLISH} "Java 8 has not been found in the Windows registry!$\r$\n(Key: SOFTWARE\JavaSoft\Java Runtime Environment)$\r$\nOpen download location (adoptium.net) in Browser?"
LangString yacyNoJavaFoundContinue ${LANG_ENGLISH} "Click Retry to check again for installed Java version!"

LangString stillRunning ${LANG_FRENCH} "YaCy is still active. Please stop YaCy first."
LangString keepData 0 "Do you want to keep the data?"
LangString finishPage 0 "Show how to configure the Windows Firewall for YaCy."
LangString yacyNoHd 0 "YaCy should be installed on a hard disk. Continue with selected folder?"
LangString yacyNeedSpace 0 "We recommend ${RecommendSpace} GB free space for YaCy. There are only $TempDriveFree GB left. Continue anyway?"
LangString yacyNeedOs 0 "Please run Windows 2000 or better (e.g. Windows XP, Vista or Windows 7) for YaCy."
LangString yacyNoJavaFoundOpenBrowser 0 "Java 8 has not been found in the Windows registry!$\r$\n(Key: SOFTWARE\JavaSoft\Java Runtime Environment)$\r$\nOpen download location (adoptium.net) in Browser?"
LangString yacyNoJavaFoundContinue 0 "Click Retry to check again for installed Java version!"

LangString stillRunning ${LANG_GERMAN} "YaCy ist noch aktiv. Bitte beenden Sie YaCy."
LangString keepData 0 "Möchten Sie die Daten behalten?"
LangString finishPage 0 "Zeige die Windows Firewall Konfiguration fuer YaCy."
LangString yacyNoHd 0 "YaCy sollte auf einer Festplatte installiert werden. Soll der gewählte Ordner trotzdem verwendet werden?"
LangString yacyNeedSpace 0 "Wir empfehlen ${RecommendSpace} GB für YaCy. Es sind noch $TempDriveFree GB frei. Trotzdem fortfahren?"
LangString yacyNeedOs 0 "YaCy benoetigt Windows 2000 oder besser (z.B. Windows XP, Vista oder Windows 7)."
LangString yacyNoJavaFoundOpenBrowser 0 "Java 8 wurde in der Windows registry nicht gefunden!$\r$\n(Key: SOFTWARE\JavaSoft\Java Runtime Environment)$\r$\nDie Downloadseite (adoptium.net) im Browser öffnen?"
LangString yacyNoJavaFoundContinue 0 "Wähle Wiederholen, um erneut zu versuchen, Java zu finden!"

; ----------------------------------------
; INSTALLABLE MODULES

;InstType "Normal"

Section "check Java 8 installed" Sec_Java_id
   	SectionIn 1 RO
   	SetShellVarContext current

	; init of JRE section
	; detect JRE first
	var /global InstalledJREVersion
	${If} ${RunningX64}
		SetRegView 64
	${EndIf}
    Retry:
	ReadRegStr $InstalledJREVersion HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" "CurrentVersion"

	${If} $InstalledJREVersion != "11"
		MessageBox MB_ICONEXCLAMATION|MB_YESNO "$(yacyNoJavaFoundOpenBrowser)" IDNO ContinueWithoutJava
		ExecShell open "https://adoptium.net/de/temurin/releases/?version=8"

		MessageBox MB_ICONEXCLAMATION|MB_ABORTRETRYIGNORE "$(yacyNoJavaFoundContinue)" IDIGNORE ContinueWithoutJava IDRETRY Retry
		Abort
	${EndIf}
    ContinueWithoutJava:
SectionEnd

Section "YaCy"
	SectionIn 1 RO
	SetShellVarContext current ; use system variables (folders) for current user
	RMDir /r "$INSTDIR\lib" ;remove old libraries in case of update
	RMDir /r "$SMPROGRAMS\YaCy" ;clear old shortcuts
	Delete "$QUICKLAUNCH\YaCy-Search.lnk" ;old
	Delete "$DESKTOP\YaCy-Search.lnk" ;old
	Delete "$SMSTARTUP\start YaCy (no console).lnk" ;old
	
	SetOutPath $INSTDIR
	;set noindex attribute for windows indexing service
    	nsExec::Exec 'attrib +I "$INSTDIR"'
	nsExec::Exec 'attrib +I "$INSTDIR\*" /S /D'
    
	File /r /x *.sh "RELEASE\MAIN\*"

	WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "DisplayName" "YaCy"
	WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "UninstallString" '"$INSTDIR\uninstall.exe"'
	WriteUninstaller "uninstall.exe"
SectionEnd

Section "Start Menu Group"
	SectionIn 1
	SetShellVarContext current
	CreateDirectory "$SMPROGRAMS\YaCy"
	CreateShortCut "$SMPROGRAMS\YaCy\YaCy.lnk" "$INSTDIR\startYACY.bat" "" "$INSTDIR\addon\YaCy.ico" "" SW_SHOWMINIMIZED
	CreateShortCut "$SMPROGRAMS\YaCy\Readme.lnk" "$INSTDIR\readme.txt"
	CreateShortCut "$SMPROGRAMS\YaCy\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
SectionEnd

Section "Desktop Icon"
	SectionIn 1
	SetShellVarContext current
	CreateShortCut "$DESKTOP\YaCy.lnk" "$INSTDIR\startYACY.bat" "" "$INSTDIR\addon\YaCy.ico" "" SW_SHOWMINIMIZED
SectionEnd

Section "Configure Firewall" Sec_Firewall_id
	SectionIn 1
	SetShellVarContext current
	call OpenFirewall	
SectionEnd

/*
Section "YaCy in Startup"
	SetShellVarContext current
	CreateShortCut "$SMSTARTUP\YaCy.lnk" "$INSTDIR\startYACY.bat" "" "$INSTDIR\addon\YaCy.ico" "" SW_SHOWMINIMIZED
SectionEnd
*/

; ----------------------------------------
; UNINSTALLER

Section "Uninstall"
	ClearErrors
	Delete "$INSTDIR\DATA\yacy.running"
	IfErrors 0 uninstall
	MessageBox MB_ICONSTOP "$(stillRunning)" /SD IDOK
	Goto nouninstall

	uninstall:
	Call un.CloseFirewall
	SetShellVarContext current

	RMDir /r "$INSTDIR\addon"
    	RMDir /r "$INSTDIR\bin"
	RMDir /r "$INSTDIR\classes"
	RMDir /r "$INSTDIR\defaults"
	RMDir /r "$INSTDIR\htroot"
	RMDir /r "$INSTDIR\langstats"
	RMDir /r "$INSTDIR\lib"
	RMDir /r "$INSTDIR\libbuild"
	RMDir /r "$INSTDIR\libx"
	RMDir /r "$INSTDIR\locales"
	RMDir /r "$INSTDIR\ranking"
	RMDir /r "$INSTDIR\skins"
	RMDir /r "$INSTDIR\source"
	Delete "$INSTDIR\*.*"

	MessageBox MB_YESNO|MB_ICONQUESTION "$(keepData)" /SD IDYES IDYES keepdata
	
	;delete all
	RMDir /r "$INSTDIR"
	
	;or jump to this
	keepdata:
	RMDir /r "$SMPROGRAMS\YaCy"
	Delete "$DESKTOP\YaCy.lnk"
	Delete "$SMSTARTUP\YaCy.lnk"
	
	DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy"
	nouninstall:
SectionEnd

; ----------------------------------------
; FUNCTIONS

Function .onInit
	; check Windows-Version, need Win 2000 or higher
	${If} ${AtMostWinME} 
		MessageBox MB_ICONSTOP "$(yacyNeedOs)" 
		Abort 
	${EndIf}		
	
	; init of Firewall section, only valid for WindowsXP SP2/SP3 and Vista/Win 7 with Admin 
	var /global FirewallServiceStart 
	IntOp $FirewallServiceStart 3 + 0

	${If} ${IsWinVista} 
	${OrIf} ${IsWin7}
		StrCpy $WinAddFwRulePort '${WinVistaAddFwRulePort}'
		StrCpy $WinDelFwRulePort '${WinVistaDelFwRulePort}'
		ReadRegDWORD $FirewallServiceStart HKLM "SYSTEM\CurrentControlSet\services\MpsSvc" "Start"
	${EndIf}

	${If} ${IsWinXP} 
	${AndIf} ${AtLeastServicePack} 2
		StrCpy $WinAddFwRulePort '${WinXPAddFwRulePort}'
		StrCpy $WinDelFwRulePort '${WinXPDelFwRulePort}'
		ReadRegDWORD $FirewallServiceStart HKLM "SYSTEM\CurrentControlSet\services\SharedAccess" "Start"
	${EndIf}
	
	;need Admin for firewall-config
	${IfNot} $0 = "Admin" 
		IntOp $FirewallServiceStart 3 + 0
	${EndIf}

	; hide and deselect Firewall if no proper configuration
	${If} $FirewallServiceStart > 2 
		SectionSetText ${Sec_Firewall_id} ""
		SectionSetFlags ${Sec_Firewall_id} 0
	${EndIf}
FunctionEnd

Function CheckDriveSpace
	var /global RootFolder
   	var /global TempDriveFree
   	var /global RootFolderType

	; if "\\Folder" it's a Network-Folder
   	StrCpy $RootFolder $InstDir 2
   	StrCmp $RootFolder "\\" NetworkFolder Driveletter

   	Networkfolder:
		; prepare String for DriveSpace
      		${GetRoot} $RootFolder $InstDir
      		goto NoHDD

	; now check drive-letters
   	Driveletter:
   		StrCpy $RootFolder $InstDir 3

		; prepare for {GetDrives-Loop}
   		StrCpy $RootFolderType "invalid"
   		${GetDrives} "ALL" "CheckDriveType"

		; jump if error
   		StrCmp $RootFolderType "invalid" CheckSpace

		; jump if HDD
   		StrCmp $RootFolderType "HDD" CheckSpace

   	NoHDD:
	; stay on folder-selection if user wants to give another folder, else check free space
      		MessageBox MB_ICONEXCLAMATION|MB_YESNO "$(yacyNoHd)" IDYES NextPage
      		Abort      

   	CheckSpace:

   	ClearErrors
   	${DriveSpace} $RootFolder "/D=F /S=G" $TempDriveFree
	; if DriveSpace fails for any reason -> jump ahead
   	IfErrors NextPage

   	${If} $TempDriveFree < ${RecommendSpace} 
		MessageBox MB_ICONEXCLAMATION|MB_YESNO "$(yacyNeedSpace)" IDYES NextPage
      		Abort	
	${EndIf}

   	NextPage:
FunctionEnd

Function CheckDriveType
; based on http://nsis.sourceforge.net/GetDrives  
   	${If} $9 == $RootFolder 
   		StrCpy $RootFolderType $8   
   		StrCpy $0 StopGetDrives
	${EndIf}
   	Push $0
FunctionEnd

Function OpenFirewall
	var /global ExecErrorCode
	; run netsh 
	nsExec::ExecToStack '$WinAddFwRulePort'
	pop $ExecErrorCode
	; if run without error register for uninstall and clear finish page 
	${If} $ExecErrorCode = "0"
		WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "DelFwRulePort" '$WinDelFwRulePort'
		IntOp $FirewallServiceStart 0 + 0
	${Else}
		IntOp $FirewallServiceStart 3 + 0
	${EndIf}
FunctionEnd

Function SHOW_PageFinish_custom
	; hide and disable firewall info from wiki if firewall is open
	${If} $FirewallServiceStart = 0 
		SendMessage $mui.FinishPage.ShowReadme ${BM_SETCHECK} 0 0
	    	ShowWindow $mui.FinishPage.ShowReadme ${SW_HIDE}
	${EndIf}
FunctionEnd

Function un.CloseFirewall
	; get string for closing port from registy
	ReadRegStr '$WinDelFwRulePort' HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "DelFwRulePort"
	; if found > run netsh to close port
	${IfNot} '$WinDelFwRulePort' == '' 
		nsExec::ExecToStack '$WinDelFwRulePort'
	${EndIf}
FunctionEnd
