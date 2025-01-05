@echo off
echo This can cause high system load.
echo To abort press CTRL+C.
echo ***
pause  
java -cp lib\yacycore.jar net.yacy.kelondro.util.OS -m
pause