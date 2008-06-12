;yacy.nsi
;--------
;(C) 2004-2006 by Alexander Schier
;(C) 2008 by David Wieditz

!define JRE_VERSION6 "1.6"
!define JRE_VERSION5 "1.5"
!define JRE_URL "http://javadl.sun.com/webapps/download/AutoDL?BundleId=18714&/jre-6u5-windows-i586-p.exe"

Name "YaCy"
Icon "RELEASE\MAIN\addon\YaCy.ico"
UninstallIcon "${NSISDIR}\Contrib\Graphics\Icons\orange-uninstall.ico"


OutFile "RELEASE\WINDOWS\yacy_v@REPL_VERSION@_@REPL_DATE@_@REPL_REVISION_NR@.exe"
InstallDir $PROGRAMFILES\YaCy
InstallDirRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "UninstallString"

SetCompressor /SOLID LZMA

InstType /CUSTOMSTRING=Custom

#InstType "Minimal"
InstType "Normal"
#InstType "Full"

; The text to prompt the user to enter a directory
ComponentText "This will install YaCy v@REPL_VERSION@ (Build @REPL_DATE@) on your computer. Select which optional things you want to be installed."
; The text to prompt the user to enter a directory
#DirText "If an old version was installed into another location (eg. AnomicHTTPProxy), you have to move the DATA Directory to the new location."
DirText "Choose a directory to install into:"

LicenseText "You must agree to this license to install YaCy."
LicenseData "gpl.txt"

Section "Binaries (required)"
	SectionIn 1 2 3 RO
	SetOutPath $INSTDIR
	
	File /r "RELEASE\MAIN\*"
	File /r "RELEASE\EXT\*"
/*
	#main files
	File "startYACY.bat"
	File "startYACY_noconsole.bat"
	File "stopYACY.bat"
	#File "startYACY_Win9x.bat"
	#File "startYACY_noconsole_Win9x.bat"
	#File "stopYACY_Win9x.bat"
	
	File "httpd.mime"
	File "yacy.badwords.example"
	File "yacy.logging"
	File "yacy.stopwords"
	File "yacy.yellow"

	#texts
	File "AUTHORS"
	File "COPYRIGHT"
	File "gpl.txt"
	File "readme.txt"
	File "ChangeLog"

	#defaults
	SetOutPath "$INSTDIR\defaults"
	File /r "defaults\*"
	
	#classes
	SetOutPath "$INSTDIR\classes"
	File /r "classes\*"
	
	#lib
	SetOutPath "$INSTDIR\lib"
	File /r "lib\*"

	#libx
	SetOutPath "$INSTDIR\libx"
	File /r "libx\*"

	#locales
	SetOutPath "$INSTDIR\locales"
	File /r "locales\*"

	#skins
	SetOutPath "$INSTDIR\skins"
	File /r "skins\*"

	#ranking
	SetOutPath "$INSTDIR\ranking"
	File /r "ranking\*"

	#htroot non devel
	SetOutPath "$INSTDIR\htroot"
	File "htroot\*.html"
	File "htroot\*.inc"
	File "htroot\*.soap"
	File "htroot\*.xml"
	File "htroot\*.xsl"
	File "htroot\*.rss"
	File "htroot\*.csv"
	File "htroot\*.class"
	File "htroot\*.ico"
    File "htroot\*.bmp"
    File "htroot\*.gif"
    File "htroot\*.png"
    File "htroot\*.src" #firefox plugin
	#File "htroot\*.gif"
	File "htroot\*.pac" #proxy autoconfig

	#yacy non-devel
	SetOutPath "$INSTDIR\htroot\yacy"
	File "htroot\yacy\*.html"
	File "htroot\yacy\*.class"

	#yacy javascript
	SetOutPath "$INSTDIR\htroot\js"
	File "htroot\js\*.js"

	SetOutPath "$INSTDIR\htroot\www"
	File "htroot\www\*.html"
	File "htroot\www\*.class"

	#yacy xml
    #TODO: Split in source/binary
	SetOutPath "$INSTDIR\htroot\xml"
	File /r "htroot\xml\*"

	#proxymsg non-devel
	SetOutPath "$INSTDIR\htroot\proxymsg"
	File "htroot\proxymsg\*.html"
	File "htroot\proxymsg\*.inc"

	#templates
	SetOutPath "$INSTDIR\htroot\env"
	File /r "htroot\env\*"

	#htdocs default
	SetOutPath "$INSTDIR\htroot\htdocsdefault"
	File "htroot\htdocsdefault\*.html"
	File "htroot\htdocsdefault\*.class"

	SetOutPath "$INSTDIR\ranking"
	File /r "ranking\*"

	SetOutPath $INSTDIR
*/


	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "DisplayName" "YaCy"
	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy" "UninstallString" '"$INSTDIR\uninstall.exe"'
	WriteUninstaller "uninstall.exe"

	Call DetectJRE
SectionEnd

/*
Section "Addons"
	SectionIn 2 3
	SetOutPath $INSTDIR\addon
	File /r "addon\*"

	SetOutPath $INSTDIR
SectionEnd
*/

#Section "Docs"
#	SectionIn 2 3
#	SetOutPath $INSTDIR\doc
#	File /r "doc\*"
#
#	SetOutPath $INSTDIR
#SectionEnd
/*
Section "Development"
	SectionIn 3
	SetOutPath $INSTDIR\source

	File /r "source\*"

	SetOutPath $INSTDIR
	File "build.xml"
	File "build.properties"
	
	SetOutPath "$INSTDIR\htroot"
	File "htroot\*.java"
	SetOutPath "$INSTDIR\htroot\yacy"
	File "htroot\yacy\*.java"
	SetOutPath "$INSTDIR\htroot\htdocsdefault"
	File "htroot\htdocsdefault\*.java"
	SetOutPath "$INSTDIR\htroot\www"
	File "htroot\www\*.java"
	
SectionEnd
*/
Section "Shortcuts in the Start Menu"
	SectionIn 1 2 3
	SetOutPath "$INSTDIR"
	CreateDirectory "$SMPROGRAMS\YaCy"
	CreateShortCut "$SMPROGRAMS\YaCy\start YaCy.lnk" "$INSTDIR\startYACY.bat" "" "$INSTDIR\addon\YaCy.ico"
	CreateShortCut "$SMPROGRAMS\YaCy\start YaCy (no console).lnk" "$INSTDIR\startYACY_noconsole.bat" "" "$INSTDIR\addon\YaCy.ico"
	CreateShortCut "$SMPROGRAMS\YaCy\stop YaCy.lnk" "$INSTDIR\stopYACY.bat" "" "$INSTDIR\addon\YaCy.ico"
	CreateShortCut "$SMPROGRAMS\YaCy\Readme.lnk" "$INSTDIR\readme.txt"
	CreateShortCut "$SMPROGRAMS\YaCy\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
	CreateShortCut "$SMPROGRAMS\YaCy\YaCy-Search.lnk" "$INSTDIR\addon\YaCy-Search.html" "" "$INSTDIR\addon\YaCy.ico"
SectionEnd

#Section "YACY on the Desktop"
#	SectionIn 1 2 3
#	SetOutPath "$INSTDIR"
#	CreateShortCut "$DESKTOP\start YACY.lnk" ""
#SectionEnd

Section "YaCy on the Desktop"
	SectionIn 1 2 3
	CreateShortCut "$DESKTOP\YaCy.lnk" "$INSTDIR\startYACY.bat" "" "$INSTDIR\addon\YaCy.ico"
SectionEnd

Section "Searchpage on the Desktop"
	CreateShortCut "$DESKTOP\YaCy-Search.lnk" "$INSTDIR\addon\YaCy-Search.html" "" "$INSTDIR\addon\YaCy.ico"
SectionEnd

Section "Searchpage in the Quicklaunch"
	SectionIn 1 2 3
	CreateShortCut "$QUICKLAUNCH\YaCy-Search.lnk" "$INSTDIR\addon\YaCy-Search.html" "" "$INSTDIR\addon\YaCy.ico"
SectionEnd

Section "YaCy in Startup"
	CreateShortCut "$SMSTARTUP\start YaCy (no console).lnk" "$INSTDIR\startYACY_noconsole.bat" "" "$INSTDIR\addon\YaCy.ico"
SectionEnd

Section "Uninstall"

	MessageBox MB_YESNO|MB_ICONQUESTION "Do you really want to uninstall YaCy?" IDNO nouninstall

	DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YaCy"

	RMDir /r "$INSTDIR\addon"
	RMDir /r "$INSTDIR\classes"
	RMDir /r "$INSTDIR\defaults"
#	RMDir /r "$INSTDIR\doc"
	RMDir /r "$INSTDIR\htroot"
	RMDir /r "$INSTDIR\lib"
	RMDir /r "$INSTDIR\libx"
	RMDir /r "$INSTDIR\locales"
	RMDir /r "$INSTDIR\ranking"
	RMDir /r "$INSTDIR\skins"
	RMDir /r "$INSTDIR\source"
	Delete "$INSTDIR\*.*"

	MessageBox MB_YESNO|MB_ICONQUESTION "Do you want to keep the Data (i.e. if you want to reinstall later)?" IDYES keepdata
	
	#delete all
	RMDir /r "$INSTDIR"
	
	#or jump to this
	keepdata:
	RMDir /r "$SMPROGRAMS\YaCy"
	Delete "$QUICKLAUNCH\YaCy-Search.lnk"
	Delete "$DESKTOP\YaCy.lnk"
	Delete "$DESKTOP\YaCy-Search.lnk"
	Delete "$SMSTARTUP\start YaCy (no console).lnk"
	
	nouninstall:
SectionEnd

Function GetJRE
# based on http://nsis.sourceforge.net/Simple_Java_Runtime_Download_Script
	MessageBox MB_OK "YaCy uses Java ${JRE_VERSION6}. It will now be downloaded and installed."

	StrCpy $2 "$TEMP\Java Runtime Environment.exe"
	nsisdl::download /TIMEOUT=30000 ${JRE_URL} $2
	Pop $R0 ;Get the return value
		StrCmp $R0 "success" +3
		MessageBox MB_OK "Download failed: $R0"
		Return
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