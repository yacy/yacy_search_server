;yacy.nsi
; ----------------------------------------
;(C) 2004-2006 by Alexander Schier
;(C) 2008 by David Wieditz
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

; ----------------------------------------
; GENERAL

VIProductVersion "@REPL_VERSION@.0.0"
VIAddVersionKey "ProductName" "YaCy"
VIAddVersionKey "LegalCopyright" "YaCy"
VIAddVersionKey "FileVersion" "@REPL_VERSION@"
VIAddVersionKey "FileDescription" "YaCy"
VIAddVersionKey "OriginalFilename" "yacy_v@REPL_VERSION@_@REPL_DATE@_@REPL_REVISION_NR@.exe"

Name "YaCy @REPL_VERSION@"
OutFile "RELEASE\WINDOWS\yacy_v@REPL_VERSION@_@REPL_DATE@_@REPL_REVISION_NR@.exe"

;default installation folder
InstallDir "$PROFILE\YaCy"
InstallDirRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "UninstallString"

;requested execution level on Vista
RequestExecutionLevel user

SetCompressor /SOLID LZMA
!insertmacro MUI_RESERVEFILE_LANGDLL ;loads faster

; ----------------------------------------
; JAVA VERSION
; http://www.java.com/de/download/manual.jsp BundleId +1 / +2

!define JRE_VERSION6 "1.6"
!define JRE_32 "http://javadl.sun.com/webapps/download/AutoDL?BundleId=37718" ;jre-6u18-windows-i586.exe
!define JRE_64 "http://javadl.sun.com/webapps/download/AutoDL?BundleId=37401" ;jre-6u18-windows-x64.exe

; ----------------------------------------
; GENERAL APPEARANCE

BrandingText "yacy.net"

!define MUI_ICON "RELEASE\MAIN\addon\YaCy.ico"
!define MUI_UNICON "${NSISDIR}\Contrib\Graphics\Icons\orange-uninstall.ico"

!define MUI_WELCOMEFINISHPAGE_BITMAP "RELEASE\MAIN\addon\installer\network.bmp"

!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "RELEASE\MAIN\addon\installer\logo.bmp"
!define MUI_HEADERIMAGE_BITMAP_NOSTRETCH

!define MUI_ABORTWARNING ;display warning before aborting installation

; ----------------------------------------
; INSTALLER PAGES

!insertmacro MUI_PAGE_WELCOME

!insertmacro MUI_PAGE_LICENSE gpl.txt

!define MUI_COMPONENTSPAGE_NODESC
!insertmacro MUI_PAGE_COMPONENTS
ComponentText "YaCy v@REPL_VERSION@ (Build @REPL_DATE@)"

!insertmacro MUI_PAGE_DIRECTORY

!insertmacro MUI_PAGE_INSTFILES

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
LangString noAdminForJava 0 "You need Administrator privileges to install Java. Java will now be downloaded to the shared documents folder. YaCy won't run without Java."

LangString stillRunning ${LANG_FRENCH} "YaCy is still active. Please stop YaCy first."
LangString keepData 0 "Do you want to keep the data?"
LangString noAdminForJava 0 "You need Administrator privileges to install Java. Java will now be downloaded to the shared documents folder. YaCy won't run without Java."

LangString stillRunning ${LANG_GERMAN} "YaCy ist noch aktiv. Bitte beenden Sie YaCy."
LangString keepData 0 "Moechten Sie die Daten behalten?"
LangString noAdminForJava 0 "Sie benoetigen Administrator-Rechte um Java zu installieren. Java wird nun in 'Gemeinsame Dokumente' gespeichert. YaCy benoetigt Java zur Ausfuehrung."

; ----------------------------------------
; INSTALLABLE MODULES

;InstType "Normal"

Section "YaCy"
	SectionIn 1 RO
	SetShellVarContext current
	RMDir /r "$SMPROGRAMS\YaCy" ;clear old shortcuts
	Delete "$QUICKLAUNCH\YaCy-Search.lnk" ;old
	Delete "$DESKTOP\YaCy-Search.lnk" ;old
	Delete "$SMSTARTUP\start YaCy (no console).lnk" ;old
	
	SetOutPath $INSTDIR
	;set noindex attribute for windows indexing service
    Exec 'attrib +I "$INSTDIR"'
    Exec 'attrib +I "$INSTDIR\*" /S /D'
    
	File /r "RELEASE\MAIN\*"

	WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "DisplayName" "YaCy"
	WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "UninstallString" '"$INSTDIR\uninstall.exe"'
	WriteUninstaller "uninstall.exe"
SectionEnd

Section "Sun Java"
    SectionIn 1
    SetShellVarContext current
    Call DetectJRE
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

/*
Section "YaCy in Startup"
	SetShellVarContext current
	CreateShortCut "$SMSTARTUP\YaCy.lnk" "$INSTDIR\startYACY.bat" "" "$INSTDIR\addon\YaCy.ico" "" SW_SHOWMINIMIZED
SectionEnd
*/

; ----------------------------------------
; UNINSTALLER

Section "Uninstall"
	IfFileExists "$INSTDIR\DATA\yacy.running" 0 uninstall
	MessageBox MB_ICONSTOP "$(stillRunning)" /SD IDOK
	Goto nouninstall
    
	uninstall:
	SetShellVarContext current

	RMDir /r "$INSTDIR\addon"
    RMDir /r "$INSTDIR\bin"
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

Function GetJRE
; based on http://nsis.sourceforge.net/Simple_Java_Runtime_Download_Script	
    ${If} ${RunningX64}
    StrCpy $3 ${JRE_64}
    ${Else}
    StrCpy $3 ${JRE_32}
    ${EndIf}
    
	userInfo::getAccountType
	Pop $0
		StrCmp $0 "Admin" download
		MessageBox MB_ICONEXCLAMATION "$(noAdminForJava)" /SD IDOK
    download:
	SetShellVarContext all
	StrCpy $2 "$DOCUMENTS\Java Runtime (install for YaCy).exe"
	SetShellVarContext current
	nsisdl::download /TIMEOUT=30000 $3 $2
	Pop $R0 ;Get the return value
		StrCmp $R0 "success" +3
		MessageBox MB_OK "Download failed: $R0" /SD IDOK
		Return
	StrCmp $0 "Admin" install
		CreateShortCut "$DESKTOP\Install Java for YaCy.lnk" "$2"
		Return ; don't delete if not admin
    install:
	ExecWait "$2 /s"
	Delete $2
FunctionEnd

Function DetectJRE
	ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" "CurrentVersion"
	StrCmp $2 ${JRE_VERSION6} doneDetectJRE
	Call GetJRE
	doneDetectJRE:
FunctionEnd