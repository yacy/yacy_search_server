#!/bin/bash
cd "`dirname $0`"

# for a production environment with high-availability requirement,
# (and if you are using the debian version of yacy)
# add the following line in /etc/crontab
# 0 *	* * *	root    cd /usr/share/yacy/bin && ./checkalive.sh

FLAG=0
if [ `./apicall.sh /Status.html | grep "</html>"` ]; then
  FLAG=1
fi

if [ $FLAG -eq '0' ]; then
    cd ..
    timeout 30s ./stopYACY.sh
    ./killYACY.sh
    rm DATA/yacy.running
    ./startYACY.sh
fi
exit