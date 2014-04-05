#!/bin/sh

# THIS IS ONLY FOR EMERGENCY CASES
# To stop YaCy, use stopYACY.sh

cd `dirname $0`
PID=`fuser DATA/LOG/yacy00.log.lck | awk '{print $1}'`
echo "process-id is " $PID
kill -3 $PID
kill -9 $PID
PID=`fuser DATA/LOG/yacy00.log | awk '{print $1}'`
echo "process-id is " $PID
kill -3 $PID
kill -9 $PID
rm -f DATA/yacy.running
echo "killed pid " $PID ", YaCy terminated"