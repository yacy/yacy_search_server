#!/usr/bin/env sh
cd "`dirname $0`"
./protectedPostApiCall.sh "IndexControlURLs_p.html" "urlhashdeleteall=&urlstring=$1"
