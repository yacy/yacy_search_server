#!/usr/bin/env sh
cd "`dirname $0`"
. ./checkDataFolder.sh

port=$(grep ^port= "$YACY_DATA_PATH/SETTINGS/yacy.conf" |cut -d= -f2)
./searchall1.sh -s localhost:$port $1