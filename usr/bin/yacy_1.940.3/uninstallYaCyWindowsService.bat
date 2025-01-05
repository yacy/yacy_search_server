@Echo Off
title YaCy Windows Service UnInstall

Rem choose service runner executable according to processor architecture
set exepath=addon\windowsService
if /I "%PROCESSOR_ARCHITECTURE%"=="AMD64" set exepath=addon\windowsService\amd64
if /I "%PROCESSOR_ARCHITECTURE%"=="IA64" set exepath=addon\windowsService\ia64


REM UnInstall YaCy Windows Service
%exepath%\prunsrv.exe //DS//YaCy 


