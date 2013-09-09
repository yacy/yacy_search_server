#!/bin/bash
cd "`dirname $0`"
port=$(grep ^port= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
./search1.sh -y localhost:$port "$1"