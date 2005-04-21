;yacy.nsi
;--------
;part of YaCy (C) by Michael Peter Christen
;this file is contributed by Alexander Schier
;Cologne, 2005
;last major change: 26.03.2005
Name "YACY"

OutFile "yacy_v0.36_20050326.exe"
InstallDir $PROGRAMFILES\YACY

InstType /CUSTOMSTRING=Custom

InstType "Minimal"
InstType "Normal"
InstType "Full"

; The text to prompt the user to enter a directory
ComponentText "This will install YaCy v0.36(Build 20050326) on your computer. Select which optional things you want installed."
; The text to prompt the user to enter a directory
#DirText "If an old Version was installed into another locAtion(eg. AnomicHTTPProxy), you have to move the DATA Directory to the new location."
DirText "Choose a directory to install in to:"

LicenseText "You must agree the License to install YaCy"
LicenseData "gpl.txt"

Section "Binaries (required)"
	SectionIn 1 2 3 RO
	SetOutPath $INSTDIR
	#main files
	File "httpd.mime"
	File "startYACY.bat"
	File "startYACY_noconsole.bat"
	File "stopYACY.bat"
	#File "httpProxy.black"   ##not included
	#File "httpProxy.command" ##Apple
	File "yacy.init"
	#File "httpProxy.sh"      ##UNIX
	File "yacy.yellow"
	
	#texts
	File "readme.txt"
	File "gpl.txt"
	File "superseed.txt"
	File "yacy.stopwords"
	
	#classes
	SetOutPath "$INSTDIR\classes"
	File /r "classes\*"

	#htroot non devel
	SetOutPath "$INSTDIR\htroot"
	File "htroot\*.html"
	File "htroot\*.xml"
	File "htroot\*.class"

	#yacy non-devel
	SetOutPath "$INSTDIR\htroot\yacy"
	File "htroot\yacy\*.html"
	File "htroot\yacy\*.class"

	#proxymsg non-devel
	SetOutPath "$INSTDIR\htroot\proxymsg"
	File "htroot\proxymsg\*.html"

	#templates
	SetOutPath "$INSTDIR\htroot\env"
	File /r "htroot\env\*"

	#htdocs default
	SetOutPath "$INSTDIR\htroot\htdocsdefault"
	File "htroot\htdocsdefault\*.html"
	File "htroot\htdocsdefault\*.class"

	SetOutPath $INSTDIR



	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YACY" "DisplayName" "YACY"
	WriteRegStr HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YACY" "UninstallString" '"$INSTDIR\uninstall.exe"'
	WriteUninstaller "uninstall.exe"

SectionEnd

Section "Addons"
	SectionIn 1 2 3
	SetOutPath $INSTDIR\addon
	File /r "addon\*"

	SetOutPath $INSTDIR
SectionEnd

Section "Docs"
	SectionIn 2 3
	SetOutPath $INSTDIR\doc
	File /r "doc\*"

	SetOutPath $INSTDIR
SectionEnd

Section "Development"
	SectionIn 3
	SetOutPath $INSTDIR\source

	File /r "source\*"

	SetOutPath $INSTDIR
	#File "wishlist.txt"
	#File "compile.bat"
	
	SetOutPath "$INSTDIR\htroot"
	File "HTROOT\*.java"
	SetOutPath "$INSTDIR\htroot\yacy"
	File "HTROOT\yacy\*.java"
	SetOutPath "$INSTDIR\htroot\htdocsdefault"
	File "htroot\htdocsdefault\*.java"
SectionEnd

Section "Shortcuts in the Start Menu"
	SectionIn 1 2 3
	SetOutPath "$INSTDIR"
	CreateDirectory "$SMPROGRAMS\YACY"
	CreateShortCut "$SMPROGRAMS\YACY\start YACY.pif" "$INSTDIR\startYACY.bat"
	CreateShortCut "$SMPROGRAMS\YACY\stop YACY.pif" "$INSTDIR\stopYACY.bat"
	CreateShortCut "$SMPROGRAMS\YACY\Readme.lnk" "$INSTDIR\readme.txt"
	CreateShortCut "$SMPROGRAMS\YACY\Uninstall.lnk" "$INSTDIR\Uninstall.exe"
	SetOutPath "$SMPROGRAMS\YACY"
	File "addon\YACY-Search.url"
	SetOutPath "$INSTDIR"
SectionEnd

#Section "YACY on the Desktop"
#	SectionIn 1 2 3
#	SetOutPath "$INSTDIR"
#	CreateShortCut "$DESKTOP\start YACY.lnk" ""
#SectionEnd

Section "YACY-Console on the Desktop"
	SectionIn 1 2 3
	SetOutPath "$INSTDIR"
	CreateShortCut "$DESKTOP\YACY-Console.pif" "$INSTDIR\startYACY.bat"
SectionEnd

Section "Searchpage in the Quicklaunch"
	SectionIn 1 2 3
	SetOutPath $QUICKLAUNCH
	File "addon\YACY-Search.url"
	SetOutPath $INSTDIR
SectionEnd

Section "Searchpage on the Desktop"
	SetOutPath $DESKTOP
	File "addon\YACY-Search.url"
	SetOutPath $INSTDIR
SectionEnd

Section "Proxy-Console in Startup"
	SetOutPath "$INSTDIR"
	CreateShortCut "$SMSTARTUP\YACY-Console.pif" "$INSTDIR\startYACY.bat"
SectionEnd

Section "Uninstall"
	DeleteRegKey HKLM "Software\Microsoft\Windows\CurrentVersion\Uninstall\YACY"

	RMDir /r "$INSTDIR\classes"
	RMDir /r "$INSTDIR\doc"
	RMDir /r "$INSTDIR\htroot"
	RMDir /r "$INSTDIR\source"
	RMDir /r "$INSTDIR\addon"
	Delete "$INSTDIR\*.*"

	MessageBox MB_YESNO|MB_ICONQUESTION "Do you want to keep the Data?" IDYES keepdata
	
	#delete all
	RMDir /r "$INSTDIR"
	
	#or jump to this
	keepdata:
	RMDir /r "$SMPROGRAMS\YACY"
	Delete "$QUICKLAUNCH\YACY-Search.url"
	Delete "$DESKTOP\YACY-Search.url"
	Delete "$DESKTOP\YACY-Console.pif"
	Delete "$SMSTARTUP\YACY-Console.pif"
SectionEnd
