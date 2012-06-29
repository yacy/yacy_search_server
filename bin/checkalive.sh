#!/bin/bash

# for a production environment with high-availability requirement,
# (and if you are using the debian version of yacy)
# add the following line in /etc/crontab
# 0 *	* * *	root    cd /usr/share/yacy/bin && ./checkalive.sh

port=$(grep ^port= ../DATA/SETTINGS/yacy.conf |cut -d= -f2)
RESULT=`wget -t 1 --spider http://localhost:$port/Status.html 2>&1`
FLAG=0

for x in $RESULT; do
    if [ "$x" = '200' ]; then
        FLAG=1
    fi
done

if [ $FLAG -eq '0' ]; then
    cd ..
    timeout 30s ./stopYACY.sh
    ./killYACY.sh
    rm DATA/yacy.running
    ./startYACY.sh
fi

exit
