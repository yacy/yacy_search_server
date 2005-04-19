@echo off

REM Script contributed by Roland Ramthun.

REM This script starts YaCy without any visible window and should work on all Windows-Operating-Systems which know the "start" command.
REM You could verify this by typing "HELP" into cmd.exe, which lists all commands supported by your specific OS.

start /B javaw -classpath classes:lib/commons-collections.jar:lib/commons-pool-1.2.jar yacy
exit