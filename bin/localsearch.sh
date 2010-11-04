#!/bin/bash
cd "`dirname $0`"
port=$(grep ^port= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
if which curl &>/dev/null; then
  curl -s "http://localhost:$port/suggest.json?resource=local&verify=false&query=$1"
elif which wget &>/dev/null; then
  wget -q -O - "http://localhost:$port/suggest.json?resource=local&verify=false&query=$1"
else
  echo "Neither curl nor wget installed!"
  exit 1
fi

