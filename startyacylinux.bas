Dim result As String


'Print "adding temp path"
'Shell Chr$(80) + Chr$(65) + Chr$(84) + Chr$(72) + Chr$(61) + Chr$(34) + Chr$(36) + Chr$(40) + Chr$(112) + Chr$(119) + Chr$(100) + Chr$(41) + Chr$(58) + Chr$(36) + Chr$(80) + Chr$(65) + Chr$(84) + Chr$(72) + Chr$(34)
'Shell "./addpath.sh"
_Delay 1
'PATH="$(pwd):$PATH"
'Shell "sudo apt update"
'Print "getting java"
'Shell "sudo apt install -y openjdk-11-jdk"
'_Delay 1
Print "Starting yacy "
Shell "echo Hello from QB64"
Shell "./startYACY.sh"
'Shell "./YaCy_1.940.2-x86_64.AppImage --appimage-extract-and-run"
Print "Yacy Started"
_Delay 10
System

