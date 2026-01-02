@Echo Off
title YaCy

Rem Setting the classpath
Set CLASSPATH=lib\yacycore.jar

Rem Stopping yacy
Echo Generated Classpath:%CLASSPATH%
if "%YACY_DATA%"=="" set "YACY_DATA=%LOCALAPPDATA%\YaCy"
java -Dyacy.data="%YACY_DATA%" -classpath %CLASSPATH% net.yacy.yacy -shutdown "%YACY_DATA%"
