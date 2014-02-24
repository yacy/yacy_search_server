@Echo Off
title YaCy

if exist DATA\yacy.noconsole del DATA\yacy.noconsole

Rem Generating the proper classpath unsing loops and labels
Set CLASSPATH=lib/yacycore.jar;htroot

REM Please change the "javastart" settings in the web-interface "Basic Configuration" -> "Advanced" 
set jmx=
set jms=
set javacmd=-Xmx600m -Xms180m
set priolvl=10
set priority=/BELOWNORMAL
set port=8090
if exist DATA\SETTINGS\httpProxy.conf GoTo :RENAMEINDEX
if exist DATA\SETTINGS\yacy.conf GoTo :GETSTARTOPTS

:STARTJAVA
set javacmd=%javacmd% -XX:-UseGCOverheadLimit -Djava.net.preferIPv4Stack=true -Djava.awt.headless=true -Dfile.encoding=UTF-8
Rem Starting YaCy
Echo Generated classpath:%CLASSPATH%
Echo JRE Parameters:%javacmd%
Echo Priority:%priority%

Echo ****************** YaCy Web Crawler/Indexer ^& Search Engine ******************
Echo **** (C) by Michael Peter Christen, usage granted under the GPL Version 2  ****
Echo ****   USE AT YOUR OWN RISK! Project home and releases: http://yacy.net/   ****
Echo **  LOG of       YaCy: DATA/LOG/yacy00.log (and yacy^<xx^>.log)              **
Echo **  STOP         YaCy: execute stopYACY.bat and wait some seconds            **
Echo **  GET HELP for YaCy: see www.yacy-websearch.net/wiki and forum.yacy.de     **
Echo *******************************************************************************
Echo  ^>^> YaCy started as daemon process. Administration at http://localhost:%port% ^<^<

title YaCy - http://localhost:%port%

Rem commandline parameter added for -config option, like -config "port=8090" "adminAccount=admin:password" 
Rem special parameter "adminAccount=admin:password" calculates and sets new admin-pwd
Rem any parameter in yacy.conf can me modified this way (make sure to use correct upper/lower case)

start "YaCy" %priority% /B /WAIT java %javacmd% -classpath %CLASSPATH% net.yacy.yacy %1 %2 %3 %4 %5 %6 %7 %8 %9

if not exist DATA\yacy.restart GoTo :END
del DATA\yacy.restart
GoTo :GETSTARTOPTS

Rem PUBLIC is now freeworld (r4575)
:RENAMEINDEX
for /F "tokens=1,2 delims==" %%i in (DATA\SETTINGS\httpProxy.conf) do (
    if "%%i"=="network.unit.name" set networkname=%%j
)
if not defined networkname set networkname=PUBLIC
cd DATA\INDEX
ren PUBLIC %networkname%
cd ..
cd ..

Rem This target is used to read java runtime parameters out of the yacy config file
:GETSTARTOPTS
for /F "tokens=1,2 delims==" %%i in (DATA\SETTINGS\yacy.conf) do (
    if "%%i"=="javastart_Xmx" set jmx=%%j
    if "%%i"=="javastart_Xms" set jms=%%j
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
