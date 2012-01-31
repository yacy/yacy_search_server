#!/bin/bash
cd "`dirname $0`"
port=$(grep ^port= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
pw=$(grep ^adminAccountBase64MD5= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)

if which curl &>/dev/null; then
  curl -s --header "Authorization: realm=$pw" "http://127.0.0.1:$port/$1"
elif which wget &>/dev/null; then
  wget -q -t 1 --timeout=5 --header "Authorization: realm=$pw" "http://127.0.0.1:$port/$1"
else
  exit 1
fi
