#!/usr/bin/env sh
# Perform a search on the Solr index of each active principal and senior peers known by the local peer
# $1 search term

cd "`dirname $0`"
. ./checkDataFolder.sh
. ./checkConfFile.sh

port=$(grep ^port= "$YACY_DATA_PATH/SETTINGS/yacy.conf" |cut -d= -f2)
./searchall1.sh "localhost:$port" "$1"