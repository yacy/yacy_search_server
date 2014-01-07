#!/bin/bash
cd "`dirname $0`"
port=$(grep ^port= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
admin=$(grep ^adminAccountUserName= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
pw=$(grep ^adminAccountBase64MD5= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)

if which curl &>/dev/null; then
  curl -s -u $admin:$pw "http://127.0.0.1:$port/$1"
elif which wget &>/dev/null; then
  wget -q -t 1 --timeout=120 --http-user $admin --http-password $pw "http://127.0.0.1:$port/$1" -O -
else
  exit 1
fi

