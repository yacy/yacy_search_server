#!/usr/bin/env sh
cd "`dirname $0`"
. ./checkDataFolder.sh
. ./checkConfFile.sh

port=$(grep ^port= "$YACY_DATA_PATH/SETTINGS/yacy.conf" |cut -d= -f2)
while [ 1 = 1 ]
do
curl "http://localhost:$port/NetworkPicture.png?width=768&height=576&bgcolor=FFFFFF" > /dev/null
done
