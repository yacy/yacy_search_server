@echo off

REM startYACY_hidden.bat 
REM -----------------------
REM part of YaCy (C) by Michael Peter Christen
REM this file provided by Roland Ramthun
REM Essen, 2005
REM last major change: 09.04.2005

REM This script starts YaCy without any visible window and should work on all Windows-Operating-Systems which know REM the "start" command.
REM You could verify this by typing "HELP" into cmd.exe, which lists all commands supported by your specific 
REM Windows version.

start /B javaw -classpath classes:lib/commons-collections.jar:lib/commons-pool-1.2.jar yacy
exit