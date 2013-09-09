#!/bin/bash
cd "`dirname $0`"
./apicall.sh "/IndexControlURLs_p.html?deletecomplete=&deleteIndex=on&deleteSolr=on&deleteCrawlQueues=on&deleteRobots=on&deleteSearchFl=on&deleteCache=on" > /dev/null