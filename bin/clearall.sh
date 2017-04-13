#!/usr/bin/env sh
cd "`dirname $0`"
./protectedPostApiCall.sh "IndexControlURLs_p.html" "deletecomplete=&deleteIndex=on&deleteSolr=on&deleteCrawlQueues=on&deleteRobots=on&deleteSearchFl=on&deleteCache=on"