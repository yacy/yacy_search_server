#!/usr/bin/env sh
# Shutdown a running YaCy server through API call
#
# $YACY_DATA_PATH : path to the YaCy DATA folder to use. When not set, the relative ./DATA path is used as a default.

cd `dirname $0`

if [ -z "$YACY_DATA_PATH" ]; then
	YACY_DATA_PATH="DATA"
fi

if [ ! -d "$YACY_DATA_PATH" ]; then
	echo "Invalid YaCy DATA folder path : $YACY_DATA_PATH"
	echo "Please fill the YACY_DATA_PATH environment variable with a valid YaCy DATA folder path."
	exit 2
fi

if [ ! -f "$YACY_DATA_PATH/yacy.running" ]; then
	echo "No YaCy server appears to be running on DATA folder at : $YACY_DATA_PATH"
	exit 1
fi

(bin/apicall.sh "Steering.html?shutdown=true" > /dev/null && \
echo "Please wait until the YaCy daemon process terminates [wget]" && \
echo "You can monitor this with 'tail -F $YACY_DATA_PATH/LOG/yacy00.log' or 'fuser $YACY_DATA_PATH/LOG/yacy00.log'") || \
exit $?

# wait until the yacy.running file disappears which means that YaCy has terminated
# If you don't want to wait, just run this concurrently

echo "WAITING FOR STOP"
while [ -f "$YACY_DATA_PATH/yacy.running" ]
do
	echo -n "."
sleep 1
done
