#!/bin/bash
cd "`dirname $0`"
./apicall.sh "/IndexControlRWIs_p.html?deletecomplete=&deleteIndex=on&deleteCrawlQueues=on&deleteCache=on&deleteRobots=on&deleteSearchFl=on"
