#!/bin/bash
cd "`dirname $0`"
./apicall.sh "/IndexControlURLs_p.html?deleteIndex=off&deleteSolr=off&deleteCache=on&deleteCrawlQueues=off&deleteRobots=on&deleteSearchFl=on&deletecomplete=" > /dev/null
