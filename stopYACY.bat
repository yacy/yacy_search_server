@Echo Off
title YaCy

Rem Setting the classpath
Set CLASSPATH=lib\yacycore.jar
Rem Check core jar exists to avoid failing silently later
if not exist lib/yacycore.jar (
	Echo "Error : lib/yacycore.jar was not found! Please first build from sources using Apache Ant."
	Exit /b 1
)

Rem Stopping yacy
Echo Generated Classpath:%CLASSPATH%
java -classpath %CLASSPATH% net.yacy.yacy -shutdown

