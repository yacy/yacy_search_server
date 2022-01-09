#!/usr/bin/env sh
cd "`dirname $0`"
. ./checkDataFolder.sh
. ./checkConfFile.sh

port=$(grep ^port= "$YACY_DATA_PATH/SETTINGS/yacy.conf" |cut -d= -f2)
./search1.sh -y localhost:$port "$1"