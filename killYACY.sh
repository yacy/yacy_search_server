#!/usr/bin/env sh

# THIS IS ONLY FOR EMERGENCY CASES
# To stop YaCy, use stopYACY.sh

PROGRAMDIR="`dirname $0`/"
DATADIR="${PROGRAMDIR}DATA/"
PID=`fuser ${DATADIR}LOG/yacy00.log.lck | awk '{print $1}'`
echo "process-id is " $PID
kill -3 $PID
kill -9 $PID
PID=`fuser ${DATADIR}LOG/yacy00.log | awk '{print $1}'`
echo "process-id is " $PID
kill -3 $PID
kill -9 $PID
rm -f ${DATADIR}yacy.running
echo "killed pid " $PID ", YaCy terminated"