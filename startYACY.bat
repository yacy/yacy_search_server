@Echo Off
title YaCy

If %1.==CPGEN. GoTo :CPGEN

Rem Generating the proper classpath unsing loops and labels
Set CLASSPATH=classes;htroot
For %%X in (lib/*.jar) Do Call %0 CPGEN lib\%%X
For %%X in (libx/*.jar) Do Call %0 CPGEN libx\%%X

set jmx=
set jms=
set javacmd=-Xmx64m -Xms10m
if exist DATA\SETTINGS\httpProxy.conf GoTo :GETJAVACMD

:STARTJAVA
Rem Starting YaCy
Echo Generated classpath:%CLASSPATH%
Echo JRE Parameters:%javacmd%

	Echo ****************** YaCy Web Crawler/Indexer ^& Search Engine *******************
	Echo **** (C) by Michael Peter Christen, usage granted unter the GPL Version 2  ****
	Echo **** USE AT YOUR OWN RISK! Project home and releases: http://yacy.net/yacy   **
	Echo **  LOG of       YaCy: DATA/LOG/yacy00.log (and yacy^<xx^>.log)                **
	Echo **  STOP         YaCy: execute stopYACY.sh and wait some seconds             **
	Echo **  GET HELP for YaCy: see www.yacy-websearch.net/wiki and www.yacy-forum.de **
	Echo *******************************************************************************
	Echo  ^>^> YaCy started as daemon process. Administration at http://localhost:8080 ^<^<
    
java %javacmd% -classpath %CLASSPATH% yacy

GoTo :END

Rem This target is used to read java runtime parameters out of the yacy config file
:GETJAVACMD
for /F "tokens=1,2 delims==" %%i in (DATA\SETTINGS\httpProxy.conf) do (
	if "%%i"=="javastart_Xmx" set jmx=%%j
	if "%%i"=="javastart_Xms" set jms=%%j
)
if defined jmx set javacmd=-%jmx%
if defined jms set javacmd=-%jms% %javacmd%

GoTo :STARTJAVA

Rem This target is used to concatenate the classpath parts 
:CPGEN
Set CLASSPATH=%CLASSPATH%;%2

Rem Target needed to jump to the end of the file
:END


