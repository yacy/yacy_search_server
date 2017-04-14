#!/usr/bin/env sh
cd "`dirname $0`"
./protectedPostApiCall.sh "IndexImportMediawiki_p.html" "file=$1"
