@Echo Off
title YaCy

REM setting startup type for proper restart
if "%YACY_DATA%"=="" set "YACY_DATA=%LOCALAPPDATA%\YaCy"
set "YACY_DATA_DIR=%YACY_DATA%\DATA"
if not exist "%YACY_DATA_DIR%" md "%YACY_DATA_DIR%"
echo . >"%YACY_DATA_DIR%\yacy.noconsole"

Rem Setting the classpath
Set CLASSPATH=lib\yacycore.jar

REM Please change the "javastart" settings in the web-interface "Basic Configuration" -> "Advanced" 
set jmx=
set jms=
set javacmd=-Xmx600m
set priolvl=10
set priority=/BELOWNORMAL
if exist "%YACY_DATA_DIR%\SETTINGS\httpProxy.conf" GoTo :RENAMEINDEX
if exist "%YACY_DATA_DIR%\SETTINGS\yacy.conf" GoTo :GETSTARTOPTS

:STARTJAVA
set javacmd=%javacmd% -Djava.awt.headless=true -Dsolr.directoryFactory=solr.MMapDirectoryFactory -Dfile.encoding=UTF-8

Rem Starting YaCy
Echo Generated classpath:%CLASSPATH%
Echo JRE Parameters:%javacmd%
Echo Priority:%priority%
Echo ***************************************************************************
Echo.
Echo If you see a message like "java" not found, you probably have to install Java.
Echo.
Echo You can download Java at http://java.com/
Echo.
Echo ***************************************************************************
Rem commandline parameter added for -config option, like -config "port=8090" "adminAccount=admin:password" 
Rem special parameter "adminAccount=admin:password" calculates and sets new admin-pwd
Rem any parameter in yacy.conf can me modified this way (make sure to use correct upper/lower case)

start %priority% java %javacmd% -Dyacy.data="%YACY_DATA%" -classpath %CLASSPATH% net.yacy.yacy %1 %2 %3 %4 %5 %6 %7 %8 %9

Echo You can close the console safely now.

GoTo :END

Rem PUBLIC is now freeworld (r4575)
:RENAMEINDEX
for /F "usebackq tokens=1,2 delims==" %%i in ("%YACY_DATA_DIR%\SETTINGS\httpProxy.conf") do (
    if "%%i"=="network.unit.name" set networkname=%%j
)
if not defined networkname set networkname=PUBLIC
pushd "%YACY_DATA_DIR%\INDEX"
ren PUBLIC %networkname%
popd

Rem This target is used to read java runtime parameters out of the yacy config file
:GETSTARTOPTS
for /F "usebackq tokens=1,2 delims==" %%i in ("%YACY_DATA_DIR%\SETTINGS\yacy.conf") do (
	if "%%i"=="javastart_Xmx" set jmx=%%j
	if "%%i"=="javastart_priority" set priolvl=%%j
)
if defined jmx set javacmd=-%jmx%
if defined jms set javacmd=-%jms% %javacmd%
if defined priolvl (
	if %priolvl% == 20 set priority=/LOW
	if %priolvl% == 10 set priority=/BELOWNORMAL
)

GoTo :STARTJAVA

Rem Target needed to jump to the end of the file
:END
