#!/bin/bash
cd "`dirname $0`"
port=$(grep ^port= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
./searchall1.sh -s localhost:$port $1