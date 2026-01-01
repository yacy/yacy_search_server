@Echo Off
title YaCy

if "%YACY_DATA%"=="" set "YACY_DATA=%LOCALAPPDATA%\YaCy"
set "YACY_DATA_DIR=%YACY_DATA%\DATA"
if not exist "%YACY_DATA_DIR%" md "%YACY_DATA_DIR%"
if exist "%YACY_DATA_DIR%\yacy.noconsole" del "%YACY_DATA_DIR%\yacy.noconsole"

Rem Generating the proper classpath unsing loops and labels
Set CLASSPATH=lib/yacycore.jar

REM Please change the "javastart" settings in the web-interface "Basic Configuration" -> "Advanced" 
set jmx=
set jms=
set javacmd=-Xmx600m
set priolvl=10
set priority=/BELOWNORMAL
set port=8090
if exist "%YACY_DATA_DIR%\SETTINGS\httpProxy.conf" GoTo :RENAMEINDEX
if exist "%YACY_DATA_DIR%\SETTINGS\yacy.conf" GoTo :GETSTARTOPTS

:STARTJAVA
set javacmd=%javacmd% -XX:-UseGCOverheadLimit -Djava.awt.headless=true -Dfile.encoding=UTF-8

Rem Starting YaCy
Echo Generated classpath:%CLASSPATH%
Echo JRE Parameters:%javacmd%
Echo Priority:%priority%

Echo ****************** YaCy Web Crawler/Indexer ^& Search Engine ******************
Echo **** (C) by Michael Peter Christen, usage granted under the GPL Version 2  ****
Echo ****   USE AT YOUR OWN RISK! Project home and releases: https://yacy.net/  ****
Echo **  LOG of       YaCy: %YACY_DATA_DIR%/LOG/yacy00.log (and yacy^<xx^>.log)              **
Echo **  STOP         YaCy: execute stopYACY.bat and wait some seconds            **
Echo **  GET HELP for YaCy: join our community at https://community.searchlab.eu  **
Echo *******************************************************************************
Echo  ^>^> YaCy started as daemon process. Administration at http://localhost:%port% ^<^<

title YaCy - http://localhost:%port%

Rem commandline parameter added for -config option, like -config "port=8090" "adminAccount=admin:password" 
Rem special parameter "adminAccount=admin:password" calculates and sets new admin-pwd
Rem any parameter in yacy.conf can me modified this way (make sure to use correct upper/lower case)

start "YaCy" %priority% /B /WAIT java %javacmd% -Dyacy.data="%YACY_DATA%" -classpath %CLASSPATH% net.yacy.yacy %1 %2 %3 %4 %5 %6 %7 %8 %9

if not exist "%YACY_DATA_DIR%\yacy.restart" GoTo :END
del "%YACY_DATA_DIR%\yacy.restart"
GoTo :GETSTARTOPTS

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
    if "%%i"=="port" set port=%%j
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
