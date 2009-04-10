#!/bin/bash
cd "`dirname $0`"
port=$(grep ^port= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
if which curl &>/dev/null; then
  curl -s "http://localhost:$port/Network.xml?page=2&ip=" | awk '/<address>/{ gsub("<address>","" );gsub("<\/address>","" ); print $0 }' | awk '{print $1}'
elif which wget &>/dev/null; then
  wget -q -O - "http://localhost:$port/Network.xml?page=2&ip=" | awk '/<address>/{ gsub("<address>","" );gsub("<\/address>","" ); print $0 }' | awk '{print $1}'
else
  exit 1
fi

