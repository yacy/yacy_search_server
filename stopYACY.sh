#!/usr/bin/env sh
PROGRAMDIR="`dirname $0`/"
DATADIR="${PROGRAMDIR}DATA/"

${PROGRAMDIR}bin/apicall.sh "Steering.html?shutdown=true" > /dev/null

echo "Please wait until the YaCy daemon process terminates [wget]"
echo "You can monitor this with 'tail -f ${DATADIR}LOG/yacy00.log' and 'fuser ${DATADIR}LOG/yacy00.log'"

# wait until the yacy.running file disappears which means that YaCy has terminated
# If you don't want to wait, just run this concurrently
while [ -f "${DATADIR}yacy.running" ]
do
sleep 1
done
