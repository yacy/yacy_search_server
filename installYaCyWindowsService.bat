@Echo Off
title YaCy Windows Service Install

:STARTJAVA
REM set the Java options
set javaopts=-Xss256k;-XX:MaxPermSize=256m;-Djava.net.preferIPv4Stack=true;-Djava.awt.headless=true;-Dfile.encoding=UTF-8

REM set max Java heap memory (in MB)
set jmx=800
set jms=180
set servicedesc="P2P SearchEngine"

Rem This target is used to read java runtime parameters out of the yacy config file
:GETSTARTOPTS
REM for /F "tokens=1,2 delims==" %%i in (DATA\SETTINGS\yacy.conf) do (
REM 	if "%%i"=="javastart_Xmx" set jmx=%%j
REM 	if "%%i"=="javastart_Xms" set jms=%%j
REM )

Rem choose service runner executable according to processor architecture
set exepath=addon\windowsService
if /I "%PROCESSOR_ARCHITECTURE%"=="AMD64" set exepath=addon\windowsService\amd64
if /I "%PROCESSOR_ARCHITECTURE%"=="IA64" set exepath=addon\windowsService\ia64

Echo JRE Parameters:%javacmd%
Echo Startpath %~dp0

REM Install YaCy as Windows Service
%exepath%\prunsrv.exe //IS//YaCy --Jvm=auto --StartMode=jvm --StartClass=net.yacy.yacy --Classpath=htroot;lib/yacycore.jar --StartPath=%~dp0 --JvmOptions=%javaopts% --Startup=auto --JvmMx=%jmx% --JvmMs=%jms% --StopMode=jvm --StopClass=net.yacy.yacy --StopParams=-shutdown --Description=%servicedesc%

if not errorlevel 1 goto installed
Echo Failed installing YaCy service
Echo maybe it is already installed 
Echo opening the service manager to edit the settings now.

addon\windowsService\prunmgr.exe //ES//YaCy
goto end

:installed
REM Start Service manager to check/edit YaCy Service settings
REM start addon\windowsService\prunmgr.exe //ES//YaCy

Echo start the YaCy service
%exepath%\prunsrv.exe //ES//YaCy

Echo wait some seconds for YaCy to startup
timeout /T 10
explorer http://localhost:8090/

:end