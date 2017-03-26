#!/usr/bin/env sh
cd "`dirname $0`"
./protectedPostApiCall.sh "IndexControlURLs_p.html" "deleteIndex=off&deleteSolr=off&deleteCache=on&deleteCrawlQueues=off&deleteRobots=on&deleteSearchFl=on&deletecomplete="
