;yacy.nsi
;--------
;(C) 2004-2006 by Alexander Schier
;(C) 2008 by David Wieditz

!define JRE_VERSION6 "1.6"
!define JRE_VERSION5 "1.5"
!define JRE_URL "http://javadl.sun.com/webapps/download/AutoDL?BundleId=23112" ;jre-6u7-windows-i586-p.exe

Name "YaCy"
Icon "RELEASE\MAIN\addon\YaCy.ico"
UninstallIcon "${NSISDIR}\Contrib\Graphics\Icons\orange-uninstall.ico"

;requested execution level on Vista
RequestExecutionLevel user

OutFile "RELEASE\WINDOWS\yacy_v@REPL_VERSION@_@REPL_DATE@_@REPL_REVISION_NR@.exe"
InstallDir "$PROFILE\YaCy"
InstallDirRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "UninstallString"

SetCompressor /SOLID LZMA

InstType /CUSTOMSTRING=Custom

#InstType "Minimal"
InstType "Normal"
#InstType "Full"

; The text to prompt the user to enter a directory
ComponentText "This will install YaCy v@REPL_VERSION@ (Build @REPL_DATE@) on your computer. Select which optional things you want to be installed."
; The text to prompt the user to enter a directory
DirText "Choose a directory to install into:"

LicenseText "You must agree to this license to install YaCy."
LicenseData "gpl.txt"

Section "Binaries (required)"
	SectionIn 1 2 3 RO
	
	;clear old shortcuts
	SetShellVarContext current
	RMDir /r "$SMPROGRAMS\YaCy"
	Delete "$QUICKLAUNCH\YaCy-Search.lnk" ;old
	Delete "$DESKTOP\YaCy-Search.lnk" ;old
	Delete "$SMSTARTUP\start YaCy (no console).lnk" ;old
	
	SetOutPath $INSTDIR
	
	File /r "RELEASE\MAIN\*"
	File /r "RELEASE\EXT\*"

	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "DisplayName" "YaCy"
	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "UninstallString" '"$INSTDIR\uninstall.exe"'
	WriteUninstaller "uninstall.exe"

	Call DetectJRE
SectionEnd

Section "Shortcuts in the Start Menu"
	SectionIn 1 2 3
	SetShellVarContext current
	CreateDirectory "$SMPROGRAMS\YaCy"
	CreateShortCut "$SMPROGRAMS\YaCy\YaCy.lnk" "$INSTDIR\startYACY.bat" "" "$INSTDIR\addon\YaCy.ico" "" SW_SHOWMINIMIZED
	CreateShortCut "$SMPROGRAMS\YaCy\stop.lnk" "$INSTDIR\stopYACY.bat" "" "$INSTDIR\addon\YaCy.ico" "" SW_SHOWMINIMIZED
	CreateShortCut "$SMPROGRAMS\YaCy\Readme.lnk" "$INSTDIR\readme.txt"
	CreateShortCut "$SMPROGRAMS\YaCy\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
SectionEnd

Section "YaCy on the Desktop"
	SectionIn 1 2 3
	SetShellVarContext current
	CreateShortCut "$DESKTOP\YaCy.lnk" "$INSTDIR\startYACY.bat" "" "$INSTDIR\addon\YaCy.ico" "" SW_SHOWMINIMIZED
SectionEnd

Section "YaCy in Startup"
	SetShellVarContext current
	CreateShortCut "$SMSTARTUP\YaCy.lnk" "$INSTDIR\startYACY.bat" "" "$INSTDIR\addon\YaCy.ico" "" SW_SHOWMINIMIZED
SectionEnd

Section "Uninstall"
	IfFileExists "$INSTDIR\DATA\yacy.running" 0 +3
	MessageBox MB_ICONSTOP "YaCy is still running. Please stop YaCy first."
	Goto nouninstall
	
	MessageBox MB_YESNO|MB_ICONQUESTION "Do you really want to uninstall YaCy?" IDNO nouninstall

	SetShellVarContext current

	RMDir /r "$INSTDIR\addon"
	RMDir /r "$INSTDIR\classes"
	RMDir /r "$INSTDIR\defaults"
	RMDir /r "$INSTDIR\htroot"
	RMDir /r "$INSTDIR\lib"
	RMDir /r "$INSTDIR\libx"
	RMDir /r "$INSTDIR\locales"
	RMDir /r "$INSTDIR\ranking"
	RMDir /r "$INSTDIR\skins"
	RMDir /r "$INSTDIR\source"
	Delete "$INSTDIR\*.*"

	MessageBox MB_YESNO|MB_ICONQUESTION "Do you want to keep the Data (i.e. if you want to reinstall later)?" IDYES keepdata
	
	;delete all
	RMDir /r "$INSTDIR"
	
	;or jump to this
	keepdata:
	RMDir /r "$SMPROGRAMS\YaCy"
	Delete "$DESKTOP\YaCy.lnk"
	Delete "$SMSTARTUP\YaCy.lnk"
	
	DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy"
	nouninstall:
SectionEnd

Function GetJRE
; based on http://nsis.sourceforge.net/Simple_Java_Runtime_Download_Script	
	MessageBox MB_OK "YaCy uses Java ${JRE_VERSION6}. \
	It will now be downloaded and installed."
	
	userInfo::getAccountType
	Pop $0
		StrCmp $0 "Admin" +3
		MessageBox MB_ICONEXCLAMATION "You need Administrator privileges to install Java. \
		It will now be downloaded to the shared documents folder. \
		YaCy won't run without Java."
	
	SetShellVarContext all
	StrCpy $2 "$DOCUMENTS\Java Runtime Environment (install for YaCy).exe"
	SetShellVarContext current
	nsisdl::download /TIMEOUT=30000 ${JRE_URL} $2
	Pop $R0 ;Get the return value
		StrCmp $R0 "success" +3
		MessageBox MB_OK "Download failed: $R0"
		Return
	StrCmp $0 "Admin" +4
		CreateShortCut "$DESKTOP\Install Java for YaCy.lnk" "$2"
		Return ; don't delete if not admin
	ExecWait $2
	Delete $2
FunctionEnd

Function DetectJRE
	ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" "CurrentVersion"
	StrCmp $2 ${JRE_VERSION6} doneDetectJRE
	StrCmp $2 ${JRE_VERSION5} doneDetectJRE
	Call GetJRE
	doneDetectJRE:
FunctionEnd