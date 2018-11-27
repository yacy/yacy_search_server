#!/usr/bin/env sh
# Check that a yacy configuration file exists under the DATA folder set by the variable YACY_DATA_PATH
# Exits with an error message and status 2 when the file is not found or is not a regular file

if [ ! -f "$YACY_DATA_PATH/SETTINGS/yacy.conf" ]; then
	echo "No YaCy configuration file found at $YACY_DATA_PATH/SETTINGS/yacy.conf"
	exit 2
fi