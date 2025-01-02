Rem
Rem 2-1-2025
Rem installer for yacy windows

Shell "copy /b java17_0 + java17_1 + java17_2 java17.msi"
Print "Joining files"
_Delay 5

Shell "start /w java17.msi /norestart"
Print "Installing java 17"
Rem _delay 140
_Delay 10
Shell "refresh.cmd"
Print "refreshing path"
_Delay 10
Shell "startYACYWin.cmd"
Print "starting yacy 1.940"
_Delay 20
Stop


