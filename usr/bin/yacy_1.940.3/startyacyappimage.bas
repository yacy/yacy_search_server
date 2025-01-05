Dim result As String


Print "adding temp path"
Shell "./addpath.sh"
_Delay 1
Shell "sudo apt update"
Print "getting java"
Shell "sudo apt install -y openjdk-11-jdk"
_Delay 1
Print "Starting yacy "
Shell "echo Hello from QB64"
Shell "./startYACY.sh"
Shell "./YaCy_1.940.2-x86_64.AppImage --appimage-extract-and-run"
Print "Yacy Started"
_Delay 10
System

