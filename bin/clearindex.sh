#!/bin/bash
cd "`dirname $0`"
./apicall.sh "/IndexControlRWIs_p.html?deleteIndex=on&deleteSolr=on&deleteCache=off&deleteCrawlQueues=on&deleteRobots=on&deleteSearchFl=on&deletecomplete=" > /dev/null
