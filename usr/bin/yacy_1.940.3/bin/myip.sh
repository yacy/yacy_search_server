#!/usr/bin/env sh
cd "`dirname $0`"
./apicall.sh "/yacy/seedlist.xml?my=" | awk '/<IP>/{ gsub("<IP>","" );gsub("<\/IP>","" ); print $0 }' | awk '{print $1}';
