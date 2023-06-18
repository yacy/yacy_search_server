#!/usr/bin/env sh
# Restart a running YaCy server through API call
#
# typical use is a periodic restart once daily using "cron" unix utility
# 
# use crontab -e
# and something like:
# 0 3 * * * /path/to/script/restartYACY.sh
# to restart at 3 AM, every day
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

(bin/apicall.sh "Steering.html?restart=true" > /dev/null && \
echo "Please wait until the YaCy daemon process restarts [wget]" && \
echo "You can monitor this with 'tail -F $YACY_DATA_PATH/LOG/yacy00.log' and 'fuser $YACY_DATA_PATH/LOG/yacy00.log'") || \
exit $?

# wait until the yacy.running file disappears which means that YaCy has terminated
# If you don't want to wait, just run this concurrently
echo "WAITING"
while [ -f "$YACY_DATA_PATH/yacy.running" ]
do
	echo -n "."
sleep 1
done

sleep 2


echo "STARTING NOW"
while [ ! -f "$YACY_DATA_PATH/yacy.running" ]
do
	echo -n "."
sleep 1
done

