#!/bin/bash
cd "`dirname $0`"
./apicall.sh "/IndexControlURLs_p.html?urlhashdeleteall=&urlstring=$1" > /dev/null
