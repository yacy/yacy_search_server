#!/usr/bin/env sh
# Fill the YACY_BIN_PATH, YACY_APP_PATH and the YACY_DATA_PATH (when empty) relatively to the current working directory
# Then check that the provided YaCy DATA folder exists
# Exits with an error message and status 2 when the DATA folder is not found

YACY_BIN_PATH="`pwd`"
YACY_APP_PATH="`dirname $YACY_BIN_PATH`"

if [ -z "$YACY_DATA_PATH" ]; then
	YACY_DATA_PATH="$YACY_APP_PATH/DATA"
fi

if [ ! -d "$YACY_DATA_PATH" ]; then
	echo "Invalid YaCy DATA folder path : $YACY_DATA_PATH"
	echo "Please fill the YACY_DATA_PATH environment variable with a valid YaCy DATA folder path."
	exit 2
fi