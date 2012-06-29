#!/bin/bash
cd "`dirname $0`"
./apicall.sh "/IndexControlRWIs_p.html?deletecomplete=&deleteIndex=on&deleteSolr=on&deleteCrawlQueues=on&deleteRobots=on&deleteSearchFl=on&deleteCache=off" > /dev/null
