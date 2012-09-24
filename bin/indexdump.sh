#!/bin/bash
cd "`dirname $0`"
./apicall0.sh "/IndexControlURLs_p.xml?indexdump=" | awk '/<dumpfile>/{ gsub("<dumpfile>","" );gsub("<\/dumpfile>","" ); print $0 }' | awk '{print $1}';