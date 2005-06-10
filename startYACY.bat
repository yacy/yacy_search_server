@Echo Off
If %1.==CPGEN. GoTo :CPGEN

Rem Generating the proper classpath unsing loops and labels
Set CLASSPATH=classes;htroot
For %%X in (lib/*.jar) Do Call %0 CPGEN lib\%%X
For %%X in (libx/*.jar) Do Call %0 CPGEN libx\%%X

Rem Starting yacy
Echo Generated Classpath:%CLASSPATH%
java -classpath %CLASSPATH% yacy

GoTo :END

Rem This target is used to concatenate the classpath parts 
:CPGEN
Set CLASSPATH=%CLASSPATH%;%2

Rem Target needed to jump to the end of the file
:END


