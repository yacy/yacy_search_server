#!/bin/bash
cd "`dirname $0`"
./apicall.sh "/Load_RSS_p.html?indexAllItemContent=&repeat=off&url=$1" > /dev/null
