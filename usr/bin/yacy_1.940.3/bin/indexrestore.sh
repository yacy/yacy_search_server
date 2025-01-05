#!/usr/bin/env sh
cd "`dirname $0`"
./apicall.sh "/IndexExport_p.html?indexrestore=&dumpfile=$1" > /dev/null
