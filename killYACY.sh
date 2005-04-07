#!/bin/sh

# THIS IS ONLY FOR EMERGENCY CASES
# To stop YaCy, use stopYACY.sh

cd `dirname $0`
PID=`fuser yacy.log | awk '{print $2}'`
echo "process-id is " $PID
kill -3 $PID
sleep 1
kill -9 $PID
echo "killed pid " $PID ", YaCy terminated"

