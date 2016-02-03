@Echo Off
title YaCy

Rem Setting the classpath
Set CLASSPATH=lib\yacycore.jar

Rem Stopping yacy
Echo Generated Classpath:%CLASSPATH%
java -classpath %CLASSPATH% net.yacy.yacy -shutdown

