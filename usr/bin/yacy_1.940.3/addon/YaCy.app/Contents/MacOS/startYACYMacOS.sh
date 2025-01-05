#!/usr/bin/env sh

# Launcher for YaCy in a MacOS bundle : 
# rely on the generic startYACY.sh, but specifies the user home relative path for YaCy data
# This data directory is set in conforming to OS X File System Programming Guide 
# see : https://developer.apple.com/library/ios/documentation/FileManagement/Conceptual/FileSystemProgrammingGuide/MacOSXDirectories/MacOSXDirectories.html

"`dirname $0`"/startYACY.sh -s 'Library/Application Support/net.yacy.YaCy'
