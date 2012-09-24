#!/bin/bash
cd "`dirname $0`"
./apicall.sh "/IndexControlURLs_p.xml?indexrestore=&dumpfile=$1" > /dev/null
