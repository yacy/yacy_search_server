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

; ----------------------------------------
; GENERAL

Name "YaCy @REPL_VERSION@"
OutFile "RELEASE\WINDOWS\yacy_v@REPL_VERSION@_@REPL_DATE@_@REPL_REVISION_NR@.exe"

;default installation folder
InstallDir "$PROFILE\YaCy"
InstallDirRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "UninstallString"

;requested execution level on Vista
RequestExecutionLevel user

SetCompressor /SOLID LZMA
!insertmacro MUI_RESERVEFILE_LANGDLL ;loads faster

; ----------------------------------------
; JAVA VERSION

!define JRE_VERSION6 "1.6"
!define JRE_URL "http://javadl.sun.com/webapps/download/AutoDL?BundleId=23112" ;jre-6u7-windows-i586-p.exe

; ----------------------------------------
; GENERAL APPEARANCE

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
!insertmacro MUI_LANGUAGE "Spanish"
!insertmacro MUI_LANGUAGE "SpanishInternational"
!insertmacro MUI_LANGUAGE "SimpChinese"
!insertmacro MUI_LANGUAGE "TradChinese"
!insertmacro MUI_LANGUAGE "Japanese"
!insertmacro MUI_LANGUAGE "Korean"
!insertmacro MUI_LANGUAGE "Italian"
!insertmacro MUI_LANGUAGE "Dutch"
!insertmacro MUI_LANGUAGE "Danish"
!insertmacro MUI_LANGUAGE "Swedish"
!insertmacro MUI_LANGUAGE "Norwegian"
!insertmacro MUI_LANGUAGE "NorwegianNynorsk"
!insertmacro MUI_LANGUAGE "Finnish"
!insertmacro MUI_LANGUAGE "Greek"
!insertmacro MUI_LANGUAGE "Russian"
!insertmacro MUI_LANGUAGE "Portuguese"
!insertmacro MUI_LANGUAGE "PortugueseBR"
!insertmacro MUI_LANGUAGE "Polish"
!insertmacro MUI_LANGUAGE "Ukrainian"
!insertmacro MUI_LANGUAGE "Czech"
!insertmacro MUI_LANGUAGE "Slovak"
!insertmacro MUI_LANGUAGE "Croatian"
!insertmacro MUI_LANGUAGE "Bulgarian"
!insertmacro MUI_LANGUAGE "Hungarian"
!insertmacro MUI_LANGUAGE "Thai"
!insertmacro MUI_LANGUAGE "Romanian"
!insertmacro MUI_LANGUAGE "Latvian"
!insertmacro MUI_LANGUAGE "Macedonian"
!insertmacro MUI_LANGUAGE "Estonian"
!insertmacro MUI_LANGUAGE "Turkish"
!insertmacro MUI_LANGUAGE "Lithuanian"
!insertmacro MUI_LANGUAGE "Slovenian"
!insertmacro MUI_LANGUAGE "Serbian"
!insertmacro MUI_LANGUAGE "SerbianLatin"
!insertmacro MUI_LANGUAGE "Arabic"
!insertmacro MUI_LANGUAGE "Farsi"
!insertmacro MUI_LANGUAGE "Hebrew"
!insertmacro MUI_LANGUAGE "Indonesian"
!insertmacro MUI_LANGUAGE "Mongolian"
!insertmacro MUI_LANGUAGE "Luxembourgish"
!insertmacro MUI_LANGUAGE "Albanian"
!insertmacro MUI_LANGUAGE "Breton"
!insertmacro MUI_LANGUAGE "Belarusian"
!insertmacro MUI_LANGUAGE "Icelandic"
!insertmacro MUI_LANGUAGE "Malay"
!insertmacro MUI_LANGUAGE "Bosnian"
!insertmacro MUI_LANGUAGE "Kurdish"
!insertmacro MUI_LANGUAGE "Irish"
!insertmacro MUI_LANGUAGE "Uzbek"
!insertmacro MUI_LANGUAGE "Galician"
!insertmacro MUI_LANGUAGE "Afrikaans"
!insertmacro MUI_LANGUAGE "Catalan"

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
	
	File /r "RELEASE\MAIN\*"
	File /r "RELEASE\EXT\*"

	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "DisplayName" "YaCy"
	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "UninstallString" '"$INSTDIR\uninstall.exe"'
	WriteUninstaller "uninstall.exe"
SectionEnd

Section "Java"
    SectionIn 1 RO
    SetShellVarContext current
    Call DetectJRE
SectionEnd

Section "Start Menu Group"
	SectionIn 1
	SetShellVarContext current
	CreateDirectory "$SMPROGRAMS\YaCy"
	CreateShortCut "$SMPROGRAMS\YaCy\YaCy.lnk" "$INSTDIR\startYACY.bat" "" "$INSTDIR\addon\YaCy.ico" "" SW_SHOWMINIMIZED
	CreateShortCut "$SMPROGRAMS\YaCy\stop.lnk" "$INSTDIR\stopYACY.bat" "" "$INSTDIR\addon\YaCy.ico" "" SW_SHOWMINIMIZED
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
	IfFileExists "$INSTDIR\DATA\yacy.running" 0 +3
	MessageBox MB_ICONSTOP "YaCy is still running. Please stop YaCy first." /SD IDOK
	Goto nouninstall
	
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

	MessageBox MB_YESNO|MB_ICONQUESTION "Keep the data?" /SD IDYES IDYES keepdata
	
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

; ----------------------------------------
; FUNCTIONS

Function GetJRE
; based on http://nsis.sourceforge.net/Simple_Java_Runtime_Download_Script	
	userInfo::getAccountType
	Pop $0
		StrCmp $0 "Admin" +3
		MessageBox MB_ICONEXCLAMATION "You need Administrator privileges to install Java. \
		It will now be downloaded to the shared documents folder. \
		YaCy won't run without Java." /SD IDOK
	
	SetShellVarContext all
	StrCpy $2 "$DOCUMENTS\Java Runtime Environment (install for YaCy).exe"
	SetShellVarContext current
	nsisdl::download /TIMEOUT=30000 ${JRE_URL} $2
	Pop $R0 ;Get the return value
		StrCmp $R0 "success" +3
		MessageBox MB_OK "Download failed: $R0" /SD IDOK
		Return
	StrCmp $0 "Admin" +3
		CreateShortCut "$DESKTOP\Install Java for YaCy.lnk" "$2"
		Return ; don't delete if not admin
	ExecWait "$2 /s"
	Delete $2
FunctionEnd

Function DetectJRE
	ReadRegStr $2 HKLM "SOFTWARE\JavaSoft\Java Runtime Environment" "CurrentVersion"
	StrCmp $2 ${JRE_VERSION6} doneDetectJRE
	Call GetJRE
	doneDetectJRE:
FunctionEnd