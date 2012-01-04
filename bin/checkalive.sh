#!/bin/bash

# add in /etc/crontab
# 0 *	* * *	yacy    cd /home/yacy/production/bin && ./checkalive.sh

RESULT=`wget --spider http://localhost:8090/Status.html 2>&1`
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
