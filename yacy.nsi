;yacy.nsi
;--------
;part of YaCy (C) by Michael Peter Christen
;this file is contributed by Alexander Schier
;Cologne, 2005
;last major change: 22.07.2005
Name "YaCy"

OutFile "yacy_v0.44_20060307_1842.exe"
InstallDir $PROGRAMFILES\YaCy

SetCompress auto
SetCompressor bzip2

InstType /CUSTOMSTRING=Custom

InstType "Minimal"
InstType "Normal"
InstType "Full"

; The text to prompt the user to enter a directory
ComponentText "This will install YaCy v0.44(Build 20060307) on your computer. Select which optional things you want to be installed."
; The text to prompt the user to enter a directory
#DirText "If an old Version was installed into another locAtion(eg. AnomicHTTPProxy), you have to move the DATA Directory to the new location."
DirText "Choose a directory to install into:"

LicenseText "You must agree the license to install YaCy"
LicenseData "gpl.txt"

Section "Binaries (required)"
	SectionIn 1 2 3 RO
	SetOutPath $INSTDIR
	#main files
	File "httpd.mime"
	File "startYACY.bat"
	File "startYACY_Win9x.bat"
	File "startYACY_noconsole_Win9x.bat"
	File "startYACY_noconsole.bat"
	File "stopYACY.bat"
	File "stopYACY_Win9x.bat"
	#File "httpProxy.command" ##Apple
	File "yacy.init"
	#File "httpProxy.sh"      ##UNIX
	File "yacy.yellow"
	File "yacy.stopwords"
	File "yacy.badwords.example"
	
	#texts
	File "readme.txt"
	File "gpl.txt"
	File "superseed.txt"
	File "yacy.stopwords"
	File "yacy.logging"
	File "ChangeLog"
	File "COPYRIGHT"
	File "yacy.stopwords.de"
	
	#lib
	SetOutPath "$INSTDIR\lib"
	File /r "lib\*"

	#classes
	SetOutPath "$INSTDIR\classes"
	File /r "classes\*"

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

	#yacy xml
    #TODO: Split in source/binary
	SetOutPath "$INSTDIR\htroot\xml"
	File /r "htroot\xml\*"

	#yacy/seedUpload
	SetOutPath "$INSTDIR\htroot\yacy\seedUpload"
	File "htroot\yacy\seedUpload\*.html"

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

	SetOutPath "$INSTDIR\doc"
	File "doc\This_is_a_test_if_the_archive_file_containing_YaCy_was_unpacked_correctly_If_not_please_use_gnu_tar_instead.txt"

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
	File "build.xml"
	File "build.properties"
	
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
	CreateShortCut "$SMPROGRAMS\YACY\start YACY(no console).pif" "$INSTDIR\startYACY_noconsole.bat"
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

Section "YaCy-Console on the Desktop"
	SectionIn 1 2 3
	SetOutPath "$INSTDIR"
	CreateShortCut "$DESKTOP\YaCy-Console.pif" "$INSTDIR\startYACY.bat"
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
	RMDir /r "$INSTDIR\lib"
	RMDir /r "$INSTDIR\doc"
	RMDir /r "$INSTDIR\htroot"
	RMDir /r "$INSTDIR\locales"
	RMDir /r "$INSTDIR\source"
	RMDir /r "$INSTDIR\addon"
	Delete "$INSTDIR\*.*"

	MessageBox MB_YESNO|MB_ICONQUESTION "Do you want to keep the Data (i.e. if you want to reinstall later)?" IDYES keepdata
	
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
